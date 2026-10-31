package poorcatgirl.module.modules;

import poorcatgirl.Poorcatgirl;
import poorcatgirl.enums.ChatColors;
import poorcatgirl.event.EventTarget;
import poorcatgirl.event.types.EventType;
import poorcatgirl.events.PacketEvent;
import poorcatgirl.events.Render2DEvent;
import poorcatgirl.module.Module;
import poorcatgirl.util.ColorUtil;
import poorcatgirl.util.RenderUtil;
import poorcatgirl.util.TeamUtil;
import poorcatgirl.util.TimerUtil;
import poorcatgirl.property.properties.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class TargetHUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final DecimalFormat healthFormat = new DecimalFormat("0.0", new DecimalFormatSymbols(Locale.US));
    private static final DecimalFormat diffFormat = new DecimalFormat("+0.0;-0.0", new DecimalFormatSymbols(Locale.US));
    private final TimerUtil lastAttackTimer = new TimerUtil();
    private final TimerUtil animTimer = new TimerUtil();
    private EntityLivingBase lastTarget = null;
    private EntityLivingBase target = null;
    private ResourceLocation headTexture = null;
    private float oldHealth = 0.0F;
    private float newHealth = 0.0F;
    private float maxHealth = 0.0F;
    public final ModeProperty style = new ModeProperty("style", 1, new String[]{"POORCATGIRL", "RAVEN_LEGACY", "MATERIAL_WAVE"});
    private EntityLivingBase ravenTarget;
    private long ravenLastAlive;
    private long ravenHealthChanged;
    private float ravenHealthFrom;
    private float ravenHealthTo;
    public final ModeProperty color = new ModeProperty("color", 0, new String[]{"DEFAULT", "HUD"});
    public final ModeProperty posX = new ModeProperty("position-x", 1, new String[]{"LEFT", "MIDDLE", "RIGHT"});
    public final ModeProperty posY = new ModeProperty("position-y", 1, new String[]{"TOP", "MIDDLE", "BOTTOM"});
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 1.5F);
    public final IntProperty offX = new IntProperty("offset-x", 0, -255, 255);
    public final IntProperty offY = new IntProperty("offset-y", 40, -255, 255);
    public final PercentProperty background = new PercentProperty("background", 25);
    public final BooleanProperty head = new BooleanProperty("head", true);
    public final BooleanProperty indicator = new BooleanProperty("indicator", true);
    public final BooleanProperty outline = new BooleanProperty("outline", false);
    public final BooleanProperty animations = new BooleanProperty("animations", true);
    public final BooleanProperty shadow = new BooleanProperty("shadow", true);
    public final BooleanProperty kaOnly = new BooleanProperty("ka-only", true);
    public final BooleanProperty chatPreview = new BooleanProperty("chat-preview", false);
    public final BooleanProperty glow = new BooleanProperty("glow", true);
    public final ColorProperty glowColor = new ColorProperty("glow-color", 0xFF66FF);
    public final BooleanProperty waveMoves = new BooleanProperty("wave-move", true);

    private EntityLivingBase resolveTarget() {
        KillAura killAura = (KillAura) Poorcatgirl.moduleManager.modules.get(KillAura.class);
        if (killAura.isEnabled() && TeamUtil.isEntityLoaded(killAura.getTarget())) {
            return killAura.getTarget();
        } else if (!(Boolean) this.kaOnly.getValue()
                && !this.lastAttackTimer.hasTimeElapsed(1500L)
                && TeamUtil.isEntityLoaded(this.lastTarget)) {
            return this.lastTarget;
        } else {
            return this.chatPreview.getValue() && mc.currentScreen instanceof GuiChat ? mc.thePlayer : null;
        }
    }

    private ResourceLocation getSkin(EntityLivingBase entityLivingBase) {
        if (entityLivingBase instanceof EntityPlayer) {
            if (entityLivingBase instanceof AbstractClientPlayer) {
                AbstractClientPlayer clientPlayer = (AbstractClientPlayer) entityLivingBase;
                if (clientPlayer.hasSkin()) return clientPlayer.getLocationSkin();
            }
            if (mc.getNetHandler() == null) {
                return DefaultPlayerSkin.getDefaultSkin(entityLivingBase.getUniqueID());
            }
            NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(entityLivingBase.getUniqueID());
            if (playerInfo == null) {
                playerInfo = mc.getNetHandler().getPlayerInfo(entityLivingBase.getName());
            }
            if (playerInfo != null) {
                return playerInfo.getLocationSkin();
            }
            return DefaultPlayerSkin.getDefaultSkin(entityLivingBase.getUniqueID());
        }
        return null;
    }

    private Color getTargetColor(EntityLivingBase entityLivingBase) {
        if (entityLivingBase instanceof EntityPlayer) {
            if (TeamUtil.isFriend((EntityPlayer) entityLivingBase)) {
                return Poorcatgirl.friendManager.getColor();
            }
            if (TeamUtil.isTarget((EntityPlayer) entityLivingBase)) {
                return Poorcatgirl.targetManager.getColor();
            }
        }
        switch (this.color.getValue()) {
            case 0:
                if (!(entityLivingBase instanceof EntityPlayer)) {
                    return new Color(-1);
                }
                return TeamUtil.getTeamColor((EntityPlayer) entityLivingBase, 1.0F);
            case 1:
                int rgb = ((HUD) Poorcatgirl.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()).getRGB();
                return new Color(rgb);
            default:
                return new Color(-1);
        }
    }

    public TargetHUD() {
        super("TargetHUD", false, true);
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (this.isEnabled() && this.style.getValue() == 1) {
            this.renderRavenTarget();
            return;
        }
        if (this.isEnabled() && this.style.getValue() == 2) {
            this.renderMaterialWaveTarget();
            return;
        }
        if (this.isEnabled() && mc.thePlayer != null) {
            EntityLivingBase entityLivingBase = this.target;
            this.target = this.resolveTarget();
            if (this.target != null) {
                float health = (mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount()) / 2.0F;
                float abs = this.target.getAbsorptionAmount() / 2.0F;
                float heal = this.target.getHealth() / 2.0F + abs;
                if (this.target != entityLivingBase) {
                    this.headTexture = null;
                    this.animTimer.setTime();
                    this.oldHealth = heal;
                    this.newHealth = heal;
                }
                if (!this.animations.getValue() || this.animTimer.hasTimeElapsed(150L)) {
                    this.oldHealth = this.newHealth;
                    this.newHealth = heal;
                    this.maxHealth = this.target.getMaxHealth() / 2.0F;
                    if (this.oldHealth != this.newHealth) {
                        this.animTimer.reset();
                    }
                }
                ResourceLocation resourceLocation = this.getSkin(this.target);
                if (resourceLocation != null) {
                    this.headTexture = resourceLocation;
                }
                float elapsedTime = (float) Math.min(Math.max(this.animTimer.getElapsedTime(), 0L), 150L);
                float healthRatio = Math.min(Math.max(RenderUtil.lerpFloat(this.newHealth, this.oldHealth, elapsedTime / 150.0F) / this.maxHealth, 0.0F), 1.0F);
                Color targetColor = this.getTargetColor(this.target);
                Color healthBarColor = this.color.getValue() == 0 ? ColorUtil.getHealthBlend(healthRatio) : targetColor;
                float healthDeltaRatio = Math.min(Math.max((health - heal + 1.0F) / 2.0F, 0.0F), 1.0F);
                Color healthDeltaColor = ColorUtil.getHealthBlend(healthDeltaRatio);
                ScaledResolution scaledResolution = new ScaledResolution(mc);
                String targetNameText = ChatColors.formatColor(String.format("&r%s&r", TeamUtil.stripName(this.target)));
                int targetNameWidth = mc.fontRendererObj.getStringWidth(targetNameText);
                String healthText = ChatColors.formatColor(
                        String.format("&r&f%s%s❤&r", healthFormat.format(heal), abs > 0.0F ? "&6" : "&c")
                );
                int healthTextWidth = mc.fontRendererObj.getStringWidth(healthText);
                String statusText = ChatColors.formatColor(String.format("&r&l%s&r", heal == health ? "D" : (heal < health ? "W" : "L")));
                int statusTextWidth = mc.fontRendererObj.getStringWidth(statusText);
                String healthDiffText = ChatColors.formatColor(
                        String.format("&r%s&r", heal == health ? "0.0" : diffFormat.format(health - heal))
                );
                int healthDiffWidth = mc.fontRendererObj.getStringWidth(healthDiffText);
                float barContentWidth = Math.max(
                        (float) targetNameWidth + (this.indicator.getValue() ? 2.0F + (float) statusTextWidth + 2.0F : 0.0F),
                        (float) healthTextWidth + (this.indicator.getValue() ? 2.0F + (float) healthDiffWidth + 2.0F : 0.0F)
                );
                float headIconOffset = this.head.getValue() && this.headTexture != null ? 25.0F : 0.0F;
                float barTotalWidth = Math.max(headIconOffset + 70.0F, headIconOffset + 2.0F + barContentWidth + 2.0F);
                float posX = this.offX.getValue().floatValue() / this.scale.getValue();
                switch (this.posX.getValue()) {
                    case 1:
                        posX += (float) scaledResolution.getScaledWidth() / this.scale.getValue() / 2.0F - barTotalWidth / 2.0F;
                        break;
                    case 2:
                        posX *= -1.0F;
                        posX += (float) scaledResolution.getScaledWidth() / this.scale.getValue() - barTotalWidth;
                }
                float posY = this.offY.getValue().floatValue() / this.scale.getValue();
                switch (this.posY.getValue()) {
                    case 1:
                        posY += (float) scaledResolution.getScaledHeight() / this.scale.getValue() / 2.0F - 13.5F;
                        break;
                    case 2:
                        posY *= -1.0F;
                        posY += (float) scaledResolution.getScaledHeight() / this.scale.getValue() - 27.0F;
                }
                GlStateManager.pushMatrix();
                GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 0.0F);
                GlStateManager.translate(posX, posY, -450.0F);
                // 外圈柔光颜色；关闭时不画光晕。
                if (this.glow.getValue()) {
                    poorcatgirl.util.FrostedGlass.drawEdgeColored(
                            0.0F, 0.0F, barTotalWidth, 27.0F, 2.0F, 1.0F,
                            this.glowColor.getValue());
                }
                RenderUtil.enableRenderState();
                int backgroundColor = new Color(0.0F, 0.0F, 0.0F, (float) this.background.getValue() / 100.0F).getRGB();
                int outlineColor = this.outline.getValue() ? targetColor.getRGB() : new Color(0, 0, 0, 0).getRGB();
                RenderUtil.drawOutlineRect(0.0F, 0.0F, barTotalWidth, 27.0F, 1.5F, backgroundColor, outlineColor);
                RenderUtil.drawRect(headIconOffset + 2.0F, 22.0F, barTotalWidth - 2.0F, 25.0F, ColorUtil.darker(healthBarColor, 0.2F).getRGB());
                RenderUtil.drawRect(headIconOffset + 2.0F, 22.0F, headIconOffset + 2.0F + healthRatio * (barTotalWidth - 2.0F - headIconOffset - 2.0F), 25.0F, healthBarColor.getRGB());
                RenderUtil.disableRenderState();
                GlStateManager.disableDepth();
                GlStateManager.enableBlend();
                GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                mc.fontRendererObj.drawString(targetNameText, headIconOffset + 2.0F, 2.0F, -1, this.shadow.getValue());
                mc.fontRendererObj.drawString(healthText, headIconOffset + 2.0F, 12.0F, -1, this.shadow.getValue());
                if (this.indicator.getValue()) {
                    mc.fontRendererObj.drawString(statusText, barTotalWidth - 2.0F - (float) statusTextWidth, 2.0F, healthDeltaColor.getRGB(), this.shadow.getValue());
                    mc.fontRendererObj.drawString(healthDiffText, barTotalWidth - 2.0F - (float) healthDiffWidth, 12.0F, ColorUtil.darker(healthDeltaColor, 0.8F).getRGB(), this.shadow.getValue());
                }
                if (this.head.getValue() && this.headTexture != null) {
                    GlStateManager.color(1.0F, 1.0F, 1.0F);
                    mc.getTextureManager().bindTexture(this.headTexture);
                    Gui.drawScaledCustomSizeModalRect(2, 2, 8.0F, 8.0F, 8, 8, 23, 23, 64.0F, 64.0F);
                    Gui.drawScaledCustomSizeModalRect(2, 2, 40.0F, 8.0F, 8, 8, 23, 23, 64.0F, 64.0F);
                    GlStateManager.color(1.0F, 1.0F, 1.0F);
                }
                GlStateManager.disableBlend();
                GlStateManager.enableDepth();
                GlStateManager.popMatrix();
            }
        }
    }

    private boolean prepareLegacyTarget(long now) {
        if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null) {
            ravenTarget = null;
            return false;
        }
        EntityLivingBase current = resolveTarget();
        if (current != null) {
            float health = current.isDead ? 0.0F : Math.max(0.0F, Math.min(1.0F, current.getHealth() / Math.max(1.0F, current.getMaxHealth())));
            if (current != ravenTarget) {
                ravenTarget = current;
                ravenHealthFrom = ravenHealthTo = health;
                headTexture = null;
            } else if (health != ravenHealthTo) {
                float t = Math.min(1.0F, (now - ravenHealthChanged) / 350.0F);
                ravenHealthFrom += (ravenHealthTo - ravenHealthFrom) * t;
                ravenHealthTo = health;
                ravenHealthChanged = now;
            }
            ResourceLocation skin = getSkin(current);
            if (skin != null) headTexture = skin;
            ravenLastAlive = now;
        }
        return ravenTarget != null;
    }

    private float legacyOpacity(long now) {
        float opacity = 1.0F - Math.max(0.0F, Math.min(1.0F, (now - ravenLastAlive - 400L) / 400.0F));
        if (opacity <= 0.0F) {
            ravenTarget = null;
        }
        return opacity;
    }

    private void renderRavenTarget() {
        long now = System.currentTimeMillis();
        if (!prepareLegacyTarget(now)) {
            return;
        }
        float opacity = legacyOpacity(now);
        if (opacity <= 0.0F) {
            return;
        }
        String text = ravenTarget.getDisplayName().getFormattedText() + " " + healthFormat.format(ravenTarget.getHealth());
        if (indicator.getValue()) {
            text += ravenHealthTo <= (mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount()) / Math.max(1.0F, mc.thePlayer.getMaxHealth()) ? " \u00a7aW" : " \u00a7cL";
        }
        ScaledResolution resolution = new ScaledResolution(mc);
        float avatar = headTexture == null ? 0.0F : 28.0F;
        float width = mc.fontRendererObj.getStringWidth(text) + 24.0F + avatar;
        float x = (resolution.getScaledWidth() - width) * 0.5F + offX.getValue();
        float y = resolution.getScaledHeight() * 0.5F + 15.0F + offY.getValue();
        int alpha = Math.max(1, Math.round(opacity * 255.0F));
        int accent = getTargetColor(ravenTarget).getRGB() & 0xFFFFFF;
        // 卡片底用玻璃，阴影不再画，描边和柔光由 FrostedGlass 出
        HUD hud = HUD.get();
        boolean glassDrawn = false;
        if (hud == null || hud.glassOn(hud.glassTarget)) {
            glassDrawn = poorcatgirl.util.FrostedGlass.drawHudBlurColored(x, y, width, 35.0F, 8.0F,
                    resolution.getScaledWidth(), resolution.getScaledHeight(), opacity,
                    this.glow.getValue(), this.glowColor.getValue());
        }
        if (!glassDrawn) {
            RenderUtil.drawRoundedRect(x, y, width, 35.0F, 8.0F, (Math.min(alpha, 110) << 24));
        }
        float contentX = x + 8.0F + avatar;
        RenderUtil.drawRoundedRect(contentX - 2.0F, y + 25.0F, width - avatar - 12.0F, 5.0F, 2.0F, (Math.min(alpha, 110) << 24));
        float t = Math.min(1.0F, Math.max(0.0F, (now - ravenHealthChanged) / 350.0F));
        float health = animations.getValue() ? ravenHealthFrom + (ravenHealthTo - ravenHealthFrom) * t : ravenHealthTo;
        RenderUtil.drawRoundedRect(contentX - 2.0F, y + 25.0F, (width - avatar - 12.0F) * health, 5.0F, 2.0F, (Math.min(alpha, 210) << 24) | accent);
        if (headTexture != null) {
            GlStateManager.pushMatrix();
            GlStateManager.enableBlend();
            GlStateManager.color(1.0F, 1.0F, 1.0F, alpha / 255.0F);
            mc.getTextureManager().bindTexture(headTexture);
            Gui.drawScaledCustomSizeModalRect((int) x + 4, (int) y + 5, 8.0F, 8.0F, 8, 8, 23, 23, 64.0F, 64.0F);
            Gui.drawScaledCustomSizeModalRect((int) x + 4, (int) y + 5, 40.0F, 8.0F, 8, 8, 23, 23, 64.0F, 64.0F);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }
        mc.fontRendererObj.drawString(text, contentX, y + 8.0F, (alpha << 24) | 0xDCDCDC, shadow.getValue());
    }

    private void renderMaterialWaveTarget() {
        long now = System.currentTimeMillis();
        if (!prepareLegacyTarget(now)) {
            return;
        }
        float opacity = legacyOpacity(now);
        if (opacity <= 0.0F) {
            return;
        }
        EntityLivingBase target = ravenTarget;
        String name = target.getDisplayName().getFormattedText();
        float rawHealth = Math.max(0.0F, target.getHealth());
        float rawTotal = rawHealth + Math.max(0.0F, target.getAbsorptionAmount());
        String healthText = healthFormat.format(rawHealth);
        String statusText = indicator.getValue()
                ? (rawTotal <= mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount() ? "\u00a7aW" : "\u00a7cL")
                : "";
        String valueText = statusText.isEmpty() ? healthText : healthText + " " + statusText;

        ScaledResolution resolution = new ScaledResolution(mc);
        float avatar = headTexture == null ? 0.0F : 26.0F;
        float nameWidth = mc.fontRendererObj.getStringWidth(name);
        float valueWidth = mc.fontRendererObj.getStringWidth(valueText);
        float width = Math.max(126.0F, avatar + 18.0F + nameWidth + 10.0F + valueWidth);
        float height = 35.0F;
        float x = (resolution.getScaledWidth() - width) * 0.5F + offX.getValue();
        float y = resolution.getScaledHeight() * 0.5F + 15.0F + offY.getValue();
        int alpha = Math.max(1, Math.round(opacity * 255.0F));
        int accent = getTargetColor(target).getRGB() & 0xFFFFFF;

        HUD hud = HUD.get();
        boolean glassDrawn = false;
        if (hud == null || hud.glassOn(hud.glassTarget)) {
            glassDrawn = poorcatgirl.util.FrostedGlass.drawHudBlurColored(x, y, width, height, 11.0F,
                    resolution.getScaledWidth(), resolution.getScaledHeight(), opacity,
                    this.glow.getValue(), this.glowColor.getValue());
        }
        if (!glassDrawn) {
            RenderUtil.drawRoundedRect(x, y, width, height, 11.0F, (Math.min(alpha, 110) << 24));
        }

        float contentX = x + 9.0F + avatar;
        float t = Math.min(1.0F, Math.max(0.0F, (now - ravenHealthChanged) / 350.0F));
        float health = animations.getValue() ? ravenHealthFrom + (ravenHealthTo - ravenHealthFrom) * t : ravenHealthTo;
        float barWidth = width - (contentX - x) - 9.0F;
        float phase = waveMoves.getValue() ? (System.nanoTime() % 1_000_000_000L)
                / 1_000_000_000.0F * (float) (Math.PI * 2.0) : 0.0F;
        RenderUtil.enableRenderState();
        RenderUtil.drawMaterialWaveBar(contentX, y + 21.5F, barWidth, health, phase,
                (Math.min(alpha, 70) << 24) | 0xFFFFFF,
                (Math.min(alpha, 235) << 24) | accent);
        RenderUtil.disableRenderState();
        if (headTexture != null) {
            GlStateManager.pushMatrix();
            GlStateManager.enableBlend();
            GlStateManager.color(1.0F, 1.0F, 1.0F, alpha / 255.0F);
            mc.getTextureManager().bindTexture(headTexture);
            Gui.drawScaledCustomSizeModalRect((int) x + 4, (int) y + 4, 8.0F, 8.0F, 8, 8, 22, 22, 64.0F, 64.0F);
            Gui.drawScaledCustomSizeModalRect((int) x + 4, (int) y + 4, 40.0F, 8.0F, 8, 8, 22, 22, 64.0F, 64.0F);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }
        mc.fontRendererObj.drawString(name, contentX, y + 7.0F, (alpha << 24) | 0xF2F2F2, shadow.getValue());
        mc.fontRendererObj.drawString(valueText, x + width - 9.0F - valueWidth, y + 7.0F,
                (alpha << 24) | 0xFFFFFF, shadow.getValue());
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() == EventType.SEND && event.getPacket() instanceof C02PacketUseEntity) {
            C02PacketUseEntity packet = (C02PacketUseEntity) event.getPacket();
            if (packet.getAction() != Action.ATTACK) {
                return;
            }
            Entity entity = packet.getEntityFromWorld(mc.theWorld);
            if (entity instanceof EntityLivingBase) {
                if (entity instanceof EntityArmorStand) {
                    return;
                }
                this.lastAttackTimer.reset();
                this.lastTarget = (EntityLivingBase) entity;
            }
        }
    }
}
