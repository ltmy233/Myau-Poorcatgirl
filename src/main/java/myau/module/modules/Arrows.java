package myau.module.modules;

import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.mixin.IAccessorEntityRenderer;
import myau.mixin.IAccessorMinecraft;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.ModeProperty;
import myau.util.RenderUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class Arrows extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty arrowType = new ModeProperty("arrow-type", 0, new String[]{"Caret", "Greater than", "Triangle"});
    public final FloatProperty circleRadius = new FloatProperty("circle-radius", 50.0F, 30.0F, 200.0F);
    public final BooleanProperty teamColor = new BooleanProperty("team-color", true);
    public final BooleanProperty hideTeammates = new BooleanProperty("hide-teammates", true);
    public final BooleanProperty renderFriends = new BooleanProperty("render-friends", true);
    public final BooleanProperty renderEnemies = new BooleanProperty("render-enemies", true);
    public final BooleanProperty renderDistance = new BooleanProperty("render-distance", true);
    public final BooleanProperty renderOnlyOffscreen = new BooleanProperty("render-only-offscreen", false);

    private static final int FRIEND_COLOR = new Color(0, 255, 0, 255).getRGB();
    private static final int ENEMY_COLOR = new Color(255, 0, 0, 255).getRGB();
    private static final FloatBuffer MODELVIEW = GLAllocation.createDirectFloatBuffer(16);
    private static final FloatBuffer PROJECTION = GLAllocation.createDirectFloatBuffer(16);
    private static final IntBuffer VIEWPORT = GLAllocation.createDirectIntBuffer(16);
    private static final FloatBuffer SCREEN_COORDS = GLAllocation.createDirectFloatBuffer(3);

    public Arrows() {
        super("Arrows", false);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        if (mc.currentScreen != null || !nullCheck()) {
            return;
        }
        try {
            for (EntityPlayer entityPlayer : mc.theWorld.playerEntities) {
                if (entityPlayer == null || entityPlayer == mc.thePlayer) {
                    continue;
                }
                if (TeamUtil.isBot(entityPlayer)) {
                    continue;
                }
                this.renderIndicatorFor(entityPlayer, event.getPartialTicks());
            }
        } catch (Exception e) {
        }
    }

    private void renderIndicatorFor(EntityPlayer entityPlayer, float partialTicks) {
        if (this.renderOnlyOffscreen.getValue() && isInViewFrustum(entityPlayer)) {
            return;
        }
        int color = -1;

        if (isTeammate(entityPlayer) && this.hideTeammates.getValue()) {
            return;
        }
        if (this.renderFriends.getValue() && TeamUtil.isFriend(entityPlayer)) {
            color = FRIEND_COLOR;
        } else if (this.renderEnemies.getValue() && TeamUtil.isTarget(entityPlayer)) {
            color = ENEMY_COLOR;
        } else if (this.teamColor.getValue()) {
            color = getColorFromEntity(entityPlayer);
        }

        double x = entityPlayer.lastTickPosX + (entityPlayer.posX - entityPlayer.lastTickPosX) * partialTicks - mc.getRenderManager().viewerPosX;
        double y = entityPlayer.lastTickPosY + (entityPlayer.posY - entityPlayer.lastTickPosY) * partialTicks - mc.getRenderManager().viewerPosY + entityPlayer.height / 2;
        double z = entityPlayer.lastTickPosZ + (entityPlayer.posZ - entityPlayer.lastTickPosZ) * partialTicks - mc.getRenderManager().viewerPosZ;

        ((IAccessorEntityRenderer) mc.entityRenderer).callSetupCameraTransform(((IAccessorMinecraft) mc).getTimer().renderPartialTicks, 0);

        ScaledResolution scaledResolution = new ScaledResolution(mc);
        Vec3 vec = convertTo2D(scaledResolution.getScaleFactor(), x, y, z);
        mc.entityRenderer.setupOverlayRendering();

        if (vec != null) {
            ScaledResolution res = new ScaledResolution(mc);

            double dx = vec.xCoord - res.getScaledWidth() / 2.0;
            double dy = vec.yCoord - res.getScaledHeight() / 2.0;
            boolean inFrustum = vec.zCoord < 1.0003684;

            if (!inFrustum) {
                dx *= -1.0;
                dy *= -1.0;
            }

            double angle1 = Math.atan2(dx, dy);
            double angle2 = Math.atan2(dy, dx) * 57.295780181884766 + 90.0;
            double hypotenuse = Math.hypot(dx, dy);
            double radiusInput = this.circleRadius.getValue();

            if (inFrustum && hypotenuse < radiusInput + 15.0) {
                return;
            }

            double baseX = res.getScaledWidth() / 2.0;
            double baseY = res.getScaledHeight() / 2.0;
            double sinAng = Math.sin(angle1);
            double cosAng = Math.cos(angle1);
            double renderX = baseX + radiusInput * sinAng;
            double renderY = baseY + radiusInput * cosAng;

            GlStateManager.pushMatrix();
            GlStateManager.translate(renderX, renderY, 0.0);
            GlStateManager.rotate((float) angle2, 0.0f, 0.0f, 1.0f);
            GlStateManager.scale(1.0f, 1.0f, 1.0f);

            int arrowInput = this.arrowType.getValue();

            if (arrowInput == 0) {
                if (color == -1) {
                    GL11.glColor3d(1.0, 1.0, 1.0);
                } else {
                    int rgb = color;
                    float red = ((rgb >> 16) & 0xFF) / 255.0F;
                    float green = ((rgb >> 8) & 0xFF) / 255.0F;
                    float blue = (rgb & 0xFF) / 255.0F;
                    GL11.glColor4f(red, green, blue, 1.0f);
                }

                GL11.glEnable(GL11.GL_BLEND);
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glEnable(GL11.GL_LINE_SMOOTH);

                double halfAngle = 0.6108652353286743;
                double size = 9.0;
                double offsetY = 5.0;
                GL11.glLineWidth(3.0f);
                GL11.glBegin(GL11.GL_LINE_STRIP);
                GL11.glVertex2d(Math.sin(-halfAngle) * size, Math.cos(-halfAngle) * size - offsetY);
                GL11.glVertex2d(0.0, -offsetY);
                GL11.glVertex2d(Math.sin(halfAngle) * size, Math.cos(halfAngle) * size - offsetY);
                GL11.glEnd();
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                GL11.glDisable(GL11.GL_BLEND);
                GL11.glDisable(GL11.GL_LINE_SMOOTH);
            } else if (arrowInput == 1) {
                GlStateManager.rotate(-90.0f, 0.0f, 0.0f, 1.0f);
                GlStateManager.scale(1.5, 1.5, 1.5);
                mc.fontRendererObj.drawString(">", -2.0f, -4.0f, color, false);
            } else if (arrowInput == 2) {
                draw2DPolygon(0.0, 0.0, 5.0, 3, mergeAlpha(color, 255));
            }

            GlStateManager.popMatrix();

            renderX = baseX + (radiusInput - 13.0) * sinAng;
            renderY = baseY + (radiusInput - 13.0) * cosAng;

            GlStateManager.pushMatrix();
            GlStateManager.translate(renderX, renderY, 0.0);
            GlStateManager.scale(0.8, 0.8, 0.8);

            if (this.renderDistance.getValue()) {
                String text = (int) mc.thePlayer.getDistanceToEntity(entityPlayer) + "m";
                mc.fontRendererObj.drawString(text, (float) (-mc.fontRendererObj.getStringWidth(text) / 2), -4.0f, -1, true);
            }

            GlStateManager.popMatrix();
        }
    }

    private static Vec3 convertTo2D(int scaleFactor, double x, double y, double z) {
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODELVIEW);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJECTION);
        GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT);

        boolean result = GLU.gluProject(
                (float) x,
                (float) y,
                (float) z,
                MODELVIEW,
                PROJECTION,
                VIEWPORT,
                SCREEN_COORDS
        );

        if (result) {
            return new Vec3(SCREEN_COORDS.get(0) / scaleFactor, (Display.getHeight() - SCREEN_COORDS.get(1)) / scaleFactor, SCREEN_COORDS.get(2));
        }

        return null;
    }

    private static void draw2DPolygon(double x, double y, double radius, int sides, int color) {
        if (sides < 3) {
            return;
        }
        float a = (color >> 24 & 0xFF) / 255.0f;
        float r = (color >> 16 & 0xFF) / 255.0f;
        float g = (color >> 8 & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glColor4f(r, g, b, a);
        double rad180 = Math.toRadians(180.0);
        worldrenderer.begin(6, DefaultVertexFormats.POSITION);
        for (int i = 0; i < sides; ++i) {
            double angle = 6.283185307179586 * i / sides + rad180;
            worldrenderer.pos(x + Math.sin(angle) * radius, y + Math.cos(angle) * radius, 0.0).endVertex();
        }
        tessellator.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    private static boolean isInViewFrustum(EntityPlayer entityPlayer) {
        return RenderUtil.isInViewFrustum(entityPlayer.getEntityBoundingBox(), 0.0) || entityPlayer.ignoreFrustumCheck;
    }

    private static boolean nullCheck() {
        return mc.thePlayer != null && mc.theWorld != null;
    }

    private static int mergeAlpha(int color, int alpha) {
        return (color & 0xFFFFFF) | alpha << 24;
    }

    private static boolean isTeammate(EntityPlayer entityPlayer) {
        try {
            if (mc.thePlayer.isOnSameTeam(entityPlayer)
                    || mc.thePlayer.getDisplayName().getUnformattedText().startsWith(entityPlayer.getDisplayName().getUnformattedText().substring(0, 2))
                    || getNetworkDisplayName().startsWith(entityPlayer.getDisplayName().getUnformattedText().substring(0, 2))) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static String getNetworkDisplayName() {
        try {
            net.minecraft.client.network.NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
            return ScorePlayerTeam.formatPlayerName(playerInfo.getPlayerTeam(), playerInfo.getGameProfile().getName());
        } catch (Exception ignored) {
        }
        return "";
    }

    private static int getColorFromEntity(EntityPlayer entityPlayer) {
        ScorePlayerTeam scorePlayerTeam = (ScorePlayerTeam) entityPlayer.getTeam();
        if (scorePlayerTeam != null) {
            String s = FontRenderer.getFormatFromString(scorePlayerTeam.getColorPrefix());
            if (s.length() >= 2) {
                return mc.fontRendererObj.getColorCode(s.charAt(1));
            }
        }
        String displayName = entityPlayer.getDisplayName().getFormattedText();
        displayName = removeFormatCodes(displayName);
        if (displayName.isEmpty() || !displayName.startsWith("\u00a7") || displayName.charAt(1) == 'f') {
            return -1;
        }
        switch (displayName.charAt(1)) {
            case '0':
                return 0x000000;
            case '1':
                return 0x0000AA;
            case '2':
                return 0x00AA00;
            case '3':
                return 0x00AAAA;
            case '4':
                return 0xAA0000;
            case '5':
                return 0xAA00AA;
            case '6':
                return 0xFFAA00;
            case '7':
                return 0xAAAAAA;
            case '8':
                return 0x555555;
            case '9':
                return 0x5555FF;
            case 'a':
                return 0x55FF55;
            case 'b':
                return 0x55FFFF;
            case 'c':
                return 0xFF5555;
            case 'd':
                return 0xFF55FF;
            case 'e':
                return 0xFFFF55;
            default:
                return -1;
        }
    }

    private static String removeFormatCodes(String string) {
        return string.replace("\u00a7k", "").replace("\u00a7l", "").replace("\u00a7m", "").replace("\u00a7n", "").replace("\u00a7o", "").replace("\u00a7r", "");
    }
}
