package myau.mixin;

import myau.Myau;
import myau.module.modules.HUD;
import myau.util.RenderUtil;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ServerListEntryNormal;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@SideOnly(Side.CLIENT)
@Mixin(value = ServerListEntryNormal.class, priority = 9999)
public class MixinServerListEntryNormal {

    @Redirect(
            method = "drawEntry",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Gui;drawRect(IIIII)V",
                    ordinal = 0
            )
    )
    private void myauRoundedServerBackdrop(int left, int top, int right, int bottom, int color) {
        HUD hud = Myau.moduleManager == null ? null : (HUD) Myau.moduleManager.getModule(HUD.class);
        if (hud != null && hud.rounded.getValue()) {
            float radius = Math.min(hud.cornerRadius.getValue(),
                    Math.min((float) (right - left), (float) (bottom - top)) * 0.5F);
            if (radius > 0.0F) {
                RenderUtil.drawRoundedRect((float) left, (float) top,
                        (float) (right - left), (float) (bottom - top), radius, color);
                return;
            }
        }
        Gui.drawRect(left, top, right, bottom, color);
    }
}
