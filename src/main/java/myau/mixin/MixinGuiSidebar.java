package myau.mixin;

import myau.Myau;
import myau.module.modules.HUD;
import myau.util.RenderUtil;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.Minecraft;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "revamp.sidebarmod.gui.GuiSidebar", remap = false)
public abstract class MixinGuiSidebar {

    private int betterSidebarRows;
    private boolean betterSidebarTitleGlass;

    // revamp 侧栏背板同样是"行底 + 标题底"两段拼的，分段铺磨砂底、最后统一收边
    private boolean betterSidebarGlassAny;
    private float betterSidebarMinX;
    private float betterSidebarMinY;
    private float betterSidebarMaxX;
    private float betterSidebarMaxY;
    private float betterSidebarRadius;
    private float betterSidebarOuterRadius;

    private static boolean betterSidebarGlassOn(HUD hud) {
        return hud == null || hud.glassOn(hud.glassScoreboard);
    }

    private void drawSidebarPanel(net.minecraft.scoreboard.ScoreObjective objective,
                                  net.minecraft.client.gui.ScaledResolution resolution, HUD hud) {
        Scoreboard scoreboard = objective.getScoreboard();
        java.util.List<Score> scores = new java.util.ArrayList<>(scoreboard.getSortedScores(objective));
        scores.removeIf(score -> score.getPlayerName().startsWith("#"));
        if (scores.size() > 15) scores = new java.util.ArrayList<>(scores.subList(scores.size() - 15, scores.size()));
        int maxWidth = Minecraft.getMinecraft().fontRendererObj.getStringWidth(objective.getDisplayName());
        for (Score score : scores) {
            ScorePlayerTeam team = scoreboard.getPlayersTeam(score.getPlayerName());
            String name = ScorePlayerTeam.formatPlayerName(team, score.getPlayerName());
            String value = ": " + net.minecraft.util.EnumChatFormatting.RED + score.getScorePoints();
            maxWidth = Math.max(maxWidth, Minecraft.getMinecraft().fontRendererObj.getStringWidth(name + value));
        }
        int lineHeight = Minecraft.getMinecraft().fontRendererObj.FONT_HEIGHT;
        int rowBase = resolution.getScaledHeight() / 2 + scores.size() * lineHeight / 3;
        int top = rowBase - (scores.size() + 1) * lineHeight - 1;
        int bottom = rowBase + lineHeight;
        int left = resolution.getScaledWidth() - maxWidth - 5;
        int right = resolution.getScaledWidth() - 1;
        float radius = hud != null && hud.rounded.getValue() ? hud.cornerRadius.getValue() : 0.0F;
        boolean glassDrawn = betterSidebarGlass(left, top, right, bottom, hud, radius, radius, true, true);
        if (glassDrawn) {
            myau.util.FrostedGlass.drawEdge(left, top, right - left, bottom - top, radius, 1.0F);
        } else {
            RenderUtil.drawRoundedRect(left, top, right - left, bottom - top, radius,
                    new java.awt.Color(58, 62, 68, 108).getRGB(), true, true, true, true);
        }
    }

    private boolean betterSidebarGlass(int left, int top, int right, int bottom, HUD hud,
                                       float radiusTop, float radiusBottom,
                                       boolean edgeTop, boolean edgeBottom) {
        if (!betterSidebarGlassOn(hud)) return false;
        float w = (float) (right - left);
        float h = (float) (bottom - top);
        if (w < 4.0F || h < 4.0F) return false;
        float rT = Math.max(0.0F, Math.min(radiusTop, h * 0.5F));
        float rB = Math.max(0.0F, Math.min(radiusBottom, h * 0.5F));
        if (rT > 0.0F && rT < this.betterSidebarOuterRadius) this.betterSidebarOuterRadius = rT;
        if (rB > 0.0F && rB < this.betterSidebarOuterRadius) this.betterSidebarOuterRadius = rB;
        net.minecraft.client.gui.ScaledResolution sr =
                new net.minecraft.client.gui.ScaledResolution(net.minecraft.client.Minecraft.getMinecraft());
        if (!myau.util.FrostedGlass.draw((float) left, (float) top, w, h, rT, rB,
                edgeTop, edgeBottom, sr.getScaledWidth(), sr.getScaledHeight(), 1.0F, false)) {
            return false;
        }
        if (this.betterSidebarGlassAny) {
            if (left < this.betterSidebarMinX) this.betterSidebarMinX = left;
            if (top < this.betterSidebarMinY) this.betterSidebarMinY = top;
            if (right > this.betterSidebarMaxX) this.betterSidebarMaxX = right;
            if (bottom > this.betterSidebarMaxY) this.betterSidebarMaxY = bottom;
        } else {
            this.betterSidebarMinX = left;
            this.betterSidebarMinY = top;
            this.betterSidebarMaxX = right;
            this.betterSidebarMaxY = bottom;
            this.betterSidebarGlassAny = true;
        }
        return true;
    }

    private void betterSidebarFinishEdge() {
        if (!this.betterSidebarGlassAny) return;
        this.betterSidebarGlassAny = false;
        float w = this.betterSidebarMaxX - this.betterSidebarMinX;
        float h = this.betterSidebarMaxY - this.betterSidebarMinY;
        float radius = Math.min(this.betterSidebarRadius, h * 0.5F);
        if (this.betterSidebarOuterRadius < radius) radius = this.betterSidebarOuterRadius;
        myau.util.FrostedGlass.drawEdge(this.betterSidebarMinX, this.betterSidebarMinY,
                w, h, radius, 1.0F);
    }

    @Inject(method = "drawSidebar", at = @At("HEAD"))
    private void betterSidebarHead(net.minecraft.scoreboard.ScoreObjective objective,
                                   net.minecraft.client.gui.ScaledResolution resolution, CallbackInfo ci) {
        this.betterSidebarRows = 0;
        this.betterSidebarTitleGlass = false;
        this.betterSidebarGlassAny = false;
        this.betterSidebarMinX = Float.MAX_VALUE;
        this.betterSidebarMinY = Float.MAX_VALUE;
        this.betterSidebarMaxX = -Float.MAX_VALUE;
        this.betterSidebarMaxY = -Float.MAX_VALUE;
        this.betterSidebarRadius = 0.0F;
        this.betterSidebarOuterRadius = Float.MAX_VALUE;
        HUD hud = HUD.get();
        if (hud != null && hud.rounded.getValue()) {
            this.betterSidebarRadius = hud.cornerRadius.getValue();
        }
        this.drawSidebarPanel(objective, resolution, hud);
    }

    @Redirect(
            method = "drawSidebar",
            at = @At(
                    value = "INVOKE",
                    target = "Lrevamp/sidebarmod/gui/GuiSidebar;func_73734_a(IIIII)V",
                    ordinal = 0,
                    remap = false
            ),
            remap = false
    )
    private void betterSidebarRow(int left, int top, int right, int bottom, int color) {
        this.betterSidebarRows++;
    }

    @Redirect(
            method = "drawSidebar",
            at = @At(
                    value = "INVOKE",
                    target = "Lrevamp/sidebarmod/gui/GuiSidebar;func_73734_a(IIIII)V",
                    ordinal = 1,
                    remap = false
            ),
            remap = false
    )
    private void betterSidebarTitle(int left, int top, int right, int bottom, int color) {
    }

    @Redirect(
            method = "drawSidebar",
            at = @At(
                    value = "INVOKE",
                    target = "Lrevamp/sidebarmod/gui/GuiSidebar;func_73734_a(IIIII)V",
                    ordinal = 2,
                    remap = false
            ),
            remap = false
    )
    private void betterSidebarSeparator(int left, int top, int right, int bottom, int color) {
    }
}
