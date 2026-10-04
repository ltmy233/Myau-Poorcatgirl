package myau.module.modules;

import myau.event.EventTarget;
import myau.events.Render3DEvent;
import myau.module.Module;
import myau.property.properties.FloatProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.StringUtils;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.text.DecimalFormat;

public class TNTTimer extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int BEDWARS_FUSE_OFFSET = 28;
    private static final float BEDWARS_MAX_FUSE = 52.0f;
    private static final float NORMAL_MAX_FUSE = 80.0f;

    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 3.0F);
    private final DecimalFormat timeFormatter = new DecimalFormat("0.0");

    public TNTTimer() {
        super("TNTTimer", false);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        RenderManager rm = mc.getRenderManager();
        FontRenderer fr = mc.fontRendererObj;
        float partialTicks = event.getPartialTicks();
        boolean bedwars = isSidebarBedwars();

        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityTNTPrimed)) continue;
            EntityTNTPrimed tnt = (EntityTNTPrimed) entity;

            int fuse = bedwars ? (tnt.fuse - BEDWARS_FUSE_OFFSET) : tnt.fuse;
            if (fuse < 1) continue;

            double distSq = tnt.getDistanceSqToEntity(mc.thePlayer);
            if (distSq > 4096.0) continue;

            double x = tnt.lastTickPosX + (tnt.posX - tnt.lastTickPosX) * partialTicks - rm.viewerPosX;
            double y = tnt.lastTickPosY + (tnt.posY - tnt.lastTickPosY) * partialTicks - rm.viewerPosY;
            double z = tnt.lastTickPosZ + (tnt.posZ - tnt.lastTickPosZ) * partialTicks - rm.viewerPosZ;

            renderTimer(fr, rm, tnt, x, y, z, fuse, partialTicks, bedwars);
        }
    }

    private void renderTimer(FontRenderer fr, RenderManager rm, EntityTNTPrimed tnt,
                             double x, double y, double z, int fuse, float partialTicks,
                             boolean bedwars) {
        String time = timeFormatter.format((fuse - partialTicks) / 20.0f);
        float maxFuse = bedwars ? BEDWARS_MAX_FUSE : NORMAL_MAX_FUSE;
        float green = Math.min(fuse / maxFuse, 1.0f);
        Color color = new Color(1.0f - green, green, 0.0f);

        float s = 0.02666667f * this.scale.getValue();

        GL11.glPushMatrix();
        GlStateManager.translate((float) x, (float) y + tnt.height + 0.5f, (float) z);
        GL11.glNormal3f(0.0f, 1.0f, 0.0f);
        GlStateManager.rotate(-rm.playerViewY, 0.0f, 1.0f, 0.0f);
        GlStateManager.rotate(rm.playerViewX * (mc.gameSettings.thirdPersonView == 2 ? -1 : 1), 1.0f, 0.0f, 0.0f);
        GlStateManager.scale(-s, -s, s);

        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        int halfWidth = fr.getStringWidth(time) >> 1;

        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableTexture2D();

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(-halfWidth - 1, -1, 0.0).color(0.0f, 0.0f, 0.0f, 0.25f).endVertex();
        wr.pos(-halfWidth - 1, 8, 0.0).color(0.0f, 0.0f, 0.0f, 0.25f).endVertex();
        wr.pos(halfWidth + 1, 8, 0.0).color(0.0f, 0.0f, 0.0f, 0.25f).endVertex();
        wr.pos(halfWidth + 1, -1, 0.0).color(0.0f, 0.0f, 0.0f, 0.25f).endVertex();
        tessellator.draw();

        GlStateManager.enableTexture2D();
        fr.drawString(time, -halfWidth, 0, color.getRGB());

        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glPopMatrix();
    }

    private boolean isSidebarBedwars() {
        if (mc.theWorld == null) return false;
        Scoreboard sb = mc.theWorld.getScoreboard();
        if (sb == null) return false;
        ScoreObjective objective = sb.getObjectiveInDisplaySlot(1);
        return objective != null && StringUtils.stripControlCodes(objective.getDisplayName()).contains("BED WARS");
    }
}
