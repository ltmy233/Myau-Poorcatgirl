package myau.mixin;

import myau.util.FrostedGlass;
import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptionSlider;
import net.minecraft.client.gui.GuiSlider;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(value = GuiButton.class, priority = 9999)
public abstract class MixinGuiButton {

    @Shadow
    public int xPosition;

    @Shadow
    public int yPosition;

    @Shadow
    public int width;

    @Shadow
    public int height;

    @Shadow
    public String displayString;

    @Shadow
    public boolean enabled;

    @Shadow
    public boolean visible;

    @Shadow(remap = false)
    public int packedFGColour;

    @Shadow
    protected boolean hovered;

    @Shadow
    protected abstract void mouseDragged(Minecraft mc, int mouseX, int mouseY);

    private static boolean glassOn() {
        myau.module.modules.HUD hud = myau.module.modules.HUD.get();
        return hud == null || hud.glassOn(hud.glassGui);
    }

    private static boolean glassPanel(float x, float y, float w, float h, float radius, int sw, int sh) {
        if (!glassOn()) return false;
        if (Minecraft.getMinecraft().currentScreen != null) {
            return FrostedGlass.drawFrosted(x, y, w, h, radius, sw, sh, 1.0F);
        }
        return FrostedGlass.draw(x, y, w, h, radius, sw, sh, 1.0F);
    }

    @Inject(method = "drawButton", at = @At("HEAD"), cancellable = true)
    private void myauDrawButton(Minecraft mc, int mouseX, int mouseY, CallbackInfo ci) {
        if (!this.visible) {
            ci.cancel();
            return;
        }
        ScaledResolution sr = new ScaledResolution(mc);
        this.hovered = this.enabled && mouseX >= this.xPosition && mouseY >= this.yPosition
                && mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height;
        if ((Object) this instanceof GuiOptionSlider || (Object) this instanceof GuiSlider) {
            // 原版 drawButton 里带滑块拖拽更新，取值前先跑一遍
            this.mouseDragged(mc, mouseX, mouseY);
            float t;
            String text;
            if ((Object) this instanceof GuiOptionSlider) {
                IAccessorGuiOptionSlider slider = (IAccessorGuiOptionSlider) (Object) this;
                t = slider.getSliderValue();
                text = this.displayString;
                GameSettings.Options options = slider.getOptions();
                if (options != null) {
                    String label;
                    try {
                        label = net.minecraft.client.resources.I18n.format(options.getEnumString());
                    } catch (Throwable ignored) {
                        label = options.getEnumString();
                    }
                    float denormalized = options.denormalizeValue(t);
                    text = options.getEnumFloat()
                            ? label + ": " + Math.round(denormalized * 100.0F) + "%"
                            : label + ": " + Math.round(denormalized);
                }
            } else {
                IAccessorGuiSlider slider = (IAccessorGuiSlider) (Object) this;
                t = slider.getSliderPosition();
                String label;
                try {
                    label = slider.callGetDisplayString();
                } catch (Throwable ignored) {
                    label = this.displayString;
                }
                text = label != null && !label.isEmpty() ? label : this.displayString;
            }
            if (!glassPanel(this.xPosition, this.yPosition, this.width, this.height, 5.0F,
                    sr.getScaledWidth(), sr.getScaledHeight())) {
                RenderUtil.drawRoundedRect(this.xPosition, this.yPosition, this.width, this.height, 5.0F, 0x4D000000);
            }
            if (this.hovered) {
                RenderUtil.drawRoundedRect(this.xPosition, this.yPosition, this.width, this.height, 5.0F, 0x26FFFFFF);
            }
            if (t < 0.0F) t = 0.0F;
            if (t > 1.0F) t = 1.0F;
            RenderUtil.drawRoundedRect(this.xPosition + 3, this.yPosition + 3,
                    (this.width - 6) * t, this.height - 6, 3.0F, this.enabled ? 0x40FFFFFF : 0x22FFFFFF);
            RenderUtil.drawRoundedRect(this.xPosition + 3 + (this.width - 6) * t - 3.0F,
                    this.yPosition + 1, 6.0F, this.height - 2, 3.0F, this.enabled ? 0xE6FFFFFF : 0x88FFFFFF);
            if (!this.enabled) {
                RenderUtil.drawRoundedRect(this.xPosition, this.yPosition, this.width, this.height, 5.0F, 0x33000000);
            }
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            int color = this.enabled ? (this.hovered ? 0xFFFFFF : 0xF0F0F0) : 0xA0A0A0;
            if (this.packedFGColour != 0) {
                color = this.packedFGColour;
            }
            float strW = mc.fontRendererObj.getStringWidth(text);
            mc.fontRendererObj.drawStringWithShadow(text,
                    this.xPosition + (this.width - strW) / 2.0F,
                    this.yPosition + (this.height - 8.0F) / 2.0F, color);
            ci.cancel();
            return;
        }
        if (!glassPanel(this.xPosition, this.yPosition, this.width, this.height, 5.0F,
                sr.getScaledWidth(), sr.getScaledHeight())) {
            RenderUtil.drawRoundedRect(this.xPosition, this.yPosition, this.width, this.height, 5.0F, 0x4D000000);
        }
        if (this.hovered) {
            RenderUtil.drawRoundedRect(this.xPosition, this.yPosition, this.width, this.height, 5.0F, 0x26FFFFFF);
        }
        if (!this.enabled) {
            RenderUtil.drawRoundedRect(this.xPosition, this.yPosition, this.width, this.height, 5.0F, 0x33000000);
        }
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        int color = this.enabled ? (this.hovered ? 0xFFFFFF : 0xF0F0F0) : 0xA0A0A0;
        if (this.packedFGColour != 0) {
            color = this.packedFGColour;
        }
        float strW = mc.fontRendererObj.getStringWidth(this.displayString);
        mc.fontRendererObj.drawStringWithShadow(this.displayString,
                this.xPosition + (this.width - strW) / 2.0F,
                this.yPosition + (this.height - 8.0F) / 2.0F, color);
        ci.cancel();
    }
}
