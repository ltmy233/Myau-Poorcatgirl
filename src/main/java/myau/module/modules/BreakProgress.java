package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ModeProperty;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.util.BlockPos;
import net.minecraft.block.material.Material;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.opengl.GL11;

public class BreakProgress extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final String[] MODES = {"Percentage", "Second", "Decimal"};

    public final ModeProperty mode = new ModeProperty("mode", 0, MODES);
    public final BooleanProperty showManual = new BooleanProperty("show-manual", true);
    public final BooleanProperty fadeIn = new BooleanProperty("fade-in", false);

    private float progress;
    private BlockPos block;
    private String progressStr = "";

    public BreakProgress() {
        super("BreakProgress", false);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        if (this.progress == 0.0f || this.block == null || !nullCheck()) {
            return;
        }
        double x = this.block.getX() + 0.5 - mc.getRenderManager().viewerPosX;
        double y = this.block.getY() + 0.5 - mc.getRenderManager().viewerPosY;
        double z = this.block.getZ() + 0.5 - mc.getRenderManager().viewerPosZ;
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y, (float) z);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0f, 1.0f, 0.0f);
        GlStateManager.rotate((mc.gameSettings.thirdPersonView == 2 ? -1 : 1) * mc.getRenderManager().playerViewX, 1.0f, 0.0f, 0.0f);
        GlStateManager.scale(-0.02266667f, -0.02266667f, -0.02266667f);
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GL11.glEnable(GL11.GL_BLEND);
        int colorAlpha = mergeAlpha(-1, Math.max(10, (int) (255 * progress)));
        mc.fontRendererObj.drawString(this.progressStr, (float) (-mc.fontRendererObj.getStringWidth(this.progressStr) / 2), -3.0f, this.fadeIn.getValue() ? colorAlpha : -1, true);
        GL11.glDisable(GL11.GL_BLEND);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.popMatrix();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.POST) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (mc.thePlayer.capabilities.isCreativeMode || !mc.thePlayer.capabilities.allowEdit) {
            this.resetVariables();
            return;
        }
        if (!this.showManual.getValue() || mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            this.resetVariables();
            return;
        }
        this.progress = ((IAccessorPlayerControllerMP) mc.playerController).getCurBlockDamageMP();
        if (this.progress == 0.0f) {
            this.resetVariables();
            return;
        }
        this.block = mc.objectMouseOver.getBlockPos();
        this.setProgress();
    }

    @Override
    public void onDisabled() {
        this.resetVariables();
    }

    private void setProgress() {
        switch (this.mode.getValue()) {
            case 0: {
                this.progressStr = (int) (100.0 * (this.progress / 1.0)) + "%";
                break;
            }
            case 1: {
                double timeLeft = round(((1.0f - this.progress) / getBlockHardness(getBlock(this.block), mc.thePlayer.getHeldItem(), false, false)) / 20.0, 1);
                this.progressStr = timeLeft == 0 ? "0" : timeLeft + "s";
                break;
            }
            case 2: {
                this.progressStr = String.valueOf(round(this.progress, 2));
                break;
            }
            default:
                break;
        }
    }

    private void resetVariables() {
        this.progress = 0.0f;
        this.block = null;
        this.progressStr = "";
    }

    private static boolean nullCheck() {
        return mc.thePlayer != null && mc.theWorld != null;
    }

    private static int mergeAlpha(int color, int alpha) {
        return (color & 0xFFFFFF) | alpha << 24;
    }

    private static double round(double val, int decimalPlaces) {
        if (decimalPlaces == 0) {
            return (double) Math.round(val);
        }
        double p = Math.pow(10.0D, decimalPlaces);
        return (double) Math.round(val * p) / p;
    }

    private static Block getBlock(BlockPos blockPos) {
        if (mc.theWorld == null || blockPos == null) {
            return Blocks.air;
        }
        return mc.theWorld.getBlockState(blockPos).getBlock();
    }

    private static float getBlockHardness(Block block, ItemStack itemStack, boolean ignoreSlow, boolean ignoreGround) {
        float hardness = block.getBlockHardness(mc.theWorld, null);
        if (hardness < 0.0f) {
            return 0.0f;
        }
        return (block.getMaterial().isToolNotRequired() || (itemStack != null && itemStack.canHarvestBlock(block)))
                ? (getToolDigEfficiency(itemStack, block, ignoreSlow, ignoreGround) / hardness / 30.0f)
                : (getToolDigEfficiency(itemStack, block, ignoreSlow, ignoreGround) / hardness / 100.0f);
    }

    private static float getToolDigEfficiency(ItemStack itemStack, Block block, boolean ignoreSlow, boolean ignoreGround) {
        float efficiency = (itemStack == null) ? 1.0f : itemStack.getItem().getStrVsBlock(itemStack, block);
        if (efficiency > 1.0f) {
            int enchantmentLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, itemStack);
            if (enchantmentLevel > 0 && itemStack != null) {
                efficiency += enchantmentLevel * enchantmentLevel + 1;
            }
        }
        if (mc.thePlayer.isPotionActive(Potion.digSpeed)) {
            efficiency *= 1.0f + (mc.thePlayer.getActivePotionEffect(Potion.digSpeed).getAmplifier() + 1) * 0.2f;
        }
        if (!ignoreSlow) {
            if (mc.thePlayer.isPotionActive(Potion.digSlowdown)) {
                float slowFactor;
                switch (mc.thePlayer.getActivePotionEffect(Potion.digSlowdown).getAmplifier()) {
                    case 0:
                        slowFactor = 0.3f;
                        break;
                    case 1:
                        slowFactor = 0.09f;
                        break;
                    case 2:
                        slowFactor = 0.0027f;
                        break;
                    default:
                        slowFactor = 8.1E-4f;
                        break;
                }
                efficiency *= slowFactor;
            }
            if (mc.thePlayer.isInsideOfMaterial(Material.water) && !EnchantmentHelper.getAquaAffinityModifier(mc.thePlayer)) {
                efficiency /= 5.0f;
            }
            if (!mc.thePlayer.onGround && !ignoreGround) {
                efficiency /= 5.0f;
            }
        }
        return efficiency;
    }
}
