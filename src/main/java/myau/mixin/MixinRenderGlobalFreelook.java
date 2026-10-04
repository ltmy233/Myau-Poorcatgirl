package myau.mixin;

import myau.module.modules.Freelook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import org.lwjgl.util.vector.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RenderGlobal.class)
public abstract class MixinRenderGlobalFreelook {
    @Shadow
    @Final
    private Minecraft mc;

    private boolean active(Entity entity) {
        return entity == mc.getRenderViewEntity() && Freelook.isActive();
    }

    @Redirect(method = "setupTerrain", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;rotationPitch:F"))
    private float freelookPitch(Entity entity) {
        return active(entity) ? Freelook.cameraPitch : entity.rotationPitch;
    }

    @Redirect(method = "setupTerrain", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;rotationYaw:F"))
    private float freelookYaw(Entity entity) {
        return active(entity) ? Freelook.cameraYaw : entity.rotationYaw;
    }

    @Redirect(method = "setupTerrain", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;getViewVector(Lnet/minecraft/entity/Entity;D)Lorg/lwjgl/util/vector/Vector3f;"))
    private Vector3f freelookViewVector(RenderGlobal renderGlobal, Entity entity, double partialTicks) {
        if (!active(entity)) {
            float pitch = (float) (entity.prevRotationPitch + (entity.rotationPitch - entity.prevRotationPitch) * partialTicks);
            float yaw = (float) (entity.prevRotationYaw + (entity.rotationYaw - entity.prevRotationYaw) * partialTicks);
            float cosYaw = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
            float sinYaw = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
            float cosPitch = -MathHelper.cos(-pitch * 0.017453292F);
            float sinPitch = MathHelper.sin(-pitch * 0.017453292F);
            return new Vector3f(sinYaw * cosPitch, sinPitch, cosYaw * cosPitch);
        }
        float pitch = Freelook.cameraPitch;
        float yaw = Freelook.cameraYaw;
        if (mc.gameSettings.thirdPersonView == 2) pitch += 180.0F;
        float cosYaw = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float sinYaw = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float cosPitch = -MathHelper.cos(-pitch * 0.017453292F);
        float sinPitch = MathHelper.sin(-pitch * 0.017453292F);
        return new Vector3f(sinYaw * cosPitch, sinPitch, cosYaw * cosPitch);
    }
}
