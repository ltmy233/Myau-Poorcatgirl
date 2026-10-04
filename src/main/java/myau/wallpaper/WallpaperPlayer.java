package myau.wallpaper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public final class WallpaperPlayer {
    private static final Logger logger = LogManager.getLogger();
    private static final WallpaperPlayer INSTANCE = new WallpaperPlayer();
    private static final long FADE_NANOS = 300_000_000L;

    private static final class FrameData {
        final int index;
        final byte[] data;
        final ByteBuffer buf;
        final int w;
        final int h;
        final boolean bgr;

        FrameData(int index, byte[] data, int w, int h, boolean bgr) {
            this.index = index;
            this.data = data;
            this.buf = null;
            this.w = w;
            this.h = h;
            this.bgr = bgr;
        }

        FrameData(int index, ByteBuffer buf, int w, int h, boolean bgr) {
            this.index = index;
            this.data = null;
            this.buf = buf;
            this.w = w;
            this.h = h;
            this.bgr = bgr;
        }
    }

    private Thread thread;
    private volatile boolean running;
    private volatile boolean error;
    private final ArrayBlockingQueue<FrameData> queue = new ArrayBlockingQueue<FrameData>(6);
    private final AtomicInteger wanted = new AtomicInteger(-1);

    private volatile Process mp4Process;
    private volatile boolean mp4Live;
    private volatile boolean mp4Failed;
    private volatile boolean paused;
    private final ConcurrentLinkedQueue<ByteBuffer> freeBufs = new ConcurrentLinkedQueue<ByteBuffer>();
    private long mp4Uploads;
    private long mp4LogAt;

    private WallpaperInfo info;
    private WallpaperInfo pending;
    private int texCur;
    private int texOut;
    private int curW;
    private int curH;
    private int outW;
    private int outH;
    private long playStart;
    private long fadeStart;
    private ByteBuffer uploadBuf;

    private WallpaperPlayer() {
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        running = false;
                        Process p = mp4Process;
                        if (p != null) p.destroy();
                    } catch (Throwable ignored) {
                    }
                }
            }, "Myau-WallpaperShutdown"));
        } catch (Throwable ignored) {
        }
    }

    public static WallpaperPlayer get() {
        return INSTANCE;
    }

    public void pause() {
        paused = true;
    }

    private boolean shouldIdle() {
        if (paused) return true;
        Minecraft mc = Minecraft.getMinecraft();
        return mc != null && mc.theWorld != null;
    }

    public synchronized boolean draw(ScaledResolution sr) {
        paused = false;
        WallpaperManager mgr = WallpaperManager.get();
        if (error) {
            stopThread();
            releaseTextures();
            return false;
        }
        if (!mgr.isEnabled()) {
            reset();
            return false;
        }
        WallpaperInfo sel = mgr.getSelected();
        if (sel == null) {
            reset();
            return false;
        }
        if (info == null) {
            if (pending == null || !pending.id.equals(sel.id)) beginSwitch(sel);
        } else if (!info.id.equals(sel.id)) {
            if (pending == null || !pending.id.equals(sel.id)) beginSwitch(sel);
        } else if (pending != null) {
            stopThread();
            clearQueue();
            pending = null;
            startThread(info);
        } else if (thread == null || !thread.isAlive()) {
            startThread(info);
        }

        long now = System.nanoTime();
        FrameData f = queue.poll();
        if (f != null) {
            FrameData g;
            while ((g = queue.poll()) != null) {
                releaseFrame(f);
                f = g;
            }
            uploadFrame(f);
            releaseFrame(f);
            if (mp4Live) {
                mp4Uploads++;
                if (now - mp4LogAt > 5_000_000_000L) {
                    logger.info(String.format("[Wallpaper] mode=mp4, delivered=%.1ffps, free=%d",
                            mp4Uploads * 1.0e9 / (now - mp4LogAt), freeBufs.size()));
                    mp4Uploads = 0;
                    mp4LogAt = now;
                }
            }
        }
        if (info == null || texCur == 0 || curW <= 0 || curH <= 0) return false;
        if (!mp4Live) {
            int idx = (int) ((now - playStart) / 1_000_000_000.0 * info.fps);
            idx %= info.frames;
            if (idx < 0) idx = 0;
            wanted.set(idx);
        }
        drawFrames(sr, now);
        return true;
    }

    private void releaseFrame(FrameData f) {
        if (f != null && f.buf != null) freeBufs.offer(f.buf);
    }

    private void clearQueue() {
        FrameData f;
        while ((f = queue.poll()) != null) releaseFrame(f);
    }

    public synchronized void stop() {
        reset();
        error = false;
    }

    private void reset() {
        stopThread();
        clearQueue();
        pending = null;
        error = false;
        mp4Failed = false;
        releaseTextures();
    }

    private void releaseTextures() {
        if (texCur > 0) {
            GL11.glDeleteTextures(texCur);
            texCur = 0;
        }
        if (texOut > 0) {
            GL11.glDeleteTextures(texOut);
            texOut = 0;
        }
        info = null;
        curW = curH = outW = outH = 0;
        fadeStart = 0;
    }

    private void beginSwitch(WallpaperInfo target) {
        stopThread();
        clearQueue();
        wanted.set(0);
        error = false;
        mp4Failed = false;
        pending = target;
        startThread(target);
    }

    private void startThread(final WallpaperInfo target) {
        running = true;
        final File ffmpeg = new File("config/Myau/bin/ffmpeg.exe");
        final File src = new File(target.dir, "source.mp4");
        final boolean mp4 = !mp4Failed && ffmpeg.isFile() && src.isFile();
        mp4Live = false;
        if (mp4) {
            mp4Uploads = 0;
            mp4LogAt = System.nanoTime();
        }
        logger.info("Wallpaper decode mode=" + (mp4 ? "mp4" : "frames") + " wall=" + target.id);
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                if (mp4) {
                    mp4Loop(target, ffmpeg, src);
                } else {
                    decodeLoop(target);
                }
            }
        }, mp4 ? "Myau-WallpaperMp4" : "Myau-WallpaperDecoder");
        thread.setDaemon(true);
        thread.start();
    }

    private void stopThread() {
        running = false;
        mp4Live = false;
        Process p = mp4Process;
        mp4Process = null;
        if (p != null) {
            p.destroy();
        }
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(300L);
            } catch (InterruptedException ignored) {
            }
            thread = null;
        }
    }

    private void mp4Loop(WallpaperInfo target, File ffmpeg, File src) {
        int frameBytes = target.width * target.height * 3;
        Process p = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    ffmpeg.getAbsolutePath(), "-re", "-stream_loop", "-1",
                    "-i", src.getAbsolutePath(), "-an",
                    "-f", "rawvideo", "-pix_fmt", "bgr24", "-");
            File logFile = new File("config/Myau/bin/ffmpeg.log");
            if (logFile.getParentFile() != null) logFile.getParentFile().mkdirs();
            pb.redirectError(logFile);
            p = pb.start();
            mp4Process = p;
            mp4Live = true;
            logger.info("Wallpaper mp4 mode: " + src.getName() + " " + target.width + "x" + target.height);
            ReadableByteChannel ch = Channels.newChannel(p.getInputStream());
            int index = 0;
            while (running) {
                if (shouldIdle()) {
                    Thread.sleep(50L);
                    continue;
                }
                ByteBuffer buf = freeBufs.poll();
                if (buf == null) buf = ByteBuffer.allocateDirect(frameBytes);
                buf.clear();
                buf.limit(frameBytes);
                while (buf.hasRemaining()) {
                    if (ch.read(buf) < 0) throw new IllegalStateException("ffmpeg stdout EOF");
                }
                buf.flip();
                FrameData data = new FrameData(index++, buf, target.width, target.height, true);
                boolean queued = false;
                while (running) {
                    if (queue.offer(data)) {
                        queued = true;
                        break;
                    }
                    Thread.sleep(10L);
                }
                if (!queued) freeBufs.offer(buf);
            }
        } catch (InterruptedException e) {
            // 正常收工，不用报
        } catch (Throwable t) {
            if (running) logger.error("Wallpaper mp4 decode failed, falling back to JPEG frames", t);
        } finally {
            mp4Live = false;
            if (p != null) {
                p.destroy();
                if (mp4Process == p) mp4Process = null;
            }
            if (running) mp4Failed = true;
        }
    }

    private void decodeLoop(WallpaperInfo target) {
        int frames = Math.max(1, target.frames);
        long pos = -1L;
        while (running) {
            if (shouldIdle()) {
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException e) {
                    break;
                }
                continue;
            }
            int base = wanted.get();
            if (base < 0) {
                try {
                    Thread.sleep(8L);
                } catch (InterruptedException e) {
                    break;
                }
                continue;
            }
            long cand;
            if (pos < 0) {
                cand = base;
            } else {
                cand = pos + 1;
                int nextIdx = (int) (cand % frames);
                int ahead = (nextIdx - base + frames) % frames;
                if (ahead > 2 && ahead < frames - 2) {
                    try {
                        Thread.sleep(8L);
                    } catch (InterruptedException e) {
                        break;
                    }
                    continue;
                }
            }
            try {
                int idx = (int) (cand % frames);
                File file = target.frame(idx);
                BufferedImage img = ImageIO.read(file);
                if (img == null) throw new IllegalStateException("empty image " + file);
                FrameData data = toFrameData(idx, img);
                while (running) {
                    if (queue.offer(data)) {
                        pos = cand;
                        break;
                    }
                    Thread.sleep(10L);
                }
            } catch (InterruptedException e) {
                break;
            } catch (Throwable t) {
                logger.error("Wallpaper decode failed: " + target.id + " frame " + (cand % frames), t);
                error = true;
                break;
            }
        }
    }

    private static FrameData toFrameData(int index, BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        if (img.getType() == BufferedImage.TYPE_3BYTE_BGR) {
            byte[] src = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
            return new FrameData(index, src, w, h, true);
        }
        int[] px = img.getRGB(0, 0, w, h, null, 0, w);
        byte[] rgb = new byte[w * h * 3];
        for (int i = 0, j = 0; i < px.length; i++) {
            int p = px[i];
            rgb[j++] = (byte) ((p >> 16) & 0xFF);
            rgb[j++] = (byte) ((p >> 8) & 0xFF);
            rgb[j++] = (byte) (p & 0xFF);
        }
        return new FrameData(index, rgb, w, h, false);
    }

    private void uploadFrame(FrameData f) {
        if (pending != null) {
            if (fadeStart != 0) return;
            if (texCur == 0) {
                texCur = GL11.glGenTextures();
                curW = 0;
                curH = 0;
                uploadInto(texCur, f, true);
                info = pending;
                pending = null;
                playStart = System.nanoTime();
                fadeStart = 0;
            } else {
                if (texOut == 0) {
                    texOut = GL11.glGenTextures();
                    outW = 0;
                    outH = 0;
                }
                uploadInto(texOut, f, false);
                int it = texCur, iw = curW, ih = curH;
                texCur = texOut;
                curW = outW;
                curH = outH;
                texOut = it;
                outW = iw;
                outH = ih;
                fadeStart = System.nanoTime();
                info = pending;
                pending = null;
                playStart = fadeStart;
            }
            return;
        }
        if (texCur > 0) uploadInto(texCur, f, true);
    }

    private void uploadInto(int tex, FrameData f, boolean isCur) {
        int haveW = isCur ? curW : outW;
        int haveH = isCur ? curH : outH;
        int need = f.w * f.h * 3;
        ByteBuffer src;
        if (f.buf != null) {
            f.buf.clear();
            f.buf.limit(need);
            src = f.buf;
        } else {
            if (uploadBuf == null || uploadBuf.capacity() < need) {
                uploadBuf = BufferUtils.createByteBuffer(need);
            }
            uploadBuf.clear();
            int n = Math.min(f.data.length, need);
            uploadBuf.put(f.data, 0, n);
            uploadBuf.flip();
            src = uploadBuf;
        }
        GlStateManager.bindTexture(tex);
        int fmt = f.bgr ? org.lwjgl.opengl.GL12.GL_BGR : GL11.GL_RGB;
        if (haveW != f.w || haveH != f.h) {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB8, f.w, f.h, 0, fmt,
                    GL11.GL_UNSIGNED_BYTE, src);
            if (isCur) {
                curW = f.w;
                curH = f.h;
            } else {
                outW = f.w;
                outH = f.h;
            }
        } else {
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, f.w, f.h, fmt,
                    GL11.GL_UNSIGNED_BYTE, src);
        }
    }

    private void drawFrames(ScaledResolution sr, long now) {
        int sw = sr.getScaledWidth();
        int sh = sr.getScaledHeight();
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableAlpha();
        boolean fading = fadeStart != 0;
        float t = 1.0F;
        if (fading) {
            t = (now - fadeStart) / (float) FADE_NANOS;
            if (t >= 1.0F) {
                fading = false;
                fadeStart = 0;
            }
        }
        if (fading && texOut > 0 && outW > 0 && outH > 0) {
            drawCover(sw, sh, texOut, outW, outH, 1.0F);
        }
        drawCover(sw, sh, texCur, curW, curH, fading ? t : 1.0F);
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void drawCover(int sw, int sh, int tex, int tw, int th, float alpha) {
        float scale = Math.max(sw / (float) tw, sh / (float) th);
        float dw = tw * scale;
        float dh = th * scale;
        float ox = (sw - dw) * 0.5F;
        float oy = (sh - dh) * 0.5F;
        GlStateManager.bindTexture(tex);
        GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);
        net.minecraft.client.renderer.Tessellator tess = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(7, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_TEX);
        wr.pos(ox, oy + dh, 0.0D).tex(0.0D, 1.0D).endVertex();
        wr.pos(ox + dw, oy + dh, 0.0D).tex(1.0D, 1.0D).endVertex();
        wr.pos(ox + dw, oy, 0.0D).tex(1.0D, 0.0D).endVertex();
        wr.pos(ox, oy, 0.0D).tex(0.0D, 0.0D).endVertex();
        tess.draw();
    }
}
