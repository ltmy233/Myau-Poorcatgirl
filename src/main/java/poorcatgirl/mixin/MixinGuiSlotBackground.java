package poorcatgirl.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.util.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import org.lwjgl.BufferUtils;
import java.nio.IntBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Invoker;

import poorcatgirl.util.RenderUtil;
import poorcatgirl.util.FrostedGlass;

@SideOnly(Side.CLIENT)
@Mixin(GuiSlot.class)
public abstract class MixinGuiSlotBackground {

    @Shadow public int top;
    @Shadow public int bottom;
    @Shadow public int left;
    @Shadow public int right;
    @Shadow public int width;
    @Shadow protected float amountScrolled;

    @Invoker("getContentHeight")
    public abstract int poorcatgirlContentHeight();

    @Invoker("getScrollBarX")
    public abstract int poorcatgirlScrollBarX();

    @Invoker("getListWidth")
    public abstract int poorcatgirlListWidth();

    private boolean poorcatgirlBackgroundFailureLogged;
    private boolean poorcatgirlClipWasEnabled;
    private boolean poorcatgirlClipSaved;
    private final IntBuffer poorcatgirlPreviousClip = BufferUtils.createIntBuffer(16);

    @Inject(method = "drawContainerBackground", at = @At("HEAD"), cancellable = true, remap = false)
    private void poorcatgirlMenuContainerBackground(Tessellator tessellator, CallbackInfo ci) {
        try {
            if (poorcatgirlMenuActive()) {
                ci.cancel();
            }
        } catch (Throwable failure) {
            poorcatgirlLogBackgroundFailure(failure);
        }
    }

    @Inject(method = "overlayBackground", at = @At("HEAD"), cancellable = true)
    private void poorcatgirlMenuOverlayBackground(int top, int bottom, int left, int right, CallbackInfo ci) {
        try {
            if (poorcatgirlMenuActive()) {
                ci.cancel();
            }
        } catch (Throwable failure) {
            poorcatgirlLogBackgroundFailure(failure);
        }
    }

    private static boolean poorcatgirlMenuActive() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc != null && mc.theWorld == null;
    }

    @Inject(method = "drawScreen", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiSlot;drawBackground()V", shift = At.Shift.AFTER))
    private void poorcatgirlClipList(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (!poorcatgirlMenuActive()) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getMinecraft();
            ScaledResolution sr = new ScaledResolution(mc);
            int scale = sr.getScaleFactor();
            int clipWidth = this.right - this.left;
            int clipHeight = this.bottom - this.top;
            this.poorcatgirlClipWasEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            this.poorcatgirlPreviousClip.clear();
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX, this.poorcatgirlPreviousClip);
            this.poorcatgirlClipSaved = true;
            int x = this.left * scale;
            int y = (sr.getScaledHeight() - this.bottom) * scale;
            int endX = x + Math.max(0, clipWidth) * scale;
            int endY = y + Math.max(0, clipHeight) * scale;
            if (this.poorcatgirlClipWasEnabled) {
                endX = Math.min(endX, this.poorcatgirlPreviousClip.get(0) + this.poorcatgirlPreviousClip.get(2));
                endY = Math.min(endY, this.poorcatgirlPreviousClip.get(1) + this.poorcatgirlPreviousClip.get(3));
                x = Math.max(x, this.poorcatgirlPreviousClip.get(0));
                y = Math.max(y, this.poorcatgirlPreviousClip.get(1));
            }
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(x, y, Math.max(0, endX - x), Math.max(0, endY - y));
            if (clipWidth > 0 && clipHeight > 0) {
                int rowWidth = Math.max(0, this.poorcatgirlListWidth());
                int rowLeft = Math.max(this.left, this.left + this.width / 2 - rowWidth / 2 - 4);
                int rowRight = Math.min(this.right, this.left + this.width / 2 + (rowWidth + 1) / 2 + 4);
                FrostedGlass.drawGaussianBackground(rowLeft, this.top, Math.max(0, rowRight - rowLeft), clipHeight,
                        sr.getScaledWidth(), sr.getScaledHeight(), 1.0F);
            }
        } catch (Throwable failure) {
            poorcatgirlLogBackgroundFailure(failure);
        }
    }

    @Inject(method = "drawScreen", at = @At("RETURN"))
    private void poorcatgirlFinishList(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        try {
            if (poorcatgirlMenuActive()) poorcatgirlDrawGlassScrollbar();
        } catch (Throwable failure) {
            poorcatgirlLogBackgroundFailure(failure);
        } finally {
            if (this.poorcatgirlClipSaved) {
                GL11.glScissor(this.poorcatgirlPreviousClip.get(0), this.poorcatgirlPreviousClip.get(1),
                        this.poorcatgirlPreviousClip.get(2), this.poorcatgirlPreviousClip.get(3));
                if (this.poorcatgirlClipWasEnabled) GL11.glEnable(GL11.GL_SCISSOR_TEST);
                else GL11.glDisable(GL11.GL_SCISSOR_TEST);
                this.poorcatgirlClipSaved = false;
            }
        }
    }

    private void poorcatgirlDrawGlassScrollbar() {
        int trackHeight = this.bottom - this.top;
        int contentHeight = this.poorcatgirlContentHeight();
        if (trackHeight <= 0 || contentHeight <= 0) {
            return;
        }
        int maxScroll = Math.max(0, contentHeight - (trackHeight - 4));
        if (maxScroll <= 0) {
            return;
        }
        int thumbHeight = trackHeight * trackHeight / Math.max(1, contentHeight);
        int maxThumb = Math.max(8, trackHeight - 8);
        thumbHeight = MathHelper.clamp_int(thumbHeight, Math.min(32, maxThumb), maxThumb);
        int thumbY = (int) this.amountScrolled * (trackHeight - thumbHeight) / maxScroll + this.top;
        thumbY = Math.max(this.top, Math.min(thumbY, this.bottom - thumbHeight));
        int x = this.poorcatgirlScrollBarX();
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        if (!FrostedGlass.drawFrosted(x + 0.5F, thumbY + 1.0F, 5.0F,
                Math.max(2.0F, thumbHeight - 2.0F), 2.5F,
                sr.getScaledWidth(), sr.getScaledHeight(), 1.0F)) {
            RenderUtil.drawRoundedRect(x + 0.5F, thumbY + 1.0F, 5.0F, Math.max(2.0F, thumbHeight - 2.0F),
                    2.5F, 0x55FFFFFF);
        }
        RenderUtil.drawRoundedRect(x + 1.5F, thumbY + 2.0F, 3.0F, Math.max(2.0F, thumbHeight - 4.0F),
                1.5F, 0x33FFFFFF);
    }

    @Redirect(method = "drawScreen",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;draw()V", ordinal = 0))
    private void poorcatgirlSkipListTopFade(Tessellator tessellator) {
        poorcatgirlSkipListStrip(tessellator);
    }

    @Redirect(method = "drawScreen",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;draw()V", ordinal = 1))
    private void poorcatgirlSkipListBottomFade(Tessellator tessellator) {
        poorcatgirlSkipListStrip(tessellator);
    }

    @Redirect(method = "drawScreen",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;draw()V", ordinal = 2))
    private void poorcatgirlSkipListScrollTrack(Tessellator tessellator) {
        poorcatgirlSkipListStrip(tessellator);
    }

    @Redirect(method = "drawScreen",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;draw()V", ordinal = 3))
    private void poorcatgirlSkipListThumb(Tessellator tessellator) {
        poorcatgirlSkipListStrip(tessellator);
    }

    @Redirect(method = "drawScreen",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;draw()V", ordinal = 4))
    private void poorcatgirlSkipListThumbHighlight(Tessellator tessellator) {
        poorcatgirlSkipListStrip(tessellator);
    }

    private static void poorcatgirlSkipListStrip(Tessellator tessellator) {
        if (!poorcatgirlMenuActive()) {
            tessellator.draw();
            return;
        }
        WorldRenderer renderer = tessellator.getWorldRenderer();
        try {
            renderer.finishDrawing();
            renderer.reset();
        } catch (Throwable failure) {
            try {
                renderer.reset();
            } catch (Throwable ignored) {
            }
        }
    }

    private void poorcatgirlLogBackgroundFailure(Throwable failure) {
        if (!this.poorcatgirlBackgroundFailureLogged) {
            this.poorcatgirlBackgroundFailureLogged = true;
            System.err.println("[Wallpaper] slot background skipped: " + failure);
            failure.printStackTrace();
        }
    }
}
