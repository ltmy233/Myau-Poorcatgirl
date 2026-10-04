package myau.mixin;

import myau.module.modules.Saturation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SideOnly(Side.CLIENT)
@Mixin(value = {EntityRenderer.class}, priority = 9997)
public abstract class MixinEntityRendererSaturation {
    @Shadow
    private ShaderGroup theShaderGroup;

    @Inject(
            method = {"isShaderActive"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void saturation$isShaderActive(CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
        if (Saturation.shaderGroup != null && OpenGlHelper.shadersSupported) {
            callbackInfoReturnable.setReturnValue(true);
        }
    }

    @Inject(
            method = {"getShaderGroup"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void saturation$getShaderGroup(CallbackInfoReturnable<ShaderGroup> callbackInfoReturnable) {
        if (Saturation.shaderGroup != null && OpenGlHelper.shadersSupported && this.theShaderGroup == null) {
            callbackInfoReturnable.setReturnValue(Saturation.shaderGroup);
        }
    }

    @Inject(
            method = {"updateShaderGroupSize"},
            at = {@At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;createBindEntityOutlineFbs(II)V"
            )}
    )
    private void saturation$updateShaderGroupSize(int width, int height, CallbackInfo callbackInfo) {
        if (Saturation.shaderGroup != null) {
            Saturation.shaderGroup.createBindFramebuffers(width, height);
        }
    }

    @Inject(
            method = {"updateCameraAndRender"},
            at = {@At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;renderEntityOutlineFramebuffer()V",
                    shift = At.Shift.AFTER
            )}
    )
    private void saturation$render(float partialTicks, long nanoTime, CallbackInfo callbackInfo) {
        if (Saturation.shaderGroup != null) {
            int previousMode = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_MATRIX_MODE);
            GlStateManager.matrixMode(5890);
            GlStateManager.pushMatrix();
            try {
                GlStateManager.loadIdentity();
                Saturation.shaderGroup.loadShaderGroup(partialTicks);
            } finally {
                GlStateManager.matrixMode(5890);
                GlStateManager.popMatrix();
                GlStateManager.matrixMode(previousMode);
            }
        }
    }
}
