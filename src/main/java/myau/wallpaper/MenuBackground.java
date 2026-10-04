package myau.wallpaper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

public final class MenuBackground {
    private MenuBackground() {}

    public static void draw(ScaledResolution resolution) {
        WallpaperPlayer.get().draw(resolution);
    }
}
