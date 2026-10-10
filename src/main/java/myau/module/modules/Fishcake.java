package myau.module.modules;

import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.module.Module;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

public class Fishcake extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final FloatProperty size = new FloatProperty("size", 32.0F, 12.0F, 96.0F);
    public final FloatProperty speed = new FloatProperty("speed", 160.0F, -720.0F, 720.0F);
    public final ModeProperty posX = new ModeProperty("position-x", 2, new String[]{"LEFT", "CENTER", "RIGHT"});
    public final ModeProperty posY = new ModeProperty("position-y", 0, new String[]{"TOP", "MIDDLE", "BOTTOM"});
    public final IntProperty offsetX = new IntProperty("offset-x", 10, -255, 255);
    public final IntProperty offsetY = new IntProperty("offset-y", 10, -255, 255);

    public Fishcake() {
        super("Fishcake", false);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled() || mc.gameSettings.showDebugInfo) {
            return;
        }
        int texture = myau.util.Fishcake.texture();
        if (texture <= 0) {
            return;
        }

        ScaledResolution sr = new ScaledResolution(mc);
        float half = this.size.getValue() * 0.5F;
        float cx;
        if (this.posX.getValue() == 0) {
            cx = (float) this.offsetX.getValue() + half;
        } else if (this.posX.getValue() == 1) {
            cx = sr.getScaledWidth() * 0.5F + (float) this.offsetX.getValue();
        } else {
            cx = (float) sr.getScaledWidth() - (float) this.offsetX.getValue() - half;
        }
        float cy;
        if (this.posY.getValue() == 0) {
            cy = (float) this.offsetY.getValue() + half;
        } else if (this.posY.getValue() == 1) {
            cy = sr.getScaledHeight() * 0.5F + (float) this.offsetY.getValue();
        } else {
            cy = (float) sr.getScaledHeight() - (float) this.offsetY.getValue() - half;
        }
        float angle = (System.currentTimeMillis() % 3600000L) / 1000.0F * this.speed.getValue();

        int prevTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, cy, 0.0F);
        GlStateManager.rotate(angle, 0.0F, 0.0F, 1.0F);
        RenderUtil.enableRenderState();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0F, 0.0F);
        GL11.glVertex2f(-half, -half);
        GL11.glTexCoord2f(0.0F, 1.0F);
        GL11.glVertex2f(-half, half);
        GL11.glTexCoord2f(1.0F, 1.0F);
        GL11.glVertex2f(half, half);
        GL11.glTexCoord2f(1.0F, 0.0F);
        GL11.glVertex2f(half, -half);
        GL11.glEnd();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTexture);
        RenderUtil.disableRenderState();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }
}
