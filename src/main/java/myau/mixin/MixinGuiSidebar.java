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
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "revamp.sidebarmod.gui.GuiSidebar", remap = false)
public abstract class MixinGuiSidebar {

    @Shadow
    public float scale;
    @Shadow
    public int offsetX;
    @Shadow
    public int offsetY;

    private int betterSidebarRows;
    private boolean betterSidebarTitleGlass;

    // 侧栏背板分两段铺磨砂底，最后统一收边
    private boolean betterSidebarGlassAny;
    private float betterSidebarMinX;
    private float betterSidebarMinY;
    private float betterSidebarMaxX;
    private float betterSidebarMaxY;
    private float betterSidebarRadius;
    private float betterSidebarOuterRadius;

    private static float betterSidebarLastScale = 1.0F;
    private static int betterSidebarLastOx = 0;
    private static int betterSidebarLastOy = 0;
    private static int betterSidebarLastRows = 15;

    private static boolean betterSidebarGlassOn(HUD hud) {
        return hud == null || hud.glassOn(hud.glassScoreboard);
    }

    private static int betterSidebarRowLimit(HUD hud) {
        if (hud == null || !hud.isEnabled()) return 15;
        return Math.max(5, Math.min(hud.scoreboardRows.getValue(), 15));
    }

    private void drawSidebarPanel(net.minecraft.scoreboard.ScoreObjective objective,
                                  net.minecraft.client.gui.ScaledResolution resolution, HUD hud) {
        Scoreboard scoreboard = objective.getScoreboard();
        java.util.List<Score> scores = new java.util.ArrayList<>(scoreboard.getSortedScores(objective));
        scores.removeIf(score -> score.getPlayerName().startsWith("#"));
        int rowLimit = betterSidebarRowLimit(hud);
        if (scores.size() > rowLimit) {
            scores = new java.util.ArrayList<>(scores.subList(0, rowLimit));
        }
        int maxWidth = Minecraft.getMinecraft().fontRendererObj.getStringWidth(objective.getDisplayName());
        for (Score score : scores) {
            ScorePlayerTeam team = scoreboard.getPlayersTeam(score.getPlayerName());
            String name = ScorePlayerTeam.formatPlayerName(team, score.getPlayerName());
            String value = ": " + net.minecraft.util.EnumChatFormatting.RED + score.getScorePoints();
            maxWidth = Math.max(maxWidth, Minecraft.getMinecraft().fontRendererObj.getStringWidth(name + value));
        }
        int lineHeight = Minecraft.getMinecraft().fontRendererObj.FONT_HEIGHT;
        int rowCount = scores.size();
        int rowBase = resolution.getScaledHeight() / 2 + rowCount * lineHeight / 3;
        int top = rowBase - (rowCount + 1) * lineHeight - 1;
        int bottom = rowBase + lineHeight;
        int left = resolution.getScaledWidth() - maxWidth - 5;
        int right = resolution.getScaledWidth() - 1;
        float radius = hud != null && hud.rounded.getValue() ? hud.cornerRadius.getValue() : 0.0F;
        float s = 1.0F;
        int ox = 0;
        int oy = 0;
        if (hud != null && hud.isEnabled()) {
            s = hud.scoreboardScale.getValue();
            ox = hud.scoreboardOffsetX.getValue();
            oy = hud.scoreboardOffsetY.getValue();
        }
        int sidebarHeight = rowCount * lineHeight;
        float anchorX = (float) (resolution.getScaledWidth() - 3);
        float anchorY = (float) (resolution.getScaledHeight() / 2 + sidebarHeight / 3 - sidebarHeight / 2);
        float leftF = anchorX + ((float) left - anchorX) * s + (float) ox;
        float topF = anchorY + ((float) top - anchorY) * s + (float) oy;
        float rightF = anchorX + ((float) right - anchorX) * s + (float) ox;
        float bottomF = anchorY + ((float) bottom - anchorY) * s + (float) oy;
        float radiusS = radius * s;
        boolean glassDrawn = betterSidebarGlass(leftF, topF, rightF, bottomF, hud, radiusS, radiusS, true, true);
        if (glassDrawn) {
            myau.util.FrostedGlass.drawEdge(leftF, topF, rightF - leftF, bottomF - topF, radiusS, 1.0F);
        } else {
            RenderUtil.drawRoundedRect(leftF, topF, rightF - leftF, bottomF - topF, radiusS,
                    new java.awt.Color(58, 62, 68, 108).getRGB(), true, true, true, true);
        }
    }

    private boolean betterSidebarGlass(float left, float top, float right, float bottom, HUD hud,
                                       float radiusTop, float radiusBottom,
                                       boolean edgeTop, boolean edgeBottom) {
        if (!betterSidebarGlassOn(hud)) return false;
        float w = right - left;
        float h = bottom - top;
        if (w < 4.0F || h < 4.0F) return false;
        float rT = Math.max(0.0F, Math.min(radiusTop, h * 0.5F));
        float rB = Math.max(0.0F, Math.min(radiusBottom, h * 0.5F));
        if (rT > 0.0F && rT < this.betterSidebarOuterRadius) this.betterSidebarOuterRadius = rT;
        if (rB > 0.0F && rB < this.betterSidebarOuterRadius) this.betterSidebarOuterRadius = rB;
        net.minecraft.client.gui.ScaledResolution sr =
                new net.minecraft.client.gui.ScaledResolution(net.minecraft.client.Minecraft.getMinecraft());
        if (!myau.util.FrostedGlass.draw(left, top, w, h, rT, rB,
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
        float userScale = 1.0F;
        int userOx = 0;
        int userOy = 0;
        if (hud != null && hud.isEnabled()) {
            userScale = hud.scoreboardScale.getValue();
            userOx = hud.scoreboardOffsetX.getValue();
            userOy = hud.scoreboardOffsetY.getValue();
        }
        this.scale = userScale;
        this.offsetX = userOx;
        this.offsetY = userOy;
        if (Math.abs(userScale - betterSidebarLastScale) > 0.001F
                || userOx != betterSidebarLastOx || userOy != betterSidebarLastOy
                || betterSidebarRowLimit(hud) != betterSidebarLastRows) {
            betterSidebarLastScale = userScale;
            betterSidebarLastOx = userOx;
            betterSidebarLastOy = userOy;
            betterSidebarLastRows = betterSidebarRowLimit(hud);
        }
        this.drawSidebarPanel(objective, resolution, hud);
    }

    @Redirect(
            method = "drawSidebar",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/scoreboard/Scoreboard;func_96534_i(Lnet/minecraft/scoreboard/ScoreObjective;)Ljava/util/Collection;",
                    remap = false
            ),
            remap = false
    )
    private java.util.Collection<Score> betterSidebarSortedScores(Scoreboard scoreboard, net.minecraft.scoreboard.ScoreObjective objective) {
        int n = betterSidebarRowLimit(HUD.get());
        java.util.List<Score> all = new java.util.ArrayList<>();
        for (Score score : scoreboard.getSortedScores(objective)) {
            if (!score.getPlayerName().startsWith("#")) {
                all.add(score);
            }
        }
        if (all.size() > n) {
            all = new java.util.ArrayList<>(all.subList(0, n));
        }
        return all;
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
