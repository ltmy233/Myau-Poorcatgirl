package myau.wallpaper;

import java.io.File;

public final class WallpaperInfo {
    public final String id;
    public final String name;
    public final double fps;
    public final int frames;
    public final int width;
    public final int height;
    public final File dir;
    public final File poster;

    public WallpaperInfo(String id, String name, double fps, int frames, int width, int height, File dir) {
        this.id = id;
        this.name = name;
        this.fps = fps;
        this.frames = frames;
        this.width = width;
        this.height = height;
        this.dir = dir;
        File jpg = new File(dir, "poster.jpg");
        this.poster = jpg.isFile() ? jpg : new File(dir, "poster.png");
    }

    public File frame(int index) {
        return new File(dir, String.format("f_%04d.jpg", index + 1));
    }
}
