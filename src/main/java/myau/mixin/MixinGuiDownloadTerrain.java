package myau.mixin;

import myau.wallpaper.MenuBackground;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@SideOnly(Side.CLIENT)
@Mixin(value = GuiDownloadTerrain.class, priority = 10000)
public abstract class MixinGuiDownloadTerrain {
    @Redirect(
            method = "drawScreen",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiScreen;drawBackground(I)V"
            ),
            require = 0
    )
    private void myauDrawTerrainBackground(GuiScreen screen, int tint) {
        MenuBackground.draw(new ScaledResolution(Minecraft.getMinecraft()));
    }
}
