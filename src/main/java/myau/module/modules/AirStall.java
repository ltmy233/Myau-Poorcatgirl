package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.Priority;
import myau.events.LoadWorldEvent;
import myau.events.MoveInputEvent;
import myau.events.PlayerUpdateEvent;
import myau.events.UpdateEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import net.minecraft.client.Minecraft;

public class AirStall extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private boolean wasFlying;
    private float frozenYaw;
    private float frozenPitch;

    public final BooleanProperty lockRotation = new BooleanProperty("lock-rotation", false);

    public AirStall() {
        super("AirStall", false);
    }

    @Override
    public void onEnabled() {
        this.wasFlying = mc.thePlayer != null && mc.thePlayer.capabilities.isFlying;
        if (mc.thePlayer != null) {
            this.frozenYaw = mc.thePlayer.rotationYaw;
            this.frozenPitch = mc.thePlayer.rotationPitch;
        }
        this.freeze();
    }

    @Override
    public void onDisabled() {
        if (mc.thePlayer != null) {
            mc.thePlayer.capabilities.isFlying = this.wasFlying;
        }
        this.wasFlying = false;
    }

    @EventTarget(Priority.HIGHEST)
    public void onTick(TickEvent event) {
        if (this.isEnabled() && mc.thePlayer != null) {
            this.freeze();
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onMoveInput(MoveInputEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.thePlayer.movementInput == null) return;
        mc.thePlayer.movementInput.moveForward = 0.0F;
        mc.thePlayer.movementInput.moveStrafe = 0.0F;
        mc.thePlayer.movementInput.jump = false;
        mc.thePlayer.movementInput.sneak = false;
    }

    @EventTarget(Priority.HIGHEST)
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (this.isEnabled() && mc.thePlayer != null) this.freeze();
    }

    @EventTarget(Priority.HIGHEST)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || event.getType() != myau.event.types.EventType.PRE) return;
        if (!this.lockRotation.getValue()) return;
        event.setRotation(this.frozenYaw, this.frozenPitch, 7);
        event.setPervRotation(this.frozenYaw, 7);
    }

    @EventTarget
    public void onWorldLoad(LoadWorldEvent event) {
        this.setEnabled(false);
    }

    private void freeze() {
        if (mc.thePlayer == null) return;
        mc.thePlayer.motionX = 0.0;
        mc.thePlayer.motionY = 0.0;
        mc.thePlayer.motionZ = 0.0;
        mc.thePlayer.fallDistance = 0.0F;
        mc.thePlayer.capabilities.isFlying = true;
    }
}
