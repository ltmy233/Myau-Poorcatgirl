package poorcatgirl.mixin;

import poorcatgirl.Poorcatgirl;
import poorcatgirl.module.modules.Chams;
import poorcatgirl.module.modules.ViewClip;
import poorcatgirl.module.modules.Xray;
import net.minecraft.client.renderer.chunk.SetVisibility;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SideOnly(Side.CLIENT)
@Mixin(value = {VisGraph.class}, priority = 9999)
public abstract class MixinVisGraph {
    @Inject(
            method = {"func_178606_a"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void func_178606_a(CallbackInfo callbackInfo) {
        if (Poorcatgirl.moduleManager != null) {
            if (Poorcatgirl.moduleManager.modules.get(Chams.class).isEnabled()
                    || Poorcatgirl.moduleManager.modules.get(ViewClip.class).isEnabled()
                    || Poorcatgirl.moduleManager.modules.get(Xray.class).isEnabled()) {
                callbackInfo.cancel();
            }
        }
    }

    @Inject(
            method = {"computeVisibility"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void computeVisibility(CallbackInfoReturnable<SetVisibility> callbackInfoReturnable) {
        if (Poorcatgirl.moduleManager != null) {
            if (Poorcatgirl.moduleManager.modules.get(Chams.class).isEnabled()
                    || Poorcatgirl.moduleManager.modules.get(ViewClip.class).isEnabled()
                    || Poorcatgirl.moduleManager.modules.get(Xray.class).isEnabled()) {
                SetVisibility setVisibility = new SetVisibility();
                setVisibility.setAllVisible(true);
                callbackInfoReturnable.setReturnValue(setVisibility);
            }
        }
    }
}
