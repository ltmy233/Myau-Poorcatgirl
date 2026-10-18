package myau.module.modules;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.enums.ChatColors;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.Render2DEvent;
import myau.events.TickEvent;
import myau.mixin.IAccessorGuiChat;
import myau.module.Module;
import myau.util.ColorUtil;
import myau.util.RenderUtil;
import myau.property.properties.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class HUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private List<Module> activeModules = new ArrayList<>();
    private final LinkedHashMap<Module, ListEntry> listAnim = new LinkedHashMap<>();
    private final List<Module> listOrder = new ArrayList<>();
    private long lastAnimNanos = -1L;
    private boolean listPrimed = false;
    private long nextColorOffset = 0L;

    private static class ListEntry {
        final Module module;
        float progress;
        float y;
        float targetY;
        long colorOffset;
        boolean removing;
        boolean snap = true;

        ListEntry(Module module) {
            this.module = module;
        }
    }
    public final ModeProperty colorMode = new ModeProperty(
            "color", 3, new String[]{"RAINBOW", "CHROMA", "ASTOLFO", "CUSTOM1", "CUSTOM12", "CUSTOM123"}
    );
    public final FloatProperty colorSpeed = new FloatProperty("color-speed", 1.0F, 0.5F, 1.5F);
    public final PercentProperty colorSaturation = new PercentProperty("color-saturation", 50);
    public final PercentProperty colorBrightness = new PercentProperty("color-brightness", 100);
    public final ColorProperty custom1 = new ColorProperty("custom-color-1", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 3 || this.colorMode.getValue() == 4 || this.colorMode.getValue() == 5);
    public final ColorProperty custom2 = new ColorProperty("custom-color-2", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 4 || this.colorMode.getValue() == 5);
    public final ColorProperty custom3 = new ColorProperty("custom-color-3", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 5);
    public final ModeProperty posX = new ModeProperty("position-x", 0, new String[]{"LEFT", "RIGHT"});
    public final ModeProperty posY = new ModeProperty("position-y", 0, new String[]{"TOP", "BOTTOM"});
    public final IntProperty offsetX = new IntProperty("offset-x", 2, 0, 255);
    public final IntProperty offsetY = new IntProperty("offset-y", 2, 0, 255);
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 1.5F);
    public final PercentProperty background = new PercentProperty("background", 25);
    public final BooleanProperty showBar = new BooleanProperty("bar", true);
    public final BooleanProperty shadow = new BooleanProperty("shadow", true);
    public final BooleanProperty suffixes = new BooleanProperty("suffixes", true);
    public final BooleanProperty lowerCase = new BooleanProperty("lower-case", false);
    public final BooleanProperty chatOutline = new BooleanProperty("chat-outline", true);
    public final BooleanProperty blinkTimer = new BooleanProperty("blink-timer", true);
    public final BooleanProperty toggleSound = new BooleanProperty("toggle-sounds", true);
    public final BooleanProperty toggleAlerts = new BooleanProperty("toggle-alerts", false);
    public final BooleanProperty notifications = new BooleanProperty("notifications", true);
    public final BooleanProperty rounded = new BooleanProperty("rounded", true);
    public final FloatProperty cornerRadius = new FloatProperty("corner-radius", 4.0F, 1.0F, 8.0F, () -> rounded.getValue());
    public final FloatProperty padding = new FloatProperty("padding", 2.0F, 0.0F, 6.0F);
    public final BooleanProperty smoothList = new BooleanProperty("smooth-list", true);
    public final FloatProperty scoreboardScale = new FloatProperty("scoreboard-scale", 1.0F, 0.5F, 2.0F);
    public final IntProperty scoreboardRows = new IntProperty("scoreboard-rows", 15, 5, 15);
    public final IntProperty scoreboardOffsetX = new IntProperty("scoreboard-offset-x", 0, -160, 160);
    public final IntProperty scoreboardOffsetY = new IntProperty("scoreboard-offset-y", 0, -80, 80);

    // 玻璃总开关 + 各面板独立开关
    public final BooleanProperty glass = new BooleanProperty("glass", true);
    public final BooleanProperty glassGui = new BooleanProperty("glass-gui", true, this.glass::getValue);
    public final BooleanProperty glassChat = new BooleanProperty("glass-chat", true, this.glass::getValue);
    public final BooleanProperty glassTab = new BooleanProperty("glass-tab", true, this.glass::getValue);
    public final BooleanProperty glassScoreboard = new BooleanProperty("glass-scoreboard", true, this.glass::getValue);
    public final BooleanProperty glassList = new BooleanProperty("glass-list", true, this.glass::getValue);
    public final BooleanProperty glassNotify = new BooleanProperty("glass-notify", true, this.glass::getValue);
    public final BooleanProperty glassTarget = new BooleanProperty("glass-target", true, this.glass::getValue);
    public final BooleanProperty tabAnimation = new BooleanProperty("tab-animation", true);
    public final FloatProperty glowSize = new FloatProperty("glow-size", 1.45F, 0.5F, 3.0F);
    public final PercentProperty glowOpacity = new PercentProperty("glow-opacity", 100);
    public final PercentProperty glowBlur = new PercentProperty("glow-blur", 100);

    public boolean glassOn(BooleanProperty surface) {
        return this.glass.getValue() && surface.getValue();
    }

    /** 取 HUD 实例；manager 没起来时返回 null。 */
    public static HUD get() {
        if (Myau.moduleManager == null) {
            return null;
        }
        return (HUD) Myau.moduleManager.getModule(HUD.class);
    }

    private String getModuleName(Module module) {
        String moduleName = module.getName();
        if (this.lowerCase.getValue()) {
            moduleName = moduleName.toLowerCase(Locale.ROOT);
        }
        return moduleName;
    }

    private String[] getModuleSuffix(Module module) {
        String[] moduleSuffix = module.getSuffix();
        if (this.lowerCase.getValue()) {
            for (int i = 0; i < moduleSuffix.length; i++) {
                moduleSuffix[i] = moduleSuffix[i].toLowerCase();
            }
        }
        return moduleSuffix;
    }

    private int getModuleWidth(Module module) {
        return this.calculateStringWidth(
                this.getModuleName(module), this.getModuleSuffix(module)
        );
    }

    private int calculateStringWidth(String string, String[] arr) {
        int width = mc.fontRendererObj.getStringWidth(string);
        if (this.suffixes.getValue()) {
            for (String str : arr) {
                width += 3 + mc.fontRendererObj.getStringWidth(str);
            }
        }
        return width;
    }

    private float getColorCycle(long long3, long long4) {
        long speed = (long) (3000.0 / Math.pow(Math.min(Math.max(0.5F, this.colorSpeed.getValue()), 1.5F), 3.0));
        return 1.0F - (float) (Math.abs(long3 - long4 * 300L) % speed) / (float) speed;
    }

    public HUD() {
        super("HUD", true, true);
    }

    public Color getColor(long time) {
        return this.getColor(time, 0L);
    }

    public Color getColor(long time, long offset) {
        Color color = Color.white;
        switch (this.colorMode.getValue()) {
            case 0:
                color = ColorUtil.fromHSB(this.getColorCycle(time, offset), 1.0F, 1.0F);
                break;
            case 1:
                color = ColorUtil.fromHSB(this.getColorCycle(time / 3L, 0L), 1.0F, 1.0F);
                break;
            case 2:
                float cycle = this.getColorCycle(time, offset);
                if (cycle % 1.0F < 0.5F) {
                    cycle = 1.0F - cycle % 1.0F;
                }
                color = ColorUtil.fromHSB(cycle, 1.0F, 1.0F);
                break;
            case 3:
                color = new Color(this.custom1.getValue());
                break;
            case 4:
                double cycle1 = this.getColorCycle(time, offset);
                color = ColorUtil.interpolate(
                        (float) (2.0 * Math.abs(cycle1 - Math.floor(cycle1 + 0.5))),
                        new Color(this.custom1.getValue()),
                        new Color(this.custom2.getValue())
                );
                break;
            case 5:
                double cycle2 = this.getColorCycle(time, offset);
                float floor = (float) (2.0 * Math.abs(cycle2 - Math.floor(cycle2 + 0.5)));
                if (floor <= 0.5F) {
                    color = ColorUtil.interpolate(floor * 2.0F, new Color(this.custom1.getValue()), new Color(this.custom2.getValue()));
                } else {
                    color = ColorUtil.interpolate((floor - 0.5F) * 2.0F, new Color(this.custom2.getValue()), new Color(this.custom3.getValue()));
                }
        }
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        return Color.getHSBColor(
                hsb[0],
                hsb[1] * (this.colorSaturation.getValue().floatValue() / 100.0F),
                hsb[2] * (this.colorBrightness.getValue().floatValue() / 100.0F)
        );
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.POST) {
            this.activeModules = Myau.moduleManager.modules.values().stream().filter(module -> module.isEnabled() && !module.isHidden()).sorted(Comparator.comparingInt(this::getModuleWidth).reversed()).collect(Collectors.<Module>toList());
            if (this.smoothList.getValue()) {
                this.syncListAnim();
            } else {
                this.listAnim.clear();
                this.listOrder.clear();
                this.listPrimed = false;
            }
        }
    }

    private void syncListAnim() {
        for (Module module : this.listOrder) {
            ListEntry entry = this.listAnim.get(module);
            if (entry != null) {
                entry.removing = !this.activeModules.contains(module);
            }
        }
        for (Module module : this.activeModules) {
            if (this.listAnim.containsKey(module)) {
                continue;
            }
            ListEntry entry = new ListEntry(module);
            entry.progress = this.listPrimed ? 0.0F : 1.0F;
            entry.snap = true;
            entry.colorOffset = this.nextColorOffset++;
            this.listAnim.put(module, entry);
            int want = this.activeModules.indexOf(module);
            int seen = 0;
            int pos = this.listOrder.size();
            for (int i = 0; i < this.listOrder.size(); i++) {
                ListEntry other = this.listAnim.get(this.listOrder.get(i));
                if (other == null || other.removing) {
                    continue;
                }
                if (seen == want) {
                    pos = i;
                    break;
                }
                seen++;
            }
            this.listOrder.add(pos, module);
        }
        for (int i = this.listOrder.size() - 1; i >= 0; i--) {
            ListEntry entry = this.listAnim.get(this.listOrder.get(i));
            if (entry != null && entry.removing && entry.progress <= 0.0F) {
                this.listOrder.remove(i);
                this.listAnim.remove(entry.module);
            }
        }
        this.listPrimed = true;
    }

    private void animateList(float dt, float baseY, float rowStep, float dir) {
        for (int i = 0; i < this.listOrder.size(); i++) {
            ListEntry entry = this.listAnim.get(this.listOrder.get(i));
            if (entry == null) {
                continue;
            }
            if (!entry.removing) {
                entry.targetY = baseY + i * rowStep * dir;
            }
            float target = entry.removing ? 0.0F : 1.0F;
            float duration = entry.removing ? 0.22F : 0.26F;
            if (entry.progress < target) {
                entry.progress = Math.min(target, entry.progress + dt / duration);
            } else if (entry.progress > target) {
                entry.progress = Math.max(target, entry.progress - dt / duration);
            }
            if (entry.snap) {
                entry.y = entry.targetY;
                entry.snap = false;
            } else {
                entry.y += (entry.targetY - entry.y) * (1.0F - (float) Math.exp(-dt / 0.05F));
                if (Math.abs(entry.targetY - entry.y) < 0.005F) {
                    entry.y = entry.targetY;
                }
            }
        }
    }

    private static float smoothstep(float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        return t * t * (3.0F - 2.0F * t);
    }

    private static int colorWithAlpha(int color, float alpha) {
        int a = (int) (((color >>> 24) & 0xFF) * alpha);
        return (color & 0x00FFFFFF) | (a << 24);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (this.chatOutline.getValue() && mc.currentScreen instanceof GuiChat) {
            String text = ((IAccessorGuiChat) mc.currentScreen).getInputField().getText().trim();
            if (Myau.commandManager != null && Myau.commandManager.isTypingCommand(text)) {
                RenderUtil.enableRenderState();
                RenderUtil.drawOutlineRect(
                        2.0F,
                        (float) (mc.currentScreen.height - 14),
                        (float) (mc.currentScreen.width - 2),
                        (float) (mc.currentScreen.height - 2),
                        1.5F,
                        0,
                        this.getColor(System.currentTimeMillis()).getRGB()
                );
                RenderUtil.disableRenderState();
            }
        }
        if (this.isEnabled() && !mc.gameSettings.showDebugInfo) {
            float height = (float) mc.fontRendererObj.FONT_HEIGHT - 1.0F;
            float x = (float) this.offsetX.getValue()
                    + (1.0F + (this.showBar.getValue() ? (this.shadow.getValue() ? 2.0F : 1.0F) : 0.0F)) * this.scale.getValue();
            float y = (float) this.offsetY.getValue() + 1.0F * this.scale.getValue();
            if (this.posX.getValue() == 1) {
                x = (float) new ScaledResolution(mc).getScaledWidth() - x;
            }
            if (this.posY.getValue() == 1) {
                y = (float) new ScaledResolution(mc).getScaledHeight() - y - height * this.scale.getValue();
            }
            GlStateManager.pushMatrix();
            GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 0.0F);
            long l = System.currentTimeMillis();
            long offset = 0L;
            float rowStep = (height + (this.shadow.getValue() ? 1.0F : 0.0F) + this.padding.getValue() * 2.0F) * this.scale.getValue();
            float dir = this.posY.getValue() == 0 ? 1.0F : -1.0F;
            if (this.smoothList.getValue()) {
                long now = System.nanoTime();
                float dt = this.lastAnimNanos < 0L ? 0.0F : Math.min((now - this.lastAnimNanos) / 1.0E9F, 0.05F);
                this.lastAnimNanos = now;
                this.animateList(dt, y, rowStep, dir);
                int firstActive = -1;
                int lastActive = -1;
                for (int i = 0; i < this.listOrder.size(); i++) {
                    ListEntry entry = this.listAnim.get(this.listOrder.get(i));
                    if (entry != null && !entry.removing) {
                        if (firstActive < 0) {
                            firstActive = i;
                        }
                        lastActive = i;
                    }
                }
                for (int i = 0; i < this.listOrder.size(); i++) {
                    Module module = this.listOrder.get(i);
                    ListEntry entry = this.listAnim.get(module);
                    if (entry == null || entry.progress <= 0.0F) {
                        continue;
                    }
                    float s = smoothstep(entry.progress);
                    this.drawListEntry(module, x, entry.y, s, i == firstActive, i == lastActive, entry.colorOffset, l);
                }
                offset = this.activeModules.size();
            } else {
                for (Module module : this.activeModules) {
                    this.drawListEntry(module, x, y, 1.0F, offset == 0L, offset == this.activeModules.size() - 1, offset, l);
                    y += rowStep * dir;
                    offset++;
                }
            }
            if (this.blinkTimer.getValue()) {
                BlinkModules blinkingModule = Myau.blinkManager.getBlinkingModule();
                if (blinkingModule != BlinkModules.NONE && blinkingModule != BlinkModules.AUTO_BLOCK) {
                    long movementPacketSize = Myau.blinkManager.countMovement();
                    if (movementPacketSize > 0L) {
                        GlStateManager.enableBlend();
                        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                        mc.fontRendererObj
                                .drawString(
                                        String.valueOf(movementPacketSize),
                                        (float) new ScaledResolution(mc).getScaledWidth() / 2.0F / this.scale.getValue()
                                                - (float) mc.fontRendererObj.getStringWidth(String.valueOf(movementPacketSize)) / 2.0F,
                                        (float) new ScaledResolution(mc).getScaledHeight() / 5.0F * 3.0F / this.scale.getValue(),
                                        this.getColor(l, offset).getRGB() & 16777215 | -1090519040,
                                        this.shadow.getValue()
                                );
                        GlStateManager.disableBlend();
                    }
                }
            }
            GlStateManager.enableDepth();
            GlStateManager.popMatrix();
        }
        renderNotifications();
    }

    private void drawListEntry(Module module, float x, float entryY, float s, boolean isFirst, boolean isLast, long colorOffset, long time) {
        String moduleName = this.getModuleName(module);
        String[] moduleSuffix = this.getModuleSuffix(module);
        float height = (float) mc.fontRendererObj.FONT_HEIGHT - 1.0F;
        float totalWidth = (float) (this.calculateStringWidth(moduleName, moduleSuffix) - (this.shadow.getValue() ? 0 : 1));
        int color = colorWithAlpha(this.getColor(time, colorOffset).getRGB(), s);
        float pad = this.padding.getValue();
        float slide = (1.0F - s) * 18.0F * (this.posX.getValue() == 0 ? -1.0F : 1.0F);
        float xDraw = x / this.scale.getValue() + slide;
        float yDraw = entryY / this.scale.getValue();
        float bgX1 = xDraw - 1.0F - pad - (this.posX.getValue() == 0 ? 0.0F : totalWidth);
        float bgY1 = yDraw - pad - (this.posY.getValue() == 0 ? (isFirst ? 1.0F : 0.0F) : (this.shadow.getValue() ? 1.0F : 0.0F));
        float bgX2 = xDraw + 1.0F + pad + (this.posX.getValue() == 0 ? totalWidth : 0.0F);
        float bgY2 = yDraw + height + pad + (this.posY.getValue() == 0 ? (this.shadow.getValue() ? 1.0F : 0.0F) : (isFirst ? 1.0F : 0.0F));
        if (s < 1.0F) {
            float w = bgX2 - bgX1;
            if (this.posX.getValue() == 0) {
                bgX2 = bgX1 + w * s;
            } else {
                bgX1 = bgX2 - w * s;
            }
        }
        RenderUtil.enableRenderState();
        // 玻璃开着用磨砂底，画不出来退回半透明黑底；background=0 仍然表示不画底
        boolean glassDrawn = false;
        if (this.background.getValue() > 0 && this.glassOn(this.glassList)) {
            ScaledResolution sr = new ScaledResolution(mc);
            float glassW = bgX2 - bgX1;
            float glassH = bgY2 - bgY1;
            float glassR = this.rounded.getValue()
                    ? Math.min(this.cornerRadius.getValue(), Math.min(glassW, glassH) * 0.5F)
                    : 0.0F;
            glassDrawn = myau.util.FrostedGlass.draw(bgX1, bgY1, glassW, glassH, glassR,
                    sr.getScaledWidth(), sr.getScaledHeight(),
                    Math.max(0.0F, Math.min(1.0F, s)), true);
        }
        if (!glassDrawn && this.background.getValue() > 0) {
            int bgColor = new Color(0.0F, 0.0F, 0.0F, this.background.getValue().floatValue() / 100.0F * s).getRGB();
            if (this.rounded.getValue()) {
                float bgW = bgX2 - bgX1;
                float bgH = bgY2 - bgY1;
                float rad = this.cornerRadius.getValue();
                boolean sideLeft = this.posX.getValue() == 1;
                boolean sideRight = this.posX.getValue() == 0;
                RenderUtil.drawRoundedRect(
                        bgX1, bgY1, bgW, bgH, rad, bgColor,
                        sideLeft, sideRight,
                        sideLeft, sideRight
                );
            } else {
                RenderUtil.drawRect(bgX1, bgY1, bgX2, bgY2, bgColor);
            }
        }
        if (this.showBar.getValue()) {
            if (this.shadow.getValue()) {
                RenderUtil.drawRect(
                        xDraw + (this.posX.getValue() == 0 ? -3.0F : 1.0F),
                        yDraw - (this.posY.getValue() == 0 ? (isFirst ? 1.0F : 0.0F) : 1.0F),
                        xDraw + (this.posX.getValue() == 0 ? -2.0F : 2.0F),
                        yDraw + height + (this.posY.getValue() == 0 ? 1.0F : (isFirst ? 1.0F : 0.0F)),
                        color
                );
                RenderUtil.drawRect(
                        xDraw + (this.posX.getValue() == 0 ? -2.0F : 2.0F),
                        yDraw - (this.posY.getValue() == 0 ? (isFirst ? 1.0F : 0.0F) : 1.0F),
                        xDraw + (this.posX.getValue() == 0 ? -1.0F : 3.0F),
                        yDraw + height + (this.posY.getValue() == 0 ? 1.0F : (isFirst ? 1.0F : 0.0F)),
                        (color & 16579836) >> 2 | color & 0xFF000000
                );
            } else {
                RenderUtil.drawRect(
                        xDraw + (this.posX.getValue() == 0 ? -2.0F : 1.0F),
                        yDraw - (this.posY.getValue() == 0 ? (isFirst ? 1.0F : 0.0F) : 0.0F),
                        xDraw + (this.posX.getValue() == 0 ? -1.0F : 2.0F),
                        yDraw + height + (this.posY.getValue() == 0 ? 0.0F : (isFirst ? 1.0F : 0.0F)),
                        color
                );
            }
        }
        RenderUtil.disableRenderState();
        GlStateManager.disableDepth();
        boolean fade = s < 1.0F;
        if (fade) {
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
        float textX = xDraw - (this.posX.getValue() == 1 ? totalWidth : 0.0F);
        if (this.shadow.getValue()) {
            mc.fontRendererObj.drawStringWithShadow(moduleName, textX, yDraw, color);
        } else {
            mc.fontRendererObj.drawString(
                    moduleName,
                    textX,
                    yDraw + (this.posY.getValue() == 1 ? 1.0F : 0.0F),
                    color,
                    false
            );
        }
        if (this.suffixes.getValue() && moduleSuffix.length > 0) {
            int suffixColor = colorWithAlpha(ChatColors.GRAY.toAwtColor(), s);
            float width = (float) mc.fontRendererObj.getStringWidth(moduleName) + 3.0F;
            for (String string : moduleSuffix) {
                if (this.shadow.getValue()) {
                    mc.fontRendererObj.drawStringWithShadow(string, textX + width, yDraw, suffixColor);
                } else {
                    mc.fontRendererObj.drawString(
                            string,
                            textX + width,
                            yDraw + (this.posY.getValue() == 1 ? 1.0F : 0.0F),
                            suffixColor,
                            false
                    );
                }
                width += (float) mc.fontRendererObj.getStringWidth(string) + (this.shadow.getValue() ? 3.0F : 2.0F);
            }
        }
        if (fade) {
            GlStateManager.disableBlend();
        }
    }

    private void renderNotifications() {
        if (!this.notifications.getValue()) return;

        try {
            if (Myau.notificationManager == null) return;

            java.util.List<myau.management.NotificationManager.NotificationEntry> entries = Myau.notificationManager.getActive();
            if (entries.isEmpty()) return;

            float notificationScale = Math.max(0.5F, Math.min(1.5F, this.scale.getValue()));
            ScaledResolution sr = new ScaledResolution(mc);
            float scaledWidth = sr.getScaledWidth() / notificationScale;
            float scaledHeight = sr.getScaledHeight() / notificationScale;
            float margin = 8.0F;
            float paddingX = 8.0F;
            float paddingY = 5.0F;
            float spacing = 4.0F;
            float y = scaledHeight - margin;

            GlStateManager.pushMatrix();
            GlStateManager.scale(notificationScale, notificationScale, 1.0F);

            for (int i = entries.size() - 1; i >= 0; i--) {
                myau.management.NotificationManager.NotificationEntry entry = entries.get(i);
                float alpha = notificationAlpha(entry);
                if (alpha <= 0.01F) continue;

                String text = modernNotificationText(entry.message);
                float textWidth = mc.fontRendererObj.getStringWidth(text);
                float textHeight = mc.fontRendererObj.FONT_HEIGHT;
                float boxWidth = Math.max(86.0F, textWidth + paddingX * 2.0F + 2.0F);
                float boxHeight = textHeight + paddingY * 2.0F + 3.0F;
                float x = scaledWidth - margin - boxWidth;
                y -= boxHeight;

                drawModernNotification(entry, text, x, y, boxWidth, boxHeight, paddingX, paddingY, alpha);
                y -= spacing;
            }

            GlStateManager.popMatrix();
        } catch (Exception ignored) {
        }
    }

    private void drawModernNotification(myau.management.NotificationManager.NotificationEntry entry, String text,
                                        float x, float y, float boxWidth, float boxHeight,
                                        float paddingX, float paddingY, float alpha) {
        float motion = notificationMotion(entry);
        float slide = (1.0F - motion) * 14.0F + (1.0F - alpha) * 5.0F;
        float renderX = x + slide;
        int statusColor = notificationStatusColor(text, alpha);
        int bg = new Color(10, 12, 16, (int) (92 * alpha)).getRGB();
        int border = new Color(255, 255, 255, (int) (24 * alpha)).getRGB();
        int neutralText = new Color(238, 241, 245, (int) (242 * alpha)).getRGB();
        int depth = new Color(0, 0, 0, (int) (28 * alpha)).getRGB();
        float radius = 5.0F;

        // 玻璃开着用磨砂卡，投影/深色底/白边不再画
        boolean glassDrawn = false;
        if (this.glassOn(this.glassNotify)) {
            ScaledResolution sr = new ScaledResolution(mc);
            glassDrawn = myau.util.FrostedGlass.draw(renderX, y, boxWidth, boxHeight, radius,
                    sr.getScaledWidth(), sr.getScaledHeight(), alpha, true);
        }
        if (!glassDrawn) {
            RenderUtil.drawRoundedRect(renderX + 1.0F, y + 1.5F, boxWidth, boxHeight, radius + 1.0F,
                    depth, true, true, true, true);
            RenderUtil.drawRoundedRect(renderX, y, boxWidth, boxHeight, radius, bg, true, true, true, true);
            RenderUtil.drawRoundedRectOutline(renderX + 0.5F, y + 0.5F, boxWidth - 1.0F, boxHeight - 1.0F,
                    radius, 1.0F, border, true, true, true, true);
        }

        float progress = notificationProgress(entry);
        float progressX = renderX + 6.0F;
        float progressY = y + boxHeight - 2.0F;
        float progressW = boxWidth - 12.0F;
        RenderUtil.drawRoundedRect(progressX, progressY, progressW, 1.0F, 0.5F,
                new Color(255, 255, 255, (int) (8 * alpha)).getRGB(), true, true, true, true);
        RenderUtil.drawRoundedRect(progressX, progressY, Math.max(1.0F, progressW * progress), 1.0F, 0.5F,
                statusColor, true, true, true, true);

        drawNotificationText(text, renderX + paddingX + 1.0F, y + paddingY + 1.0F, neutralText, statusColor);
    }

    private float notificationAlpha(myau.management.NotificationManager.NotificationEntry entry) {
        if (entry.durationMillis <= 0) return 1.0F;
        float age = entry.getAge();
        float remaining = entry.durationMillis - age;
        float fade = Math.min(220.0F, entry.durationMillis / 3.0F);
        float alpha = Math.min(1.0F, Math.min(age / fade, remaining / fade));
        alpha = Math.max(0.0F, Math.min(1.0F, alpha));
        return alpha * alpha * (3.0F - 2.0F * alpha);
    }

    private float notificationProgress(myau.management.NotificationManager.NotificationEntry entry) {
        if (entry.durationMillis <= 0) return 1.0F;
        return Math.max(0.0F, Math.min(1.0F, 1.0F - entry.getAge() / (float) entry.durationMillis));
    }

    private float notificationMotion(myau.management.NotificationManager.NotificationEntry entry) {
        if (entry.durationMillis <= 0) return 1.0F;
        float age = entry.getAge();
        float remaining = entry.durationMillis - age;
        float in = Math.max(0.0F, Math.min(1.0F, age / 260.0F));
        float out = Math.max(0.0F, Math.min(1.0F, remaining / 220.0F));
        float motion = Math.min(in, out);
        return motion * motion * (3.0F - 2.0F * motion);
    }

    private String modernNotificationText(String message) {
        if (message == null) return "";
        return message
                .replace(" was toggled successfully", " enabled")
                .replace(" was untoggled successfully", " disabled");
    }

    private int notificationStatusColor(String text, float alpha) {
        String lower = text.toLowerCase(Locale.ROOT);
        int rgb = lower.endsWith(" enabled") ? 0x41D982 : lower.endsWith(" disabled") ? 0xFF5C6C : 0xE5E9F0;
        int a = Math.max(0, Math.min(255, (int) (245 * alpha)));
        return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, a).getRGB();
    }

    private void drawNotificationText(String text, float x, float y, int neutralColor, int statusColor) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.endsWith(" enabled")) {
            drawSplitNotificationText(text, " enabled", x, y, neutralColor, statusColor);
        } else if (lower.endsWith(" disabled")) {
            drawSplitNotificationText(text, " disabled", x, y, neutralColor, statusColor);
        } else {
            mc.fontRendererObj.drawString(text, (int) x, (int) y, neutralColor, false);
        }
    }

    private void drawSplitNotificationText(String text, String suffix, float x, float y, int neutralColor, int statusColor) {
        String main = text.substring(0, text.length() - suffix.length());
        mc.fontRendererObj.drawString(main, (int) x, (int) y, neutralColor, false);
        mc.fontRendererObj.drawString(suffix.trim(), (int) (x + mc.fontRendererObj.getStringWidth(main + " ")), (int) y, statusColor, false);
    }
}
