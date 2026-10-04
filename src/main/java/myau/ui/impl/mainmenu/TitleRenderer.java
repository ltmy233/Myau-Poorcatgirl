package myau.ui.impl.mainmenu;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;

public final class TitleRenderer {
    private static final String TEXT = "Poorcatgirl";
    private static final String FONT_PATH = "/assets/myau/fonts/Pacifico-Regular.ttf";
    private static final float TEXT_HEIGHT = 40.0F;

    private static boolean built;
    private static boolean loadFailed;
    private static int textureId = -1;
    private static float textW;
    private static float textQuadW;
    private static float textQuadH;
    private static float padX;
    private static float padY;

    private TitleRenderer() {
    }

    public static boolean render(int scaledWidth, int scaledHeight) {
        if (!ensureBuilt()) return false;
        float bob = (float) (Math.sin(System.currentTimeMillis() * 0.001D) * 1.5D);
        float textTop = scaledHeight / 8.0F + bob;
        float fit = Math.min(1.0F, Math.max(0.4F, (scaledWidth - 40.0F) / textQuadW));
        float drawWidth = textQuadW * fit;
        float textLeft = (scaledWidth - drawWidth) * 0.5F;
        if (textTop < 2.0F) {
            textTop = 2.0F;
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(textLeft, textTop - padY * fit, 0.0F);
        GlStateManager.scale(fit, fit, 1.0F);
        drawText(0.0F, 0.0F);
        GlStateManager.popMatrix();
        return true;
    }

    private static boolean ensureBuilt() {
        if (built) return !loadFailed;
        built = true;
        try {
            InputStream in = TitleRenderer.class.getResourceAsStream(FONT_PATH);
            if (in == null) {
                loadFailed = true;
                return false;
            }
            Font font = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(96.0F);
            in.close();

            BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D pg = probe.createGraphics();
            pg.setFont(font);
            FontMetrics fm = pg.getFontMetrics();
            int textPx = fm.stringWidth(TEXT);
            int ascent = fm.getAscent();
            int descent = fm.getDescent();
            pg.dispose();

            int pad = 12;
            int shadow = 4;
            int texW = textPx + pad * 2 + shadow * 2;
            int texH = ascent + descent + pad * 2 + shadow * 2;
            BufferedImage img = new BufferedImage(texW, texH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setFont(font);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            float baseline = pad + ascent;
            g.setColor(new Color(0, 0, 0, 90));
            g.drawString(TEXT, pad + shadow, baseline + shadow);
            g.setPaint(new GradientPaint(0.0F, pad, new Color(0xE8, 0xE8, 0xEC),
                    0.0F, pad + ascent + descent, new Color(0xA8, 0xA8, 0xB0)));
            g.drawString(TEXT, pad, baseline);
            g.dispose();

            int[] pixels = img.getRGB(0, 0, texW, texH, null, 0, texW);
            ByteBuffer buf = BufferUtils.createByteBuffer(pixels.length * 4);
            for (int p : pixels) {
                buf.put((byte) ((p >> 16) & 0xFF));
                buf.put((byte) ((p >> 8) & 0xFF));
                buf.put((byte) (p & 0xFF));
                buf.put((byte) ((p >> 24) & 0xFF));
            }
            buf.flip();

            textureId = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, texW, texH, 0, GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE, buf);

            float k = TEXT_HEIGHT / (float) (ascent + descent);
            textW = textPx * k;
            textQuadW = texW * k;
            textQuadH = texH * k;
            padX = pad * k;
            padY = pad * k;
        } catch (Throwable t) {
            loadFailed = true;
        }
        return !loadFailed;
    }

    private static void drawText(float px, float py) {
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableAlpha();
        GlStateManager.disableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.bindTexture(textureId);
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_TEX);
        wr.pos(px, py + textQuadH, 0.0D).tex(0.0D, 1.0D).endVertex();
        wr.pos(px + textQuadW, py + textQuadH, 0.0D).tex(1.0D, 1.0D).endVertex();
        wr.pos(px + textQuadW, py, 0.0D).tex(1.0D, 0.0D).endVertex();
        wr.pos(px, py, 0.0D).tex(0.0D, 0.0D).endVertex();
        tess.draw();
        GlStateManager.enableAlpha();
        GlStateManager.enableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
