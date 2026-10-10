package myau.mixin;

import myau.Myau;
import myau.module.modules.HUD;
import myau.util.FrostedGlass;
import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(value = GuiPlayerTabOverlay.class, priority = 9999)
public abstract class MixinGuiPlayerTabOverlay {

    @Shadow
    private IChatComponent header;

    @Shadow
    private IChatComponent footer;

    private boolean betterTabMatrixPushed;
    private boolean betterTabHasHeader;
    private boolean betterTabHasFooter;

    // 玻璃面板是 header/rows/footer 三段拼起来的，三段各来一道描边就会在拼缝上留白线。
    // 所以分段只铺磨砂底，等最后一段画完再按并集补一圈描边 + 柔光。
    private boolean betterTabGlassAny;
    private float betterTabMinX;
    private float betterTabMinY;
    private float betterTabMaxX;
    private float betterTabMaxY;
    private float betterTabGlassRadius;
    private float betterTabOuterRadius;

    private static HUD betterTabHud() {
        if (Myau.moduleManager == null) {
            return null;
        }
        return (HUD) Myau.moduleManager.getModule(HUD.class);
    }

    private boolean betterTabGlassEnabled() {
        HUD hud = betterTabHud();
        return hud == null || hud.glassOn(hud.glassTab);
    }

    @Inject(method = "updatePlayerList", at = @At("HEAD"))
    private void betterTabAnimate(boolean show, CallbackInfo ci) {
        HUD hud = betterTabHud();
        myau.util.TabAnim.enabled = hud == null || hud.tabAnimation.getValue();
        myau.util.TabAnim.overlay = (GuiPlayerTabOverlay) (Object) this;
        myau.util.TabAnim.update(show);
    }

    @Inject(method = "renderPlayerlist", at = @At("HEAD"))
    private void betterTabScaleIn(int width, net.minecraft.scoreboard.Scoreboard scoreboard,
                                  net.minecraft.scoreboard.ScoreObjective objective, CallbackInfo ci) {
        this.betterTabHasHeader = this.header != null;
        this.betterTabHasFooter = this.footer != null;
        this.betterTabMatrixPushed = false;
        this.betterTabGlassAny = false;
        this.betterTabMinX = Float.MAX_VALUE;
        this.betterTabMinY = Float.MAX_VALUE;
        this.betterTabMaxX = -Float.MAX_VALUE;
        this.betterTabMaxY = -Float.MAX_VALUE;
        this.betterTabGlassRadius = 0.0F;
        this.betterTabOuterRadius = Float.MAX_VALUE;
        HUD hud = betterTabHud();
        if (hud != null && hud.rounded.getValue()) {
            this.betterTabGlassRadius = hud.cornerRadius.getValue();
        }
        float sy = myau.util.TabAnim.getScaleY();
        float sx = myau.util.TabAnim.getScaleX();
        if (!myau.util.TabAnim.settled()) {
            float anchorX = (float) width * 0.5F;
            GlStateManager.pushMatrix();
            GlStateManager.translate(anchorX, 0.0F, 0.0F);
            GlStateManager.scale(sx, sy, 1.0F);
            GlStateManager.translate(-anchorX, 0.0F, 0.0F);
            this.betterTabMatrixPushed = true;
        }
    }

    @Inject(method = "renderPlayerlist", at = @At("RETURN"))
    private void betterTabScaleOut(CallbackInfo ci) {
        if (this.betterTabMatrixPushed) {
            this.betterTabMatrixPushed = false;
            GlStateManager.popMatrix();
        }
    }

    /**
     * 分段玻璃底。画成了就记下并集并返回 true（调用方不再画原样式）。
     */
    private boolean betterTabGlass(int left, int top, int right, int bottom,
                                   float radiusTop, float radiusBottom,
                                   boolean edgeTop, boolean edgeBottom) {
        if (!this.betterTabGlassEnabled()) {
            return false;
        }
        float w = (float) (right - left);
        float h = (float) (bottom - top);
        if (w < 4.0F || h < 4.0F) {
            return false;
        }
        float rT = Math.max(0.0F, Math.min(radiusTop, h * 0.5F));
        float rB = Math.max(0.0F, Math.min(radiusBottom, h * 0.5F));
        if (rT > 0.0F && rT < this.betterTabOuterRadius) this.betterTabOuterRadius = rT;
        if (rB > 0.0F && rB < this.betterTabOuterRadius) this.betterTabOuterRadius = rB;
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        if (!FrostedGlass.draw((float) left, (float) top, w, h, rT, rB,
                edgeTop, edgeBottom, sr.getScaledWidth(), sr.getScaledHeight(), 1.0F, false)) {
            return false;
        }
        if (this.betterTabGlassAny) {
            if (left < this.betterTabMinX) this.betterTabMinX = left;
            if (top < this.betterTabMinY) this.betterTabMinY = top;
            if (right > this.betterTabMaxX) this.betterTabMaxX = right;
            if (bottom > this.betterTabMaxY) this.betterTabMaxY = bottom;
        } else {
            this.betterTabMinX = left;
            this.betterTabMinY = top;
            this.betterTabMaxX = right;
            this.betterTabMaxY = bottom;
            this.betterTabGlassAny = true;
        }
        return true;
    }

    private void betterTabFinishEdge() {
        if (!this.betterTabGlassAny) return;
        this.betterTabGlassAny = false;
        float w = this.betterTabMaxX - this.betterTabMinX;
        float h = this.betterTabMaxY - this.betterTabMinY;
        float radius = Math.min(this.betterTabGlassRadius, h * 0.5F);
        if (this.betterTabOuterRadius < radius) radius = this.betterTabOuterRadius;
        FrostedGlass.drawEdge(this.betterTabMinX, this.betterTabMinY, w, h, radius, 1.0F);
    }

    @Redirect(
            method = "renderPlayerlist",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiPlayerTabOverlay;drawRect(IIIII)V",
                    ordinal = 0
            )
    )
    private void betterTabHeaderRect(int left, int top, int right, int bottom, int color) {
        if (this.betterTabGlass(left, top, right, bottom,
                this.betterTabGlassRadius, 0.0F, true, false)) {
            return;
        }
        HUD hud = betterTabHud();
        if (hud != null && hud.rounded.getValue()) {
            float radius = Math.min(hud.cornerRadius.getValue(), (float) (bottom - top) * 0.5F);
            if (radius > 0.0F) {
                RenderUtil.drawRoundedRect(
                        (float) left,
                        (float) top,
                        (float) (right - left),
                        (float) (bottom - top),
                        radius,
                        color,
                        true,
                        true,
                        false,
                        false
                );
                return;
            }
        }
        Gui.drawRect(left, top, right, bottom, color);
    }

    @Redirect(
            method = "renderPlayerlist",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiPlayerTabOverlay;drawRect(IIIII)V",
                    ordinal = 1
            )
    )
    private void betterTabRowsRect(int left, int top, int right, int bottom, int color) {
        boolean roundTop = !this.betterTabHasHeader;
        boolean roundBottom = !this.betterTabHasFooter;
        if (this.betterTabGlass(left, top, right, bottom,
                roundTop ? this.betterTabGlassRadius : 0.0F,
                roundBottom ? this.betterTabGlassRadius : 0.0F,
                roundTop, roundBottom)) {
            // 服务端没给 footer 时，footer 那次回调不会来，收边只能在这里做
            if (!this.betterTabHasFooter) {
                this.betterTabFinishEdge();
            }
            return;
        }
        HUD hud = betterTabHud();
        if (hud != null && hud.rounded.getValue() && (roundTop || roundBottom)) {
            float radius = Math.min(hud.cornerRadius.getValue(), (float) (bottom - top) * 0.5F);
            if (radius > 0.0F) {
                RenderUtil.drawRoundedRect(
                        (float) left,
                        (float) top,
                        (float) (right - left),
                        (float) (bottom - top),
                        radius,
                        color,
                        roundTop,
                        roundTop,
                        roundBottom,
                        roundBottom
                );
                return;
            }
        }
        Gui.drawRect(left, top, right, bottom, color);
    }

    @Redirect(
            method = "renderPlayerlist",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiPlayerTabOverlay;drawRect(IIIII)V",
                    ordinal = 3
            )
    )
    private void betterTabFooterRect(int left, int top, int right, int bottom, int color) {
        if (this.betterTabGlass(left, top, right, bottom,
                0.0F, this.betterTabGlassRadius, false, true)) {
            this.betterTabFinishEdge();
            return;
        }
        HUD hud = betterTabHud();
        if (hud != null && hud.rounded.getValue()) {
            float radius = Math.min(hud.cornerRadius.getValue(), (float) (bottom - top) * 0.5F);
            if (radius > 0.0F) {
                RenderUtil.drawRoundedRect(
                        (float) left,
                        (float) top,
                        (float) (right - left),
                        (float) (bottom - top),
                        radius,
                        color,
                        false,
                        false,
                        true,
                        true
                );
                return;
            }
        }
        Gui.drawRect(left, top, right, bottom, color);
    }
}
