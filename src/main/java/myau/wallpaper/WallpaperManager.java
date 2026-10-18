package myau.wallpaper;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class WallpaperManager {
    private static final Logger logger = LogManager.getLogger();
    private static final Gson gson = new Gson();
    private static final WallpaperManager INSTANCE = new WallpaperManager();
    /** 就这一个壁纸，不做轮换。 */
    private static final String FIXED_ID = "nachoneko";
    /** 打包进 jar 的壁纸，磁盘缺了自动释放。 */
    private static final String BUNDLE_PATH = "assets/myau/wallpapers/nachoneko";
    private static final String BUNDLE_FFMPEG = "assets/myau/bin/ffmpeg.exe";

    public static class Manifest {
        @SerializedName("name")
        public String name;
        @SerializedName("fps")
        public double fps;
        @SerializedName("frames")
        public int frames;
        @SerializedName("width")
        public int width;
        @SerializedName("height")
        public int height;
    }

    public static class Config {
        @SerializedName("enabled")
        public boolean enabled = true;
    }

    private final List<WallpaperInfo> wallpapers = new ArrayList<WallpaperInfo>();
    private Config config = new Config();
    private boolean scanned;

    private WallpaperManager() {
    }

    public static WallpaperManager get() {
        return INSTANCE;
    }

    public synchronized void scan() {
        wallpapers.clear();
        extractBundledWallpaper();
        File base = new File("config/Myau/wallpapers");
        File[] dirs = base.listFiles();
        if (dirs != null) {
            for (File dir : dirs) {
                if (!dir.isDirectory()) continue;
                File manifestFile = new File(dir, "manifest.json");
                if (!manifestFile.isFile()) continue;
                try {
                    Reader reader = new InputStreamReader(new FileInputStream(manifestFile), StandardCharsets.UTF_8);
                    Manifest m = gson.fromJson(reader, Manifest.class);
                    reader.close();
                    if (m == null || m.frames <= 0 || m.fps <= 0.0 || m.width <= 0 || m.height <= 0) {
                        logger.error("Bad wallpaper manifest: " + manifestFile);
                        continue;
                    }
                    String name = m.name == null || m.name.isEmpty() ? dir.getName() : m.name;
                    WallpaperInfo info = new WallpaperInfo(dir.getName(), name, m.fps, m.frames, m.width, m.height, dir);
                    File firstFrame = info.frame(0);
                    if (!firstFrame.isFile()) {
                        logger.error("Wallpaper has no frames: " + dir);
                        continue;
                    }
                    wallpapers.add(info);
                } catch (Throwable t) {
                    logger.error("Failed to read wallpaper manifest: " + manifestFile, t);
                }
            }
        }
        Collections.sort(wallpapers, new Comparator<WallpaperInfo>() {
            @Override
            public int compare(WallpaperInfo a, WallpaperInfo b) {
                return a.id.compareTo(b.id);
            }
        });
        loadConfig();
        scanned = true;
        logger.info("Scanned " + wallpapers.size() + " wallpapers, fixed=" + FIXED_ID + ", enabled=" + config.enabled);
    }

    private void loadConfig() {
        File file = new File("config/Myau/wallpapers.json");
        if (!file.isFile()) return;
        try {
            Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
            Config c = gson.fromJson(reader, Config.class);
            reader.close();
            if (c != null) config = c;
        } catch (Throwable t) {
            logger.error("Failed to read wallpapers.json", t);
        }
    }

    private void extractBundledWallpaper() {
        InputStream manifestStream = WallpaperManager.class.getResourceAsStream("/" + BUNDLE_PATH + "/manifest.json");
        if (manifestStream == null) return;
        try {
            Reader reader = new InputStreamReader(manifestStream, StandardCharsets.UTF_8);
            Manifest m = gson.fromJson(reader, Manifest.class);
            reader.close();
            if (m == null || m.frames <= 0) return;

            File dir = new File("config/Myau/wallpapers/" + FIXED_ID);
            if (!dir.isDirectory() && !dir.mkdirs()) {
                logger.error("Cannot create wallpaper directory: " + dir);
                return;
            }
            int written = 0;
            File manifestFile = new File(dir, "manifest.json");
            if (!manifestFile.isFile() || manifestFile.length() == 0L) {
                written += copyBundleResource(BUNDLE_PATH + "/manifest.json", manifestFile) ? 1 : 0;
            }
            for (int i = 1; i <= m.frames; i++) {
                File frame = new File(dir, String.format("f_%04d.jpg", i));
                if (frame.isFile() && frame.length() > 0L) continue;
                String name = String.format(BUNDLE_PATH + "/f_%04d.jpg", i);
                if (copyBundleResource(name, frame)) written++;
            }
            File poster = new File(dir, "poster.jpg");
            if (!poster.isFile() || poster.length() == 0L) {
                copyBundleResource(BUNDLE_PATH + "/poster.jpg", poster);
            }

            File mp4 = new File(dir, "source.mp4");
            if (!mp4.isFile() || mp4.length() == 0L) {
                if (copyBundleResource(BUNDLE_PATH + "/source.mp4", mp4)) written++;
            }
            File binDir = new File("config/Myau/bin");
            if (!binDir.isDirectory() && !binDir.mkdirs()) {
                logger.error("Cannot create ffmpeg directory: " + binDir);
            }
            File ffmpeg = new File(binDir, "ffmpeg.exe");
            if (!ffmpeg.isFile() || ffmpeg.length() == 0L) {
                if (copyBundleResource(BUNDLE_FFMPEG, ffmpeg)) written++;
            }

            if (written > 0) {
                logger.info("Extracted bundled wallpaper assets " + FIXED_ID + " (" + written + " files)");
            }
        } catch (Throwable t) {
            logger.error("Failed to extract bundled wallpaper", t);
        }
    }

    private static boolean copyBundleResource(String resource, File dst) {
        InputStream in = WallpaperManager.class.getResourceAsStream("/" + resource);
        if (in == null) return false;
        File tmp = new File(dst.getPath() + ".tmp");
        try {
            try {
                OutputStream out = new FileOutputStream(tmp);
                try {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                } finally {
                    out.close();
                }
            } finally {
                in.close();
            }
            if (dst.exists() && !dst.delete()) return false;
            if (!tmp.renameTo(dst)) {
                InputStream src = new FileInputStream(tmp);
                try {
                    OutputStream out = new FileOutputStream(dst);
                    try {
                        byte[] buf = new byte[65536];
                        int n;
                        while ((n = src.read(buf)) > 0) out.write(buf, 0, n);
                    } finally {
                        out.close();
                    }
                } finally {
                    src.close();
                }
                tmp.delete();
            }
            return dst.isFile() && dst.length() > 0L;
        } catch (IOException t) {
            logger.error("Failed to extract " + resource, t);
            tmp.delete();
            return false;
        }
    }

    public synchronized WallpaperInfo getSelected() {
        ensureScanned();
        return find(FIXED_ID);
    }

    public synchronized boolean isEnabled() {
        ensureScanned();
        return config.enabled;
    }

    private void ensureScanned() {
        if (!scanned) scan();
    }

    private WallpaperInfo find(String id) {
        for (WallpaperInfo info : wallpapers) {
            if (info.id.equals(id)) return info;
        }
        return null;
    }
}
