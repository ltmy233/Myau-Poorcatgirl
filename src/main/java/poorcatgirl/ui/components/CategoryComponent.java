package poorcatgirl.ui.components;

import poorcatgirl.module.Module;
import poorcatgirl.ui.Component;
import poorcatgirl.ui.Lang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class CategoryComponent {
    private static final int MAX_HEIGHT = 500;
    private static final float PANEL_RADIUS = 6.0F;
    private static final double SCROLL_RATE = Math.sqrt(340.0D);
    private static final int SCROLL_NOTCH_PIXELS = 18;

    public ArrayList<Component> modulesInCategory = new ArrayList<>();
    public String categoryName;
    private boolean categoryOpened;
    private int width;
    private int y;
    private int x;
    private final int bh;
    public boolean dragging;
    public int xx;
    public int yy;
    public boolean pin = false;
    private double marginY, marginX;
    private int scroll = 0;
    private double animScroll = 0;
    private double scrollVelocity = 0;
    private long scrollFrameNanos = 0L;
    private int height = 0;

    public CategoryComponent(String category, List<Module> modules) {
        this.categoryName = category;
        this.width = 92;
        this.x = 5;
        this.y = 5;
        this.bh = 13;
        this.xx = 0;
        this.categoryOpened = false;
        this.dragging = false;
        int tY = this.bh + 3;
        this.marginX = 80;
        this.marginY = 4.5;
        for (Module mod : modules) {
            ModuleComponent b = new ModuleComponent(mod, this, tY);
            this.modulesInCategory.add(b);
            tY += 16;
        }
    }

    public ArrayList<Component> getModules() {
        return this.modulesInCategory;
    }

    public void setX(int n) {
        this.x = n;
    }

    public void setY(int y) {
        this.y = y;
    }

    public void mousePressed(boolean d) {
        this.dragging = d;
    }

    public boolean isPin() {
        return this.pin;
    }

    public void setPin(boolean on) {
        this.pin = on;
    }

    public boolean isOpened() {
        return this.categoryOpened;
    }

    public void setOpened(boolean on) {
        this.categoryOpened = on;
    }

    private int getEffectiveMaxHeight() {
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        int availableHeight = sr.getScaledHeight() - (this.y + this.bh + 3);
        poorcatgirl.module.modules.HUD hud = poorcatgirl.module.modules.HUD.get();
        int configuredHeight = hud == null ? MAX_HEIGHT : hud.clickGuiMaxHeight.getValue();
        return Math.max(50, Math.min(configuredHeight, availableHeight));
    }

    public void render(FontRenderer renderer) {
        this.width = 92;
        update();
        height = 0;
        for (Component moduleRenderManager : this.modulesInCategory) {
            height += moduleRenderManager.getHeight();
        }
        int effectiveMaxHeight = getEffectiveMaxHeight();
        int maxScroll = Math.max(0, height - effectiveMaxHeight);
        if (scroll > maxScroll) scroll = maxScroll;
        if (scroll < 0) scroll = 0;
        advanceScroll(maxScroll);
        boolean openPanel = !this.modulesInCategory.isEmpty() && this.categoryOpened;
        int displayHeight = Math.min(height, effectiveMaxHeight);
        float panelH = openPanel ? this.bh + displayHeight + 4 : this.bh + 3;
        if (openPanel) {
            // v2.3 black plate stays; Gaussian blur goes underneath, outer silhouette rounded.
            ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
            poorcatgirl.util.FrostedGlass.drawGuiBlur(this.x - 1, this.y,
                    this.width + 2, panelH, PANEL_RADIUS, PANEL_RADIUS,
                    sr.getScaledWidth(), sr.getScaledHeight(), 1.0F);
            poorcatgirl.util.RenderUtil.drawRoundedRect(this.x - 1, this.y,
                    this.width + 2, panelH, PANEL_RADIUS,
                    new Color(0, 0, 0, 90).getRGB(), true, true, true, true);
        }
        poorcatgirl.util.RenderUtil.drawRoundedRect(this.x - 1, this.y,
                this.width + 2, this.bh + 3, PANEL_RADIUS,
                new Color(0, 0, 0, 186).getRGB(), true, true, !openPanel, !openPanel);
        renderer.drawString(Lang.t(this.categoryName), (float) (this.x + 2), (float) (this.y + 4), -1, false);
        renderer.drawString(this.categoryOpened ? "-" : "+", (float) (this.x + marginX), (float) ((double) this.y + marginY), Color.white.getRGB(), false);
        if (this.categoryOpened && !this.modulesInCategory.isEmpty()) {
            int renderHeight = 0;
            int scrollOffset = (int) Math.round(animScroll);
            ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
            double scale = sr.getScaleFactor();
            int bottom = this.y + this.bh + effectiveMaxHeight + 3;
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor((int) (this.x * scale), (int) ((sr.getScaledHeight() - bottom) * scale), (int) (this.width * scale), (int) (effectiveMaxHeight * scale));
            GL11.glPushMatrix();
            try {
                GL11.glTranslated(0.0D, scrollOffset - animScroll, 0.0D);
                for (Component c2 : this.modulesInCategory) {
                    int compHeight = c2.getHeight();
                    int drawY = renderHeight - scrollOffset;
                    c2.setComponentStartAt(this.bh + 3 + drawY);
                    if (renderHeight + compHeight > animScroll &&
                            renderHeight < animScroll + effectiveMaxHeight) {
                        c2.draw(new AtomicInteger(0));
                    }
                    renderHeight += compHeight;
                }
            } finally {
                GL11.glPopMatrix();
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
            if (height > effectiveMaxHeight) {
                float scrollY = (float) this.y + this.bh + 3 + (float) (animScroll * effectiveMaxHeight / height);
                float barHeight = Math.max(6.0F, (float) effectiveMaxHeight * effectiveMaxHeight / height);
                poorcatgirl.util.RenderUtil.drawRoundedRect(
                        this.x + this.width - 3.5F, scrollY, 3.0F, barHeight, 1.5F,
                        new Color(255, 255, 255, 48).getRGB(), true, true, true, true);
                poorcatgirl.util.RenderUtil.drawRoundedRect(
                        this.x + this.width - 3.0F, scrollY + 1.0F, 2.0F,
                        Math.max(2.0F, barHeight - 2.0F), 1.0F,
                        new Color(255, 255, 255, 60).getRGB(), true, true, true, true);
            }
        }
        poorcatgirl.util.RenderUtil.drawRoundedRect(this.x - 1.0F,
                this.y + panelH - 1.0F, this.width + 2.0F, 1.0F, 0.5F,
                poorcatgirl.module.modules.GuiModule.getAccentColor(),
                false, false, true, true);
    }

    private void advanceScroll(int maxScroll) {
        long now = System.nanoTime();
        double dt = this.scrollFrameNanos == 0L ? 1.0D / 60.0D
                : (now - this.scrollFrameNanos) / 1_000_000_000.0D;
        this.scrollFrameNanos = now;
        dt = Math.max(0.0D, Math.min(0.05D, dt));
        if (dt <= 0.0D) {
            return;
        }
        double distance = animScroll - scroll;
        double step = (scrollVelocity + SCROLL_RATE * distance) * dt;
        double decay = Math.exp(-SCROLL_RATE * dt);
        animScroll = scroll + (distance + step) * decay;
        scrollVelocity = (scrollVelocity - SCROLL_RATE * step) * decay;
        if (distance * (animScroll - scroll) < 0.0D) {
            animScroll = scroll;
            scrollVelocity = 0.0D;
        }
        if (animScroll <= 0.0D) {
            animScroll = 0.0D;
            scrollVelocity = 0.0D;
        }
        if (animScroll >= maxScroll) {
            animScroll = maxScroll;
            scrollVelocity = 0.0D;
        }
        if (Math.abs(scroll - animScroll) < 0.05D && Math.abs(scrollVelocity) < 0.75D) {
            animScroll = scroll;
            scrollVelocity = 0.0D;
        }
    }

    public void update() {
        int offset = this.bh + 3;
        for (Component component : this.modulesInCategory) {
            component.setComponentStartAt(offset);
            offset += component.getHeight();
        }
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getWidth() {
        return this.width;
    }

    public void handleDrag(int x, int y) {
        if (this.dragging) {
            this.setX(x - this.xx);
            this.setY(y - this.yy);
        }
    }

    public boolean isHovered(int x, int y) {
        return x >= this.x + 92 - 13 && x <= this.x + this.width && (float) y >= (float) this.y + 2.0F && y <= this.y + this.bh + 1;
    }

    public boolean mousePressed(int x, int y) {
        return x >= this.x + 77 && x <= this.x + this.width - 6 && (float) y >= (float) this.y + 2.0F && y <= this.y + this.bh + 1;
    }

    public boolean insideArea(int x, int y) {
        return x >= this.x && x <= this.x + this.width && y >= this.y && y <= this.y + this.bh;
    }

    public boolean isInViewport(int mouseY) {
        if (!this.categoryOpened) {
            return false;
        }
        int top = this.y + this.bh + 3;
        return mouseY >= top && mouseY < top + Math.min(height, getEffectiveMaxHeight());
    }

    public String getName() {
        return categoryName;
    }

    public void setLocation(int parseInt, int parseInt1) {
        this.x = parseInt;
        this.y = parseInt1;
    }

    public void onScroll(int mouseX, int mouseY, int scrollAmount) {
        if (!categoryOpened || height <= getEffectiveMaxHeight()) return;

        if (mouseX >= this.x && mouseX <= this.x + width && isInViewport(mouseY)) {
            scroll -= scrollAmount * SCROLL_NOTCH_PIXELS;
            int effectiveMaxHeight = getEffectiveMaxHeight();
            scroll = Math.max(0, Math.min(scroll, height - effectiveMaxHeight));
        }
    }
}
