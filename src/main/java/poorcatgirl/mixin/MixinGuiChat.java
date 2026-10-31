package poorcatgirl.mixin;

import poorcatgirl.Poorcatgirl;
import poorcatgirl.module.modules.BetterChat;
import poorcatgirl.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@SideOnly(Side.CLIENT)
@Mixin(GuiChat.class)
public abstract class MixinGuiChat {

    private BetterChat betterChat() {
        if (Poorcatgirl.moduleManager == null) {
            return null;
        }
        return (BetterChat) Poorcatgirl.moduleManager.getModule(BetterChat.class);
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
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        // 输入条用玻璃；关了或画不出来就还是原来的底
        poorcatgirl.module.modules.HUD hud = poorcatgirl.module.modules.HUD.get();
        boolean useGlass = hud == null || hud.glassOn(hud.glassChat);
        // 输入条在 HUD 之后绘制，必须盖住物品栏。
        poorcatgirl.util.FrostedGlass.drawChatBackground((float) left, (float) top, width, height, radius,
                sr.getScaledWidth(), sr.getScaledHeight(), 1.0F, useGlass, color, false);
    }
}
