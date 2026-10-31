package poorcatgirl.mixin;

import poorcatgirl.Poorcatgirl;
import poorcatgirl.event.EventManager;
import poorcatgirl.events.Render2DEvent;
import poorcatgirl.module.modules.NickHider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraftforge.client.GuiIngameForge;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(value = {GuiIngameForge.class}, priority = 9999)
public abstract class MixinGuiIngameForge {
    @Inject(
            method = {"renderGameOverlay"},
            at = {@At("HEAD")}
    )
    private void captureHudBackground(float partialTicks, CallbackInfo callbackInfo) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.hideGUI) {
            return;
        }
        poorcatgirl.module.modules.HUD hud = poorcatgirl.module.modules.HUD.get();
        if (hud != null && hud.glass.getValue()) {
            poorcatgirl.util.FrostedGlass.captureWorld();
        }
    }

    @Inject(
            method = {"renderGameOverlay"},
            at = {@At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/client/GuiIngameForge;renderTitle(IIF)V",
                    shift = At.Shift.AFTER,
                    remap = false
            )}
    )
    private void renderGameOverlay(float float1, CallbackInfo callbackInfo) {
        EventManager.call(new Render2DEvent(float1));
    }

    @Inject(
            method = {"renderPlayerList"},
            at = {@At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiPlayerTabOverlay;updatePlayerList(Z)V",
                    ordinal = 1,
                    shift = At.Shift.AFTER
            )},
            require = 0
    )
    private void betterTabExit(int width, int height, CallbackInfo callbackInfo) {
        if (poorcatgirl.util.TabAnim.overlay == null) {
            return;
        }
        if (poorcatgirl.util.TabAnim.settled() || poorcatgirl.util.TabAnim.isShowing()) {
            return;
        }
        if (poorcatgirl.util.TabAnim.getScaleY() <= 0.005F) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) {
            return;
        }
        Scoreboard scoreboard = mc.theWorld.getScoreboard();
        poorcatgirl.util.TabAnim.overlay.renderPlayerlist(width, scoreboard, scoreboard.getObjectiveInDisplaySlot(0));
    }

    @Redirect(
            method = {"renderExperience"},
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/entity/EntityPlayerSP;experience:F"
            )
    )
    private float renderExperience(EntityPlayerSP entityPlayerSP) {
        if (Poorcatgirl.moduleManager == null) {
            return entityPlayerSP.experience;
        } else {
            NickHider event = (NickHider) Poorcatgirl.moduleManager.modules.get(NickHider.class);
            return event.isEnabled() && event.level.getValue() ? 0.0F : entityPlayerSP.experience;
        }
    }

    @Redirect(
            method = {"renderExperience"},
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/entity/EntityPlayerSP;experienceLevel:I"
            )
    )
    private int renderExperienceLevel(EntityPlayerSP entityPlayerSP) {
        if (Poorcatgirl.moduleManager == null) {
            return entityPlayerSP.experienceLevel;
        } else {
            NickHider event = (NickHider) Poorcatgirl.moduleManager.modules.get(NickHider.class);
            return event.isEnabled() && event.level.getValue() ? 0 : entityPlayerSP.experienceLevel;
        }
    }
}
