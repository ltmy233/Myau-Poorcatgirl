package myau.mixin;

import myau.Myau;
import myau.module.modules.BetterChat;
import myau.util.RenderUtil;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@SideOnly(Side.CLIENT)
@Mixin(value = GuiNewChat.class, priority = 2000)
public abstract class MixinGuiNewChat extends Gui {
    private static final float BETTER_CHAT_MAX_PANEL_HEIGHT = 180.0F;

    @Shadow
    private boolean isScrolled;

    @Shadow
    private int scrollPos;

    @Shadow
    public abstract float getChatScale();

    @Shadow
    public abstract int getLineCount();

    @Shadow
    private List<ChatLine> drawnChatLines;

    @Shadow
    private Minecraft mc;

    @Shadow
    public abstract boolean getChatOpen();

    @Shadow
    public abstract int getChatWidth();

    private float betterChatPercent;
    private int betterChatNewLines;
    private long betterChatPrevMillis = System.currentTimeMillis();
    private float betterChatAnimPercent;
    private int betterChatLineBeingDrawn;
    private boolean betterChatPanelPending;
    private int betterChatPanelLeft;
    private int betterChatPanelTop = Integer.MAX_VALUE;
    private int betterChatPanelBottom = Integer.MIN_VALUE;
    private int betterChatPanelRight;
    private int betterChatPanelColor;
    private float betterChatFrameDeltaMs;
    private boolean betterChatDrawnThisFrame;
    private boolean betterChatDrawActive;
    private float betterChatScrollPosition;
    private float betterChatScrollOffset;
    private float betterChatNewMessageOffset;
    private float betterChatDrawLeft;
    private float betterChatDrawTop;
    private float betterChatDrawRight;
    private float betterChatDrawBottom;

    private BetterChat betterChat() {
        if (Myau.moduleManager == null) {
            return null;
        }
        return (BetterChat) Myau.moduleManager.getModule(BetterChat.class);
    }

    private static float betterChatClamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }

    @Inject(method = "drawChat", at = @At("HEAD"))
    private void betterChatUpdateAnim(CallbackInfo ci) {
        long current = System.currentTimeMillis();
        long diff = current - this.betterChatPrevMillis;
        this.betterChatPrevMillis = current;
        this.betterChatFrameDeltaMs = betterChatClamp((float) diff, 0.0F, 100.0F);
        this.betterChatDrawnThisFrame = false;
        this.betterChatNewMessageOffset += (0.0F - this.betterChatNewMessageOffset)
                * (1.0F - (float) Math.exp(-this.betterChatFrameDeltaMs / 85.0F));
        if (Math.abs(this.betterChatNewMessageOffset) < 0.05F) this.betterChatNewMessageOffset = 0.0F;
        BetterChat mod = this.betterChat();
        if (mod == null || !mod.isEnabled() || !mod.smooth.getValue()) {
            this.betterChatScrollPosition = this.scrollPos;
            this.betterChatScrollOffset = 0.0F;
            this.betterChatPercent = 1.0F;
            this.betterChatAnimPercent = 1.0F;
            return;
        }
        float scrollStep = 1.0F - (float) Math.exp(-this.betterChatFrameDeltaMs / 65.0F);
        this.betterChatScrollPosition += (this.scrollPos - this.betterChatScrollPosition) * scrollStep;
        if (Math.abs(this.scrollPos - this.betterChatScrollPosition) < 0.01F) {
            this.betterChatScrollPosition = this.scrollPos;
        }
        this.betterChatScrollOffset = (this.betterChatScrollPosition - this.scrollPos) * 9.0F;
        if (this.betterChatPercent < 1.0F) {
            this.betterChatPercent += 0.0035F * this.betterChatFrameDeltaMs;
        }
        this.betterChatPercent = betterChatClamp(this.betterChatPercent, 0.0F, 1.0F);
        float t = this.betterChatPercent;
        float eased = t < 0.5F
                ? 4.0F * t * t * t
                : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 3.0D) / 2.0F;
        this.betterChatAnimPercent = betterChatClamp(eased, 0.0F, 1.0F);
    }

    @Inject(method = "drawChat", at = @At("RETURN"))
    private void betterChatEarlyReturn(CallbackInfo ci) {
        if (!this.betterChatDrawnThisFrame) {
            this.betterChatDrawActive = false;
            this.betterChatPanelPending = false;
            this.betterChatPanelLeft = 0;
            this.betterChatPanelTop = Integer.MAX_VALUE;
            this.betterChatPanelBottom = Integer.MIN_VALUE;
            this.betterChatPanelRight = 0;
        }
    }

    @Inject(
            method = "drawChat",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;pushMatrix()V",
                    ordinal = 0,
                    shift = At.Shift.AFTER
            )
    )
    private void betterChatTranslate(CallbackInfo ci) {
        BetterChat mod = this.betterChat();
        if (mod == null || !mod.isEnabled()) {
            return;
        }
        GlStateManager.translate(mod.xOffset.getValue(),
                mod.yOffset.getValue() + this.betterChatScrollOffset + this.betterChatNewMessageOffset, 0.0F);
    }

    @Redirect(
            method = "drawChat",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiNewChat;drawRect(IIIII)V",
                    ordinal = 0
            )
    )
    private void betterChatClearBackground(int left, int top, int right, int bottom, int color) {
        BetterChat mod = this.betterChat();
        if (mod == null || !mod.isEnabled()) {
            drawRect(left, top, right, bottom, color);
            return;
        }
        if (mod.clear.getValue()) {
            return;
        }
        if (mod.rounded.getValue()) {
            return;
        }
        drawRect(left, top, right, bottom, color);
    }

    @Inject(
            method = "drawChat",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;scale(FFF)V",
                    ordinal = 0,
                    shift = At.Shift.AFTER
            )
    )
    private void betterChatPanelBackground(int updateCounter, CallbackInfo ci) {
        this.betterChatDrawnThisFrame = true;
        BetterChat mod = this.betterChat();
        if (mod != null && mod.isEnabled() && mod.rounded.getValue() && !mod.clear.getValue()) {
            int lineLimit = this.getLineCount();
            int visibleLines = 0;
            int strongestAlpha = 0;
            boolean chatOpen = this.getChatOpen();
            for (int i = 0; i < lineLimit && i + this.scrollPos < this.drawnChatLines.size(); i++) {
                ChatLine line = this.drawnChatLines.get(i + this.scrollPos);
                if (line == null) continue;
                int age = updateCounter - line.getUpdatedCounter();
                if (age >= 200 && !chatOpen) continue;
                double fade = 1.0D - age / 200.0D;
                fade = Math.max(0.0D, Math.min(1.0D, fade * 10.0D));
                fade *= fade;
                int alpha = chatOpen ? 255 : (int) (255.0D * fade);
                strongestAlpha = Math.max(strongestAlpha, alpha);
                visibleLines++;
            }
            if (visibleLines > 0 && strongestAlpha > 3) {
                float opacity = this.mc.gameSettings.chatOpacity * 0.9F + 0.1F;
                int alpha = (int) (strongestAlpha * opacity * 0.5F);
                int color = Math.max(0, Math.min(255, alpha)) << 24;
                float width = this.getChatWidth() / this.getChatScale() + 4.0F;
                float height = visibleLines * 9.0F;
                RenderUtil.drawRoundedRect(0.0F, -height, width, height, 6.0F, color);
            }
            this.betterChatDrawActive = false;
            this.betterChatPanelPending = false;
            this.betterChatPanelLeft = 0;
            this.betterChatPanelTop = Integer.MAX_VALUE;
            this.betterChatPanelBottom = Integer.MIN_VALUE;
            this.betterChatPanelRight = 0;
            return;
        }
        boolean wantPanel = mod != null && mod.isEnabled() && mod.rounded.getValue() && !mod.clear.getValue();
        boolean hasTarget = wantPanel && this.betterChatPanelPending
                && this.betterChatPanelBottom > this.betterChatPanelTop
                && this.betterChatPanelRight > this.betterChatPanelLeft;

        if (!wantPanel) {
            this.betterChatDrawActive = false;
        } else if (hasTarget && !this.betterChatDrawActive) {
            this.betterChatDrawActive = true;
            this.betterChatDrawLeft = this.betterChatPanelLeft;
            this.betterChatDrawRight = this.betterChatPanelRight;
            this.betterChatDrawBottom = this.betterChatPanelBottom;
            this.betterChatDrawTop = this.betterChatPanelBottom;
        }

        if (this.betterChatDrawActive) {
            float targetLeft;
            float targetTop;
            float targetRight;
            float targetBottom;
            if (hasTarget) {
                targetLeft = this.betterChatPanelLeft;
                targetTop = this.betterChatPanelTop;
                targetRight = this.betterChatPanelRight;
                targetBottom = this.betterChatPanelBottom;
            } else {
                targetLeft = this.betterChatDrawLeft;
                targetTop = this.betterChatDrawBottom;
                targetRight = this.betterChatDrawRight;
                targetBottom = this.betterChatDrawBottom;
            }
            if (targetBottom - targetTop > BETTER_CHAT_MAX_PANEL_HEIGHT) {
                targetTop = targetBottom - BETTER_CHAT_MAX_PANEL_HEIGHT;
            }

            // 新消息一来，文字动画会从头开始跳，这时候面板尺寸不能跟着上一帧走，直接对齐目标。
            boolean snap = mod == null || !mod.smooth.getValue() || this.betterChatPercent < 1.0F;
            if (snap) {
                this.betterChatDrawLeft = targetLeft;
                this.betterChatDrawTop = targetTop;
                this.betterChatDrawRight = targetRight;
                this.betterChatDrawBottom = targetBottom;
            } else {
                float delta = this.betterChatFrameDeltaMs;
                float rate = targetTop < this.betterChatDrawTop ? 0.045F : 0.026F;
                float step = 1.0F - (float) Math.exp((double) (-(rate * delta)));
                this.betterChatDrawLeft += (targetLeft - this.betterChatDrawLeft) * step;
                this.betterChatDrawTop += (targetTop - this.betterChatDrawTop) * step;
                this.betterChatDrawRight += (targetRight - this.betterChatDrawRight) * step;
                this.betterChatDrawBottom += (targetBottom - this.betterChatDrawBottom) * step;
                if (Math.abs(targetLeft - this.betterChatDrawLeft) < 0.5F) {
                    this.betterChatDrawLeft = targetLeft;
                }
                if (Math.abs(targetTop - this.betterChatDrawTop) < 0.5F) {
                    this.betterChatDrawTop = targetTop;
                }
                if (Math.abs(targetRight - this.betterChatDrawRight) < 0.5F) {
                    this.betterChatDrawRight = targetRight;
                }
                if (Math.abs(targetBottom - this.betterChatDrawBottom) < 0.5F) {
                    this.betterChatDrawBottom = targetBottom;
                }
                if (Math.abs(targetTop - this.betterChatDrawTop) > 45.0F) {
                    this.betterChatDrawTop = targetTop;
                }
            }

            float width = this.betterChatDrawRight - this.betterChatDrawLeft;
            float height = this.betterChatDrawBottom - this.betterChatDrawTop;
            boolean collapsing = !hasTarget;

            if (collapsing && height < 0.5F) {
                this.betterChatDrawActive = false;
            }

            if (this.betterChatDrawActive) {
                float shift = this.betterChatDrawTop - targetTop;
                if (shift > 0.0F) {
                    GlStateManager.translate(0.0F, shift, 0.0F);
                }
                if (width >= 0.5F && height >= 0.5F) {
                    int color = this.betterChatPanelColor;
                    float alphaScale = this.betterChatAnimPercent;
                    if (collapsing) {
                        alphaScale *= betterChatClamp(height / 12.0F, 0.0F, 1.0F);
                    }
                    int alpha = (int) (((color >>> 24) & 0xFF) * alphaScale);
                    color = (alpha << 24) | (color & 0xFFFFFF);
                    float radius = Math.min(8.0F, height * 0.5F);
                    RenderUtil.drawRoundedRect(this.betterChatDrawLeft, this.betterChatDrawTop, width, height, radius, color);
                }
            }
        }

        this.betterChatPanelPending = false;
        this.betterChatPanelLeft = 0;
        this.betterChatPanelTop = Integer.MAX_VALUE;
        this.betterChatPanelBottom = Integer.MIN_VALUE;
        this.betterChatPanelRight = 0;
    }

    @ModifyArg(
            method = "drawChat",
            at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;", ordinal = 0, remap = false),
            index = 0
    )
    private int betterChatLineIndex(int line) {
        this.betterChatLineBeingDrawn = line;
        return line;
    }

    @ModifyArg(
            method = "drawChat",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;drawStringWithShadow(Ljava/lang/String;FFI)I"),
            index = 3
    )
    private int betterChatTextOpacity(int original) {
        BetterChat mod = this.betterChat();
        if (mod != null && mod.isEnabled() && mod.smooth.getValue() && this.betterChatLineBeingDrawn <= this.betterChatNewLines) {
            int opacity = (original >> 24) & 0xFF;
            opacity = (int) ((float) opacity * this.betterChatAnimPercent);
            return (original & ~(0xFF << 24)) | (opacity << 24);
        }
        return original;
    }

    @Inject(method = "printChatMessageWithOptionalDeletion", at = @At("HEAD"))
    private void betterChatResetAnim(CallbackInfo ci) {
        if (!this.isScrolled) this.betterChatPercent = 0.0F;
    }

    @ModifyVariable(method = "setChatLine", at = @At("STORE"), ordinal = 0)
    private List<IChatComponent> betterChatSetNewLines(List<IChatComponent> original) {
        this.betterChatNewLines = original.size() - 1;
        BetterChat mod = this.betterChat();
        if (!this.isScrolled && mod != null && mod.isEnabled() && mod.smooth.getValue()) {
            this.betterChatNewMessageOffset = Math.min(12.0F,
                    this.betterChatNewMessageOffset + Math.max(1, original.size() - 1) * 9.0F);
        }
        return original;
    }

    private static final SimpleDateFormat betterChatTimeFormat = new SimpleDateFormat("HH:mm");

    @ModifyVariable(method = "setChatLine", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private IChatComponent betterChatTimestamp(IChatComponent component) {
        BetterChat mod = this.betterChat();
        if (mod == null || !mod.isEnabled() || !mod.timestamps.getValue() || component == null) {
            return component;
        }
        ChatComponentText prefix = new ChatComponentText("§8[" + betterChatTimeFormat.format(new Date()) + "]§r ");
        prefix.appendSibling(component);
        return prefix;
    }

    @ModifyConstant(method = "setChatLine", constant = @Constant(intValue = 100))
    private int betterChatHistory(int original) {
        BetterChat mod = this.betterChat();
        if (mod != null && mod.isEnabled() && mod.history.getValue()) {
            return 10000;
        }
        return original;
    }

    @ModifyVariable(method = "getChatComponent", at = @At(value = "STORE", ordinal = 0), ordinal = 3)
    private int betterChatMouseX(int original) {
        BetterChat mod = this.betterChat();
        if (mod == null || !mod.isEnabled()) {
            return original;
        }
        return original - mod.xOffset.getValue();
    }

    @ModifyVariable(method = "getChatComponent", at = @At(value = "STORE", ordinal = 0), ordinal = 4)
    private int betterChatMouseY(int original) {
        BetterChat mod = this.betterChat();
        if (mod == null || !mod.isEnabled()) {
            return original;
        }
        return original + mod.yOffset.getValue();
    }
}
