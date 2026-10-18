package myau.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
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

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * 磨砂玻璃：背景模糊 + 1px 白描边 + 外圈柔光（参考 liquid-glass-react / Epsilon）。
 * 自身用 MVP 投到屏幕坐标再画，聊天栏 translate/scale、3D 名牌透视下位置都对。
 * 返回 false = 没画成，调用方必须兜原样式；异常一律吞掉，不能漏进渲染循环。
 */
public final class FrostedGlass {
    private static final IntBuffer VIEWPORT_BUFFER = BufferUtils.createIntBuffer(16);
    private static final FloatBuffer MV_BUFFER = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer PROJ_BUFFER = BufferUtils.createFloatBuffer(16);
    private static final float[] MV = new float[16];
    private static final float[] PROJ = new float[16];
    private static final float[] MVP = new float[16];

    private static final String VERTEX =
            "#version 120\n" +
            "varying vec2 vGui;\n" +
            "void main(void) {\n" +
            "    vGui = gl_Vertex.xy;\n" +
            "    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n" +
            "}\n";

    private static final String BLUR_VERT =
            "#version 120\n" +
            "varying vec2 vUv;\n" +
            "void main(void) {\n" +
            "    vUv = gl_Vertex.xy;\n" +
            "    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n" +
            "}\n";

    private static final String BLUR_FRAG =
            "#version 120\n" +
            "uniform sampler2D uContent;\n" +
            "uniform vec2 uStep;\n" +
            "varying vec2 vUv;\n" +
            "void main(void) {\n" +
            "    vec2 uv = vUv;\n" +
            "    vec4 c = texture2D(uContent, uv) * 0.30;\n" +
            "    c += (texture2D(uContent, uv + uStep) + texture2D(uContent, uv - uStep)) * 0.18;\n" +
            "    c += (texture2D(uContent, uv + uStep * 2.0) + texture2D(uContent, uv - uStep * 2.0)) * 0.10;\n" +
            "    c += (texture2D(uContent, uv + uStep * 3.0) + texture2D(uContent, uv - uStep * 3.0)) * 0.06;\n" +
            "    gl_FragColor = c;\n" +
            "}\n";

    private static final String FRAGMENT =
            "#version 120\n" +
            "uniform sampler2D uContent;\n" +
            "uniform vec4 uRect;\n" +
            "uniform vec2 uMap;\n" +
            "uniform float uRadius;\n" +
            "uniform float uRadiusTop;\n" +
            "uniform float uRadiusBottom;\n" +
            "uniform float uClipTop;\n" +
            "uniform float uClipBot;\n" +
            "uniform float uAA;\n" +
            "uniform float uSat;\n" +
            "uniform float uTint;\n" +
            "uniform float uDisp;\n" +
            "uniform float uBevelH;\n" +
            "uniform float uHiA;\n" +
            "uniform float uShA;\n" +
            "uniform float uLineW;\n" +
            "uniform float uRimA;\n" +
            "uniform float uAlpha;\n" +
            "uniform float uMode;\n" +
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
            // vGui 就是屏幕 GUI 坐标
            "    vec2 p = vGui;\n" +
            "    vec2 hs = (uRect.zw - uRect.xy) * 0.5;\n" +
            "    vec2 c = p - (uRect.xy + hs);\n" +
            "    float r = min(uRadius, min(hs.x, hs.y));\n" +
            "    float rS = min((c.y < 0.0) ? uRadiusTop : uRadiusBottom, min(hs.x, hs.y));\n" +
            "    float sdFx = sdRound(c, hs, r);\n" +
            "    float sd = sdRound(c, hs, rS);\n" +
            "    float aa = max(uAA, 0.25);\n" +
            "    float dT = c.y + hs.y;\n" +
            "    float dB = hs.y - c.y;\n" +
            "    float dL = c.x + hs.x;\n" +
            "    float dR = hs.x - c.x;\n" +
            "    if (sd >= aa) discard;\n" +
            "    float edge = clamp(0.5 - sd / aa, 0.0, 1.0);\n" +
            // 拼缝边不做 AA，否则接缝处叠出半透明缝
            "    if (uClipTop < 0.5 && dT < aa && dT <= dL && dT <= dR) edge = 1.0;\n" +
            "    if (uClipBot < 0.5 && dB < aa && dB <= dL && dB <= dR) edge = 1.0;\n" +
            "    vec2 grad = gdRound(c, hs, rS);\n" +
            "    vec2 uv = vec2(p.x * uMap.x, 1.0 - p.y * uMap.y);\n" +
            // 贴边采样点沿法线外推做边缘折射
            "    float band = clamp(min(hs.x, hs.y) * 0.35, 1.0, 10.0);\n" +
            // 拼缝处不画折射带和 inset 明暗带
            "    float supNear = 1.0;\n" +
            "    float supBevel = 1.0;\n" +
            "    if (uClipTop < 0.5) {\n" +
            "        supNear *= clamp(dT / band, 0.0, 1.0);\n" +
            "        supBevel *= clamp(dT / max(uBevelH, 0.5), 0.0, 1.0);\n" +
            "    }\n" +
            "    if (uClipBot < 0.5) {\n" +
            "        supNear *= clamp(dB / band, 0.0, 1.0);\n" +
            "        supBevel *= clamp(dB / max(uBevelH, 0.5), 0.0, 1.0);\n" +
            "    }\n" +
            "    float near = (1.0 - clamp(-sdFx / band, 0.0, 1.0)) * supNear;\n" +
            "    uv += grad * (uDisp * near * near);\n" +
            "    uv = clamp(uv, vec2(0.002), vec2(0.998));\n" +
            "    vec3 bg = texture2D(uContent, uv).rgb;\n" +
            "    float lum = dot(bg, vec3(0.2126, 0.7152, 0.0722));\n" +
            "    if (uMode < 0.5) {\n" +
            // saturate(140%)，不加蒙层，底色就是背景本身
            "        vec3 col = mix(vec3(lum), bg, uSat);\n" +
            "        col = mix(col, vec3(1.0), uTint);\n" +
            // 顶部内亮、底部内暗
            "        float bt = clamp(1.0 + min(sdFx, 0.0) / max(uBevelH, 0.5), 0.0, 1.0) * supBevel;\n" +
            "        col += vec3(1.0) * uHiA * max(0.0, -grad.y) * bt;\n" +
            "        col *= 1.0 - uShA * max(0.0, grad.y) * bt;\n" +
            "        gl_FragColor = vec4(clamp(col, 0.0, 1.0), edge * uAlpha);\n" +
            "    } else {\n" +
            // 135° 渐变边环
            "        float span = max((uRect.z - uRect.x) + (uRect.w - uRect.y), 0.0001);\n" +
            "        float t = clamp(((p.x - uRect.x) + (p.y - uRect.y)) / span, 0.0, 1.0);\n" +
            "        float sweep = t < 0.33 ? (t / 0.33) * 0.12\n" +
            "                    : t < 0.66 ? 0.12 + ((t - 0.33) / 0.33) * 0.28\n" +
            "                    : 0.40 * (1.0 - (t - 0.66) / 0.34);\n" +
            "        float line = clamp(1.0 - abs(sd) / max(uLineW, 0.1), 0.0, 1.0);\n" +
            // 高光随背景亮度
            "        float ring = (0.40 + sweep * 1.10) * mix(0.8, 1.35, clamp(lum, 0.0, 1.0));\n" +
            "        float a = clamp(line * ring * uRimA * edge, 0.0, 1.0) * uAlpha;\n" +
            "        gl_FragColor = vec4(1.0, 1.0, 1.0, a);\n" +
            "    }\n" +
            "}\n";

    private static final String GLOW_FRAG =
            "#version 120\n" +
            "uniform vec4 uRect;\n" +
            "uniform float uRadius;\n" +
            "uniform float uBlurR;\n" +
            "uniform float uAA;\n" +
            "uniform float uAlpha;\n" +
            "varying vec2 vGui;\n" +
            "float sdRound(vec2 c, vec2 hs, float r) {\n" +
            "    vec2 q = abs(c) - (hs - vec2(r));\n" +
            "    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;\n" +
            "}\n" +
            "float maskAt(vec2 p, vec2 center, vec2 hs, float r) {\n" +
            "    return 1.0 - smoothstep(0.0, uAA, sdRound(p - center, hs, r));\n" +
            "}\n" +
            "void main() {\n" +
            "    vec2 hs = (uRect.zw - uRect.xy) * 0.5;\n" +
            "    vec2 center = (uRect.xy + uRect.zw) * 0.5;\n" +
            "    float r = min(uRadius, min(hs.x, hs.y));\n" +
            "    float baseDistance = sdRound(vGui - center, hs, r);\n" +
            "    float edgeAA = max(uAA, 0.0001);\n" +
            "    if (baseDistance <= -edgeAA || baseDistance >= uBlurR) discard;\n" +
            "    float blurredMask = 1.0 - smoothstep(0.0, uAA, baseDistance);\n" +
            "    for (int i = 0; i < 8; i++) {\n" +
            "        float ang = float(i) * 0.3926990817;\n" +
            "        vec2 dir = vec2(cos(ang), sin(ang));\n" +
            "        for (int j = 1; j <= 5; j++) {\n" +
            "            vec2 off = dir * uBlurR * (float(j) / 5.0);\n" +
            "            blurredMask += maskAt(vGui + off, center, hs, r);\n" +
            "            blurredMask += maskAt(vGui - off, center, hs, r);\n" +
            "        }\n" +
            "    }\n" +
            "    blurredMask *= 0.012345679;\n" +
            "    float outsideCoverage = smoothstep(-edgeAA, 0.0, baseDistance);\n" +
            "    float fadeWidth = min(uAA, uBlurR);\n" +
            "    float rangeCoverage = 1.0 - smoothstep(uBlurR - fadeWidth, uBlurR, max(baseDistance, 0.0));\n" +
            "    float outA = clamp(blurredMask * outsideCoverage * rangeCoverage, 0.0, 1.0) * uAlpha;\n" +
            "    if (outA < 0.001) discard;\n" +
            "    gl_FragColor = vec4(1.0, 1.0, 1.0, outA);\n" +
            "}\n";

    // 实底：半透明深灰圆角矩形，不采样背景
    private static final String FLAT_FRAG =
            "#version 120\n" +
            "uniform vec4 uRect;\n" +
            "uniform float uRadiusTop;\n" +
            "uniform float uRadiusBottom;\n" +
            "uniform float uAA;\n" +
            "uniform vec4 uColor;\n" +
            "uniform float uAlpha;\n" +
            "varying vec2 vGui;\n" +
            "float sdRound(vec2 c, vec2 hs, float r) {\n" +
            "    vec2 q = abs(c) - (hs - vec2(r));\n" +
            "    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;\n" +
            "}\n" +
            "void main() {\n" +
            "    vec2 hs = (uRect.zw - uRect.xy) * 0.5;\n" +
            "    vec2 center = (uRect.xy + uRect.zw) * 0.5;\n" +
            "    vec2 c = vGui - center;\n" +
            "    float r = min(c.y < 0.0 ? uRadiusTop : uRadiusBottom, min(hs.x, hs.y));\n" +
            "    float sd = sdRound(c, hs, r);\n" +
            "    float aa = max(uAA, 0.0001);\n" +
            "    float mask = 1.0 - smoothstep(-aa, aa, sd);\n" +
            "    if (mask <= 0.001) discard;\n" +
            "    gl_FragColor = vec4(uColor.rgb, uColor.a * mask * uAlpha);\n" +
            "}\n";

    private static int program;
    private static boolean programTried;
    private static boolean drawFailureLogged;
    private static int uContent = -1;
    private static int uRect = -1;
    private static int uMap = -1;
    private static int uRadius = -1;
    private static int uRadiusTop = -1;
    private static int uRadiusBottom = -1;
    private static int uClipTop = -1;
    private static int uClipBot = -1;
    private static int uAA = -1;
    private static int uSat = -1;
    private static int uTint = -1;
    private static int uDisp = -1;
    private static int uBevelH = -1;
    private static int uHiA = -1;
    private static int uShA = -1;
    private static int uLineW = -1;
    private static int uRimA = -1;
    private static int uAlpha = -1;
    private static int uMode = -1;

    private static int blurProgram;
    private static boolean blurTried;
    private static int uBlurContent = -1;
    private static int uBlurStep = -1;

    private static int glowProgram;
    private static boolean glowTried;
    private static int uGlowRect = -1;
    private static int uGlowRadius = -1;
    private static int uGlowBlurR = -1;
    private static int uGlowAA = -1;
    private static int uGlowAlpha = -1;

    private static int flatProgram;
    private static boolean flatTried;
    private static int uFlatRect = -1;
    private static int uFlatRadiusTop = -1;
    private static int uFlatRadiusBottom = -1;
    private static int uFlatAA = -1;
    private static int uFlatColor = -1;
    private static int uFlatAlpha = -1;

    private static boolean scissorOn;
    private static boolean scissorWasOn;
    private static int scissorPrevX;
    private static int scissorPrevY;
    private static int scissorPrevW;
    private static int scissorPrevH;
    // glGetInteger 的 buffer 至少要 16 个元素
    private static final IntBuffer SCISSOR_BUFFER = BufferUtils.createIntBuffer(16);

    // 游戏内柔光放大倍数
    private static final float IN_GAME_GLOW_SCALE = 1.0F;

    private static float gameGlowScale() {
        myau.module.modules.HUD hud = myau.module.modules.HUD.get();
        return hud == null ? 1.45F : hud.glowSize.getValue();
    }

    private static float gameGlowAlpha() {
        myau.module.modules.HUD hud = myau.module.modules.HUD.get();
        return hud == null ? 1.0F : hud.glowOpacity.getValue() / 100.0F;
    }

    private static float gameGlowBlur() {
        myau.module.modules.HUD hud = myau.module.modules.HUD.get();
        return hud == null ? 1.0F : hud.glowBlur.getValue() / 100.0F;
    }

    private static int frameTex = -1;
    private static int frameTexW;
    private static int frameTexH;
    private static long frameSeq;
    private static long frameCapturedSeq = Long.MIN_VALUE;
    private static long frameCapturedNs;
    private static boolean frameValid;
    private static boolean frameBlurred;

    private static Framebuffer down1;
    private static Framebuffer down2;
    private static Framebuffer downTmp;
    private static Framebuffer down3;
    private static int downW1;
    private static int downH1;

    private FrostedGlass() {
    }

    public static void newFrame() {
        disableScissor();
        frameSeq++;
    }

    public static boolean draw(float x, float y, float w, float h, float radius,
                               int scaledWidth, int scaledHeight, float alpha) {
        return draw(x, y, w, h, radius, scaledWidth, scaledHeight, alpha, true);
    }

    /**
     * 画一块玻璃。返回 false 时调用方必须兜原样式底，否则控件没背景。
     *
     * @param edge false 只铺磨砂底、不画描边柔光，给分段拼接的面板用
     */
    public static boolean draw(float x, float y, float w, float h, float radius,
                               int scaledWidth, int scaledHeight, float alpha, boolean edge) {
        return draw(x, y, w, h, radius, radius, scaledWidth, scaledHeight, alpha, edge);
    }

    /**
     * 同上，上/下圆角分开传。
     *
     * @param radiusTop    上两角半径
     * @param radiusBottom 下两角半径
     * @param edge false 只铺磨砂底、不画描边柔光，给分段拼接的面板用
     */
    public static boolean draw(float x, float y, float w, float h,
                               float radiusTop, float radiusBottom,
                               int scaledWidth, int scaledHeight, float alpha, boolean edge) {
        return draw(x, y, w, h, radiusTop, radiusBottom, true, true,
                scaledWidth, scaledHeight, alpha, edge);
    }

    /**
     * 同上，额外指定上下边是不是面板外边界（false = 拼缝，压掉折射/明暗带）。
     */
    public static boolean draw(float x, float y, float w, float h,
                               float radiusTop, float radiusBottom,
                               boolean edgeTop, boolean edgeBottom,
                               int scaledWidth, int scaledHeight, float alpha, boolean edge) {
        return drawImpl(x, y, w, h, radiusTop, radiusBottom, edgeTop, edgeBottom,
                scaledWidth, scaledHeight, alpha, edge, false);
    }

    /** 磨砂玻璃版，只给主界面卡片用；游戏内走 {@link #draw} 实底。 */
    public static boolean drawFrosted(float x, float y, float w, float h, float radius,
                                      int scaledWidth, int scaledHeight, float alpha) {
        if (alpha <= 0.0F || w < 4.0F || h < 4.0F || scaledWidth <= 0 || scaledHeight <= 0) return false;
        return drawImpl(x, y, w, h, radius, radius, true, true,
                scaledWidth, scaledHeight, alpha, true, true);
    }

    public static boolean drawBlurredFlat(float x, float y, float w, float h,
                                          int scaledWidth, int scaledHeight, float alpha) {
        if (alpha <= 0.0F || w < 4.0F || h < 4.0F || scaledWidth <= 0 || scaledHeight <= 0) return false;
        return drawImpl(x, y, w, h, 0.0F, 0.0F, true, true,
                scaledWidth, scaledHeight, alpha, false, true);
    }

    private static boolean drawImpl(float x, float y, float w, float h,
                                    float radiusTop, float radiusBottom,
                                    boolean edgeTop, boolean edgeBottom,
                                    int scaledWidth, int scaledHeight, float alpha, boolean edge,
                                    boolean frosted) {
        return drawImpl(x, y, w, h, radiusTop, radiusBottom, edgeTop, edgeBottom,
                scaledWidth, scaledHeight, alpha, edge, frosted, false);
    }

    private static boolean drawImpl(float x, float y, float w, float h,
                                    float radiusTop, float radiusBottom,
                                    boolean edgeTop, boolean edgeBottom,
                                    int scaledWidth, int scaledHeight, float alpha, boolean edge,
                                    boolean frosted, boolean flatBlur) {
        if (alpha <= 0.0F || w < 4.0F || h < 4.0F || scaledWidth <= 0 || scaledHeight <= 0) return false;
        Minecraft mc = Minecraft.getMinecraft();
        int dispW = mc.displayWidth;
        int dispH = mc.displayHeight;
        if (dispW <= 0 || dispH <= 0) return false;

        // 进出成对还原状态，漏一个 enable 位后面的模块就画不出来
        boolean prevDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean prevTex = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean prevAlpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean prevCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        int prevBlendSrc = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int prevBlendDst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int prevBlendSrcA = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int prevBlendDstA = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        try {
            IntBuffer ib = VIEWPORT_BUFFER;
            ib.clear();
            GL11.glGetInteger(GL11.GL_VIEWPORT, ib);
            boolean viewportOk = ib.get(0) == 0 && ib.get(1) == 0
                    && ib.get(2) == dispW && ib.get(3) == dispH;

            float sx = dispW / (float) scaledWidth;
            float sy = dispH / (float) scaledHeight;

            // 本地坐标 → 屏幕坐标，带 w 除法，透视下也对
            float[] box = screenBox(readMvp(), x, y, w, h, scaledWidth, scaledHeight);
            if (box == null) return false;
            float minX = box[0];
            float minY = box[1];
            float maxX = box[2];
            float maxY = box[3];
            if (maxX - minX < 4.0F || maxY - minY < 4.0F) return false;

            // 半径换算到屏幕单位，夹到半宽半高
            float ratioX = (maxX - minX) / w;
            float ratioY = (maxY - minY) / h;
            float scaleR = (ratioX + ratioY) * 0.5F;
            float rTop = radiusTop * scaleR;
            float rBot = radiusBottom * scaleR;
            if (!(rTop > 0.0F)) rTop = 0.0F;
            if (!(rBot > 0.0F)) rBot = 0.0F;
            float maxR = Math.min(maxX - minX, maxY - minY) * 0.5F;
            if (rTop > maxR) rTop = maxR;
            if (rBot > maxR) rBot = maxR;
            float radius = Math.max(radiusTop, radiusBottom);

            // shader 挂了就返回 false 走原样式
            if (frosted || flatBlur) {
                if (!ensureProgram()) return false;
                // 先抓帧和模糊，再画玻璃，否则会采到自己
                if (!prepareBackground(dispW, dispH, viewportOk)) return false;
            } else if (!ensureFlatProgram()) {
                return false;
            }
            boolean blurred = down3 != null && frameBlurred;

            // 贴形状的外圈柔光
            if (edge) drawGlow(x, y, w, h, radius, alpha, frosted ? 1.0F : gameGlowScale());

            if (frosted || flatBlur) {
                boolean ok = drawGlass(blurred ? down3.framebufferTexture : frameTex,
                        minX, minY, maxX - minX, maxY - minY, rTop, rBot,
                        scaledWidth, scaledHeight, dispW, dispH, sx, sy, alpha,
                        edgeTop, edgeBottom, flatBlur ? false : edge, flatBlur);
                if (ok && edge && !flatBlur) {
                    drawOutline(x, y, w, h, radius, alpha);
                }
                return ok;
            }
            return drawFlat(box, rTop, rBot, scaledWidth, scaledHeight, sx, alpha);
        } catch (Throwable t) {
            // 渲染里不能抛，失败当没画成
            logFailure(t);
            return false;
        } finally {
            restore(prevDepth, GlStateManager::enableDepth, GlStateManager::disableDepth);
            restore(prevBlend, GlStateManager::enableBlend, GlStateManager::disableBlend);
            restore(prevTex, GlStateManager::enableTexture2D, GlStateManager::disableTexture2D);
            restore(prevAlpha, GlStateManager::enableAlpha, GlStateManager::disableAlpha);
            restore(prevCull, GlStateManager::enableCull, GlStateManager::disableCull);
            GlStateManager.tryBlendFuncSeparate(prevBlendSrc, prevBlendDst, prevBlendSrcA, prevBlendDstA);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    /**
     * 只补外圈柔光，不画底。分段面板在所有段画完后调一次。
     */
    public static void drawEdge(float x, float y, float w, float h, float radius, float alpha) {
        if (alpha <= 0.0F || w < 4.0F || h < 4.0F) return;
        try {
            drawGlow(x, y, w, h, radius, alpha, gameGlowScale());
        } catch (Throwable t) {
            logFailure(t);
        }
    }

    private static void restore(boolean wasOn, Runnable enable, Runnable disable) {
        if (wasOn) enable.run();
        else disable.run();
    }

    /**
     * 把后续绘制裁进矩形（坐标同 {@link #draw}，会过 MVP 投到屏幕）。装不上返回 false。
     */
    public static boolean enableScissor(float x, float y, float w, float h,
                                        int scaledWidth, int scaledHeight) {
        disableScissor();
        if (w <= 0.0F || h <= 0.0F || scaledWidth <= 0 || scaledHeight <= 0) return false;
        Minecraft mc = Minecraft.getMinecraft();
        int dispW = mc.displayWidth;
        int dispH = mc.displayHeight;
        if (dispW <= 0 || dispH <= 0) return false;
        IntBuffer ib = VIEWPORT_BUFFER;
        ib.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, ib);
        if (ib.get(0) != 0 || ib.get(1) != 0 || ib.get(2) != dispW || ib.get(3) != dispH) return false;
        float[] box = screenBox(readMvp(), x, y, w, h, scaledWidth, scaledHeight);
        if (box == null) return false;
        float sx = dispW / (float) scaledWidth;
        float sy = dispH / (float) scaledHeight;
        int left = (int) Math.floor(box[0] * sx);
        int right = (int) Math.ceil(box[2] * sx);
        int bottom = (int) Math.floor(dispH - box[3] * sy);
        int top = (int) Math.ceil(dispH - box[1] * sy);
        if (left < 0) left = 0;
        if (bottom < 0) bottom = 0;
        if (right > dispW) right = dispW;
        if (top > dispH) top = dispH;
        if (right - left <= 0 || top - bottom <= 0) return false;
        SCISSOR_BUFFER.clear();
        GL11.glGetInteger(GL11.GL_SCISSOR_BOX, SCISSOR_BUFFER);
        scissorPrevX = SCISSOR_BUFFER.get(0);
        scissorPrevY = SCISSOR_BUFFER.get(1);
        scissorPrevW = SCISSOR_BUFFER.get(2);
        scissorPrevH = SCISSOR_BUFFER.get(3);
        scissorWasOn = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(left, bottom, right - left, top - bottom);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        scissorOn = true;
        return true;
    }

    public static void disableScissor() {
        if (!scissorOn) return;
        scissorOn = false;
        GL11.glScissor(scissorPrevX, scissorPrevY, scissorPrevW, scissorPrevH);
        if (scissorWasOn) GL11.glEnable(GL11.GL_SCISSOR_TEST);
        else GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    // ---------------------------------------------------------------- 抓帧 / 模糊

    private static boolean prepareBackground(int dispW, int dispH, boolean viewportOk) {
        int prevActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int prevFb = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        IntBuffer ib = VIEWPORT_BUFFER;
        ib.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, ib);
        int vpX = ib.get(0);
        int vpY = ib.get(1);
        int vpW = ib.get(2);
        int vpH = ib.get(3);
        int prevTex = 0;
        boolean prevTexEnabled = false;
        boolean ok;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            prevTexEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            ensureFrameCopy(dispW, dispH, viewportOk);
            ok = frameValid && blurWholeFrame(dispW, dispH);
        } catch (Throwable t) {
            logFailure(t);
            ok = false;
        } finally {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, prevFb);
            GL11.glViewport(vpX, vpY, vpW, vpH);
            if (prevTexEnabled) GL11.glEnable(GL11.GL_TEXTURE_2D);
            else GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);
            GL11.glPopAttrib();
            GL13.glActiveTexture(prevActive);
        }
        return ok;
    }

    private static void ensureFrameCopy(int dispW, int dispH, boolean viewportOk) {
        long now = System.nanoTime();
        if (frameCapturedSeq == frameSeq && frameValid && now - frameCapturedNs < 200_000_000L) return;
        frameCapturedSeq = frameSeq;
        frameCapturedNs = now;
        frameValid = false;
        frameBlurred = false;
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

    // 整帧模糊一帧只算一次，所有玻璃共用；降采样到 1/4 再做分离式高斯
    private static boolean blurWholeFrame(int dispW, int dispH) {
        if (frameBlurred && down1 != null) return true;
        if (!ensureScreenDowns(dispW, dispH)) return false;
        if (!pass(down1, frameTex, 0.0F, 0.0F, 1.0F, 1.0F)) return false;
        if (!pass(down2, down1.framebufferTexture, 0.0F, 0.0F, 1.0F, 1.0F)) return false;
        if (!blurDown2()) {
            if (!pass(down3, down2.framebufferTexture, 0.0F, 0.0F, 1.0F, 1.0F)) return false;
        }
        frameBlurred = true;
        return true;
    }

    private static boolean blurDown2() {
        if (!ensureBlurProgram()) return false;
        float stepH = 1.0F / Math.max(1, down2.framebufferWidth);
        float stepV = 1.0F / Math.max(1, down2.framebufferHeight);
        if (!blurPass(downTmp, down2.framebufferTexture, stepH, 0.0F)) return false;
        return blurPass(down3, downTmp.framebufferTexture, 0.0F, stepV);
    }

    private static boolean ensureBlurProgram() {
        if (blurProgram > 0 && !GL20.glIsProgram(blurProgram)) {
            blurProgram = 0;
            blurTried = false;
        }
        if (blurProgram > 0) return true;
        if (blurTried) return false;
        blurTried = true;
        int vs = compile(GL20.GL_VERTEX_SHADER, BLUR_VERT);
        int fs = compile(GL20.GL_FRAGMENT_SHADER, BLUR_FRAG);
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
            System.err.println("[FrostedGlass] blur link failed: " + GL20.glGetProgramInfoLog(id, 8192));
            GL20.glDeleteProgram(id);
            return false;
        }
        blurProgram = id;
        uBlurContent = GL20.glGetUniformLocation(id, "uContent");
        uBlurStep = GL20.glGetUniformLocation(id, "uStep");
        return true;
    }

    private static boolean blurPass(Framebuffer dst, int srcTex, float stepU, float stepV) {
        if (!ensureBlurProgram()) return false;
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
            GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
            GL20.glUseProgram(blurProgram);
            if (uBlurContent >= 0) GL20.glUniform1i(uBlurContent, 0);
            if (uBlurStep >= 0) GL20.glUniform2f(uBlurStep, stepU, stepV);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2d(0.0D, 0.0D);
            GL11.glVertex2d(0.0D, 0.0D);
            GL11.glTexCoord2d(1.0D, 0.0D);
            GL11.glVertex2d(1.0D, 0.0D);
            GL11.glTexCoord2d(1.0D, 1.0D);
            GL11.glVertex2d(1.0D, 1.0D);
            GL11.glTexCoord2d(0.0D, 1.0D);
            GL11.glVertex2d(0.0D, 1.0D);
            GL11.glEnd();
        } finally {
            GL20.glUseProgram(0);
            endPassOrtho();
        }
        return true;
    }

    private static boolean ensureScreenDowns(int dispW, int dispH) {
        int w1 = Math.max(1, dispW / 2);
        int h1 = Math.max(1, dispH / 2);
        int w2 = Math.max(1, w1 / 2);
        int h2 = Math.max(1, h1 / 2);
        if (down1 != null && downW1 == w1 && downH1 == h1) return true;
        deleteDowns();
        try {
            down1 = new Framebuffer(w1, h1, false);
            down2 = new Framebuffer(w2, h2, false);
            downTmp = new Framebuffer(w2, h2, false);
            down3 = new Framebuffer(w2, h2, false);
            setLinear(down1);
            setLinear(down2);
            setLinear(downTmp);
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
        if (downTmp != null) {
            downTmp.deleteFramebuffer();
            downTmp = null;
        }
        if (down3 != null) {
            down3.deleteFramebuffer();
            down3 = null;
        }
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

    // ---------------------------------------------------------------- 柔光 / 描边

    // 外圈柔光：SDF 外扩，按形状内采样点占比发光，只在形状外侧。
    // scale 是放大倍数：游戏内实底面板用更大的光，主界面磨砂卡保持原样。
    private static void drawGlow(float x, float y, float w, float h, float radius, float alpha, float scale) {
        if (alpha <= 0.0F) return;
        Minecraft mc = Minecraft.getMinecraft();
        int dispW = mc.displayWidth;
        int dispH = mc.displayHeight;
        if (dispW <= 0 || dispH <= 0) return;
        ScaledResolution sr = new ScaledResolution(mc);
        int scaledW = sr.getScaledWidth();
        int scaledH = sr.getScaledHeight();
        if (scaledW <= 0 || scaledH <= 0) return;
        float[] box = screenBox(readMvp(), x, y, w, h, scaledW, scaledH);
        if (box == null) return;
        float bw = box[2] - box[0];
        float bh = box[3] - box[1];
        if (bw < 4.0F || bh < 4.0F) return;
        if (!ensureGlowProgram()) {
            drawGlowLegacy(x, y, w, h, radius, alpha);
            return;
        }
        // 半径是本地单位，先折算成屏幕单位
        float scaleR = ((bw / w) + (bh / h)) * 0.5F;
        if (!(scale > 1.0F)) scale = 1.0F;
        float r = Math.max(0.0F, Math.min(radius * scaleR, Math.min(bw, bh) * 0.5F));
        float blurFactor = gameGlowBlur();
        float blurR = Math.min(10.0F * scale * blurFactor, Math.max(4.0F * scale * blurFactor, Math.min(bw, bh) * 0.5F));
        float glowAlpha = Math.min(220.0F, 110.0F * scale) / 255.0F * gameGlowAlpha();
        float sx = dispW / (float) scaledW;
        float aa = 1.0F / Math.max(1.0F, sx);
        if (aa < 0.5F) aa = 0.5F;
        if (aa > 1.5F) aa = 1.5F;
        int prevActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        boolean mvPushed = false;
        boolean projPushed = false;
        try {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            projPushed = true;
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            mvPushed = true;

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0D, scaledW, scaledH, 0.0D, -1.0D, 1.0D);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glLoadIdentity();

            GL20.glUseProgram(glowProgram);
            if (uGlowRect >= 0) GL20.glUniform4f(uGlowRect, box[0], box[1], box[2], box[3]);
            if (uGlowRadius >= 0) GL20.glUniform1f(uGlowRadius, r);
            if (uGlowBlurR >= 0) GL20.glUniform1f(uGlowBlurR, blurR);
            if (uGlowAA >= 0) GL20.glUniform1f(uGlowAA, aa);
            if (uGlowAlpha >= 0) GL20.glUniform1f(uGlowAlpha, alpha * glowAlpha);

            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            quad(box[0] - blurR, box[1] - blurR, bw + blurR * 2.0F, bh + blurR * 2.0F);
        } catch (Throwable t) {
            logFailure(t);
        } finally {
            if (mvPushed) {
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
            }
            if (projPushed) {
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
            }
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopAttrib();
            GL13.glActiveTexture(prevActive);
            GL20.glUseProgram(prevProgram);
        }
    }

    private static void drawGlowLegacy(float x, float y, float w, float h, float radius, float alpha) {
        int rgb = 0xFFFFFF;
        float[] dist = {0.75F, 1.6F, 2.5F, 3.5F, 4.6F, 5.8F, 7.1F, 8.5F, 10.0F, 11.6F};
        float[] strength = {0.34F, 0.30F, 0.26F, 0.22F, 0.18F, 0.145F, 0.11F, 0.08F, 0.05F, 0.025F};
        for (int i = 0; i < dist.length; i++) {
            int a = Math.round(255.0F * strength[i] * alpha);
            if (a <= 0) continue;
            RenderUtil.drawRoundedRectOutline(
                    x - dist[i], y - dist[i],
                    w + dist[i] * 2.0F, h + dist[i] * 2.0F,
                    radius + dist[i],
                    1.4F,
                    (a << 24) | rgb,
                    true, true, true, true
            );
        }
    }

    private static boolean ensureGlowProgram() {
        if (glowProgram > 0 && !GL20.glIsProgram(glowProgram)) {
            glowProgram = 0;
            glowTried = false;
        }
        if (glowProgram > 0) return true;
        if (glowTried) return false;
        glowTried = true;
        int vs = compile(GL20.GL_VERTEX_SHADER, VERTEX);
        int fs = compile(GL20.GL_FRAGMENT_SHADER, GLOW_FRAG);
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
            System.err.println("[FrostedGlass] glow link failed: " + GL20.glGetProgramInfoLog(id, 8192));
            GL20.glDeleteProgram(id);
            return false;
        }
        glowProgram = id;
        uGlowRect = GL20.glGetUniformLocation(id, "uRect");
        uGlowRadius = GL20.glGetUniformLocation(id, "uRadius");
        uGlowBlurR = GL20.glGetUniformLocation(id, "uBlurR");
        uGlowAA = GL20.glGetUniformLocation(id, "uAA");
        uGlowAlpha = GL20.glGetUniformLocation(id, "uAlpha");
        return true;
    }

    private static boolean ensureFlatProgram() {
        if (flatProgram > 0 && !GL20.glIsProgram(flatProgram)) {
            flatProgram = 0;
            flatTried = false;
        }
        if (flatProgram > 0) return true;
        if (flatTried) return false;
        flatTried = true;
        int vs = compile(GL20.GL_VERTEX_SHADER, VERTEX);
        int fs = compile(GL20.GL_FRAGMENT_SHADER, FLAT_FRAG);
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
            System.err.println("[FrostedGlass] flat link failed: " + GL20.glGetProgramInfoLog(id, 8192));
            GL20.glDeleteProgram(id);
            return false;
        }
        flatProgram = id;
        uFlatRect = GL20.glGetUniformLocation(id, "uRect");
        uFlatRadiusTop = GL20.glGetUniformLocation(id, "uRadiusTop");
        uFlatRadiusBottom = GL20.glGetUniformLocation(id, "uRadiusBottom");
        uFlatAA = GL20.glGetUniformLocation(id, "uAA");
        uFlatColor = GL20.glGetUniformLocation(id, "uColor");
        uFlatAlpha = GL20.glGetUniformLocation(id, "uAlpha");
        return uFlatRect >= 0 && uFlatColor >= 0;
    }

    private static boolean drawFlat(float[] box, float rTop, float rBot,
                                    int scaledW, int scaledH, float sx, float alpha) {
        if (!ensureFlatProgram()) return false;
        float w = box[2] - box[0];
        float h = box[3] - box[1];
        if (w < 4.0F || h < 4.0F) return false;
        float aa = 1.0F / Math.max(1.0F, sx);
        if (aa < 0.5F) aa = 0.5F;
        if (aa > 1.5F) aa = 1.5F;
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        boolean mvPushed = false;
        boolean projPushed = false;
        try {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            projPushed = true;
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            mvPushed = true;

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0D, scaledW, scaledH, 0.0D, -1.0D, 1.0D);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glLoadIdentity();

            GL20.glUseProgram(flatProgram);
            GL20.glUniform4f(uFlatRect, box[0], box[1], box[2], box[3]);
            if (uFlatRadiusTop >= 0) GL20.glUniform1f(uFlatRadiusTop, rTop);
            if (uFlatRadiusBottom >= 0) GL20.glUniform1f(uFlatRadiusBottom, rBot);
            if (uFlatAA >= 0) GL20.glUniform1f(uFlatAA, aa);
            GL20.glUniform4f(uFlatColor, 58.0F / 255.0F, 62.0F / 255.0F, 68.0F / 255.0F, 108.0F / 255.0F);
            if (uFlatAlpha >= 0) GL20.glUniform1f(uFlatAlpha, alpha);

            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            quad(box[0], box[1], w, h);
            return true;
        } catch (Throwable t) {
            logFailure(t);
            return false;
        } finally {
            if (mvPushed) {
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
            }
            if (projPushed) {
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
            }
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopAttrib();
            GL20.glUseProgram(prevProgram);
        }
    }

    // 1px 描边，画在玻璃上收边
    private static void drawOutline(float x, float y, float w, float h, float radius, float alpha) {
        if (alpha <= 0.0F) return;
        int a = Math.round(90.0F * alpha);
        if (a <= 0) return;
        RenderUtil.drawRoundedRectOutline(
                x + 0.5F, y + 0.5F,
                w - 1.0F, h - 1.0F,
                Math.max(0.0F, radius - 0.5F),
                1.0F,
                (a << 24) | 0xFFFFFF,
                true, true, true, true
        );
    }

    // shader 挂掉的兜底：贴边高光，顶亮底暗
    public static void drawRim(float x, float y, float w, float h, float radius) {
        float r = Math.min(radius, Math.min(w, h) * 0.5F);
        if (r < 1.0F || w <= 4.0F || h <= 4.0F) return;
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(1.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 0.55F);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int a = 180; a <= 360; a += 3) {
            double rad = Math.toRadians(a);
            double cx = a <= 270 ? x + r : x + w - r;
            double cy = y + r;
            GL11.glVertex2d(cx + Math.cos(rad) * r, cy + Math.sin(rad) * r);
        }
        GL11.glEnd();
        GlStateManager.color(0.0F, 0.0F, 0.0F, 0.4F);
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
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    // ---------------------------------------------------------------- 玻璃本体

    private static boolean drawGlass(int tex, float x, float y, float w, float h,
                                     float radiusTop, float radiusBottom,
                                     int scaledW, int scaledH, int dispW, int dispH,
                                     float sx, float sy, float alpha,
                                     boolean edgeTop, boolean edgeBottom, boolean edge, boolean flatBlur) {
        if (!ensureProgram()) return false;
        if (tex <= 0) return false;
        float minD = Math.min(w, h);
        if (minD < 4.0F) return false;
        int prevActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        boolean mvPushed = false;
        boolean projPushed = false;
        try {
            // 切到 GUI 正交矩阵，片元里 p = vGui 直接对上 uRect 和背景采样
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            projPushed = true;
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            mvPushed = true;

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0D, scaledW, scaledH, 0.0D, -1.0D, 1.0D);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glLoadIdentity();

            float aa = 1.0F / Math.max(1.0F, sx);
            if (aa < 0.5F) aa = 0.5F;
            if (aa > 1.5F) aa = 1.5F;

            GL20.glUseProgram(program);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
            GL20.glUniform1i(uContent, 0);
            GL20.glUniform4f(uRect, x, y, x + w, y + h);
            GL20.glUniform2f(uMap, 1.0F / (float) scaledW, 1.0F / (float) scaledH);
            GL20.glUniform1f(uAlpha, alpha);
            GL20.glUniform1f(uRadius, Math.min(Math.max(radiusTop, radiusBottom), minD * 0.5F));
            GL20.glUniform1f(uRadiusTop, Math.min(radiusTop, minD * 0.5F));
            GL20.glUniform1f(uRadiusBottom, Math.min(radiusBottom, minD * 0.5F));
            // 是外边界才画折射/明暗带，拼缝处压掉
            GL20.glUniform1f(uClipTop, edgeTop ? 1.0F : 0.0F);
            GL20.glUniform1f(uClipBot, edgeBottom ? 1.0F : 0.0F);
            GL20.glUniform1f(uAA, aa);
            // backdrop-filter: saturate(140%)
            GL20.glUniform1f(uSat, flatBlur ? 1.0F : 1.40F);
            GL20.glUniform1f(uTint, flatBlur ? 0.0F : 0.52F);
            // inset 深度按元素尺寸同比缩放
            GL20.glUniform1f(uBevelH, Math.max(1.0F, Math.min(4.0F, minD * 0.05F)));
            GL20.glUniform1f(uHiA, flatBlur ? 0.0F : 0.25F);
            GL20.glUniform1f(uShA, flatBlur ? 0.0F : 0.35F);
            // 边环宽 ≈ 1.5 设备像素
            GL20.glUniform1f(uLineW, 1.5F * aa);
            GL20.glUniform1f(uRimA, 1.0F);
            // 边缘折射量（uv 单位）
            GL20.glUniform1f(uDisp, flatBlur ? 0.0F : 0.0017F);

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

            // 边环加色；分段模式交给 drawOutline 统一画
            if (edge) {
                GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
                GL20.glUniform1f(uMode, 1.0F);
                quad(x, y, w, h);
            }
            return true;
        } catch (Throwable t) {
            logFailure(t);
            return false;
        } finally {
            if (mvPushed) {
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
            }
            if (projPushed) {
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
            }
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopAttrib();
            GL13.glActiveTexture(prevActive);
            GL20.glUseProgram(prevProgram);
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

    private static float[] readMvp() {
        MV_BUFFER.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MV_BUFFER);
        for (int i = 0; i < 16; i++) MV[i] = MV_BUFFER.get(i);
        PROJ_BUFFER.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJ_BUFFER);
        for (int i = 0; i < 16; i++) PROJ[i] = PROJ_BUFFER.get(i);
        // MVP = PROJ * MV，都是列主序
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                float sum = 0.0F;
                for (int k = 0; k < 4; k++) {
                    sum += PROJ[k * 4 + r] * MV[c * 4 + k];
                }
                MVP[c * 4 + r] = sum;
            }
        }
        return MVP;
    }

    // 四角过 MVP → NDC → 屏幕坐标，取包围盒
    private static float[] screenBox(float[] m, float x, float y, float w, float h,
                                     int scaledW, int scaledH) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            float px = (i == 0 || i == 3) ? x : x + w;
            float py = (i < 2) ? y : y + h;
            float cw = m[3] * px + m[7] * py + m[15];
            // w<=0 = 角在相机背后，除法会翻到屏幕另一侧，直接放弃
            if (!(cw > 1.0E-6F)) return null;
            float ndcX = (m[0] * px + m[4] * py + m[12]) / cw;
            float ndcY = (m[1] * px + m[5] * py + m[13]) / cw;
            float guiX = (ndcX * 0.5F + 0.5F) * (float) scaledW;
            float guiY = (0.5F - ndcY * 0.5F) * (float) scaledH;
            if (guiX < minX) minX = guiX;
            if (guiX > maxX) maxX = guiX;
            if (guiY < minY) minY = guiY;
            if (guiY > maxY) maxY = guiY;
        }
        if (!Float.isFinite(minX) || !Float.isFinite(minY)
                || !Float.isFinite(maxX) || !Float.isFinite(maxY)) return null;
        if (maxX < 0.0F || minX > (float) scaledW
                || maxY < 0.0F || minY > (float) scaledH) return null;
        // 贴近平面时包围盒会爆炸，直接放弃
        if (maxX - minX > (float) scaledW * 8.0F || maxY - minY > (float) scaledH * 8.0F) return null;
        return new float[]{minX, minY, maxX, maxY};
    }

    private static void logFailure(Throwable t) {
        if (drawFailureLogged) return;
        drawFailureLogged = true;
        System.err.println("[FrostedGlass] draw failed: " + t);
        t.printStackTrace();
    }

    // ---------------------------------------------------------------- shader

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
            System.err.println("[FrostedGlass] link failed: " + GL20.glGetProgramInfoLog(id, 8192));
            GL20.glDeleteProgram(id);
            return false;
        }
        program = id;
        uContent = loc("uContent");
        uRect = loc("uRect");
        uMap = loc("uMap");
        uRadius = loc("uRadius");
        uRadiusTop = loc("uRadiusTop");
        uRadiusBottom = loc("uRadiusBottom");
        uClipTop = loc("uClipTop");
        uClipBot = loc("uClipBot");
        uAA = loc("uAA");
        uSat = loc("uSat");
        uTint = loc("uTint");
        uDisp = loc("uDisp");
        uBevelH = loc("uBevelH");
        uHiA = loc("uHiA");
        uShA = loc("uShA");
        uLineW = loc("uLineW");
        uRimA = loc("uRimA");
        uAlpha = loc("uAlpha");
        uMode = loc("uMode");
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
            System.err.println("[FrostedGlass] compile failed: " + GL20.glGetShaderInfoLog(shader, 8192));
            GL20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }
}
