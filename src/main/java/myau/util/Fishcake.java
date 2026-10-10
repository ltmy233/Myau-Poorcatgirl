package myau.util;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class Fishcake {
    private static final String EMOJI = "\uD83C\uDF65";
    private static final int CELL = 128;
    private static final int FONT_SIZE = 96;

    private static int texture;
    private static boolean failed;

    private Fishcake() {
    }

    public static int texture() {
        if (texture > 0) {
            return texture;
        }
        if (failed) {
            return -1;
        }
        failed = true;
        try {
            BufferedImage image = render();
            if (image != null) {
                texture = upload(image);
            }
        } catch (Throwable t) {
            System.err.println("[Fishcake] " + t);
        }
        return texture;
    }

    private static BufferedImage render() throws Exception {
        File fontFile = emojiFontFile();
        if (fontFile == null || !fontFile.isFile()) {
            return null;
        }
        byte[] ttf = Files.readAllBytes(fontFile.toPath());
        ByteBuffer data = ByteBuffer.wrap(ttf).order(ByteOrder.BIG_ENDIAN);
        Map<Integer, int[]> colr = readColr(data);
        Color[] palette = readCpal(data);
        if (colr.isEmpty() || palette.length == 0) {
            return null;
        }
        Font font = Font.createFont(Font.TRUETYPE_FONT, fontFile).deriveFont(Font.PLAIN, (float) FONT_SIZE);
        BufferedImage image = new BufferedImage(CELL, CELL, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, 0, CELL, CELL);
            g.setComposite(AlphaComposite.SrcOver);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setFont(font);
            FontRenderContext ctx = g.getFontRenderContext();
            GlyphVector base = font.createGlyphVector(ctx, EMOJI);
            Rectangle2D bounds = base.getVisualBounds();
            if (bounds.getWidth() <= 0.0D || bounds.getHeight() <= 0.0D) {
                return null;
            }
            float x = (float) ((CELL - bounds.getWidth()) / 2.0D - bounds.getX());
            float y = (float) ((CELL - bounds.getHeight()) / 2.0D - bounds.getY());
            int[] layers = null;
            for (int i = 0; i < base.getNumGlyphs(); i++) {
                layers = colr.get(base.getGlyphCode(i) & 0xFFFF);
                if (layers != null) {
                    break;
                }
            }
            if (layers == null || layers.length == 0) {
                return null;
            }
            for (int i = 0; i + 1 < layers.length; i += 2) {
                int paletteIndex = layers[i + 1];
                Color color = paletteIndex >= 0 && paletteIndex < palette.length
                        ? palette[paletteIndex] : Color.WHITE;
                if (color.getAlpha() == 0) {
                    continue;
                }
                GlyphVector layer = font.createGlyphVector(ctx, new int[]{layers[i]});
                g.setColor(color);
                g.drawGlyphVector(layer, x, y);
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    private static int upload(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int[] argb = image.getRGB(0, 0, w, h, null, 0, w);
        ByteBuffer buf = BufferUtils.createByteBuffer(w * h * 4);
        for (int px : argb) {
            buf.put((byte) ((px >> 16) & 0xFF));
            buf.put((byte) ((px >> 8) & 0xFF));
            buf.put((byte) (px & 0xFF));
            buf.put((byte) ((px >> 24) & 0xFF));
        }
        buf.flip();
        int prev = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int id = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prev);
        return id;
    }

    private static File emojiFontFile() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String windir = System.getenv("WINDIR");
            return new File(windir == null ? "C:\\Windows" : windir, "Fonts/seguiemj.ttf");
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return new File("/System/Library/Fonts/Apple Color Emoji.ttc");
        }
        File ttf = new File("/usr/share/fonts/truetype/noto/NotoColorEmoji.ttf");
        return ttf.isFile() ? ttf : new File("/usr/share/fonts/opentype/noto/NotoColorEmoji.ttf");
    }

    private static Map<Integer, int[]> readColr(ByteBuffer data) {
        int base = findTable(data, "COLR");
        if (base < 0) {
            return new HashMap<>();
        }
        int numBaseGlyphRecords = u16(data, base + 2);
        int baseGlyphRecordsOffset = i32(data, base + 4);
        int layerRecordsOffset = i32(data, base + 8);
        int numLayerRecords = u16(data, base + 12);
        Map<Integer, int[]> result = new HashMap<>();
        for (int i = 0; i < numBaseGlyphRecords; i++) {
            int record = base + baseGlyphRecordsOffset + i * 6;
            int glyphId = u16(data, record);
            int firstLayerIndex = u16(data, record + 2);
            int numLayers = u16(data, record + 4);
            if (numLayers <= 0 || firstLayerIndex + numLayers > numLayerRecords) {
                continue;
            }
            int[] layers = new int[numLayers * 2];
            for (int layerIndex = 0; layerIndex < numLayers; layerIndex++) {
                int layerRecord = base + layerRecordsOffset + (firstLayerIndex + layerIndex) * 4;
                layers[layerIndex * 2] = u16(data, layerRecord);
                layers[layerIndex * 2 + 1] = u16(data, layerRecord + 2);
            }
            result.put(glyphId, layers);
        }
        return result;
    }

    private static Color[] readCpal(ByteBuffer data) {
        int base = findTable(data, "CPAL");
        if (base < 0) {
            return new Color[0];
        }
        int numPaletteEntries = u16(data, base + 2);
        int numColorRecords = u16(data, base + 6);
        int offsetFirstColorRecord = i32(data, base + 8);
        int firstPaletteIndex = u16(data, base + 12);
        int count = Math.min(numPaletteEntries, Math.max(0, numColorRecords - firstPaletteIndex));
        Color[] colors = new Color[Math.max(0, count)];
        for (int i = 0; i < colors.length; i++) {
            int colorRecord = base + offsetFirstColorRecord + (firstPaletteIndex + i) * 4;
            int blue = u8(data, colorRecord);
            int green = u8(data, colorRecord + 1);
            int red = u8(data, colorRecord + 2);
            int alpha = u8(data, colorRecord + 3);
            colors[i] = new Color(red, green, blue, alpha);
        }
        return colors;
    }

    private static int findTable(ByteBuffer data, String tag) {
        int numTables = u16(data, 4);
        for (int i = 0; i < numTables; i++) {
            int record = 12 + i * 16;
            String found = new String(new char[]{
                    (char) u8(data, record),
                    (char) u8(data, record + 1),
                    (char) u8(data, record + 2),
                    (char) u8(data, record + 3)
            });
            if (tag.equals(found)) {
                return i32(data, record + 8);
            }
        }
        return -1;
    }

    private static int u8(ByteBuffer data, int offset) {
        return data.get(offset) & 0xFF;
    }

    private static int u16(ByteBuffer data, int offset) {
        return data.getShort(offset) & 0xFFFF;
    }

    private static int i32(ByteBuffer data, int offset) {
        return data.getInt(offset);
    }
}
