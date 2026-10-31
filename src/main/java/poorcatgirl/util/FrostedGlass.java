package poorcatgirl.util;

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
 * 磨砂玻璃：背景模糊 + 1px 白描边 + 外圈柔光。
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

    private static final float BACKGROUND_BLUR_STRENGTH = 10.0F;
    private static final float BLUR_GRAY_RED = 58.0F / 255.0F;
    private static final float BLUR_GRAY_GREEN = 62.0F / 255.0F;
    private static final float BLUR_GRAY_BLUE = 68.0F / 255.0F;
    private static final float BLUR_GRAY_AMOUNT = 108.0F / 255.0F;
    private static final float CHAT_GRAY_AMOUNT = 127.0F / 255.0F;
    private static final float HUD_GRAY_AMOUNT = 110.0F / 255.0F;
    private static final float UI_GRAY_AMOUNT = 0.0F;
    private static final float GUI_GRAY_AMOUNT = 92.0F / 255.0F;
    private static float pendingGrayAmount = -1.0F;
    private static int pendingBackingColor = -1;
    private static float pendingMinDimension = 4.0F;
    private static float pendingGradientBottom = -1.0F;
    private static int pendingGlowColor = 0xFFFFFF;
    private static boolean pendingMenuCapture;
    private static Framebuffer menuFrame;
    private static Framebuffer menuGaussA;
    private static Framebuffer menuGaussB;
    private static final float GLASS_TINT = 0.52F;
    private static final float BUTTON_GLASS_TINT = 0.74F;
    private static final float GUI_GAUSS_STRENGTH = 3.15F;
    private static final long GUI_BLUR_REFRESH_NS = 80_000_000L;
    private static float guiBackdropDim = 0.0F;
    private static final String GAUSS_FRAG =
            "#version 120\n" +
            "uniform sampler2D uContent;\n" +
            "uniform vec2 uInputResolution;\n" +
            "uniform float uBlurStrength;\n" +
            "uniform vec2 uDirection;\n" +
            "void main(void) {\n" +
            "    vec2 uv = gl_FragCoord.xy / uInputResolution;\n" +
            "    vec2 texel = 1.0 / uInputResolution;\n" +
            "    float sigma = max(uBlurStrength * 0.5, 0.0001);\n" +
            "    float twoSigmaSq = 2.0 * sigma * sigma;\n" +
            "    float stepSize = max(uBlurStrength * 0.25, 0.0001);\n" +
            "    vec4 sum = vec4(0.0);\n" +
            "    float total = 0.0;\n" +
            "    for (int i = -4; i <= 4; i++) {\n" +
            "        float fi = float(i);\n" +
            "        float d = fi * stepSize;\n" +
            "        float w = exp(-(d * d) / twoSigmaSq);\n" +
            "        vec4 c = texture2D(uContent, uv + uDirection * texel * d);\n" +
            "        sum += c * w;\n" +
            "        total += w;\n" +
            "    }\n" +
            "    gl_FragColor = sum / total;\n" +
            "}\n";
    private static final String BLUR_FRAG =
            "#version 120\n" +
            "uniform sampler2D uContent;\n" +
            "uniform vec2 uInputResolution;\n" +
            "uniform float uBlurStrength;\n" +
            "void main(void) {\n" +
            "    const float TAU = 6.28318530718;\n" +
            "    const int DIRECTION_COUNT = 16;\n" +
            "    const int RING_COUNT = 5;\n" +
            "    const float SAMPLE_COUNT = float(1 + DIRECTION_COUNT * RING_COUNT);\n" +
            "    vec2 uv = gl_FragCoord.xy / uInputResolution;\n" +
            "    vec2 radius = max(uBlurStrength, 0.0) / uInputResolution;\n" +
            "    vec4 center = texture2D(uContent, uv);\n" +
            "    vec3 colorSum = center.rgb;\n" +
            "    for (int direction = 0; direction < DIRECTION_COUNT; direction++) {\n" +
            "        float angle = TAU * float(direction) / float(DIRECTION_COUNT);\n" +
            "        vec2 offset = vec2(cos(angle), sin(angle)) * radius;\n" +
            "        for (int ring = 1; ring <= RING_COUNT; ring++) {\n" +
            "            vec4 sampleColor = texture2D(uContent, uv + offset * (float(ring) / float(RING_COUNT)));\n" +
            "            float signal = max(sampleColor.a, max(sampleColor.r, max(sampleColor.g, sampleColor.b)));\n" +
            "            colorSum += signal > 1e-5 ? sampleColor.rgb : center.rgb;\n" +
            "        }\n" +
            "    }\n" +
            "    gl_FragColor = vec4(colorSum / SAMPLE_COUNT, center.a);\n" +
            "}\n";

    // v2.3 card blur: half-res, quarter-res, then a 7-tap separable pass.
    private static final String FROSTED_BLUR_FRAG =
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
            "uniform float uDim;\n" +
            "uniform float uTint;\n" +
            "uniform vec4 uGrayBacking;\n" +
            "uniform float uDisp;\n" +
            "uniform float uBevelH;\n" +
            "uniform float uHiA;\n" +
            "uniform float uShA;\n" +
            "uniform float uLineW;\n" +
            "uniform float uRimA;\n" +
            "uniform float uAlpha;\n" +
            "uniform float uMode;\n" +
            "uniform float uEpsilonMask;\n" +
            // 底部渐变：<=0 关闭，否则 alpha 从 1.0 线性降到 uGradBot。
            "uniform float uGradBot;\n" +
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
            "    float aa = uEpsilonMask > 0.5 ? max(fwidth(sd), 1e-4) : max(uAA, 0.25);\n" +
            "    float dT = c.y + hs.y;\n" +
            "    float dB = hs.y - c.y;\n" +
            "    float dL = c.x + hs.x;\n" +
            "    float dR = hs.x - c.x;\n" +
            "    if (sd >= aa) discard;\n" +
            "    float edge = uEpsilonMask > 0.5 ? 1.0 - smoothstep(-aa, aa, sd) : clamp(0.5 - sd / aa, 0.0, 1.0);\n" +
            // 拼缝边不做 AA，否则接缝处叠出半透明缝
            "    if (uClipTop < 0.5 && dT < aa && dT <= dL && dT <= dR) edge = 1.0;\n" +
            "    if (uClipBot < 0.5 && dB < aa && dB <= dL && dB <= dR) edge = 1.0;\n" +
            "    float gradA = 1.0;\n" +
            "    if (uGradBot > 0.001) {\n" +
            "        float gy = clamp((p.y - uRect.y) / max(uRect.w - uRect.y, 0.0001), 0.0, 1.0);\n" +
            "        gradA = mix(1.0, uGradBot, gy);\n" +
            "    }\n" +
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
            "    if (uEpsilonMask < 0.5) uv = clamp(uv, vec2(0.002), vec2(0.998));\n" +
            "    vec3 bg = texture2D(uContent, uv).rgb;\n" +
            "    float lum = dot(bg, vec3(0.2126, 0.7152, 0.0722));\n" +
            "    if (uMode < 0.5) {\n" +
            // saturate(140%)，不加蒙层，底色就是背景本身
            "        vec3 col = mix(vec3(lum), bg, uSat);\n" +
            "        col = mix(col, vec3(1.0), uTint);\n" +
            "        col *= (1.0 - uDim);\n" +
            // 顶部内亮、底部内暗
            "        float bt = clamp(1.0 + min(sdFx, 0.0) / max(uBevelH, 0.5), 0.0, 1.0) * supBevel;\n" +
            "        col += vec3(1.0) * uHiA * max(0.0, -grad.y) * bt;\n" +
            "        col *= 1.0 - uShA * max(0.0, grad.y) * bt;\n" +
            "        col = mix(col, uGrayBacking.rgb, uGrayBacking.a);\n" +
            "        gl_FragColor = vec4(clamp(col, 0.0, 1.0), edge * uAlpha * gradA);\n" +
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
            "        float a = clamp(line * ring * uRimA * edge, 0.0, 1.0) * uAlpha * gradA;\n" +
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
            "uniform vec3 uGlowColor;\n" +
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
            "    gl_FragColor = vec4(uGlowColor, outA);\n" +
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
    private static int uDim = -1;
    private static int uTint = -1;
    private static int uGrayBacking = -1;
    private static int uDisp = -1;
    private static int uBevelH = -1;
    private static int uHiA = -1;
    private static int uShA = -1;
    private static int uLineW = -1;
    private static int uRimA = -1;
    private static int uAlpha = -1;
    private static int uMode = -1;
    private static int uEpsilonMask = -1;
    private static int uGradBot = -1;

    private static int blurProgram;
    private static boolean blurTried;
    private static int uBlurContent = -1;
    private static int uBlurResolution = -1;
    private static int uBlurStrength = -1;

    private static int glowProgram;
    private static boolean glowTried;
    private static int uGlowRect = -1;
    private static int uGlowRadius = -1;
    private static int uGlowBlurR = -1;
    private static int uGlowAA = -1;
    private static int uGlowAlpha = -1;
    private static int uGlowColor = -1;

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
        poorcatgirl.module.modules.HUD hud = poorcatgirl.module.modules.HUD.get();
        return hud == null ? 1.45F : hud.glowSize.getValue();
    }

    private static float gameGlowAlpha() {
        poorcatgirl.module.modules.HUD hud = poorcatgirl.module.modules.HUD.get();
        return hud == null ? 1.0F : hud.glowOpacity.getValue() / 100.0F;
    }

    private static float gameGlowBlur() {
        poorcatgirl.module.modules.HUD hud = poorcatgirl.module.modules.HUD.get();
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
    private static long lastFrameBlurDemandSeq = Long.MIN_VALUE;

    private static Framebuffer blurredFrame;
    private static Framebuffer hudBlurTmp;

    private static int chatFrameTex = -1;
    private static int chatFrameW;
    private static int chatFrameH;
    private static boolean chatFrameValid;
    private static boolean chatBlurReady;
    private static long chatCapturedSeq = Long.MIN_VALUE;
    private static Framebuffer chatBlurredFrame;
    private static Framebuffer chatBlurTmp;
    private static long chatBlurNs;
    private static boolean pendingLiveChatCapture;

    private static Framebuffer guiGaussA;
    private static Framebuffer guiGaussB;
    private static int guiFrameTex = -1;
    private static int guiFrameW;
    private static int guiFrameH;
    private static boolean guiFrameValid;
    private static long guiFrameNs;
    private static int guiGaussTex = -1;
    private static int guiGaussW;
    private static int guiGaussH;
    private static long guiGaussNs;
    private static long lastCaptureScreenNs;
    private static int gaussProgram;
    private static boolean gaussTried;
    private static int uGaussContent = -1;
    private static int uGaussResolution = -1;
    private static int uGaussStrength = -1;
    private static int uGaussDirection = -1;

    // v2.3 card pipeline (main menu + vanilla buttons).
    private static int frostedProgram;
    private static boolean frostedTried;
    private static int uFrostedContent = -1;
    private static int uFrostedStep = -1;
    private static Framebuffer frostedDown1;
    private static Framebuffer frostedDown2;
    private static Framebuffer frostedTmp;
    private static Framebuffer frostedOut;
    private static int frostedW1;
    private static int frostedH1;
    private static int frostedTex = -1;
    private static long frostedSeq = Long.MIN_VALUE;

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

    /** v2.3 pipeline with a brighter mix, for buttons with no glass card behind them. */
    public static boolean drawFrostedBright(float x, float y, float w, float h, float radius,
                                            int scaledWidth, int scaledHeight, float alpha) {
        if (alpha <= 0.0F || w < 4.0F || h < 4.0F || scaledWidth <= 0 || scaledHeight <= 0) return false;
        return drawImpl(x, y, w, h, radius, radius, true, true,
                scaledWidth, scaledHeight, alpha, true, true, false, 0.0F, false,
                true, false, BUTTON_GLASS_TINT);
    }

    public static boolean drawBlurredFlat(float x, float y, float w, float h,
                                          int scaledWidth, int scaledHeight, float alpha) {
        if (alpha <= 0.0F || w < 4.0F || h < 4.0F || scaledWidth <= 0 || scaledHeight <= 0) return false;
        return drawImpl(x, y, w, h, 0.0F, 0.0F, true, true,
                scaledWidth, scaledHeight, alpha, false, false, true, 0.0F, false);
    }

    // ---- HUD / GUI blur entry points

    public static boolean drawHudBlur(float x, float y, float w, float h, float radius,
                                      int scaledWidth, int scaledHeight, float alpha) {
        return drawHudBlur(x, y, w, h, radius, scaledWidth, scaledHeight, alpha, true);
    }

    public static boolean drawHudBlur(float x, float y, float w, float h, float radius,
                                      int scaledWidth, int scaledHeight, float alpha, boolean edge) {
        return drawHudBlur(x, y, w, h, radius, radius, true, true,
                scaledWidth, scaledHeight, alpha, edge);
    }

    public static boolean drawHudBlur(float x, float y, float w, float h,
                                      float radiusTop, float radiusBottom,
                                      boolean edgeTop, boolean edgeBottom,
                                      int scaledWidth, int scaledHeight, float alpha, boolean edge) {
        return drawImpl(x, y, w, h, radiusTop, radiusBottom, edgeTop, edgeBottom,
                scaledWidth, scaledHeight, alpha, edge, false, true, 0.0F, false, true);
    }

    /** 3D 名牌/伤害标签复用上一帧的模糊，不在世界渲染阶段抓帧。 */
    public static boolean drawHudBlurLastFrame(float x, float y, float w, float h, float radius,
                                               int scaledWidth, int scaledHeight, float alpha) {
        return drawImpl(x, y, w, h, radius, radius, true, true,
                scaledWidth, scaledHeight, alpha, false, false, true, 0.0F, true, true);
    }

    /** 3D 名牌/伤害标签复用上一帧的模糊，不在世界渲染阶段抓帧。 */
    public static boolean drawNameTagBlur(float x, float y, float w, float h, float radius,
                                          int scaledWidth, int scaledHeight, float alpha) {
        float previousMinimum = pendingMinDimension;
        float previousAmount = pendingGrayAmount;
        int previousColor = pendingBackingColor;
        try {
            pendingMinDimension = 1.0F;
            pendingGrayAmount = BLUR_GRAY_AMOUNT;
            pendingBackingColor = -1;
            return drawImpl(x, y, w, h, radius, radius, true, true,
                    scaledWidth, scaledHeight, alpha, false, false, true, 0.0F, true, false, false, GLASS_TINT);
        } finally {
            pendingMinDimension = previousMinimum;
            pendingGrayAmount = previousAmount;
            pendingBackingColor = previousColor;
        }
    }

    /** HUD 玻璃，可自定义柔光色；glow=false 时不画外圈光和描边，只影响本次调用。 */
    public static boolean drawHudBlurColored(float x, float y, float w, float h, float radius,
                                             int scaledWidth, int scaledHeight, float alpha,
                                             boolean glow, int glowRgb) {
        int prev = pendingGlowColor;
        pendingGlowColor = glowRgb & 0xFFFFFF;
        try {
            return drawImpl(x, y, w, h, radius, radius, true, true,
                    scaledWidth, scaledHeight, alpha, glow, false, true, 0.0F, false, true);
        } finally {
            pendingGlowColor = prev;
        }
    }

    public static boolean drawGuiBlur(float x, float y, float w, float h, float radius,
                                      int scaledWidth, int scaledHeight, float alpha) {
        return drawGuiBlur(x, y, w, h, radius, radius, scaledWidth, scaledHeight, alpha);
    }

    public static void setGuiBackdropDim(float dim) {
        guiBackdropDim = dim < 0.0F ? 0.0F : (dim > 1.0F ? 1.0F : dim);
    }

    public static boolean drawGuiBlur(float x, float y, float w, float h,
                                      float radiusTop, float radiusBottom,
                                      int scaledWidth, int scaledHeight, float alpha) {
        return drawImpl(x, y, w, h, radiusTop, radiusBottom, true, true,
                scaledWidth, scaledHeight, alpha, false, false, true, guiBackdropDim, false, true, false, GLASS_TINT);
    }

    public static boolean drawGaussianBackground(float x, float y, float w, float h,
                                                  int scaledWidth, int scaledHeight, float alpha) {
        float previousAmount = pendingGrayAmount;
        boolean previousMenu = pendingMenuCapture;
        try {
            pendingGrayAmount = 0.0F;
            pendingMenuCapture = true;
            return drawImpl(x, y, w, h, 6.0F, 6.0F, true, true,
                    scaledWidth, scaledHeight, alpha, false, false, true,
                    0.0F, false, true, false, 0.0F);
        } finally {
            pendingGrayAmount = previousAmount;
            pendingMenuCapture = previousMenu;
        }
    }

    public static void drawChatBackground(float x, float y, float w, float h, float radius,
                                          int scaledWidth, int scaledHeight, float alpha,
                                          boolean useBlur, int fallbackColor) {
        drawChatBackground(x, y, w, h, radius, scaledWidth, scaledHeight, alpha, useBlur, fallbackColor, true);
    }

    /** @param excludeHotbar chat history must leave the hotbar strip; the input bar must cover it. */
    public static void drawChatBackground(float x, float y, float w, float h, float radius,
                                          int scaledWidth, int scaledHeight, float alpha,
                                          boolean useBlur, int fallbackColor, boolean excludeHotbar) {
        if (w <= 0.0F || h <= 0.0F || scaledWidth <= 0 || scaledHeight <= 0) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.gameSettings.hideGUI) {
            paintChatBackground(x, y, w, h, radius, scaledWidth, scaledHeight, alpha, useBlur, fallbackColor, excludeHotbar);
            return;
        }
        if (!excludeHotbar) {
            paintChatBackground(x, y, w, h, radius, scaledWidth, scaledHeight, alpha, useBlur, fallbackColor, excludeHotbar);
            return;
        }
        int dispW = mc.displayWidth;
        int dispH = mc.displayHeight;
        if (dispW <= 0 || dispH <= 0) return;
        int center = scaledWidth / 2;
        int left = Math.max(0, (int) Math.floor((center - 93) * dispW / (double) scaledWidth));
        int right = Math.min(dispW, (int) Math.ceil((center + 93) * dispW / (double) scaledWidth));
        int hotbarHeight = Math.min(dispH, (int) Math.ceil(24.0D * dispH / scaledHeight));
        boolean wasScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        IntBuffer previous = BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_SCISSOR_BOX, previous);
        int clipX = wasScissor ? previous.get(0) : 0;
        int clipY = wasScissor ? previous.get(1) : 0;
        int clipRight = wasScissor ? clipX + previous.get(2) : dispW;
        int clipTop = wasScissor ? clipY + previous.get(3) : dispH;
        int[][] regions = {{0, hotbarHeight, dispW, dispH},
                {0, 0, left, hotbarHeight}, {right, 0, dispW, hotbarHeight}};
        try {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            for (int[] region : regions) {
                int rx = Math.max(clipX, region[0]);
                int ry = Math.max(clipY, region[1]);
                int rr = Math.min(clipRight, region[2]);
                int rt = Math.min(clipTop, region[3]);
                if (rr <= rx || rt <= ry) continue;
                GL11.glScissor(rx, ry, rr - rx, rt - ry);
                paintChatBackground(x, y, w, h, radius, scaledWidth, scaledHeight, alpha, useBlur, fallbackColor, excludeHotbar);
            }
        } finally {
            GL11.glScissor(previous.get(0), previous.get(1), previous.get(2), previous.get(3));
            if (wasScissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
            else GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    private static void paintChatBackground(float x, float y, float w, float h, float radius,
                                            int scaledWidth, int scaledHeight, float alpha,
                                            boolean useBlur, int fallbackColor, boolean excludeHotbar) {
        boolean ok = false;
        boolean prevLive = pendingLiveChatCapture;
        float prevAmount = pendingGrayAmount;
        int prevColor = pendingBackingColor;
        pendingGrayAmount = useBlur ? GUI_GRAY_AMOUNT : Math.max(0.0F, Math.min(1.0F,
                (fallbackColor >>> 24) / (255.0F * Math.max(0.001F, alpha))));
        pendingBackingColor = useBlur ? -1 : fallbackColor & 0xFFFFFF;
        pendingLiveChatCapture = useBlur && excludeHotbar;
        try {
            if (useBlur) {
                ok = drawImpl(x, y, w, h, radius, radius, true, true,
                        scaledWidth, scaledHeight, alpha, false, false, true, 0.0F, false, false, false, GLASS_TINT);
            }
        } finally {
            pendingLiveChatCapture = prevLive;
            pendingGrayAmount = prevAmount;
            pendingBackingColor = prevColor;
        }
        if (!ok) {
            RenderUtil.drawRoundedRect(x, y, w, h, radius, fallbackColor);
        }
        drawEdge(x, y, w, h, radius, alpha);
    }

    private static boolean drawImpl(float x, float y, float w, float h,
                                    float radiusTop, float radiusBottom,
                                    boolean edgeTop, boolean edgeBottom,
                                    int scaledWidth, int scaledHeight, float alpha, boolean edge,
                                    boolean frosted) {
        return drawImpl(x, y, w, h, radiusTop, radiusBottom, edgeTop, edgeBottom,
                scaledWidth, scaledHeight, alpha, edge, frosted, false, 0.0F, false, frosted, false,
                GLASS_TINT);
    }

    private static boolean drawImpl(float x, float y, float w, float h,
                                    float radiusTop, float radiusBottom,
                                    boolean edgeTop, boolean edgeBottom,
                                    int scaledWidth, int scaledHeight, float alpha, boolean edge,
                                    boolean frosted, boolean flatBlur,
                                     float dim, boolean useCached) {
        return drawImpl(x, y, w, h, radiusTop, radiusBottom, edgeTop, edgeBottom,
                scaledWidth, scaledHeight, alpha, edge, frosted, flatBlur, dim, useCached, false, false,
                GLASS_TINT);
    }

    private static boolean drawImpl(float x, float y, float w, float h,
                                    float radiusTop, float radiusBottom,
                                    boolean edgeTop, boolean edgeBottom,
                                    int scaledWidth, int scaledHeight, float alpha, boolean edge,
                                    boolean frosted, boolean flatBlur,
                                    float dim, boolean useCached, boolean hudTint) {
        return drawImpl(x, y, w, h, radiusTop, radiusBottom, edgeTop, edgeBottom,
                scaledWidth, scaledHeight, alpha, edge, frosted, flatBlur, dim, useCached, false, hudTint,
                GLASS_TINT);
    }

    private static boolean drawImpl(float x, float y, float w, float h,
                                    float radiusTop, float radiusBottom,
                                    boolean edgeTop, boolean edgeBottom,
                                    int scaledWidth, int scaledHeight, float alpha, boolean edge,
                                    boolean frosted, boolean flatBlur,
                                    float dim, boolean useCached, boolean lightUi, boolean hudTint,
                                    float tint) {
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
            if (maxX - minX < pendingMinDimension || maxY - minY < pendingMinDimension) return false;

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
            if (useCached) {
                lastFrameBlurDemandSeq = frameSeq;
                if (!ensureProgram()) return false;
                // 3D 阶段不能抓帧，只能使用上一帧的结果。
                if (!frameBlurred || blurredFrame == null
                        || frameTexW != dispW || frameTexH != dispH) return false;
            } else if (frosted || flatBlur) {
                if (!ensureProgram()) return false;
                if (flatBlur && !lightUi) {
                    if (pendingLiveChatCapture) {
                        if (!prepareChatBackground(dispW, dispH, viewportOk)) return false;
                    } else if (!prepareBackground(dispW, dispH, viewportOk, false)) {
                        return false;
                    }
                }
            } else if (!ensureFlatProgram()) {
                return false;
            }
            boolean blurred = blurredFrame != null && frameBlurred;

            // 贴形状的外圈柔光
            if (edge) drawGlow(x, y, w, h, radius, alpha, frosted ? 1.0F : gameGlowScale());

            if (frosted || flatBlur) {
                int glassTex;
                if (lightUi && flatBlur) {
                    glassTex = pendingMenuCapture ? menuGaussianTexture(dispW, dispH, viewportOk)
                            : guiGaussianTexture(dispW, dispH, viewportOk);
                } else if (frosted) {
                    glassTex = frostedBackgroundTexture(dispW, dispH, viewportOk);
                    if (glassTex <= 0) glassTex = frameTex;
                } else if (pendingLiveChatCapture) {
                    glassTex = chatBlurReady && chatBlurredFrame != null
                            ? chatBlurredFrame.framebufferTexture : -1;
                } else {
                    glassTex = blurred ? blurredFrame.framebufferTexture : frameTex;
                }
                if (glassTex <= 0) return false;
                boolean ok = drawGlass(glassTex,
                        minX, minY, maxX - minX, maxY - minY, rTop, rBot,
                        scaledWidth, scaledHeight, dispW, dispH, sx, sy, alpha,
                        edgeTop, edgeBottom, flatBlur ? false : edge, flatBlur, dim, lightUi, hudTint,
                        tint);
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

    /** 同 {@link #drawEdge}，柔光使用指定 RGB。 */
    public static void drawEdgeColored(float x, float y, float w, float h, float radius,
                                        float alpha, int glowRgb) {
        int prev = pendingGlowColor;
        float previousMinimum = pendingMinDimension;
        pendingGlowColor = glowRgb & 0xFFFFFF;
        pendingMinDimension = 1.0F;
        try {
            drawEdge(x, y, w, h, radius, alpha);
        } finally {
            pendingGlowColor = prev;
            pendingMinDimension = previousMinimum;
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

    /** 在 renderGameOverlay HEAD 抓 HUD 帧：此时只有世界和手。 */
    public static void captureWorld() {
        Minecraft mc = Minecraft.getMinecraft();
        int dispW = mc.displayWidth;
        int dispH = mc.displayHeight;
        if (dispW <= 0 || dispH <= 0) return;
        try {
            prepareBackground(dispW, dispH, viewportMatches(), false,
                    lastFrameBlurDemandSeq == frameSeq);
        } catch (Throwable t) {
            logFailure(t);
        }
    }

    public static void captureScreen() {
        Minecraft mc = Minecraft.getMinecraft();
        int dispW = mc.displayWidth;
        int dispH = mc.displayHeight;
        if (dispW <= 0 || dispH <= 0) return;
        if (guiFrameValid && guiFrameW == dispW && guiFrameH == dispH) return;
        lastCaptureScreenNs = System.nanoTime();
        try {
            ensureGuiFrameCopy(dispW, dispH, viewportMatches(), true);
            if (!guiFrameValid) guiFrameW = -1;
        } catch (Throwable t) {
            logFailure(t);
            guiFrameValid = false;
            guiFrameW = -1;
        }
    }

    public static void invalidateGuiBackdrop() {
        guiFrameValid = false;
        guiFrameW = -1;
        guiFrameH = -1;
        guiGaussTex = -1;
        guiGaussW = -1;
        guiGaussH = -1;
        lastCaptureScreenNs = 0L;
    }

    private static boolean ensureGuiFrameCopy(int dispW, int dispH, boolean viewportOk, boolean force) {
        long now = System.nanoTime();
        if (!force && guiFrameValid && guiFrameW == dispW && guiFrameH == dispH
                && now - guiFrameNs < GUI_BLUR_REFRESH_NS) return true;
        if (guiFrameTex <= 0 || guiFrameW != dispW || guiFrameH != dispH) {
            if (guiFrameTex > 0) GL11.glDeleteTextures(guiFrameTex);
            guiFrameTex = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, guiFrameTex);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, dispW, dispH, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            guiFrameW = dispW;
            guiFrameH = dispH;
            guiFrameValid = false;
        }
        if (!viewportOk) return false;
        int prevActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int drawFb = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int prevReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glColorMask(true, true, true, true);
            if (drawFb == 0) GL11.glReadBuffer(GL11.GL_BACK);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, guiFrameTex);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            while (GL11.glGetError() != GL11.GL_NO_ERROR) {
            }
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, dispW, dispH);
            guiFrameValid = GL11.glGetError() == GL11.GL_NO_ERROR;
        } catch (Throwable t) {
            logFailure(t);
            guiFrameValid = false;
        } finally {
            GL11.glReadBuffer(prevReadBuffer);
            GL11.glPopAttrib();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);
            GL13.glActiveTexture(prevActive);
        }
        if (guiFrameValid) guiFrameNs = now;
        return guiFrameValid;
    }

    private static boolean viewportMatches() {
        Minecraft mc = Minecraft.getMinecraft();
        IntBuffer ib = VIEWPORT_BUFFER;
        ib.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, ib);
        return ib.get(0) == 0 && ib.get(1) == 0
                && ib.get(2) == mc.displayWidth && ib.get(3) == mc.displayHeight;
    }

    private static boolean prepareBackground(int dispW, int dispH, boolean viewportOk, boolean force) {
        return prepareBackground(dispW, dispH, viewportOk, force, true);
    }

    private static boolean prepareBackground(int dispW, int dispH, boolean viewportOk,
                                             boolean force, boolean blurRequested) {
        // 模块列表逐条调用，同一帧只抓取一次。
        if (!force && frameCapturedSeq == frameSeq && frameValid
                && (!blurRequested || (frameBlurred && blurredFrame != null))
                && frameTexW == dispW && frameTexH == dispH) {
            return true;
        }
        int prevActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
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
            GL20.glUseProgram(0);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glColorMask(true, true, true, true);
            ensureFrameCopy(dispW, dispH, viewportOk);
            ok = frameValid && (!blurRequested || blurWholeFrame(dispW, dispH));
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
            GL20.glUseProgram(prevProgram);
            GL11.glMatrixMode(prevMatrixMode);
        }
        return ok;
    }

    private static void ensureFrameCopy(int dispW, int dispH, boolean viewportOk) {
        long now = System.nanoTime();
        if (frameCapturedSeq == frameSeq && frameValid && frameTexW == dispW && frameTexH == dispH
                && now - frameCapturedNs < 200_000_000L) return;
        frameCapturedSeq = frameSeq;
        frameCapturedNs = now;
        frameValid = false;
        frameBlurred = false;
        if (!viewportOk) return;
        ensureFrameTex(dispW, dispH);
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {
        }
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

    private static boolean prepareChatBackground(int dispW, int dispH, boolean viewportOk) {
        if (chatCapturedSeq == frameSeq && chatFrameValid && chatBlurReady
                && chatFrameW == dispW && chatFrameH == dispH) return true;
        chatCapturedSeq = frameSeq;
        chatFrameValid = false;
        chatBlurReady = false;
        if (!viewportOk) return false;
        int prevActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int prevFb = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int prevRead = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        boolean prevTexEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glColorMask(true, true, true, true);
            ensureChatFrameTex(dispW, dispH);
            if (prevFb == 0) GL11.glReadBuffer(GL11.GL_BACK);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, chatFrameTex);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            while (GL11.glGetError() != GL11.GL_NO_ERROR) {
            }
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, dispW, dispH);
            chatFrameValid = GL11.glGetError() == GL11.GL_NO_ERROR;
            if (chatFrameValid) chatBlurReady = blurChatFrame(dispW, dispH);
        } catch (Throwable t) {
            logFailure(t);
        } finally {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, prevFb);
            GL11.glReadBuffer(prevRead);
            if (prevTexEnabled) GL11.glEnable(GL11.GL_TEXTURE_2D);
            else GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);
            GL11.glPopAttrib();
            GL13.glActiveTexture(prevActive);
        }
        return chatFrameValid && chatBlurReady;
    }

    private static void ensureChatFrameTex(int w, int h) {
        if (chatFrameTex > 0 && chatFrameW == w && chatFrameH == h) return;
        if (chatFrameTex > 0) GL11.glDeleteTextures(chatFrameTex);
        chatFrameTex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, chatFrameTex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        chatFrameW = w;
        chatFrameH = h;
    }

    private static boolean blurChatFrame(int dispW, int dispH) {
        int w = Math.max(1, dispW);
        int h = Math.max(1, dispH);
        int prevFb = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        IntBuffer ib = VIEWPORT_BUFFER;
        ib.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, ib);
        int vpX = ib.get(0);
        int vpY = ib.get(1);
        int vpW = ib.get(2);
        int vpH = ib.get(3);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        boolean ok = false;
        try {
            chatBlurTmp = ensureAuxTarget(chatBlurTmp, w, h);
            if (ensureChatTarget(w, h) && chatBlurTmp != null && ensureGaussProgram()) {
                ok = gaussPass(chatBlurTmp, chatFrameTex, w, h, 1.0F, 0.0F, GUI_GAUSS_STRENGTH)
                        && gaussPass(chatBlurredFrame, chatBlurTmp.framebufferTexture, w, h, 0.0F, 1.0F,
                        GUI_GAUSS_STRENGTH);
            }
        } catch (Throwable t) {
            logFailure(t);
        } finally {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, prevFb);
            GL11.glViewport(vpX, vpY, vpW, vpH);
            GL11.glPopAttrib();
        }
        if (ok) chatBlurNs = System.nanoTime();
        return ok;
    }

    private static boolean ensureChatTarget(int w, int h) {
        if (chatBlurredFrame != null && chatBlurredFrame.framebufferWidth == w
                && chatBlurredFrame.framebufferHeight == h) return true;
        if (chatBlurredFrame != null) {
            chatBlurredFrame.deleteFramebuffer();
            chatBlurredFrame = null;
        }
        try {
            chatBlurredFrame = new Framebuffer(w, h, false);
            setLinear(chatBlurredFrame);
            return true;
        } catch (Throwable t) {
            if (chatBlurredFrame != null) chatBlurredFrame.deleteFramebuffer();
            chatBlurredFrame = null;
            logFailure(t);
            return false;
        }
    }

    private static boolean blurWholeFrame(int dispW, int dispH) {
        int w = Math.max(1, dispW);
        int h = Math.max(1, dispH);
        if (frameBlurred && blurredFrame != null
                && blurredFrame.framebufferWidth == w && blurredFrame.framebufferHeight == h) return true;
        if (!ensureGaussProgram()) return false;
        if (!ensureBlurTarget(w, h)) return false;
        hudBlurTmp = ensureAuxTarget(hudBlurTmp, w, h);
        if (hudBlurTmp == null) return false;
        if (!gaussPass(hudBlurTmp, frameTex, w, h, 1.0F, 0.0F, GUI_GAUSS_STRENGTH)) return false;
        if (!gaussPass(blurredFrame, hudBlurTmp.framebufferTexture, w, h, 0.0F, 1.0F, GUI_GAUSS_STRENGTH)) return false;
        frameBlurred = true;
        return true;
    }

    private static Framebuffer ensureAuxTarget(Framebuffer target, int w, int h) {
        if (target != null && target.framebufferWidth == w && target.framebufferHeight == h) return target;
        if (target != null) {
            target.deleteFramebuffer();
        }
        try {
            Framebuffer created = new Framebuffer(w, h, false);
            setLinear(created);
            return created;
        } catch (Throwable t) {
            logFailure(t);
            return null;
        }
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
        uBlurResolution = GL20.glGetUniformLocation(id, "uInputResolution");
        uBlurStrength = GL20.glGetUniformLocation(id, "uBlurStrength");
        return true;
    }

    private static boolean blurPass(Framebuffer dst, int srcTex, int width, int height, float strength) {
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
            GL20.glUseProgram(blurProgram);
            if (uBlurContent >= 0) GL20.glUniform1i(uBlurContent, 0);
            if (uBlurResolution >= 0) GL20.glUniform2f(uBlurResolution, width, height);
            if (uBlurStrength >= 0) GL20.glUniform1f(uBlurStrength, strength);
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

    private static boolean ensureBlurTarget(int dispW, int dispH) {
        if (blurredFrame != null && blurredFrame.framebufferWidth == dispW
                && blurredFrame.framebufferHeight == dispH) return true;
        if (blurredFrame != null) {
            blurredFrame.deleteFramebuffer();
            blurredFrame = null;
        }
        try {
            blurredFrame = new Framebuffer(dispW, dispH, false);
            setLinear(blurredFrame);
            return true;
        } catch (Throwable t) {
            if (blurredFrame != null) blurredFrame.deleteFramebuffer();
            blurredFrame = null;
            logFailure(t);
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

    private static int menuGaussianTexture(int dispW, int dispH, boolean viewportOk) {
        if (!viewportOk || !ensureGaussProgram()) return -1;
        int previousFb = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int previousActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousRead = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousMatrix = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        IntBuffer viewport = VIEWPORT_BUFFER;
        viewport.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        int vx = viewport.get(0), vy = viewport.get(1), vw = viewport.get(2), vh = viewport.get(3);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glColorMask(true, true, true, true);
            menuFrame = ensureAuxTarget(menuFrame, dispW, dispH);
            int blurW = Math.max(1, dispW), blurH = Math.max(1, dispH);
            menuGaussA = ensureAuxTarget(menuGaussA, blurW, blurH);
            menuGaussB = ensureAuxTarget(menuGaussB, blurW, blurH);
            if (menuFrame == null || menuGaussA == null || menuGaussB == null) return -1;
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, previousFb);
            if (previousFb == 0) GL11.glReadBuffer(GL11.GL_BACK);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, menuFrame.framebufferTexture);
            while (GL11.glGetError() != GL11.GL_NO_ERROR) { }
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, dispW, dispH);
            if (GL11.glGetError() != GL11.GL_NO_ERROR) return -1;
            return gaussPass(menuGaussA, menuFrame.framebufferTexture, blurW, blurH, 1.0F, 0.0F)
                    && gaussPass(menuGaussB, menuGaussA.framebufferTexture, blurW, blurH, 0.0F, 1.0F)
                    ? menuGaussB.framebufferTexture : -1;
        } finally {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, previousFb);
            GL11.glReadBuffer(previousRead);
            GL11.glViewport(vx, vy, vw, vh);
            GL11.glPopAttrib();
            GL13.glActiveTexture(previousActive);
            GL20.glUseProgram(previousProgram);
            GL11.glMatrixMode(previousMatrix);
        }
    }

    private static int guiGaussianTexture(int dispW, int dispH, boolean viewportOk) {
        long now = System.nanoTime();
        if (guiGaussTex > 0 && guiGaussW == dispW && guiGaussH == dispH
                && now - guiGaussNs < GUI_BLUR_REFRESH_NS) {
            return guiGaussTex;
        }
        if (!guiFrameValid || guiFrameW != dispW || guiFrameH != dispH) {
            if (!ensureGuiFrameCopy(dispW, dispH, viewportOk, true)) return -1;
        }
        if (!guiFrameValid) return -1;
        int blurW = Math.max(1, dispW);
        int blurH = Math.max(1, dispH);
        int prevFb = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        IntBuffer ib = VIEWPORT_BUFFER;
        ib.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, ib);
        int vpX = ib.get(0);
        int vpY = ib.get(1);
        int vpW = ib.get(2);
        int vpH = ib.get(3);
        if (!ensureGaussProgram() || !ensureGaussTargets(blurW, blurH)) {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, prevFb);
            GL11.glViewport(vpX, vpY, vpW, vpH);
            return -1;
        }
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        boolean ok = false;
        try {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glColorMask(true, true, true, true);
            ok = gaussPass(guiGaussA, guiFrameTex, blurW, blurH, 1.0F, 0.0F)
                    && gaussPass(guiGaussB, guiGaussA.framebufferTexture, blurW, blurH, 0.0F, 1.0F);
        } catch (Throwable t) {
            logFailure(t);
        } finally {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, prevFb);
            GL11.glViewport(vpX, vpY, vpW, vpH);
            GL11.glPopAttrib();
        }
        if (!ok) return -1;
        guiGaussTex = guiGaussB.framebufferTexture;
        guiGaussW = dispW;
        guiGaussH = dispH;
        guiGaussNs = now;
        return guiGaussTex;
    }

    private static boolean ensureGaussProgram() {
        if (gaussProgram > 0 && !GL20.glIsProgram(gaussProgram)) {
            gaussProgram = 0;
            gaussTried = false;
        }
        if (gaussProgram > 0) return true;
        if (gaussTried) return false;
        gaussTried = true;
        int vs = compile(GL20.GL_VERTEX_SHADER, BLUR_VERT);
        int fs = compile(GL20.GL_FRAGMENT_SHADER, GAUSS_FRAG);
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
            System.err.println("[FrostedGlass] gaussian link failed: "
                    + GL20.glGetProgramInfoLog(id, 8192));
            GL20.glDeleteProgram(id);
            return false;
        }
        gaussProgram = id;
        uGaussContent = GL20.glGetUniformLocation(id, "uContent");
        uGaussResolution = GL20.glGetUniformLocation(id, "uInputResolution");
        uGaussStrength = GL20.glGetUniformLocation(id, "uBlurStrength");
        uGaussDirection = GL20.glGetUniformLocation(id, "uDirection");
        return uGaussContent >= 0 && uGaussResolution >= 0
                && uGaussStrength >= 0 && uGaussDirection >= 0;
    }

    private static boolean ensureGaussTargets(int w, int h) {
        if (guiGaussA != null && guiGaussA.framebufferWidth == w
                && guiGaussA.framebufferHeight == h
                && guiGaussB != null && guiGaussB.framebufferWidth == w
                && guiGaussB.framebufferHeight == h) {
            return true;
        }
        deleteGaussTargets();
        try {
            guiGaussA = new Framebuffer(w, h, false);
            setLinear(guiGaussA);
            guiGaussB = new Framebuffer(w, h, false);
            setLinear(guiGaussB);
            guiGaussTex = -1;
            return true;
        } catch (Throwable t) {
            deleteGaussTargets();
            logFailure(t);
            return false;
        }
    }

    private static void deleteGaussTargets() {
        if (guiGaussA != null) {
            guiGaussA.deleteFramebuffer();
            guiGaussA = null;
        }
        if (guiGaussB != null) {
            guiGaussB.deleteFramebuffer();
            guiGaussB = null;
        }
        guiGaussTex = -1;
    }

    // ---- v2.3 frosted-card blur

    /** v2.3 card blur: half-res, quarter-res, then a 7-tap separable pass; cached per frame. */
    private static int frostedBackgroundTexture(int dispW, int dispH, boolean viewportOk) {
        int w1 = Math.max(1, dispW / 2);
        int h1 = Math.max(1, dispH / 2);
        if (frostedSeq == frameSeq && frostedTex > 0
                && frostedW1 == w1 && frostedH1 == h1) return frostedTex;
        if (!prepareBackground(dispW, dispH, viewportOk, false, false)) return -1;
        if (!frameValid) return -1;
        int w2 = Math.max(1, w1 / 2);
        int h2 = Math.max(1, h1 / 2);
        int prevFb = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        IntBuffer ib = VIEWPORT_BUFFER;
        ib.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, ib);
        int vpX = ib.get(0);
        int vpY = ib.get(1);
        int vpW = ib.get(2);
        int vpH = ib.get(3);
        if (!ensureFrostedProgram() || !ensureFrostedTargets(w1, h1, w2, h2)) {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, prevFb);
            GL11.glViewport(vpX, vpY, vpW, vpH);
            return -1;
        }
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        boolean ok = false;
        try {
            ok = frostedPass(frostedDown1, frameTex, w1, h1, 0.0F, 0.0F)
                    && frostedPass(frostedDown2, frostedDown1.framebufferTexture, w2, h2, 0.0F, 0.0F)
                    && frostedPass(frostedTmp, frostedDown2.framebufferTexture, w2, h2, 1.0F / w2, 0.0F)
                    && frostedPass(frostedOut, frostedTmp.framebufferTexture, w2, h2, 0.0F, 1.0F / h2);
        } catch (Throwable t) {
            logFailure(t);
        } finally {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, prevFb);
            GL11.glViewport(vpX, vpY, vpW, vpH);
            GL11.glPopAttrib();
        }
        if (!ok) return -1;
        frostedTex = frostedOut.framebufferTexture;
        frostedW1 = w1;
        frostedH1 = h1;
        frostedSeq = frameSeq;
        return frostedTex;
    }

    private static boolean ensureFrostedProgram() {
        if (frostedProgram > 0 && !GL20.glIsProgram(frostedProgram)) {
            frostedProgram = 0;
            frostedTried = false;
        }
        if (frostedProgram > 0) return true;
        if (frostedTried) return false;
        frostedTried = true;
        int vs = compile(GL20.GL_VERTEX_SHADER, BLUR_VERT);
        int fs = compile(GL20.GL_FRAGMENT_SHADER, FROSTED_BLUR_FRAG);
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
            System.err.println("[FrostedGlass] frosted blur link failed: "
                    + GL20.glGetProgramInfoLog(id, 8192));
            GL20.glDeleteProgram(id);
            return false;
        }
        frostedProgram = id;
        uFrostedContent = GL20.glGetUniformLocation(id, "uContent");
        uFrostedStep = GL20.glGetUniformLocation(id, "uStep");
        return uFrostedContent >= 0 && uFrostedStep >= 0;
    }

    private static boolean ensureFrostedTargets(int w1, int h1, int w2, int h2) {
        if (frostedDown1 != null && frostedDown1.framebufferWidth == w1
                && frostedDown1.framebufferHeight == h1
                && frostedDown2 != null && frostedDown2.framebufferWidth == w2
                && frostedDown2.framebufferHeight == h2) return true;
        deleteFrostedTargets();
        try {
            frostedDown1 = new Framebuffer(w1, h1, false);
            setLinear(frostedDown1);
            frostedDown2 = new Framebuffer(w2, h2, false);
            setLinear(frostedDown2);
            frostedTmp = new Framebuffer(w2, h2, false);
            setLinear(frostedTmp);
            frostedOut = new Framebuffer(w2, h2, false);
            setLinear(frostedOut);
            return true;
        } catch (Throwable t) {
            deleteFrostedTargets();
            logFailure(t);
            return false;
        }
    }

    private static void deleteFrostedTargets() {
        if (frostedDown1 != null) {
            frostedDown1.deleteFramebuffer();
            frostedDown1 = null;
        }
        if (frostedDown2 != null) {
            frostedDown2.deleteFramebuffer();
            frostedDown2 = null;
        }
        if (frostedTmp != null) {
            frostedTmp.deleteFramebuffer();
            frostedTmp = null;
        }
        if (frostedOut != null) {
            frostedOut.deleteFramebuffer();
            frostedOut = null;
        }
        frostedTex = -1;
        frostedSeq = Long.MIN_VALUE;
    }

    private static boolean frostedPass(Framebuffer dst, int srcTex, int width, int height,
                                       float stepU, float stepV) {
        if (dst == null || srcTex <= 0) return false;
        dst.bindFramebuffer(true);
        GL11.glViewport(0, 0, width, height);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, srcTex);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        beginPassOrtho();
        try {
            GL20.glUseProgram(frostedProgram);
            GL20.glUniform1i(uFrostedContent, 0);
            GL20.glUniform2f(uFrostedStep, stepU, stepV);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2d(0.0D, 0.0D);
            GL11.glVertex2d(1.0D, 0.0D);
            GL11.glVertex2d(1.0D, 1.0D);
            GL11.glVertex2d(0.0D, 1.0D);
            GL11.glEnd();
        } finally {
            GL20.glUseProgram(0);
            endPassOrtho();
        }
        return true;
    }

    private static boolean gaussPass(Framebuffer dst, int srcTex, int width, int height,
                                     float dirX, float dirY) {
        return gaussPass(dst, srcTex, width, height, dirX, dirY, GUI_GAUSS_STRENGTH);
    }

    private static boolean gaussPass(Framebuffer dst, int srcTex, int width, int height,
                                     float dirX, float dirY, float strength) {
        if (dst == null || srcTex <= 0) return false;
        dst.bindFramebuffer(true);
        GL11.glViewport(0, 0, width, height);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, srcTex);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        beginPassOrtho();
        try {
            GL20.glUseProgram(gaussProgram);
            GL20.glUniform1i(uGaussContent, 0);
            GL20.glUniform2f(uGaussResolution, width, height);
            GL20.glUniform1f(uGaussStrength, strength);
            GL20.glUniform2f(uGaussDirection, dirX, dirY);
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
        if (bw < pendingMinDimension || bh < pendingMinDimension) return;
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
            if (uGlowColor >= 0) GL20.glUniform3f(uGlowColor,
                    ((pendingGlowColor >> 16) & 0xFF) / 255.0F,
                    ((pendingGlowColor >> 8) & 0xFF) / 255.0F,
                    (pendingGlowColor & 0xFF) / 255.0F);

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
        int rgb = pendingGlowColor & 0xFFFFFF;
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
        uGlowColor = GL20.glGetUniformLocation(id, "uGlowColor");
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
                                     boolean edgeTop, boolean edgeBottom, boolean edge, boolean flatBlur,
                                     float dim, boolean lightUi, boolean hudTint, float tint) {
        if (!ensureProgram()) return false;
        if (tex <= 0) return false;
        float minD = Math.min(w, h);
        if (minD < pendingMinDimension) return false;
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
            if (uEpsilonMask >= 0) GL20.glUniform1f(uEpsilonMask, flatBlur ? 1.0F : 0.0F);
            GL20.glUniform1f(uRadius, Math.min(Math.max(radiusTop, radiusBottom), minD * 0.5F));
            GL20.glUniform1f(uRadiusTop, Math.min(radiusTop, minD * 0.5F));
            GL20.glUniform1f(uRadiusBottom, Math.min(radiusBottom, minD * 0.5F));
            // 是外边界才画折射/明暗带，拼缝处压掉
            GL20.glUniform1f(uClipTop, edgeTop ? 1.0F : 0.0F);
            GL20.glUniform1f(uClipBot, edgeBottom ? 1.0F : 0.0F);
            GL20.glUniform1f(uAA, aa);
            // backdrop-filter: saturate(140%)
            GL20.glUniform1f(uSat, flatBlur ? 1.0F : 1.40F);
            if (uDim >= 0) GL20.glUniform1f(uDim, dim);
            GL20.glUniform1f(uTint, flatBlur ? 0.0F : tint);
            if (uGrayBacking >= 0) {
                float grayAmount = pendingGrayAmount >= 0.0F ? pendingGrayAmount
                        : flatBlur ? GUI_GRAY_AMOUNT
                        : pendingLiveChatCapture ? CHAT_GRAY_AMOUNT
                        : hudTint ? HUD_GRAY_AMOUNT
                        : lightUi && flatBlur ? GUI_GRAY_AMOUNT
                        : lightUi ? UI_GRAY_AMOUNT : BLUR_GRAY_AMOUNT;
                int backing = pendingBackingColor >= 0 ? pendingBackingColor
                        : !flatBlur && (hudTint || pendingLiveChatCapture) ? 0 : -1;
                GL20.glUniform4f(uGrayBacking,
                        backing >= 0 ? ((backing >> 16) & 255) / 255.0F : BLUR_GRAY_RED,
                        backing >= 0 ? ((backing >> 8) & 255) / 255.0F : BLUR_GRAY_GREEN,
                        backing >= 0 ? (backing & 255) / 255.0F : BLUR_GRAY_BLUE, grayAmount);
            }
            if (uGradBot >= 0) {
                GL20.glUniform1f(uGradBot,
                        pendingGradientBottom > 0.001F ? pendingGradientBottom : 0.0F);
            }
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
        uEpsilonMask = loc("uEpsilonMask");
        uMap = loc("uMap");
        uRadius = loc("uRadius");
        uRadiusTop = loc("uRadiusTop");
        uRadiusBottom = loc("uRadiusBottom");
        uClipTop = loc("uClipTop");
        uClipBot = loc("uClipBot");
        uAA = loc("uAA");
        uSat = loc("uSat");
        uDim = loc("uDim");
        uTint = loc("uTint");
        uGrayBacking = loc("uGrayBacking");
        uDisp = loc("uDisp");
        uBevelH = loc("uBevelH");
        uHiA = loc("uHiA");
        uShA = loc("uShA");
        uLineW = loc("uLineW");
        uRimA = loc("uRimA");
        uAlpha = loc("uAlpha");
        uMode = loc("uMode");
        uGradBot = loc("uGradBot");
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
