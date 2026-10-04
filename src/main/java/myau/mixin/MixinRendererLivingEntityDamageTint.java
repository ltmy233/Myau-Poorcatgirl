package myau.mixin;

import myau.module.modules.DamageTint;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SideOnly(Side.CLIENT)
@Mixin(value = {RendererLivingEntity.class}, priority = 9990)
public abstract class MixinRendererLivingEntityDamageTint {
    @Unique
    private EntityLivingBase damageTint$entity;

    @Inject(
            method = {"setBrightness"},
            at = {@At("HEAD")}
    )
    private void damageTint$captureEntity(EntityLivingBase entitylivingbaseIn, float partialTicks, boolean combineTextures, CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
        this.damageTint$entity = entitylivingbaseIn;
    }

    @ModifyArg(
            method = {"setBrightness"},
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;",
                    remap = false,
                    ordinal = 0
            )
    )
    private float damageTint$modifyRed(float f) {
        if (DamageTint.instance != null) {
            return (DamageTint.instance.tintColor.getValue() >> 16 & 255) / 255.0F;
        }
        return f;
    }

    @ModifyArg(
            method = {"setBrightness"},
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;",
                    remap = false,
                    ordinal = 1
            )
    )
    private float damageTint$modifyGreen(float f) {
        if (DamageTint.instance != null) {
            return (DamageTint.instance.tintColor.getValue() >> 8 & 255) / 255.0F;
        }
        return f;
    }

    @ModifyArg(
            method = {"setBrightness"},
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;",
                    remap = false,
                    ordinal = 2
            )
    )
    private float damageTint$modifyBlue(float f) {
        if (DamageTint.instance != null) {
            return (DamageTint.instance.tintColor.getValue() & 255) / 255.0F;
        }
        return f;
    }

    @ModifyArg(
            method = {"setBrightness"},
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;",
                    remap = false,
                    ordinal = 3
            )
    )
    private float damageTint$modifyAlpha(float f) {
        if (DamageTint.instance != null) {
            return DamageTint.computeAlpha(this.damageTint$entity);
        }
        return f;
    }
}
