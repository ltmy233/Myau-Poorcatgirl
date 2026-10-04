package myau.util;

import net.minecraft.client.gui.GuiPlayerTabOverlay;

public final class TabAnim {
    public static GuiPlayerTabOverlay overlay;

    private static final double ZETA = 0.62D;
    private static final double OMEGA = 22.0D;
    private static final double WD = OMEGA * Math.sqrt(1.0D - ZETA * ZETA);
    private static final long HIDE_NS = 170_000_000L;
    private static final long SETTLE_NS = 560_000_000L;
    private static final float SQUASH_MIN = 0.86F;
    private static final float SQUASH_MAX = 1.24F;

    private static float scaleY;
    private static float scaleX = 1.0F;
    private static float from;
    private static boolean showing;
    private static boolean settled = true;
    private static long startNs = System.nanoTime();

    private TabAnim() {
    }

    public static void update(boolean show) {
        long now = System.nanoTime();
        if (show != showing) {
            from = scaleY;
            showing = show;
            startNs = now;
            settled = false;
        }
        if (settled) {
            return;
        }
        if (showing) {
            double t = (now - startNs) / 1.0E9D;
            if ((now - startNs) >= SETTLE_NS) {
                scaleY = 1.0F;
                scaleX = 1.0F;
                settled = true;
                return;
            }
            double decay = Math.exp(-ZETA * OMEGA * t);
            double u = 1.0D - decay * (Math.cos(WD * t) + (ZETA * OMEGA / WD) * Math.sin(WD * t));
            scaleY = Math.max(0.0F, (float) (from + (1.0D - from) * u));
            scaleX = squash(scaleY);
        } else {
            double p = (double) (now - startNs) / (double) HIDE_NS;
            if (p >= 1.0D) {
                scaleY = 0.0F;
                scaleX = 1.0F;
                settled = true;
                return;
            }
            double e = p * p * p;
            scaleY = from * (float) (1.0D - e);
            scaleX = squash(scaleY);
        }
    }

    private static float squash(float sy) {
        float s = (float) (1.0D / Math.sqrt(Math.max(sy, 0.04D)));
        return Math.min(SQUASH_MAX, Math.max(SQUASH_MIN, s));
    }

    public static boolean settled() {
        return settled;
    }

    public static boolean isShowing() {
        return showing;
    }

    public static float getScaleY() {
        return scaleY;
    }

    public static float getScaleX() {
        return scaleX;
    }
}
