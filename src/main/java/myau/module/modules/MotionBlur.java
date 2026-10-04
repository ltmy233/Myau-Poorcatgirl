package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.LoadWorldEvent;
import myau.events.Render2DEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.util.ChatUtil;
import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import java.nio.IntBuffer;

public class MotionBlur extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final FloatProperty strength = new FloatProperty("strength", 0.5F, 0.0F, 1.0F);
    public final BooleanProperty onlyWhenMoving = new BooleanProperty("only-when-moving", true);

    private Framebuffer history;
    private boolean hasHistory;
    private boolean failed;
    private boolean tracking;
    private double lastX;
    private double lastY;
    private double lastZ;
    private float lastYaw;
    private float lastPitch;

    public MotionBlur() {
        super("MotionBlur", false);
    }

    @Override
    public void onEnabled() {
        this.failed = false;
        this.tracking = false;
        this.deleteHistory();
    }

    @Override
    public void onDisabled() {
        this.deleteHistory();
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        this.deleteHistory();
    }

    private void fail(String reason) {
        if (this.failed) {
            return;
        }
        this.failed = true;
        this.deleteHistory();
        try {
            ChatUtil.sendFormatted(Myau.clientName + "&cMotionBlur was disabled: " + reason);
        } catch (Exception ignored) {
        }
        try {
            this.setEnabled(false);
        } catch (Exception ignored) {
        }
    }

    private int pushFramebuffer() {
        if (!OpenGlHelper.isFramebufferEnabled()) {
            return -1;
        }
        return GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
    }

    private void popFramebuffer(int previous) {
        if (previous >= 0 && OpenGlHelper.isFramebufferEnabled()) {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, previous);
        }
    }

    private void deleteHistory() {
        if (this.history == null) {
            this.hasHistory = false;
            return;
        }
        int previous = this.pushFramebuffer();
        try {
            this.history.deleteFramebuffer();
        } catch (Throwable ignored) {
        } finally {
            this.popFramebuffer(previous);
        }
        this.history = null;
        this.hasHistory = false;
    }

    private boolean ensureHistory() {
        if (this.failed) {
            return false;
        }
        if (!OpenGlHelper.isFramebufferEnabled()) {
            this.fail("framebuffers are unavailable");
            return false;
        }
        if (mc.displayWidth <= 0 || mc.displayHeight <= 0) {
            return false;
        }
        if (this.history != null
                && (this.history.framebufferWidth != mc.displayWidth || this.history.framebufferHeight != mc.displayHeight)) {
            this.deleteHistory();
        }
        if (this.history == null) {
            int previous = this.pushFramebuffer();
            IntBuffer viewport = GLAllocation.createDirectIntBuffer(16);
            GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
            try {
                this.history = new Framebuffer(mc.displayWidth, mc.displayHeight, false);
                this.history.setFramebufferFilter(GL11.GL_NEAREST);
                this.history.framebufferClear();
            } catch (Throwable throwable) {
                this.history = null;
                GlStateManager.viewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
                this.popFramebuffer(previous);
                this.fail("framebuffer creation failed");
                return false;
            }
            GlStateManager.viewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
            this.popFramebuffer(previous);
            this.hasHistory = false;
        }
        if (this.history == null || this.history.framebufferTexture <= 0) {
            this.fail("framebuffer texture is invalid");
            return false;
        }
        return true;
    }

    private void capture() {
        int width = Math.min(mc.displayWidth, this.history.framebufferTextureWidth);
        int height = Math.min(mc.displayHeight, this.history.framebufferTextureHeight);
        if (width <= 0 || height <= 0) {
            return;
        }
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int packAlignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        try {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            this.history.bindFramebufferTexture();
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
            this.hasHistory = true;
        } finally {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, packAlignment);
            GlStateManager.bindTexture(previousTexture);
        }
    }

    private boolean isMoving() {
        if (mc.thePlayer == null) {
            return false;
        }
        if (!this.tracking) {
            return true;
        }
        if (mc.thePlayer.posX != this.lastX || mc.thePlayer.posY != this.lastY || mc.thePlayer.posZ != this.lastZ) {
            return true;
        }
        return mc.thePlayer.rotationYaw != this.lastYaw || mc.thePlayer.rotationPitch != this.lastPitch;
    }

    private void updateTracking() {
        this.tracking = mc.thePlayer != null;
        if (!this.tracking) {
            return;
        }
        this.lastX = mc.thePlayer.posX;
        this.lastY = mc.thePlayer.posY;
        this.lastZ = mc.thePlayer.posZ;
        this.lastYaw = mc.thePlayer.rotationYaw;
        this.lastPitch = mc.thePlayer.rotationPitch;
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled() || this.failed) {
            return;
        }
        try {
            if (!this.ensureHistory()) {
                return;
            }
            float blend = this.strength.getValue();
            if (blend <= 0.001F) {
                blend = 0.0F;
            } else if (this.onlyWhenMoving.getValue() && !this.isMoving()) {
                blend = 0.0F;
            }
            boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            boolean alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
            boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            boolean blendState = GL11.glIsEnabled(GL11.GL_BLEND);
            int blendSrcRGB = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
            int blendDstRGB = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
            int blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
            int blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
            int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            try {
                GlStateManager.disableDepth();
                GlStateManager.disableAlpha();
                GlStateManager.enableTexture2D();
                GlStateManager.enableBlend();
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                if (blend > 0.0F && this.hasHistory) {
                    GL14.glBlendColor(0.0F, 0.0F, 0.0F, blend);
                    GlStateManager.tryBlendFuncSeparate(
                            GL11.GL_CONSTANT_ALPHA,
                            GL11.GL_ONE_MINUS_CONSTANT_ALPHA,
                            GL11.GL_CONSTANT_ALPHA,
                            GL11.GL_ONE_MINUS_CONSTANT_ALPHA
                    );
                    RenderUtil.drawFramebuffer(this.history);
                }
                this.capture();
            } finally {
                GL14.glBlendColor(0.0F, 0.0F, 0.0F, 1.0F);
                GlStateManager.tryBlendFuncSeparate(
                        GL11.GL_SRC_ALPHA,
                        GL11.GL_ONE_MINUS_SRC_ALPHA,
                        GL11.GL_ONE,
                        GL11.GL_ONE_MINUS_SRC_ALPHA
                );
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                GlStateManager.bindTexture(previousTexture);
                GlStateManager.tryBlendFuncSeparate(blendSrcRGB, blendDstRGB, blendSrcAlpha, blendDstAlpha);
                if (blendState) {
                    GlStateManager.enableBlend();
                } else {
                    GlStateManager.disableBlend();
                }
                if (depth) {
                    GlStateManager.enableDepth();
                } else {
                    GlStateManager.disableDepth();
                }
                if (alphaTest) {
                    GlStateManager.enableAlpha();
                } else {
                    GlStateManager.disableAlpha();
                }
                if (!texture) {
                    GlStateManager.disableTexture2D();
                }
            }
            this.updateTracking();
        } catch (Throwable throwable) {
            this.fail("rendering failed (" + throwable.getClass().getSimpleName() + ")");
        }
    }
}
