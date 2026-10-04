package myau.module.modules;

import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.BlockPos;
import net.minecraft.util.DamageSource;
import net.minecraft.block.material.Material;
import net.minecraft.util.MathHelper;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class FallView extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final FloatProperty damageThreshold = new FloatProperty("damage-threshold", 0.0F, 0.0F, 100.0F);
    public final BooleanProperty disableWhileFlying = new BooleanProperty("disable-while-flying", true);
    public final BooleanProperty onlyWhileSneaking = new BooleanProperty("only-while-sneaking", false);
    public final BooleanProperty overrideHealthFormat = new BooleanProperty("override-health-format", true);
    public final BooleanProperty showDamage = new BooleanProperty("show-damage", true);
    public final BooleanProperty showDistance = new BooleanProperty("show-distance", false);

    private double fallStartY = -1;
    private double groundY = -1;
    private float cachedFallDistance = 0;
    private int cachedEnchantmentModifier = -1;
    private ItemStack[] cachedArmorInventory = new ItemStack[4];
    private boolean armorCacheValid = false;

    private Map<DamageCacheKey, Integer> damageCache = new HashMap<>();
    private static final int MAX_CACHE_SIZE = 1000;

    public FallView() {
        super("FallView", false);
    }

    @Override
    public void onDisabled() {
        this.fallStartY = -1;
        this.groundY = -1;
        this.cachedFallDistance = 0;
        this.cachedEnchantmentModifier = -1;
        this.cachedArmorInventory = new ItemStack[4];
        this.armorCacheValid = false;
        this.damageCache.clear();
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        if (mc.currentScreen != null) {
            return;
        }
        if (!nullCheck() || mc.thePlayer.capabilities.isCreativeMode) {
            return;
        }
        if (this.disableWhileFlying.getValue() && mc.thePlayer.capabilities.allowFlying) {
            return;
        }
        if (this.onlyWhileSneaking.getValue() && !mc.thePlayer.isSneaking()) {
            return;
        }

        boolean onGround = mc.thePlayer.onGround;

        if (onGround) {
            this.fallStartY = -1;
            this.groundY = -1;
            this.cachedFallDistance = 0;
        } else {
            if (this.fallStartY == -1) {
                this.fallStartY = mc.thePlayer.posY;
                this.groundY = findGroundY(mc.thePlayer.posX, mc.thePlayer.posZ);
            } else {
                double newGroundY = findGroundY(mc.thePlayer.posX, mc.thePlayer.posZ);
                if (newGroundY != this.groundY) {
                    this.groundY = newGroundY;
                    this.cachedFallDistance = 0;
                }
            }
        }

        float fallDistance = calculateFallDistance();
        if (fallDistance <= 2.5f) {
            return;
        }

        PotionEffect jumpEffect = mc.thePlayer.getActivePotionEffect(Potion.jump);
        float jumpAmplifier = (jumpEffect != null) ? ((float) (jumpEffect.getAmplifier() + 1)) : 0.0f;
        int jumpBoostLevel = jumpEffect != null ? jumpEffect.getAmplifier() + 1 : 0;

        PotionEffect resistanceEffect = mc.thePlayer.getActivePotionEffect(Potion.resistance);
        boolean hasResistance = resistanceEffect != null;
        int resistanceLevel = hasResistance ? resistanceEffect.getAmplifier() + 1 : 0;

        ItemStack[] armorInventory = new ItemStack[4];
        boolean armorChanged = false;
        int armorHash = 0;
        for (int i = 0; i < 4; i++) {
            ItemStack currentArmor = mc.thePlayer.inventory.armorItemInSlot(i);
            armorInventory[i] = currentArmor;
            if (this.cachedArmorInventory[i] != currentArmor) {
                armorChanged = true;
            }
            if (currentArmor != null) {
                armorHash = armorHash * 31 + (currentArmor.getItem() != null ? currentArmor.getItem().hashCode() : 0);
                armorHash = armorHash * 31 + currentArmor.getItemDamage();
                armorHash = armorHash * 31 + EnchantmentHelper.getEnchantmentLevel(Enchantment.featherFalling.effectId, currentArmor);
            }
        }

        int enchantmentModifier = this.cachedEnchantmentModifier;
        if (armorChanged || !this.armorCacheValid) {
            long totalModifier = 0;
            for (int i = 0; i < 100; i++) {
                int mod = EnchantmentHelper.getEnchantmentModifierDamage(armorInventory, DamageSource.fall);
                if (mod > 20) {
                    mod = 20;
                }
                totalModifier += mod;
            }
            enchantmentModifier = (int) Math.round(totalModifier / 100.0);
            this.cachedEnchantmentModifier = enchantmentModifier;
            System.arraycopy(armorInventory, 0, this.cachedArmorInventory, 0, 4);
            this.armorCacheValid = true;
            if (this.damageCache.size() > MAX_CACHE_SIZE / 2) {
                this.damageCache.clear();
            }
        }

        DamageCacheKey cacheKey = new DamageCacheKey(armorHash, fallDistance, jumpBoostLevel, resistanceLevel);
        Integer cachedFinalDamage = this.damageCache.get(cacheKey);

        int finalDamage;
        if (cachedFinalDamage != null) {
            finalDamage = cachedFinalDamage;
        } else {
            float damagePoints = fallDistance - 3.0f - jumpAmplifier;
            double damage = Math.max(0, MathHelper.ceiling_double_int(damagePoints));

            if (hasResistance && damage > 0) {
                int i = resistanceLevel * 5;
                int j = 25 - i;
                damage = j * damage / 25.0;
            }

            if (damage > 0 && enchantmentModifier > 0) {
                damage = (25 - enchantmentModifier) * damage / 25.0;
            }

            finalDamage = MathHelper.ceiling_double_int(damage);

            if (this.damageCache.size() >= MAX_CACHE_SIZE) {
                this.damageCache.clear();
            }
            this.damageCache.put(cacheKey, finalDamage);
        }

        double currentHealth = mc.thePlayer.getHealth();
        double damagePercent = (double) finalDamage / currentHealth * 100.0;
        if (this.showDamage.getValue() && finalDamage > 0 && damagePercent > this.damageThreshold.getValue()) {
            ScaledResolution scaledResolution = new ScaledResolution(mc);
            float hearts = finalDamage;
            if (this.overrideHealthFormat.getValue()) {
                hearts = finalDamage / 2.0f;
                hearts = (float) round(hearts, 1);
            }
            double percent = (double) finalDamage / currentHealth;
            String healthStr = (finalDamage >= currentHealth) ? "\u00a74" : ((percent >= 0.7) ? "\u00a7c" : ((percent >= 0.5) ? "\u00a76" : ((percent >= 0.3) ? "\u00a7e" : "\u00a7a")));
            String damageStr = healthStr + asWholeNum(hearts);
            mc.fontRendererObj.drawStringWithShadow(damageStr, scaledResolution.getScaledWidth() / 2 - mc.fontRendererObj.getStringWidth(damageStr) / 2, scaledResolution.getScaledHeight() / 2 - 15, Color.WHITE.getRGB());
        }

        if (this.showDistance.getValue()) {
            ScaledResolution scaledResolution = new ScaledResolution(mc);
            Color distanceColor = getDistanceColor(fallDistance);
            String distanceStr = asWholeNum(round(fallDistance, 2)) + "m";
            mc.fontRendererObj.drawStringWithShadow(distanceStr, scaledResolution.getScaledWidth() / 2 - mc.fontRendererObj.getStringWidth(distanceStr) / 2, scaledResolution.getScaledHeight() / 2 + 6, distanceColor.getRGB());
        }
    }

    private float calculateFallDistance() {
        if (this.fallStartY == -1 || this.groundY == -1) {
            double currentY = mc.thePlayer.posY;
            double ground = findGroundY(mc.thePlayer.posX, mc.thePlayer.posZ);
            if (ground == -1) {
                return 0;
            }
            return (float) Math.max(0, currentY - ground);
        }

        if (this.cachedFallDistance == 0) {
            this.cachedFallDistance = (float) Math.max(0, this.fallStartY - this.groundY);
        }
        return this.cachedFallDistance;
    }

    private double findGroundY(double x, double z) {
        int startY = (int) Math.floor(mc.thePlayer.posY);
        for (int y = startY; y > -1; y--) {
            BlockPos pos = new BlockPos(Math.floor(x), y, Math.floor(z));
            if (!isPlaceable(pos)) {
                return y + 1;
            }
        }
        return -1;
    }

    private Color getDistanceColor(float distance) {
        float minDistance = 2.5f;
        float maxDistance = 20.0f;

        float normalized = MathHelper.clamp_float((distance - minDistance) / (maxDistance - minDistance), 0.0f, 1.0f);

        int red = 255;
        int green = (int) (255 * (1.0f - normalized));
        int blue = 0;

        return new Color(red, green, blue);
    }

    private static boolean nullCheck() {
        return mc.thePlayer != null && mc.theWorld != null;
    }

    private static boolean isPlaceable(BlockPos blockPos) {
        Block block = getBlock(blockPos);
        return block.isReplaceable(mc.theWorld, blockPos) || isFluid(block);
    }

    private static Block getBlock(BlockPos blockPos) {
        if (mc.theWorld == null || blockPos == null) {
            return Blocks.air;
        }
        return mc.theWorld.getBlockState(blockPos).getBlock();
    }

    private static boolean isFluid(Block block) {
        return block.getMaterial() == Material.lava || block.getMaterial() == Material.water;
    }

    private static double round(double val, int decimalPlaces) {
        if (decimalPlaces == 0) {
            return (double) Math.round(val);
        }
        double p = Math.pow(10.0D, decimalPlaces);
        return (double) Math.round(val * p) / p;
    }

    private static boolean isWholeNumber(double num) {
        return num == Math.floor(num);
    }

    private static String asWholeNum(double input) {
        return isWholeNumber(input) ? (int) input + "" : String.valueOf(input);
    }

    private static class DamageCacheKey {
        final int armorHash;
        final int fallDistanceInt;
        final int jumpBoostLevel;
        final int resistanceLevel;

        DamageCacheKey(int armorHash, float fallDistance, int jumpBoost, int resistance) {
            this.armorHash = armorHash;
            this.fallDistanceInt = Math.round(fallDistance * 100);
            this.jumpBoostLevel = jumpBoost;
            this.resistanceLevel = resistance;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DamageCacheKey that = (DamageCacheKey) o;
            return armorHash == that.armorHash && fallDistanceInt == that.fallDistanceInt && jumpBoostLevel == that.jumpBoostLevel && resistanceLevel == that.resistanceLevel;
        }

        @Override
        public int hashCode() {
            return armorHash * 31 * 31 * 31 + fallDistanceInt * 31 * 31 + jumpBoostLevel * 31 + resistanceLevel;
        }
    }
}
