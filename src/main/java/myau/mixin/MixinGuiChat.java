package myau.mixin;

import myau.Myau;
import myau.module.modules.BetterChat;
import myau.util.RenderUtil;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@SideOnly(Side.CLIENT)
@Mixin(GuiChat.class)
public abstract class MixinGuiChat {

    private BetterChat betterChat() {
        if (Myau.moduleManager == null) {
            return null;
        }
        return (BetterChat) Myau.moduleManager.getModule(BetterChat.class);
    }

    @Redirect(
            method = "drawScreen",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiChat;drawRect(IIIII)V",
                    ordinal = 0
            )
    )
    private void betterChatRoundedInputBar(int left, int top, int right, int bottom, int color) {
        BetterChat mod = this.betterChat();
        if (mod == null || !mod.isEnabled() || !mod.rounded.getValue()) {
            Gui.drawRect(left, top, right, bottom, color);
            return;
        }
        float width = (float) (right - left);
        float height = (float) (bottom - top);
        float radius = Math.min(5.0F, height * 0.5F);
        RenderUtil.drawRoundedRect((float) left, (float) top, width, height, radius, color);
    }
}
