package myau.mixin;

import myau.Myau;
import myau.module.modules.AutoBlockIn;
import myau.module.modules.HUD;
import myau.module.modules.Scaffold;
import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(value = {GuiIngame.class}, priority = 9999)
public abstract class MixinGuiIngame {

    private int betterScoreboardRows;
    private boolean betterScoreboardTitleGlass;

    // 记分板背板是"行底 + 标题底"两段拼的，分段只铺磨砂底，等标题画完再统一收边
    private boolean betterScoreboardGlassAny;
    private float betterScoreboardMinX;
    private float betterScoreboardMinY;
    private float betterScoreboardMaxX;
    private float betterScoreboardMaxY;
    private float betterScoreboardRadius;
    private float betterScoreboardOuterRadius;

    private static boolean betterScoreboardGlassOn(HUD hud) {
        return hud == null || hud.glassOn(hud.glassScoreboard);
    }

    private void drawScoreboardPanel(ScoreObjective objective, ScaledResolution resolution, HUD hud) {
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
        int rowCount = scores.size();
        int rowBase = resolution.getScaledHeight() / 2 + rowCount * lineHeight / 3;
        int top = rowBase - (rowCount + 1) * lineHeight - 1;
        int bottom = rowBase + lineHeight;
        int left = resolution.getScaledWidth() - maxWidth - 5;
        int right = resolution.getScaledWidth() - 1;
        float radius = hud != null && hud.rounded.getValue() ? hud.cornerRadius.getValue() : 0.0F;
        boolean glassDrawn = betterScoreboardGlass(left, top, right, bottom, hud, radius, radius, true, true);
        if (glassDrawn) {
            myau.util.FrostedGlass.drawEdge(left, top, right - left, bottom - top, radius, 1.0F);
        } else {
            RenderUtil.drawRoundedRect(left, top, right - left, bottom - top, radius,
                    new java.awt.Color(58, 62, 68, 108).getRGB(), true, true, true, true);
        }
    }

    /** 两段共用：铺一块不带描边的磨砂底，画成记并集返回 true。 */
    private boolean betterScoreboardGlass(int left, int top, int right, int bottom, HUD hud,
                                          float radiusTop, float radiusBottom,
                                          boolean edgeTop, boolean edgeBottom) {
        if (!betterScoreboardGlassOn(hud)) return false;
        float w = (float) (right - left);
        float h = (float) (bottom - top);
        if (w < 4.0F || h < 4.0F) return false;
        float rT = Math.max(0.0F, Math.min(radiusTop, h * 0.5F));
        float rB = Math.max(0.0F, Math.min(radiusBottom, h * 0.5F));
        if (rT > 0.0F && rT < this.betterScoreboardOuterRadius) this.betterScoreboardOuterRadius = rT;
        if (rB > 0.0F && rB < this.betterScoreboardOuterRadius) this.betterScoreboardOuterRadius = rB;
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        if (!myau.util.FrostedGlass.draw((float) left, (float) top, w, h, rT, rB,
                edgeTop, edgeBottom, sr.getScaledWidth(), sr.getScaledHeight(), 1.0F, false)) {
            return false;
        }
        if (this.betterScoreboardGlassAny) {
            if (left < this.betterScoreboardMinX) this.betterScoreboardMinX = left;
            if (top < this.betterScoreboardMinY) this.betterScoreboardMinY = top;
            if (right > this.betterScoreboardMaxX) this.betterScoreboardMaxX = right;
            if (bottom > this.betterScoreboardMaxY) this.betterScoreboardMaxY = bottom;
        } else {
            this.betterScoreboardMinX = left;
            this.betterScoreboardMinY = top;
            this.betterScoreboardMaxX = right;
            this.betterScoreboardMaxY = bottom;
            this.betterScoreboardGlassAny = true;
        }
        return true;
    }

    private void betterScoreboardFinishEdge() {
        if (!this.betterScoreboardGlassAny) return;
        this.betterScoreboardGlassAny = false;
        float w = this.betterScoreboardMaxX - this.betterScoreboardMinX;
        float h = this.betterScoreboardMaxY - this.betterScoreboardMinY;
        float radius = Math.min(this.betterScoreboardRadius, h * 0.5F);
        if (this.betterScoreboardOuterRadius < radius) radius = this.betterScoreboardOuterRadius;
        myau.util.FrostedGlass.drawEdge(this.betterScoreboardMinX, this.betterScoreboardMinY,
                w, h, radius, 1.0F);
    }

    @Inject(method = "renderScoreboard", at = @At("HEAD"))
    private void betterScoreboardHead(ScoreObjective objective, ScaledResolution resolution, CallbackInfo ci) {
        this.betterScoreboardRows = 0;
        this.betterScoreboardTitleGlass = false;
        this.betterScoreboardGlassAny = false;
        this.betterScoreboardMinX = Float.MAX_VALUE;
        this.betterScoreboardMinY = Float.MAX_VALUE;
        this.betterScoreboardMaxX = -Float.MAX_VALUE;
        this.betterScoreboardMaxY = -Float.MAX_VALUE;
        this.betterScoreboardRadius = 0.0F;
        this.betterScoreboardOuterRadius = Float.MAX_VALUE;
        HUD hud = HUD.get();
        if (hud != null && hud.rounded.getValue()) {
            this.betterScoreboardRadius = hud.cornerRadius.getValue();
        }
        this.drawScoreboardPanel(objective, resolution, hud);
    }

    @Redirect(
            method = "renderScoreboard",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiIngame;drawRect(IIIII)V",
                    ordinal = 0
            )
    )
    private void betterScoreboardRow(int left, int top, int right, int bottom, int color) {
        this.betterScoreboardRows++;
    }

    @Redirect(
            method = "renderScoreboard",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiIngame;drawRect(IIIII)V",
                    ordinal = 1
            )
    )
    private void betterScoreboardTitle(int left, int top, int right, int bottom, int color) {
    }

    @Redirect(
            method = "renderScoreboard",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiIngame;drawRect(IIIII)V",
                    ordinal = 2
            )
    )
    private void betterScoreboardSeparator(int left, int top, int right, int bottom, int color) {
    }
    @Redirect(
            method = {"updateTick"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/InventoryPlayer;getCurrentItem()Lnet/minecraft/item/ItemStack;"
            )
    )
    private ItemStack updateTick(InventoryPlayer inventoryPlayer) {
        Scaffold scaffold = (Scaffold) Myau.moduleManager.modules.get(Scaffold.class);
        if (scaffold.isEnabled() && scaffold.itemSpoof.getValue()) {
            int slot = scaffold.getSlot();
            if (slot >= 0) {
                return inventoryPlayer.getStackInSlot(slot);
            }
        }
        AutoBlockIn autoBlockIn = (AutoBlockIn) Myau.moduleManager.modules.get(AutoBlockIn.class);
        if(autoBlockIn.itemSpoof.getValue() && autoBlockIn.isEnabled()){
            int slot = autoBlockIn.getSlot();
            if (slot >= 0) {
                return inventoryPlayer.getStackInSlot(slot);
            }
        }
        return inventoryPlayer.getCurrentItem();
    }
}
