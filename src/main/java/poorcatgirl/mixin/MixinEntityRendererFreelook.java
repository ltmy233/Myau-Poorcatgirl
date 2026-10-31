package poorcatgirl.mixin;

import poorcatgirl.module.modules.Freelook;
import poorcatgirl.module.modules.AutoBlockIn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@SideOnly(Side.CLIENT)
@Mixin(value = {EntityRenderer.class}, priority = 9996)
public abstract class MixinEntityRendererFreelook {
    @Redirect(
            method = {"orientCamera"},
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/entity/Entity;rotationYaw:F"
            )
    )
    private float freelookRotationYaw(Entity entity) {
        if (Freelook.isActive()) {
            return Freelook.cameraYaw;
        }
        return AutoBlockIn.renderCameraYaw(entity,entity.rotationYaw);
    }

    @Redirect(
            method = {"orientCamera"},
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/entity/Entity;prevRotationYaw:F"
            )
    )
    private float freelookPrevRotationYaw(Entity entity) {
        if (Freelook.isActive()) {
            return Freelook.cameraYaw;
        }
        return AutoBlockIn.renderCameraYaw(entity,entity.prevRotationYaw);
    }

    @Redirect(
            method = {"orientCamera"},
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/entity/Entity;rotationPitch:F"
            )
    )
    private float freelookRotationPitch(Entity entity) {
        if (Freelook.isActive()) {
            return Freelook.cameraPitch;
        }
        return AutoBlockIn.renderCameraPitch(entity,entity.rotationPitch);
    }

    @Redirect(
            method = {"orientCamera"},
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/entity/Entity;prevRotationPitch:F"
            )
    )
    private float freelookPrevRotationPitch(Entity entity) {
        if (Freelook.isActive()) {
            return Freelook.cameraPitch;
        }
        return AutoBlockIn.renderCameraPitch(entity,entity.prevRotationPitch);
    }

    @Redirect(
            method = {"updateCameraAndRender"},
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/Minecraft;inGameHasFocus:Z"
            )
    )
    private boolean freelookOverrideMouse(Minecraft minecraft) {
        if(!Freelook.isActive() && AutoBlockIn.consumeCameraMouse(minecraft))return false;
        return Freelook.overrideMouse(minecraft);
    }
}
