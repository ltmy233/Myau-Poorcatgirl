package poorcatgirl.mixin;

import poorcatgirl.module.modules.Freelook;
import poorcatgirl.module.modules.AutoBlockIn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RenderManager.class)
public abstract class MixinRenderManagerFreelook {
    @Shadow
    private float playerViewX;

    @Shadow
    private float playerViewY;

    @Redirect(method = "cacheActiveRenderInfo", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/RenderManager;playerViewX:F", opcode = Opcodes.PUTFIELD))
    private void freelookViewPitch(RenderManager manager, float value) {
        this.playerViewX = Freelook.isActive() ? Freelook.cameraPitch
                : AutoBlockIn.renderCameraPitch(Minecraft.getMinecraft().getRenderViewEntity(),value);
    }

    @Redirect(method = "cacheActiveRenderInfo", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/RenderManager;playerViewY:F", opcode = Opcodes.PUTFIELD, ordinal = 1))
    private void freelookViewYaw(RenderManager manager, float value) {
        this.playerViewY = Freelook.isActive() ? Freelook.cameraYaw
                : AutoBlockIn.renderCameraYaw(Minecraft.getMinecraft().getRenderViewEntity(),value);
    }
}
