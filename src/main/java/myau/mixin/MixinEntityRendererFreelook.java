package myau.mixin;

import myau.module.modules.Freelook;
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
        return entity.rotationYaw;
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
        return entity.prevRotationYaw;
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
        return entity.rotationPitch;
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
        return entity.prevRotationPitch;
    }

    @Redirect(
            method = {"updateCameraAndRender"},
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/Minecraft;inGameHasFocus:Z"
            )
    )
    private boolean freelookOverrideMouse(Minecraft minecraft) {
        return Freelook.overrideMouse(minecraft);
    }
}
