package myau.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.renderer.Tessellator;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(GuiSlot.class)
public abstract class MixinGuiSlotBackground {

    private boolean myauBackgroundFailureLogged;

    @Inject(method = "drawContainerBackground", at = @At("HEAD"), cancellable = true, remap = false)
    private void myauMenuContainerBackground(Tessellator tessellator, CallbackInfo ci) {
        try {
            if (myauMenuActive()) {
                ci.cancel();
            }
        } catch (Throwable failure) {
            myauLogBackgroundFailure(failure);
        }
    }

    @Inject(method = "overlayBackground", at = @At("HEAD"), cancellable = true)
    private void myauMenuOverlayBackground(int top, int bottom, int left, int right, CallbackInfo ci) {
        try {
            if (myauMenuActive()) {
                ci.cancel();
            }
        } catch (Throwable failure) {
            myauLogBackgroundFailure(failure);
        }
    }

    private static boolean myauMenuActive() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc != null && mc.theWorld == null;
    }

    private void myauLogBackgroundFailure(Throwable failure) {
        if (!this.myauBackgroundFailureLogged) {
            this.myauBackgroundFailureLogged = true;
            System.err.println("[Wallpaper] slot background skipped: " + failure);
            failure.printStackTrace();
        }
    }
}
