package myau.mixin;

import myau.Myau;
import myau.module.modules.HUD;
import myau.util.RenderUtil;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
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

    private static HUD betterTabHud() {
        if (Myau.moduleManager == null) {
            return null;
        }
        return (HUD) Myau.moduleManager.getModule(HUD.class);
    }

    @Inject(method = "updatePlayerList", at = @At("HEAD"))
    private void betterTabAnimate(boolean show, CallbackInfo ci) {
        myau.util.TabAnim.overlay = (GuiPlayerTabOverlay) (Object) this;
        myau.util.TabAnim.update(show);
    }

    @Inject(method = "renderPlayerlist", at = @At("HEAD"))
    private void betterTabScaleIn(int width, net.minecraft.scoreboard.Scoreboard scoreboard,
                                  net.minecraft.scoreboard.ScoreObjective objective, CallbackInfo ci) {
        this.betterTabHasHeader = this.header != null;
        this.betterTabHasFooter = this.footer != null;
        this.betterTabMatrixPushed = false;
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

    @Redirect(
            method = "renderPlayerlist",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiPlayerTabOverlay;drawRect(IIIII)V",
                    ordinal = 0
            )
    )
    private void betterTabHeaderRect(int left, int top, int right, int bottom, int color) {
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
