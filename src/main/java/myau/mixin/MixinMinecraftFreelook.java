package myau.mixin;

import myau.module.modules.Freelook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@SideOnly(Side.CLIENT)
@Mixin(value = {Minecraft.class}, priority = 9998)
public abstract class MixinMinecraftFreelook {
    @Redirect(
            method = {"runTick"},
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/settings/GameSettings;thirdPersonView:I",
                    opcode = Opcodes.PUTFIELD
            )
    )
    private void freelookSetThirdPersonView(GameSettings gameSettings, int value) {
        if (Freelook.instance != null && Freelook.perspectiveToggled) {
            Freelook.instance.resetPerspective();
        } else {
            gameSettings.thirdPersonView = value;
        }
    }
}
