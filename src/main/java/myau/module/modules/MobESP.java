package myau.module.modules;

import myau.event.EventTarget;
import myau.events.Render3DEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityCaveSpider;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntityGhast;
import net.minecraft.entity.monster.EntityPigZombie;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class MobESP extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final BooleanProperty healthBar = new BooleanProperty("health-bar", false);
    public final BooleanProperty shaded = new BooleanProperty("shaded", false);
    public final BooleanProperty blazes = new BooleanProperty("blazes", true);
    public final BooleanProperty creepers = new BooleanProperty("creepers", true);
    public final BooleanProperty endermen = new BooleanProperty("endermen", true);
    public final BooleanProperty ghasts = new BooleanProperty("ghasts", true);
    public final BooleanProperty silverfish = new BooleanProperty("silverfish", true);
    public final BooleanProperty skeletons = new BooleanProperty("skeletons", true);
    public final BooleanProperty slimes = new BooleanProperty("slimes", true);
    public final BooleanProperty spiders = new BooleanProperty("spiders", true);
    public final BooleanProperty zombies = new BooleanProperty("zombies", true);
    public final BooleanProperty zombiePigman = new BooleanProperty("zombie-pigman", true);

    private final Map<Class<? extends EntityLivingBase>, MobSetting> mobRenders = new HashMap<Class<? extends EntityLivingBase>, MobSetting>();

    public MobESP() {
        super("MobESP", false);

        this.mobRenders.put(EntityBlaze.class, new MobSetting(this.blazes, Color.orange.getRGB(), 69.0D));
        this.mobRenders.put(EntityCreeper.class, new MobSetting(this.creepers, Color.green.getRGB(), 69.0D));
        this.mobRenders.put(EntityEnderman.class, new MobSetting(this.endermen, Color.black.getRGB(), 106.0D));
        this.mobRenders.put(EntityGhast.class, new MobSetting(this.ghasts, Color.white.getRGB(), 143.0D));
        this.mobRenders.put(EntitySilverfish.class, new MobSetting(this.silverfish, Color.gray.getRGB(), 20.0D));
        this.mobRenders.put(EntitySkeleton.class, new MobSetting(this.skeletons, Color.white.getRGB(), 69.0D));
        this.mobRenders.put(EntitySlime.class, new MobSetting(this.slimes, Color.green.getRGB()));
        this.mobRenders.put(EntitySpider.class, new MobSetting(this.spiders, Color.black.getRGB(), 40.0D));
        this.mobRenders.put(EntityCaveSpider.class, new MobSetting(this.spiders, Color.black.getRGB(), 26.0D));
        this.mobRenders.put(EntityZombie.class, new MobSetting(this.zombies, Color.blue.getRGB()));
        this.mobRenders.put(EntityPigZombie.class, new MobSetting(this.zombiePigman, Color.pink.getRGB()));
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity instanceof EntityLivingBase && entity != mc.thePlayer) {
                if (((EntityLivingBase) entity).deathTime != 0) {
                    continue;
                }
                renderEntity((EntityLivingBase) entity, event.getPartialTicks());
            }
        }
    }

    private void renderEntity(EntityLivingBase entity, float partialTicks) {
        MobSetting mobSetting = this.mobRenders.get(entity.getClass());
        if (mobSetting != null && mobSetting.setting.getValue()) {
            renderMob(entity, mobSetting.height, mobSetting.color, partialTicks);
        }
    }

    private void renderMob(EntityLivingBase entity, double height, int rgb, float partialTicks) {
        if (this.shaded.getValue()) {
            renderShadedBox(entity, rgb, partialTicks);
        }
        if (this.healthBar.getValue()) {
            drawHealthBar(entity, height, partialTicks);
        }
    }

    private void renderShadedBox(EntityLivingBase entity, int rgb, float partialTicks) {
        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * (double) partialTicks - mc.getRenderManager().viewerPosX;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * (double) partialTicks - mc.getRenderManager().viewerPosY;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * (double) partialTicks - mc.getRenderManager().viewerPosZ;
        int red = (rgb >> 16 & 255);
        int green = (rgb >> 8 & 255);
        int blue = (rgb & 255);
        AxisAlignedBB bbox = entity.getEntityBoundingBox().expand(0.1D, 0.1D, 0.1D);
        AxisAlignedBB axis = new AxisAlignedBB(
                bbox.minX - entity.posX + x,
                bbox.minY - entity.posY + y,
                bbox.minZ - entity.posZ + z,
                bbox.maxX - entity.posX + x,
                bbox.maxY - entity.posY + y,
                bbox.maxZ - entity.posZ + z
        );
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        RenderUtil.drawFilledBox(axis, red, green, blue);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private void drawHealthBar(EntityLivingBase en, double mobHeight, float partialTicks) {
        double x = en.lastTickPosX + (en.posX - en.lastTickPosX) * (double) partialTicks - mc.getRenderManager().viewerPosX;
        double y = en.lastTickPosY + (en.posY - en.lastTickPosY) * (double) partialTicks - mc.getRenderManager().viewerPosY;
        double z = en.lastTickPosZ + (en.posZ - en.lastTickPosZ) * (double) partialTicks - mc.getRenderManager().viewerPosZ;
        GlStateManager.pushMatrix();
        int xOffset = 21;
        double health = en.getHealth() / en.getMaxHealth();
        int height = (int) (mobHeight * health);
        int healthColor = health < 0.3D ? Color.red.getRGB() : (health < 0.5D ? Color.orange.getRGB() : (health < 0.7D ? Color.yellow.getRGB() : Color.green.getRGB()));
        GL11.glTranslated(x, y - 0.2D, z);
        GL11.glRotated(-mc.getRenderManager().playerViewY, 0.0D, 1.0D, 0.0D);
        GlStateManager.disableDepth();
        GL11.glScalef(0.03F, 0.03F, 0.03F);
        Gui.drawRect(xOffset, -1, xOffset + 4, (int) (mobHeight + 1), Color.black.getRGB());
        Gui.drawRect(xOffset + 1, height, xOffset + 3, (int) mobHeight, Color.darkGray.getRGB());
        Gui.drawRect(xOffset + 1, 0, xOffset + 3, height, healthColor);
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    private static class MobSetting {
        BooleanProperty setting;
        int color;
        double height = 74.0D;

        public MobSetting(BooleanProperty setting, int color) {
            this.setting = setting;
            this.color = color;
        }

        public MobSetting(BooleanProperty setting, int color, double height) {
            this.setting = setting;
            this.color = color;
            this.height = height;
        }
    }
}
