package poorcatgirl.mixin;

import poorcatgirl.module.modules.Freelook;
import poorcatgirl.module.modules.AutoBlockIn;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@SideOnly(Side.CLIENT)
@Mixin(value = {ActiveRenderInfo.class}, priority = 9999)
public abstract class MixinActiveRenderInfoFreelook {
    @Redirect(
            method = {"updateRenderInfo"},
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/entity/player/EntityPlayer;rotationYaw:F"
            )
    )
    private static float freelookRotationYaw(EntityPlayer entityPlayer) {
        if (Freelook.isActive()) {
            return Freelook.cameraYaw;
        }
        return AutoBlockIn.renderCameraYaw(entityPlayer,entityPlayer.rotationYaw);
    }

    @Redirect(
            method = {"updateRenderInfo"},
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/entity/player/EntityPlayer;rotationPitch:F"
            )
    )
    private static float freelookRotationPitch(EntityPlayer entityPlayer) {
        if (Freelook.isActive()) {
            return Freelook.cameraPitch;
        }
        return AutoBlockIn.renderCameraPitch(entityPlayer,entityPlayer.rotationPitch);
    }
}
