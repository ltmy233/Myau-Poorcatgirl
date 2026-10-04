package myau.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.IntBuffer;

public final class LiquidGlass {
    private static final int MODE_BASE = 0;
    private static final int MODE_RIM = 1;
    private static final float RIM_ALPHA = 0.8F;
    private static final IntBuffer VIEWPORT_BUFFER = BufferUtils.createIntBuffer(16);

    private static final String VERTEX =
            "#version 120\n" +
            "varying vec2 vGui;\n" +
            "void main(void) {\n" +
            "    vGui = gl_Vertex.xy;\n" +
            "    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n" +
            "}\n";

    private static final String FRAGMENT =
            "#version 120\n" +
            "uniform sampler2D uContent;\n" +
            "uniform vec4 uRect;\n" +
            "uniform vec4 uRegion;\n" +
            "uniform vec2 uMap;\n" +
            "uniform float uRadius;\n" +
            "uniform float uAA;\n" +
            "uniform float uRefractH;\n" +
            "uniform float uRefractA;\n" +
            "uniform float uDepth;\n" +
            "uniform float uChroma;\n" +
            "uniform float uSat;\n" +
            "uniform float uTint;\n" +
            "uniform float uDark;\n" +
            "uniform float uScreenMode;\n" +
            "uniform float uMode;\n" +
            "uniform float uAngle;\n" +
            "uniform float uFalloff;\n" +
            "uniform float uHiA;\n" +
            "uniform float uRimA;\n" +
            "varying vec2 vGui;\n" +
            "float sdRound(vec2 c, vec2 hs, float r) {\n" +
            "    vec2 q = abs(c) - (hs - vec2(r));\n" +
            "    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;\n" +
            "}\n" +
            "vec2 gdRound(vec2 c, vec2 hs, float r) {\n" +
            "    vec2 q = abs(c) - (hs - vec2(r));\n" +
            "    if (q.x >= 0.0 || q.y >= 0.0) return sign(c) * normalize(max(q, vec2(0.0001)));\n" +
            "    float gx = step(q.y, q.x);\n" +
            "    return sign(c) * vec2(gx, 1.0 - gx);\n" +
            "}\n" +
            "void main() {\n" +
            "    vec2 p = vGui;\n" +
            "    vec2 hs = (uRect.zw - uRect.xy) * 0.5;\n" +
            "    vec2 c = p - (uRect.xy + hs);\n" +
            "    float r = min(uRadius, min(hs.x, hs.y));\n" +
            "    float sd = sdRound(c, hs, r);\n" +
            "    float aa = max(uAA, 0.25);\n" +
            "    if (sd >= aa) discard;\n" +
            "    float alpha = clamp(0.5 - sd / aa, 0.0, 1.0);\n" +
            "    vec2 grad = gdRound(c, hs, min(r * 1.5, min(hs.x, hs.y)));\n" +
            "    float band = max(uRefractH, 0.5);\n" +
            "    float t = clamp(1.0 + min(sd, 0.0) / band, 0.0, 1.0);\n" +
            "    if (uMode < 0.5) {\n" +
            "        float d = (1.0 - sqrt(max(0.0, 1.0 - t * t))) * uRefractA;\n" +
            "        vec2 dir = normalize(grad + uDepth * normalize(c + vec2(0.0001)));\n" +
            "        vec2 base = p + d * dir;\n" +
            "        vec2 uv;\n" +
            "        if (uScreenMode < 0.5) {\n" +
            "            uv = vec2((base.x - uRegion.x) / uRegion.z, 1.0 - (base.y - uRegion.y) / uRegion.w);\n" +
            "        } else {\n" +
            "            uv = vec2(base.x * uMap.x, 1.0 - base.y * uMap.y);\n" +
            "        }\n" +
            "        uv = clamp(uv, vec2(0.002), vec2(0.998));\n" +
            "        vec3 col;\n" +
            "        if (uChroma > 0.0) {\n" +
            "            vec2 dv = d * dir * (uChroma * ((c.x * c.y) / (max(hs.x, 1.0) * max(hs.y, 1.0))));\n" +
            "            vec2 uvd;\n" +
            "            vec2 uvm;\n" +
            "            if (uScreenMode < 0.5) {\n" +
            "                uvd = clamp(vec2((base.x + dv.x - uRegion.x) / uRegion.z, 1.0 - (base.y + dv.y - uRegion.y) / uRegion.w), vec2(0.002), vec2(0.998));\n" +
            "                uvm = clamp(vec2((base.x - dv.x - uRegion.x) / uRegion.z, 1.0 - (base.y - dv.y - uRegion.y) / uRegion.w), vec2(0.002), vec2(0.998));\n" +
            "            } else {\n" +
            "                uvd = clamp(vec2((base.x + dv.x) * uMap.x, 1.0 - (base.y + dv.y) * uMap.y), vec2(0.002), vec2(0.998));\n" +
            "                uvm = clamp(vec2((base.x - dv.x) * uMap.x, 1.0 - (base.y - dv.y) * uMap.y), vec2(0.002), vec2(0.998));\n" +
            "            }\n" +
            "            col = vec3(texture2D(uContent, uvd).r,\n" +
            "                        texture2D(uContent, uv).g,\n" +
            "                        texture2D(uContent, uvm).b);\n" +
            "        } else {\n" +
            "            col = texture2D(uContent, uv).rgb;\n" +
            "        }\n" +
            "        float lum = dot(col, vec3(0.213, 0.715, 0.072));\n" +
            "        col = mix(vec3(lum), col, uSat);\n" +
            "        float shade = max(0.0, dot(grad, normalize(vec2(0.45, -1.0))));\n" +
            "        col *= 1.0 - uDark * shade * t;\n" +
            "        col = mix(col, vec3(1.0), uTint);\n" +
            "        gl_FragColor = vec4(col, alpha);\n" +
            "    } else {\n" +
            "        vec2 nrm = vec2(cos(uAngle), sin(uAngle));\n" +
            "        float d2 = dot(grad, nrm);\n" +
            "        float glow = pow(abs(d2), uFalloff);\n" +
            "        float rim = pow(t, 2.0) * max(d2, 0.0);\n" +
            "        float a = clamp((glow * uHiA + rim * uRimA) * alpha, 0.0, 1.0);\n" +
            "        gl_FragColor = vec4(1.0, 1.0, 1.0, a);\n" +
            "    }\n" +
            "}\n";

    private static int program;
    private static boolean programTried;
    private static boolean drawFailureLogged;
    private static int uContent = -1;
    private static int uRegion = -1;
    private static int uRect = -1;
    private static int uMap = -1;
    private static int uRadius = -1;
    private static int uAA = -1;
    private static int uRefractH = -1;
    private static int uRefractA = -1;
    private static int uDepth = -1;
    private static int uChroma = -1;
    private static int uSat = -1;
    private static int uTint = -1;
    private static int uDark = -1;
    private static int uScreenMode = -1;
    private static int uMode = -1;
    private static int uAngle = -1;
    private static int uFalloff = -1;
    private static int uHiA = -1;
    private static int uRimA = -1;

    private static int frameTex = -1;
    private static int frameTexW;
    private static int frameTexH;
    private static long frameSeq;
    private static long frameCapturedSeq = Long.MIN_VALUE;
    private static long frameCapturedNs;
    private static boolean frameValid;

    private static Framebuffer down1;
    private static Framebuffer down2;
    private static Framebuffer down3;
    private static int downW1;
    private static int downH1;

    private static float qX;
    private static float qY;
    private static float qW;
    private static float qH;
    private static float qR;
    private static float qSX;
    private static float qSY;
    private static float qDW;
    private static float qDH;
    private static int qMode;

    private LiquidGlass() {
    }

    public static void newFrame() {
        frameSeq++;
    }

    public static void draw(float x, float y, float w, float h, float radius, int scaledWidth, int scaledHeight) {
        if (w < 4.0F || h < 4.0F || scaledWidth <= 0 || scaledHeight <= 0) return;
        Minecraft mc = Minecraft.getMinecraft();
        int dispW = mc.displayWidth;
        int dispH = mc.displayHeight;
        if (dispW <= 0 || dispH <= 0) return;
        boolean drawn = false;
        boolean shaderDrawn = false;
        int prevFb = 0;
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        boolean prevDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean prevDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int prevTextureBinding = 0;
        boolean prevTexture = false;
        IntBuffer ib = VIEWPORT_BUFFER;
        ib.clear();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL20.glUseProgram(0);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
            prevTextureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            prevTexture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            prevFb = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            GL11.glGetInteger(GL11.GL_VIEWPORT, ib);
            int vpX = ib.get(0);
            int vpY = ib.get(1);
            int vpW = ib.get(2);
            int vpH = ib.get(3);
            ensureFrameCopy(dispW, dispH, vpX == 0 && vpY == 0 && vpW == dispW && vpH == dispH);
            if (frameValid) {
                float sx = dispW / (float) scaledWidth;
                float sy = dispH / (float) scaledHeight;
                int rx = Math.round(x * sx);
                int ry = Math.round(y * sy);
                int rx2 = Math.round((x + w) * sx);
                int ry2 = Math.round((y + h) * sy);
                if (rx < 0) rx = 0;
                if (ry < 0) ry = 0;
                if (rx2 > dispW) rx2 = dispW;
                if (ry2 > dispH) ry2 = dispH;
                int rw = rx2 - rx;
                int rh = ry2 - ry;
                if (rw >= 4 && rh >= 4) {
                    qSX = sx;
                    qSY = sy;
                    qDW = dispW;
                    qDH = dispH;
                    // 小控件不降采样，把场景细节保住，折射才看得出来，也省得 FBO 反复重建。
                    // 面板和按钮的玻璃一起糊，两块叠在一起的地方才有层次。
                    boolean blurred = h > 12.0F && OpenGlHelper.isFramebufferEnabled() && ensureDowns(rw, rh);
                    if (blurred) blurred = blurRegion(rx, ry, rx2, ry2, dispW, dispH);
                    OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, prevFb);
                    GL11.glViewport(vpX, vpY, vpW, vpH);
                    if (blurred) {
                        shaderDrawn = drawGlass(down3.framebufferTexture, true,
                                x, y, w, h, radius, dispW, dispH, sx, sy);
                    } else {
                        shaderDrawn = drawGlass(frameTex, false,
                                x, y, w, h, radius, dispW, dispH, sx, sy);
                    }
                    if (!shaderDrawn && blurred) {
                        qMode = MODE_BASE;
                        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                        drawShape(down3.framebufferTexture, x, y, w, h, radius);
                        qMode = MODE_RIM;
                        drawShape(frameTex, x, y, w, h, radius);
                        drawn = true;
                    } else if (shaderDrawn) {
                        drawn = true;
                    }
                }
            }
        } catch (Throwable t) {
            if (!drawFailureLogged) {
                drawFailureLogged = true;
                System.err.println("[LiquidGlass] draw failed: " + t);
                t.printStackTrace();
            }
            drawn = false;
            shaderDrawn = false;
        } finally {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, prevFb);
            // framebuffer 那几个辅助方法是带缓存的，还原状态之前先同步一下
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
            if (prevTexture) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
            GlStateManager.bindTexture(-1);
            GlStateManager.bindTexture(prevTextureBinding);
            if (prevDepth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
            GlStateManager.depthMask(prevDepthMask);
            GL11.glPopAttrib();
            GlStateManager.setActiveTexture(prevActive);
            GL20.glUseProgram(prevProgram);
        }
        if (shaderDrawn) {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            return;
        }
        RenderUtil.drawRoundedRect(x, y, w, h, radius, drawn ? 0x12FFFFFF : 0x33FFFFFF);
        drawRim(x, y, w, h, radius);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static boolean drawGlass(int tex, boolean blurred,
                                     float x, float y, float w, float h, float radius,
                                     int dispW, int dispH, float sx, float sy) {
        if (!ensureProgram()) return false;
        int prevActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try {
            float minD = Math.min(w, h);
            if (minD < 4.0F) return false;
            float refractH = minD * 0.35F;
            float refractA = -minD * 0.4F;
            float aa = 1.0F / Math.max(1.0F, sx);
            if (aa < 0.5F) aa = 0.5F;
            if (aa > 1.5F) aa = 1.5F;

            GL20.glUseProgram(program);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
            GL20.glUniform1i(uContent, 0);
            GL20.glUniform4f(uRect, x, y, x + w, y + h);
            GL20.glUniform4f(uRegion, x, y, w, h);
            GL20.glUniform2f(uMap, sx / (float) dispW, sy / (float) dispH);
            GL20.glUniform1f(uScreenMode, blurred ? 0.0F : 1.0F);
            GL20.glUniform1f(uRadius, Math.min(radius, minD * 0.5F));
            GL20.glUniform1f(uAA, aa);
            GL20.glUniform1f(uRefractH, refractH);
            GL20.glUniform1f(uRefractA, refractA);
            GL20.glUniform1f(uDepth, 0.35F);
            GL20.glUniform1f(uChroma, 0.15F);
            GL20.glUniform1f(uSat, 1.3F);
            GL20.glUniform1f(uTint, 0.025F);
            GL20.glUniform1f(uDark, 0.32F);
            GL20.glUniform1f(uAngle, 2.3561945F);
            GL20.glUniform1f(uFalloff, 1.2F);
            GL20.glUniform1f(uHiA, 0.34F);
            GL20.glUniform1f(uRimA, 0.72F);

            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            GL20.glUniform1f(uMode, 0.0F);
            quad(x, y, w, h);

            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
            GL20.glUniform1f(uMode, 1.0F);
            quad(x, y, w, h);

            GL20.glUseProgram(0);
            return true;
        } catch (Throwable t) {
            GL20.glUseProgram(0);
            return false;
        } finally {
            GL13.glActiveTexture(prevActive);
        }
    }

    private static void quad(float x, float y, float w, float h) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x, y + h);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x + w, y);
        GL11.glEnd();
    }

    private static boolean ensureProgram() {
        if (program > 0 && !GL20.glIsProgram(program)) {
            program = 0;
            programTried = false;
        }
        if (program > 0) return true;
        if (programTried) return false;
        programTried = true;
        int vs = compile(GL20.GL_VERTEX_SHADER, VERTEX);
        int fs = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
        if (vs == 0 || fs == 0) {
            if (vs != 0) GL20.glDeleteShader(vs);
            if (fs != 0) GL20.glDeleteShader(fs);
            return false;
        }
        int id = GL20.glCreateProgram();
        GL20.glAttachShader(id, vs);
        GL20.glAttachShader(id, fs);
        GL20.glLinkProgram(id);
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        if (GL20.glGetProgrami(id, GL20.GL_LINK_STATUS) == 0) {
            System.err.println("[LiquidGlass] link failed: " + GL20.glGetProgramInfoLog(id, 8192));
            GL20.glDeleteProgram(id);
            return false;
        }
        program = id;
        uContent = loc("uContent");
        uRegion = loc("uRegion");
        uRect = loc("uRect");
        uMap = loc("uMap");
        uRadius = loc("uRadius");
        uAA = loc("uAA");
        uRefractH = loc("uRefractH");
        uRefractA = loc("uRefractA");
        uDepth = loc("uDepth");
        uChroma = loc("uChroma");
        uSat = loc("uSat");
        uTint = loc("uTint");
        uDark = loc("uDark");
        uScreenMode = loc("uScreenMode");
        uMode = loc("uMode");
        uAngle = loc("uAngle");
        uFalloff = loc("uFalloff");
        uHiA = loc("uHiA");
        uRimA = loc("uRimA");
        return true;
    }

    private static int loc(String name) {
        return GL20.glGetUniformLocation(program, name);
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            System.err.println("[LiquidGlass] compile failed: " + GL20.glGetShaderInfoLog(shader, 8192));
            GL20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private static void ensureFrameCopy(int dispW, int dispH, boolean viewportOk) {
        long now = System.nanoTime();
        if (frameCapturedSeq == frameSeq && frameValid && now - frameCapturedNs < 200_000_000L) return;
        frameCapturedSeq = frameSeq;
        frameCapturedNs = now;
        frameValid = false;
        if (!viewportOk) return;
        ensureFrameTex(dispW, dispH);
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {
        }
        forceOpaque();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, frameTex);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, dispW, dispH);
        if (GL11.glGetError() != GL11.GL_NO_ERROR) return;
        frameValid = true;
    }

    private static void ensureFrameTex(int w, int h) {
        if (frameTex > 0 && frameTexW == w && frameTexH == h) return;
        if (frameTex > 0) GL11.glDeleteTextures(frameTex);
        frameTex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, frameTex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        frameTexW = w;
        frameTexH = h;
    }

    private static boolean blurRegion(int rx, int ry, int rx2, int ry2, int dispW, int dispH) {
        float u1 = rx / (float) dispW;
        float u2 = rx2 / (float) dispW;
        float v1 = (dispH - ry2) / (float) dispH;
        float v2 = (dispH - ry) / (float) dispH;
        if (!pass(down1, frameTex, u1, v1, u2, v2)) return false;
        if (!pass(down2, down1.framebufferTexture, 0.0F, 0.0F, 1.0F, 1.0F)) return false;
        if (!pass(down3, down2.framebufferTexture, 0.0F, 0.0F, 1.0F, 1.0F)) return false;
        return true;
    }

    private static void drawShape(int tex, float x, float y, float w, float h, float radius) {
        qX = x;
        qY = y;
        qW = w;
        qH = h;
        qR = Math.min(radius, Math.min(w, h) * 0.5F);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        float r = qR;
        if (r < 1.0F) {
            grid(x, y, x + w, y + h);
            return;
        }
        grid(x + r, y, x + w - r, y + h);
        grid(x, y + r, x + r, y + h - r);
        grid(x + w - r, y + r, x + w, y + h - r);
        fan(x + r, y + r, r, 180.0D, 270.0D);
        fan(x + w - r, y + r, r, 270.0D, 360.0D);
        fan(x + w - r, y + h - r, r, 0.0D, 90.0D);
        fan(x + r, y + h - r, r, 90.0D, 180.0D);
    }

    private static void grid(float x1, float y1, float x2, float y2) {
        int nx = Math.max(1, Math.min(32, (int) ((x2 - x1) / 3.0F)));
        int ny = Math.max(1, Math.min(32, (int) ((y2 - y1) / 3.0F)));
        GL11.glBegin(GL11.GL_QUADS);
        for (int j = 0; j < ny; j++) {
            float ya = y1 + (y2 - y1) * j / ny;
            float yb = y1 + (y2 - y1) * (j + 1) / ny;
            for (int i = 0; i < nx; i++) {
                float xa = x1 + (x2 - x1) * i / nx;
                float xb = x1 + (x2 - x1) * (i + 1) / nx;
                glassTex(xa, yb);
                glassTex(xb, yb);
                glassTex(xb, ya);
                glassTex(xa, ya);
            }
        }
        GL11.glEnd();
    }

    private static void fan(float cx, float cy, float rr, double a0, double a1) {
        int segs = Math.max(12, (int) Math.ceil(rr * 3.0D));
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        glassTex(cx, cy);
        for (int i = 0; i <= segs; i++) {
            double a = Math.toRadians(a0 + (a1 - a0) * i / segs);
            glassTex((float) (cx + Math.cos(a) * rr), (float) (cy + Math.sin(a) * rr));
        }
        GL11.glEnd();
    }

    private static void glassTex(float px, float py) {
        if (qMode == MODE_BASE) {
            GL11.glTexCoord2f((px - qX) / qW, 1.0F - (py - qY) / qH);
            GL11.glVertex2f(px, py);
            return;
        }
        float hx = qW * 0.5F;
        float hy = qH * 0.5F;
        float rr = Math.min(qR, Math.min(hx, hy));
        float dx = px - (qX + hx);
        float dy = py - (qY + hy);
        float qx = Math.abs(dx) - hx + rr;
        float qy = Math.abs(dy) - hy + rr;
        float mx = Math.max(qx, 0.0F);
        float my = Math.max(qy, 0.0F);
        float len = (float) Math.sqrt(mx * mx + my * my);
        float sd = len + Math.min(Math.max(qx, qy), 0.0F) - rr;
        float nx;
        float ny;
        if (qx > 0.0F || qy > 0.0F) {
            if (len > 0.0F) {
                nx = (dx < 0.0F ? -mx : mx) / len;
                ny = (dy < 0.0F ? -my : my) / len;
            } else {
                nx = 0.0F;
                ny = 0.0F;
            }
        } else if (qx >= qy) {
            nx = dx < 0.0F ? -1.0F : 1.0F;
            ny = 0.0F;
        } else {
            nx = 0.0F;
            ny = dy < 0.0F ? -1.0F : 1.0F;
        }
        float band = Math.min(qW, qH) * 0.5F;
        band = Math.min(Math.max(band, 3.0F), 10.0F);
        float t = 1.0F + sd / band;
        if (t < 0.0F) t = 0.0F;
        else if (t > 1.0F) t = 1.0F;
        float str = band * 0.9F * t * t;
        float spx = px - nx * str;
        float spy = py - ny * str;
        float su = spx * qSX / qDW;
        float sv = (qDH - spy * qSY) / qDH;
        if (su < 0.0F) su = 0.0F;
        else if (su > 1.0F) su = 1.0F;
        if (sv < 0.0F) sv = 0.0F;
        else if (sv > 1.0F) sv = 1.0F;
        float a = RIM_ALPHA * t * t;
        GL11.glColor4f(1.0F, 1.0F, 1.0F, a);
        GL11.glTexCoord2f(su, sv);
        GL11.glVertex2f(px, py);
    }

    private static void drawRim(float x, float y, float w, float h, float radius) {
        float r = Math.min(radius, Math.min(w, h) * 0.5F);
        if (r < 1.0F || w <= 4.0F || h <= 4.0F) return;
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(1.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 0.5F);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int a = 180; a <= 360; a += 3) {
            double rad = Math.toRadians(a);
            double cx = a <= 270 ? x + r : x + w - r;
            double cy = y + r;
            GL11.glVertex2d(cx + Math.cos(rad) * r, cy + Math.sin(rad) * r);
        }
        GL11.glEnd();
        GlStateManager.color(0.0F, 0.0F, 0.0F, 0.3F);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int a = 0; a <= 180; a += 3) {
            double rad = Math.toRadians(a);
            double cx = a <= 90 ? x + w - r : x + r;
            double cy = y + h - r;
            GL11.glVertex2d(cx + Math.cos(rad) * r, cy + Math.sin(rad) * r);
        }
        GL11.glEnd();
        GL11.glLineWidth(1.0F);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    private static void forceOpaque() {
        beginPassOrtho();
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2d(0.0D, 0.0D);
            GL11.glVertex2d(1.0D, 0.0D);
            GL11.glVertex2d(1.0D, 1.0D);
            GL11.glVertex2d(0.0D, 1.0D);
            GL11.glEnd();
        } finally {
            endPassOrtho();
        }
    }

    private static boolean pass(Framebuffer dst, int srcTex, float u1, float v1, float u2, float v2) {
        dst.bindFramebuffer(true);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, srcTex);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        beginPassOrtho();
        try {
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2d(u1, v1);
            GL11.glVertex2d(0.0D, 0.0D);
            GL11.glTexCoord2d(u2, v1);
            GL11.glVertex2d(1.0D, 0.0D);
            GL11.glTexCoord2d(u2, v2);
            GL11.glVertex2d(1.0D, 1.0D);
            GL11.glTexCoord2d(u1, v2);
            GL11.glVertex2d(0.0D, 1.0D);
            GL11.glEnd();
        } finally {
            endPassOrtho();
        }
        return true;
    }

    private static void beginPassOrtho() {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, 1.0D, 0.0D, 1.0D, -1.0D, 1.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
    }

    private static void endPassOrtho() {
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }

    private static boolean ensureDowns(int w, int h) {
        int w1 = Math.max(1, w / 2);
        int h1 = Math.max(1, h / 2);
        int w2 = Math.max(1, w1 / 2);
        int h2 = Math.max(1, h1 / 2);
        int w3 = Math.max(1, w2 / 2);
        int h3 = Math.max(1, h2 / 2);
        if (down1 != null && downW1 == w1 && downH1 == h1) return true;
        deleteDowns();
        try {
            down1 = new Framebuffer(w1, h1, false);
            down2 = new Framebuffer(w2, h2, false);
            down3 = new Framebuffer(w3, h3, false);
            setLinear(down1);
            setLinear(down2);
            setLinear(down3);
            downW1 = w1;
            downH1 = h1;
            return true;
        } catch (Throwable t) {
            deleteDowns();
            downW1 = 0;
            downH1 = 0;
            return false;
        }
    }

    private static void setLinear(Framebuffer fb) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, fb.framebufferTexture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    }

    private static void deleteDowns() {
        if (down1 != null) {
            down1.deleteFramebuffer();
            down1 = null;
        }
        if (down2 != null) {
            down2.deleteFramebuffer();
            down2 = null;
        }
        if (down3 != null) {
            down3.deleteFramebuffer();
            down3 = null;
        }
    }
}
