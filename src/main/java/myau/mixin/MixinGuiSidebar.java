package myau.mixin;

import myau.Myau;
import myau.module.modules.HUD;
import myau.util.RenderUtil;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "revamp.sidebarmod.gui.GuiSidebar", remap = false)
public abstract class MixinGuiSidebar {

    private int betterSidebarRows;

    @Inject(method = "drawSidebar", at = @At("HEAD"))
    private void betterSidebarHead(net.minecraft.scoreboard.ScoreObjective objective,
                                   net.minecraft.client.gui.ScaledResolution resolution, CallbackInfo ci) {
        this.betterSidebarRows = 0;
    }

    @Redirect(
            method = "drawSidebar",
            at = @At(
                    value = "INVOKE",
                    target = "Lrevamp/sidebarmod/gui/GuiSidebar;func_73734_a(IIIII)V",
                    ordinal = 0,
                    remap = false
            ),
            remap = false
    )
    private void betterSidebarRow(int left, int top, int right, int bottom, int color) {
        this.betterSidebarRows++;
        HUD hud = Myau.moduleManager == null ? null : (HUD) Myau.moduleManager.getModule(HUD.class);
        if (hud != null && hud.rounded.getValue() && this.betterSidebarRows == 1) {
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

    @Redirect(
            method = "drawSidebar",
            at = @At(
                    value = "INVOKE",
                    target = "Lrevamp/sidebarmod/gui/GuiSidebar;func_73734_a(IIIII)V",
                    ordinal = 1,
                    remap = false
            ),
            remap = false
    )
    private void betterSidebarTitle(int left, int top, int right, int bottom, int color) {
        HUD hud = Myau.moduleManager == null ? null : (HUD) Myau.moduleManager.getModule(HUD.class);
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
            method = "drawSidebar",
            at = @At(
                    value = "INVOKE",
                    target = "Lrevamp/sidebarmod/gui/GuiSidebar;func_73734_a(IIIII)V",
                    ordinal = 2,
                    remap = false
            ),
            remap = false
    )
    private void betterSidebarSeparator(int left, int top, int right, int bottom, int color) {
        Gui.drawRect(left, top, right, bottom, color);
    }
}
