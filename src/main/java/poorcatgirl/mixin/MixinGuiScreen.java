package poorcatgirl.mixin;

import poorcatgirl.wallpaper.WallpaperPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(value = GuiScreen.class, priority = 9999)
public abstract class MixinGuiScreen {

    private boolean poorcatgirlWallpaperFailureLogged;

    @Inject(method = "drawDefaultBackground", at = @At("HEAD"), cancellable = true)
    private void poorcatgirlWallpaperDefaultBackground(CallbackInfo ci) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (shouldDrawWallpaper(mc)) {
                poorcatgirl.wallpaper.MenuBackground.draw(new ScaledResolution(mc));
                ci.cancel();
            }
        } catch (Throwable failure) {
            logWallpaperFailure(failure);
        }
    }

    @Inject(method = {"drawWorldBackground", "drawBackground"}, at = @At("HEAD"), cancellable = true)
    private void poorcatgirlWallpaperBackground(int tint, CallbackInfo ci) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (shouldDrawWallpaper(mc)) {
                poorcatgirl.wallpaper.MenuBackground.draw(new ScaledResolution(mc));
                ci.cancel();
            }
        } catch (Throwable failure) {
            logWallpaperFailure(failure);
        }
    }

    private boolean shouldDrawWallpaper(Minecraft mc) {
        return mc != null && (mc.theWorld == null || mc.currentScreen instanceof GuiDownloadTerrain);
    }

    private void logWallpaperFailure(Throwable failure) {
        if (!poorcatgirlWallpaperFailureLogged) {
            poorcatgirlWallpaperFailureLogged = true;
            System.err.println("[Wallpaper] screen background failed: " + failure);
            failure.printStackTrace();
        }
    }
}
