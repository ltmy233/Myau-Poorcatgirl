package myau.module.modules;

import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.TextProperty;
import myau.ui.Lang;
import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fml.client.config.GuiButtonExt;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class PotionHUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final String[] TIME_FORMAT_OPTIONS = new String[]{ "1m20s", "01:20" };
    private static final String[] SORT_OPTIONS = new String[]{ "Duration", "Length" };
    private static final String[] SORT_DIRECTION_OPTIONS = new String[]{ "Descending", "Ascending" };
    private static final String[] ALIGNMENT_OPTIONS = new String[]{ "Left", "Center", "Right" };
    private static final String[] VERTICAL_ALIGNMENT_OPTIONS = new String[]{ "Top", "Center", "Bottom" };

    private static final float DEFAULT_RELATIVE_X = 0.5f;
    private static final float DEFAULT_RELATIVE_Y = 0.5f;
    private static final int TIMER_COLOR = 0xFFAAAAAA;
    private static final int EDIT_OUTLINE_COLOR = 0xFFFFFFFF;
    private static final int BACKGROUND_COLOR = new Color(0, 0, 0, 110).getRGB();
    private static final String PLACEHOLDER_TEXT = "No Potions Active";
    private static final String EDIT_MESSAGE = "Edit the HUD position by dragging.";
    private static final String RESET_BUTTON = "Reset position";

    public final ModeProperty timeFormat = new ModeProperty("time-format", 0, TIME_FORMAT_OPTIONS);
    public final ModeProperty sortMode = new ModeProperty("sort-by", 0, SORT_OPTIONS);
    public final ModeProperty sortDirection = new ModeProperty("sort-direction", 0, SORT_DIRECTION_OPTIONS);
    public final ModeProperty horizontalAlignment = new ModeProperty("horizontal-alignment", 0, ALIGNMENT_OPTIONS);
    public final ModeProperty verticalAlignment = new ModeProperty("vertical-alignment", 0, VERTICAL_ALIGNMENT_OPTIONS);
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 2.0F);
    public final BooleanProperty editPosition = new BooleanProperty("edit-position", false);
    public final BooleanProperty excludePermanent = new BooleanProperty("exclude-permanent", false);
    public final BooleanProperty drawBackground = new BooleanProperty("draw-background", false);
    public final BooleanProperty backgroundBlur = new BooleanProperty("background-blur", true);
    public final BooleanProperty bar = new BooleanProperty("bar", true);
    public final BooleanProperty textShadow = new BooleanProperty("text-shadow", true);
    public final TextProperty potionBlacklist = new TextProperty("potion-blacklist", "");

    private float posX = Float.NaN;
    private float posY = Float.NaN;
    private float relativePosX = Float.NaN;
    private float relativePosY = Float.NaN;

    public PotionHUD() {
        super("PotionHUD", false);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        if (mc.currentScreen != null || mc.gameSettings.showDebugInfo) {
            return;
        }

        render(false);
    }

    @Override
    public void verifyValue(String name) {
        if ("edit-position".equals(name) && this.editPosition.getValue()) {
            this.editPosition.setValue(false);
            mc.displayGuiScreen(new EditScreen());
        }
    }

    public float getPosX() {
        syncPositionToResolution();
        return this.posX;
    }

    public float getPosY() {
        syncPositionToResolution();
        return this.posY;
    }

    public float getRelativePosX() {
        syncPositionToResolution();
        return this.relativePosX;
    }

    public float getRelativePosY() {
        syncPositionToResolution();
        return this.relativePosY;
    }

    public void setRelativePosition(float normalizedX, float normalizedY) {
        this.relativePosX = normalizedX;
        this.relativePosY = normalizedY;
        syncPositionToResolution();
    }

    public void setAbsolutePosition(float absoluteX, float absoluteY) {
        setAbsolutePosition(absoluteX, absoluteY, new ScaledResolution(mc));
    }

    public void resetPosition() {
        setRelativePosition(DEFAULT_RELATIVE_X, DEFAULT_RELATIVE_Y);
    }

    @Override
    public void writeConfig(com.google.gson.JsonObject object) {
        if (!Float.isNaN(this.relativePosX) && !Float.isNaN(this.relativePosY)) {
            object.addProperty("pos-x", this.relativePosX);
            object.addProperty("pos-y", this.relativePosY);
        }
    }

    @Override
    public void readConfig(com.google.gson.JsonObject object) {
        if (object.has("pos-x") && object.has("pos-y")) {
            float x = object.get("pos-x").getAsFloat();
            float y = object.get("pos-y").getAsFloat();
            if (!Float.isNaN(x) && !Float.isNaN(y)) {
                this.relativePosX = x;
                this.relativePosY = y;
            }
        }
    }

    private void render(boolean editing) {
        ScaledResolution resolution = new ScaledResolution(mc);
        syncPositionToResolution(resolution);

        RenderState state = buildRenderState(editing);
        if (state.entries.isEmpty()) {
            return;
        }

        adjustAnchorForLayoutChanges(resolution, state);
        Bounds bounds = renderState(state);
        if (editing) {
            drawBounds(bounds);
        }
    }

    private RenderState buildRenderState(boolean includePlaceholder) {
        LayoutMetrics metrics = LayoutMetrics.from(getSelectedScale());
        ArrayList<PotionEntry> entries = new ArrayList<PotionEntry>();
        Collection<PotionEffect> activeEffects = mc.thePlayer == null ? null : mc.thePlayer.getActivePotionEffects();

        if (activeEffects != null) {
            for (PotionEffect effect : activeEffects) {
                PotionEntry entry = buildEntry(effect);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        }

        sortEntries(entries);

        if (entries.isEmpty() && includePlaceholder) {
            entries.add(PotionEntry.placeholder(scaledWidth(PLACEHOLDER_TEXT)));
        }

        float maxWidth = 0.0F;
        for (PotionEntry entry : entries) {
            maxWidth = Math.max(maxWidth, entry.totalWidth);
        }

        return new RenderState(metrics, entries, maxWidth);
    }

    private PotionEntry buildEntry(PotionEffect effect) {
        if (effect == null) {
            return null;
        }

        int duration = effect.getDuration();
        if (this.excludePermanent.getValue() && duration > 32000) {
            return null;
        }

        Potion potion = Potion.potionTypes[effect.getPotionID()];
        if (potion == null) {
            return null;
        }

        if (isBlacklisted(potion.getName())) {
            return null;
        }

        String label = getPotionLabel(effect, potion);
        String durationText = formatDuration(duration);
        float labelWidth = scaledWidth(label);
        float durationWidth = durationText.isEmpty() ? 0.0F : scaledWidth(durationText);
        float gapWidth = durationWidth > 0.0F ? scaledWidth(" ") : 0.0F;
        return new PotionEntry(label, durationText, labelWidth, durationWidth, gapWidth, duration, brighten(potion.getLiquidColor(), 0.38F));
    }

    private boolean isBlacklisted(String potionName) {
        String raw = this.potionBlacklist.getValue();
        if (raw == null || raw.isEmpty()) {
            return false;
        }
        for (String entry : raw.split(",")) {
            if (entry.trim().equals(potionName)) {
                return true;
            }
        }
        return false;
    }

    private void sortEntries(List<PotionEntry> entries) {
        final int directionMultiplier = this.sortDirection.getValue() == 1 ? 1 : -1;

        if (this.sortMode.getValue() == 0) {
            entries.sort(new Comparator<PotionEntry>() {
                @Override
                public int compare(PotionEntry first, PotionEntry second) {
                    return Integer.compare(first.durationTicks, second.durationTicks) * directionMultiplier;
                }
            });
            return;
        }

        entries.sort(new Comparator<PotionEntry>() {
            @Override
            public int compare(PotionEntry first, PotionEntry second) {
                return Float.compare(first.totalWidth, second.totalWidth) * directionMultiplier;
            }
        });
    }

    private Bounds renderState(RenderState state) {
        float s = getSelectedScale();
        int horizontalAlignMode = this.horizontalAlignment.getValue();
        int verticalAlignMode = this.verticalAlignment.getValue();
        float stackHeight = state.entries.size() * state.metrics.rowHeight;
        float firstRowTop = verticalAlignMode == 0
                ? this.posY
                : verticalAlignMode == 1 ? this.posY - stackHeight / 2.0f : this.posY - stackHeight;
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        GlStateManager.pushMatrix();
        GlStateManager.scale(s, s, 1.0F);
        GlStateManager.disableDepth();
        GlStateManager.disableCull();

        for (int i = 0; i < state.entries.size(); i++) {
            PotionEntry entry = state.entries.get(i);
            float rowTop = firstRowTop + i * state.metrics.rowHeight;
            float rowLeft = horizontalAlignMode == 0
                    ? this.posX
                    : horizontalAlignMode == 2 ? this.posX - entry.totalWidth : this.posX - entry.totalWidth / 2.0f;
            float backgroundLeft = rowLeft - state.metrics.horizontalTextPadding;
            float backgroundRight = rowLeft + entry.totalWidth + state.metrics.horizontalTextPadding;
            float backgroundBottom = rowTop + state.metrics.rowHeight;
            float textY = rowTop + state.metrics.textTopPadding - state.metrics.textTopOffset;
            float timerX = rowLeft + entry.labelWidth + entry.gapWidth;

            if (i == 0) {
                state.cascadeTop = rowTop;
                state.cascadeLeft = backgroundLeft;
            }
            state.cascadeBottom = Math.max(state.cascadeBottom, backgroundBottom);
            state.cascadeRight = Math.max(state.cascadeRight, backgroundRight);
            HUD themeHud = HUD.get();
            java.awt.Color themeColor = themeHud != null ? themeHud.getColor(System.currentTimeMillis(), i) : null;
            int rowColor = themeColor != null ? (themeColor.getRGB() & 0xFFFFFF) | 0xFF000000 : entry.color;

            if (this.drawBackground.getValue() || this.backgroundBlur.getValue()) {
                float bgBlurWidth = (backgroundRight - backgroundLeft) / s;
                float bgBlurHeight = (backgroundBottom - rowTop) / s;
                boolean rounded = themeHud != null && themeHud.rounded.getValue();
                float radius = rounded
                        ? Math.min(themeHud.cornerRadius.getValue(), Math.min(bgBlurWidth, bgBlurHeight) * 0.5F)
                        : 0.0F;
                boolean glassDrawn = false;
                if (this.backgroundBlur.getValue()) {
                    ScaledResolution glassResolution = new ScaledResolution(mc);
                    glassDrawn = myau.util.FrostedGlass.draw(backgroundLeft / s, rowTop / s, bgBlurWidth, bgBlurHeight, radius,
                            glassResolution.getScaledWidth(), glassResolution.getScaledHeight(), 1.0F, true);
                }
                if (!glassDrawn) {
                    GlStateManager.enableBlend();
                    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    GlStateManager.disableTexture2D();
                    if (rounded) {
                        RenderUtil.drawRoundedRect(backgroundLeft / s, rowTop / s, bgBlurWidth, bgBlurHeight, radius,
                                BACKGROUND_COLOR, true, true, true, true);
                    } else {
                        RenderUtil.drawRect(backgroundLeft / s, rowTop / s, backgroundRight / s, backgroundBottom / s, BACKGROUND_COLOR);
                    }
                    GlStateManager.enableTexture2D();
                }
            }

            mc.fontRendererObj.drawString(entry.label, rowLeft / s, textY / s, rowColor, this.textShadow.getValue());
            if (!entry.durationText.isEmpty()) {
                mc.fontRendererObj.drawString(entry.durationText, timerX / s, textY / s, TIMER_COLOR, this.textShadow.getValue());
            }
            if (this.bar.getValue()) {
                int barColor = rowColor;
                int barDark = (barColor & 16579836) >> 2 | barColor & 0xFF000000;
                float barTop = (rowTop + state.metrics.textTopPadding) / s - 1.0F;
                float barBottom = barTop + (float) mc.fontRendererObj.FONT_HEIGHT + 2.0F;
                float barLeftEdge = backgroundLeft / s;
                float barRightEdge = backgroundRight / s;
                boolean shadow = this.textShadow.getValue();
                if (horizontalAlignMode == 2) {
                    RenderUtil.drawRect(barRightEdge - 2.0F, barTop, barRightEdge - 1.0F, barBottom, barColor);
                    if (shadow) {
                        RenderUtil.drawRect(barRightEdge - 1.0F, barTop, barRightEdge, barBottom, barDark);
                    }
                } else {
                    if (shadow) {
                        RenderUtil.drawRect(barLeftEdge, barTop, barLeftEdge + 1.0F, barBottom, barColor);
                    }
                    RenderUtil.drawRect(barLeftEdge + 1.0F, barTop, barLeftEdge + 2.0F, barBottom, shadow ? barDark : barColor);
                }
            }

            minX = Math.min(minX, backgroundLeft);
            minY = Math.min(minY, rowTop);
            maxX = Math.max(maxX, backgroundRight);
            maxY = Math.max(maxY, backgroundBottom);
        }

        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.popMatrix();

        return new Bounds(minX, minY, maxX, maxY);
    }

    private void drawBounds(Bounds bounds) {
        float left = bounds.left - 1.0f;
        float top = bounds.top - 1.0f;
        float right = bounds.right + 1.0f;
        float bottom = bounds.bottom + 1.0f;
        GlStateManager.disableCull();
        GlStateManager.disableTexture2D();
        RenderUtil.drawRect(left, top, right, top + 1.0f, EDIT_OUTLINE_COLOR);
        RenderUtil.drawRect(left, bottom - 1.0f, right, bottom, EDIT_OUTLINE_COLOR);
        RenderUtil.drawRect(left, top, left + 1.0f, bottom, EDIT_OUTLINE_COLOR);
        RenderUtil.drawRect(right - 1.0f, top, right, bottom, EDIT_OUTLINE_COLOR);
        GlStateManager.enableTexture2D();
        GlStateManager.enableCull();
    }

    private void adjustAnchorForLayoutChanges(ScaledResolution resolution, RenderState state) {
        syncPositionToResolution(resolution);
    }

    private void syncPositionToResolution() {
        syncPositionToResolution(new ScaledResolution(mc));
    }

    private void syncPositionToResolution(ScaledResolution resolution) {
        int scaledWidth = Math.max(1, resolution.getScaledWidth());
        int scaledHeight = Math.max(1, resolution.getScaledHeight());

        if (Float.isNaN(this.relativePosX) || Float.isNaN(this.relativePosY)) {
            if (Float.isNaN(this.posX) || Float.isNaN(this.posY)) {
                this.relativePosX = DEFAULT_RELATIVE_X;
                this.relativePosY = DEFAULT_RELATIVE_Y;
            } else {
                this.relativePosX = this.posX / scaledWidth;
                this.relativePosY = this.posY / scaledHeight;
            }
        }

        this.posX = this.relativePosX * scaledWidth;
        this.posY = this.relativePosY * scaledHeight;
    }

    private void setAbsolutePosition(float absoluteX, float absoluteY, ScaledResolution resolution) {
        this.posX = absoluteX;
        this.posY = absoluteY;
        int scaledWidth = Math.max(1, resolution.getScaledWidth());
        int scaledHeight = Math.max(1, resolution.getScaledHeight());
        this.relativePosX = absoluteX / scaledWidth;
        this.relativePosY = absoluteY / scaledHeight;
    }

    private float getSelectedScale() {
        return this.scale.getValue();
    }

    private float scaledWidth(String text) {
        return mc.fontRendererObj.getStringWidth(text) * getSelectedScale();
    }

    private String getPotionLabel(PotionEffect effect, Potion potion) {
        String label = StatCollector.translateToLocal(effect.getEffectName());
        if (effect.getAmplifier() >= 1) {
            label += " " + toRomanNumeral(effect.getAmplifier() + 1);
        }
        return label.isEmpty() ? StatCollector.translateToLocal(potion.getName()) : label;
    }

    private String formatDuration(int durationTicks) {
        int totalSeconds = Math.max(0, durationTicks / 20);
        if (this.timeFormat.getValue() == 1) {
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;
            return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
        }

        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        if (minutes > 0 && seconds == 0) {
            return minutes + "m";
        }
        return (minutes > 0 ? minutes + "m" : "") + seconds + "s";
    }

    private static final class PotionEntry {
        private final String label;
        private final String durationText;
        private final float labelWidth;
        private final float durationWidth;
        private final float gapWidth;
        private final float totalWidth;
        private final int durationTicks;
        private final int color;

        private PotionEntry(String label, String durationText, float labelWidth, float durationWidth, float gapWidth, int durationTicks, int color) {
            this.label = label;
            this.durationText = durationText;
            this.labelWidth = labelWidth;
            this.durationWidth = durationWidth;
            this.gapWidth = gapWidth;
            this.totalWidth = labelWidth + gapWidth + durationWidth;
            this.durationTicks = durationTicks;
            this.color = color;
        }

        private static PotionEntry placeholder(float width) {
            return new PotionEntry(PLACEHOLDER_TEXT, "", width, 0.0F, 0.0F, 0, 0xFFFFFFFF);
        }
    }

    private static final class LayoutMetrics {
        private final float textTopOffset;
        private final float textTopPadding;
        private final float horizontalTextPadding;
        private final float rowHeight;

        private LayoutMetrics(float textTopOffset, float textTopPadding, float horizontalTextPadding, float rowHeight) {
            this.textTopOffset = textTopOffset;
            this.textTopPadding = textTopPadding;
            this.horizontalTextPadding = horizontalTextPadding;
            this.rowHeight = rowHeight;
        }

        private static LayoutMetrics from(float fontScale) {
            float textTopOffset = 0.0F;
            float textTopPadding = Math.max(1.0F, 2.0F * fontScale);
            float horizontalTextPadding = Math.max(1.0F, 2.0F * fontScale);
            float textBoxHeight = Math.max(1.0F, mc.fontRendererObj.FONT_HEIGHT * fontScale);
            float rowHeight = Math.max(1.0F, textBoxHeight + textTopPadding);
            return new LayoutMetrics(textTopOffset, textTopPadding, horizontalTextPadding, rowHeight);
        }
    }

    private static final class RenderState {
        private final LayoutMetrics metrics;
        private final List<PotionEntry> entries;
        private final float maxWidth;
        private float cascadeTop;
        private float cascadeLeft;
        private float cascadeRight;
        private float cascadeBottom;

        private RenderState(LayoutMetrics metrics, List<PotionEntry> entries, float maxWidth) {
            this.metrics = metrics;
            this.entries = entries;
            this.maxWidth = maxWidth;
        }
    }

    private static final class Bounds {
        private final float left;
        private final float top;
        private final float right;
        private final float bottom;

        private Bounds(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }

    private static int brighten(int color, float amount) {
        int r = color >> 16 & 0xFF;
        int g = color >> 8 & 0xFF;
        int b = color & 0xFF;
        r = (int) (r + (255 - r) * amount);
        g = (int) (g + (255 - g) * amount);
        b = (int) (b + (255 - b) * amount);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static String toRomanNumeral(int value) {
        if (value <= 0) {
            return Integer.toString(value);
        }

        int[] values = new int[]{ 1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1 };
        String[] numerals = new String[]{ "M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I" };
        StringBuilder builder = new StringBuilder();
        int remaining = value;

        for (int i = 0; i < values.length; i++) {
            while (remaining >= values[i]) {
                builder.append(numerals[i]);
                remaining -= values[i];
            }
        }

        return builder.toString();
    }

    private class EditScreen extends GuiScreen {
        private GuiButtonExt resetPosition;
        private boolean dragging;
        private float minX;
        private float minY;
        private float maxX;
        private float maxY;
        private float actualX;
        private float actualY;
        private float lastActualX;
        private float lastActualY;
        private int lastMouseX;
        private int lastMouseY;

        @Override
        public void initGui() {
            super.initGui();
            this.buttonList.add(this.resetPosition = new GuiButtonExt(1, this.width - 90, this.height - 25, 85, 20, Lang.t(RESET_BUTTON)));
            syncPositionToResolution(new ScaledResolution(this.mc));
            this.actualX = PotionHUD.this.posX;
            this.actualY = PotionHUD.this.posY;
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            ScaledResolution resolution = new ScaledResolution(this.mc);
            if (!this.dragging) {
                syncPositionToResolution(resolution);
                this.actualX = PotionHUD.this.posX;
                this.actualY = PotionHUD.this.posY;
            }

            Gui.drawRect(0, 0, this.width, this.height, -1308622848);
            setAbsolutePosition(this.actualX, this.actualY, resolution);

            RenderState state = buildRenderState(true);
            adjustAnchorForLayoutChanges(resolution, state);
            Bounds bounds = renderState(state);
            drawBounds(bounds);

            this.minX = bounds.left;
            this.minY = bounds.top;
            this.maxX = bounds.right;
            this.maxY = bounds.bottom;
            this.actualX = PotionHUD.this.posX;
            this.actualY = PotionHUD.this.posY;

            String message = Lang.t(EDIT_MESSAGE);
            int textX = resolution.getScaledWidth() / 2 - this.fontRendererObj.getStringWidth(message) / 2;
            int textY = resolution.getScaledHeight() / 2 - 20;
            this.fontRendererObj.drawStringWithShadow(message, textX, textY, 0xFFFFFFFF);

            try {
                this.handleInput();
            } catch (IOException ignored) {
            }

            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        @Override
        protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceLastClick) {
            super.mouseClickMove(mouseX, mouseY, button, timeSinceLastClick);
            if (button != 0) {
                return;
            }

            if (this.dragging) {
                this.actualX = this.lastActualX + (mouseX - this.lastMouseX);
                this.actualY = this.lastActualY + (mouseY - this.lastMouseY);
            } else if (mouseX >= this.minX && mouseX <= this.maxX && mouseY >= this.minY && mouseY <= this.maxY) {
                this.dragging = true;
                this.lastMouseX = mouseX;
                this.lastMouseY = mouseY;
                this.lastActualX = this.actualX;
                this.lastActualY = this.actualY;
            }
        }

        private void applyDragSnap(ScaledResolution resolution) {
        }

        @Override
        protected void mouseReleased(int mouseX, int mouseY, int state) {
            super.mouseReleased(mouseX, mouseY, state);
            if (state == 0) {
                this.dragging = false;
            }
        }

        @Override
        public void actionPerformed(GuiButton button) {
            if (button == this.resetPosition) {
                resetPosition();
                this.actualX = PotionHUD.this.posX;
                this.actualY = PotionHUD.this.posY;
            }
        }

        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }
    }
}
