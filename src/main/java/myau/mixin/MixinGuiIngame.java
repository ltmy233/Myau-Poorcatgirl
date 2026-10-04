package myau.mixin;

import myau.Myau;
import myau.module.modules.AutoBlockIn;
import myau.module.modules.HUD;
import myau.module.modules.Scaffold;
import myau.util.RenderUtil;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.ScoreObjective;
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

    @Inject(method = "renderScoreboard", at = @At("HEAD"))
    private void betterScoreboardHead(CallbackInfo ci) {
        this.betterScoreboardRows = 0;
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
        HUD hud = Myau.moduleManager == null ? null : (HUD) Myau.moduleManager.getModule(HUD.class);
        if (hud != null && hud.rounded.getValue() && this.betterScoreboardRows == 1) {
            float radius = Math.min(hud.cornerRadius.getValue(), (float) (bottom - top) * 0.5F);
            if (radius > 0.0F) {
                RenderUtil.drawRoundedRect(
                        (float) left,
                        (float) top,
                        (float) (right - left),
                        (float) (bottom - top),
                        radius,
                        color,
                        false,
                        false,
                        true,
                        true
                );
                return;
            }
        }
        Gui.drawRect(left, top, right, bottom, color);
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
        HUD hud = Myau.moduleManager == null ? null : (HUD) Myau.moduleManager.getModule(HUD.class);
        if (hud != null && hud.rounded.getValue()) {
            float radius = Math.min(hud.cornerRadius.getValue(), (float) (bottom - top) * 0.5F);
            if (radius > 0.0F) {
                RenderUtil.drawRoundedRect(
                        (float) left,
                        (float) top,
                        (float) (right - left),
                        (float) (bottom - top),
                        radius,
                        color,
                        true,
                        true,
                        false,
                        false
                );
                return;
            }
        }
        Gui.drawRect(left, top, right, bottom, color);
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
        Gui.drawRect(left, top, right, bottom, color);
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
