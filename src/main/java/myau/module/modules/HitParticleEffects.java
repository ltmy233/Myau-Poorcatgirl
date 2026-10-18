package myau.module.modules;

import myau.module.Module;

import myau.event.EventTarget;
import myau.events.AttackEvent;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ColorProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumParticleTypes;

import java.awt.*;
import java.util.Random;

public class HitParticleEffects extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final Random random = new Random();

    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"Vanilla", "Critical", "Redstone", "Custom"});
    public final IntProperty amount = new IntProperty("amount", 5, 1, 20);
    public final ColorProperty customColor = new ColorProperty("custom-color", new Color(255, 0, 0).getRGB(), () -> this.mode.getValue() == 2 || this.mode.getValue() == 3); // 只在 Redstone/Custom 模式显示
    public final BooleanProperty onlyCrits = new BooleanProperty("only-crits", false);

    public HitParticleEffects() {
        super("HitParticleEffects", false);
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled()) return;
        if (!(event.getTarget() instanceof EntityLivingBase)) return;

        EntityLivingBase target = (EntityLivingBase) event.getTarget();

        if (this.onlyCrits.getValue()) {
            // 粗判原版暴击条件
            boolean isCritical = mc.thePlayer.fallDistance > 0.0F && !mc.thePlayer.onGround && !mc.thePlayer.isInWater() && !mc.thePlayer.isRiding();
            if (!isCritical) return;
        }

        double x = target.posX;
        double y = target.posY + target.getEyeHeight() / 2.0;
        double z = target.posZ;

        for (int i = 0; i < this.amount.getValue(); i++) {
            double offsetX = this.random.nextGaussian() * 0.2;
            double offsetY = this.random.nextGaussian() * 0.2;
            double offsetZ = this.random.nextGaussian() * 0.2;

            switch (this.mode.getValue()) {
                case 0: // CRIT
                    mc.theWorld.spawnParticle(EnumParticleTypes.CRIT, x + offsetX, y + offsetY, z + offsetZ, 0.0, 0.0, 0.0);
                    break;
                case 1: // CRIT_MAGIC，区分原版暴击粒子
                    mc.theWorld.spawnParticle(EnumParticleTypes.CRIT_MAGIC, x + offsetX, y + offsetY, z + offsetZ, 0.0, 0.0, 0.0);
                    break;
                case 2: // 红石粒子，用 customColor 上色
                    Color redstoneColor = new Color(this.customColor.getValue());
                    double redstoneMotionX = (double)redstoneColor.getRed()/255.0;
                    double redstoneMotionY = (double)redstoneColor.getGreen()/255.0;
                    double redstoneMotionZ = (double)redstoneColor.getBlue()/255.0;
                    // motionX/Y/Z 数值小时当 RGB 用
                    if (redstoneMotionX == 0.0 && redstoneMotionY == 0.0 && redstoneMotionZ == 0.0) { // 全黑看不见
                        redstoneMotionX = 1.0; // 黑色兜底成红
                    }
                    mc.theWorld.spawnParticle(EnumParticleTypes.REDSTONE, x + offsetX, y + offsetY, z + offsetZ, redstoneMotionX, redstoneMotionY, redstoneMotionZ);
                    break;
                case 3: // 自定义色也走红石粒子（FIREWORKS_SPARK 不好上色）
                    Color customParticleColor = new Color(this.customColor.getValue());
                    double customMotionX = (double)customParticleColor.getRed()/255.0;
                    double customMotionY = (double)customParticleColor.getGreen()/255.0;
                    double customMotionZ = (double)customParticleColor.getBlue()/255.0;
                    if (customMotionX == 0.0 && customMotionY == 0.0 && customMotionZ == 0.0) { // 全黑看不见
                        customMotionX = 1.0; // 黑色兜底成红
                    }
                    mc.theWorld.spawnParticle(EnumParticleTypes.REDSTONE, x + offsetX, y + offsetY, z + offsetZ, customMotionX, customMotionY, customMotionZ);
                    break;
            }
        }
    }
}

