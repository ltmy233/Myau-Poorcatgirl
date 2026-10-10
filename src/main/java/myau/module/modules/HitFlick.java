package myau.module.modules;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.LeftClickMouseEvent;
import myau.events.LoadWorldEvent;
import myau.events.MoveInputEvent;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.PercentProperty;
import myau.util.KeyBindUtil;
import myau.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.Random;

public class HitFlick extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Random rnd = new Random();

    private static final int IDLE = 0;
    private static final int FLICKING = 1;
    private static final int RETURNING = 2;

    private static final float AWAY_STEP = 30.0F;
    private static final float BACK_STEP = 48.0F;
    private static final float REACHED_TOLERANCE = 6.0F;
    private static final int MIN_AWAY_TICKS = 5;
    private static final int BACK_TICKS = 2;
    private static final int BLINK_FLUSH_TICKS = 7;
    private static final double MAX_TARGET_DISTANCE = 5.0;

    public final FloatProperty angle = new FloatProperty("angle", 90.0F, 0.0F, 360.0F);
    public final PercentProperty chance = new PercentProperty("chance", 100);
    public final IntProperty flickDelay = new IntProperty("flick-delay", 250, 0, 2000);
    public final BooleanProperty randomizeOffset = new BooleanProperty("randomize-offset", false);
    public final FloatProperty randomizeOffsetRange = new FloatProperty("randomize-offset-range", 0.0F, 0.0F, 180.0F);
    public final BooleanProperty strafeInvert = new BooleanProperty("strafe-invert", false);
    public final BooleanProperty selectHits = new BooleanProperty("select-hits", true);
    public final BooleanProperty useBlink = new BooleanProperty("use-blink", false);

    private int state = IDLE;
    private int stateTicks;
    private boolean attackQueued;
    private EntityLivingBase target;
    private float flickOffset;
    private long lastFlickAt;
    private boolean blinkHeld;

    public HitFlick() {
        super("HitFlick", false);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{String.valueOf(this.angle.getValue().intValue())};
    }

    @EventTarget(Priority.LOWEST)
    public void onLeftClick(LeftClickMouseEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) {
            return;
        }
        Telly telly = (Telly) Myau.moduleManager.modules.get(Telly.class);
        if (telly != null && telly.isAutomationRunning()) {
            return;
        }
        if (this.state != IDLE) {
            event.setCancelled(true);
            return;
        }
        if (!this.canStartFlick()) {
            return;
        }
        EntityLivingBase crosshair = this.getCrosshairTarget();
        if (crosshair == null || !this.isValidTarget(crosshair)) {
            return;
        }
        if (this.startFlick(crosshair, !event.isCancelled())) {
            event.setCancelled(true);
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (this.state == IDLE) {
            return;
        }
        if (mc.currentScreen != null || this.target == null || !this.isValidTarget(this.target)) {
            this.reset();
            return;
        }

        ++this.stateTicks;

        if (this.state == FLICKING) {
            this.cancelSprint();
            if (this.blinkHeld && this.stateTicks >= BLINK_FLUSH_TICKS) {
                this.flushBlink();
            }
            float[] aim = this.computeAim(this.target);
            float awayYaw = MathHelper.wrapAngleTo180_float(aim[0] + this.flickOffset);
            float newYaw = stepToward(event.getYaw(), awayYaw, AWAY_STEP);
            float newPitch = stepToward(event.getPitch(), aim[1], AWAY_STEP);
            event.setRotation(newYaw, newPitch, Priority.LOWEST);
            boolean reached = Math.abs(MathHelper.wrapAngleTo180_float(awayYaw - newYaw)) <= REACHED_TOLERANCE
                    && Math.abs(MathHelper.wrapAngleTo180_float(aim[1] - newPitch)) <= REACHED_TOLERANCE;
            if (reached || this.stateTicks >= MIN_AWAY_TICKS) {
                this.state = RETURNING;
                this.stateTicks = 0;
            }
            return;
        }

        if (this.attackQueued) {
            this.flushBlink();
            this.sendQueuedAttack();
            this.attackQueued = false;
        }
        float[] aim = this.computeAim(this.target);
        event.setRotation(stepToward(event.getYaw(), aim[0], BACK_STEP),
                stepToward(event.getPitch(), aim[1], BACK_STEP), Priority.LOWEST);
        if (this.stateTicks >= BACK_TICKS) {
            this.reset();
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!this.isEnabled() || this.state != RETURNING || mc.thePlayer == null || mc.thePlayer.movementInput == null) {
            return;
        }
        mc.thePlayer.movementInput.moveForward = 0.0F;
    }

    @EventTarget
    public void onWorldLoad(LoadWorldEvent event) {
        this.reset();
    }

    @Override
    public void onDisabled() {
        this.reset();
    }

    private boolean canStartFlick() {
        if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null) {
            return false;
        }
        if (Math.abs(MathHelper.wrapAngleTo180_float(this.getConfiguredAngle())) < 0.001F) {
            return false;
        }
        return System.currentTimeMillis() - this.lastFlickAt >= (long) this.flickDelay.getValue().intValue();
    }

    private boolean startFlick(EntityLivingBase living, boolean queueAttack) {
        if (this.chance.getValue().intValue() < Math.random() * 100.0) {
            return false;
        }
        if (this.selectHits.getValue().booleanValue() && living.hurtTime > 1) {
            return false;
        }
        float offset = this.getConfiguredAngle();
        if (Math.abs(offset) < 0.001F) {
            return false;
        }
        this.target = living;
        this.flickOffset = offset;
        this.attackQueued = queueAttack;
        this.state = FLICKING;
        this.stateTicks = 0;
        this.lastFlickAt = System.currentTimeMillis();
        this.startBlink();
        return true;
    }

    private float getConfiguredAngle() {
        float value = this.angle.getValue();
        if (this.randomizeOffset.getValue().booleanValue() && this.randomizeOffsetRange.getValue() > 0.0F) {
            value += (rnd.nextFloat() - 0.5F) * this.randomizeOffsetRange.getValue().floatValue();
        }
        value = MathHelper.wrapAngleTo180_float(value);
        if (this.strafeInvert.getValue().booleanValue()) {
            value = this.applyStrafeInvert(value);
        }
        return MathHelper.wrapAngleTo180_float(value);
    }

    private float applyStrafeInvert(float value) {
        boolean left = KeyBindUtil.isKeyDown(mc.gameSettings.keyBindLeft.getKeyCode());
        boolean right = KeyBindUtil.isKeyDown(mc.gameSettings.keyBindRight.getKeyCode());
        if (left == right || Math.abs(value) < 0.001F || Math.abs(Math.abs(value) - 180.0F) < 0.001F) {
            return value;
        }
        if ((left && value < 0.0F) || (right && value > 0.0F)) {
            return -value;
        }
        return value;
    }

    private EntityLivingBase getCrosshairTarget() {
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY) {
            return null;
        }
        Entity entity = hit.entityHit;
        if (!(entity instanceof EntityLivingBase)) {
            return null;
        }
        if (mc.thePlayer.getDistanceToEntity(entity) > MAX_TARGET_DISTANCE) {
            return null;
        }
        return (EntityLivingBase) entity;
    }

    private boolean isValidTarget(EntityLivingBase living) {
        if (living == null || living.isDead || living.getHealth() <= 0.0F) {
            return false;
        }
        if (living == mc.thePlayer || living == mc.thePlayer.ridingEntity) {
            return false;
        }
        if (mc.theWorld == null || !mc.theWorld.loadedEntityList.contains(living)) {
            return false;
        }
        return mc.thePlayer.getDistanceToEntity(living) <= MAX_TARGET_DISTANCE;
    }

    private float[] computeAim(EntityLivingBase living) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        AxisAlignedBB box = living.getEntityBoundingBox();
        double lowY = box.minY + 0.05 * (box.maxY - box.minY);
        double highY = box.minY + 0.75 * (box.maxY - box.minY);
        double deltaX = (box.minX + box.maxX) / 2.0 - eye.xCoord;
        double deltaY = eye.yCoord >= highY ? highY - eye.yCoord : (eye.yCoord <= lowY ? lowY - eye.yCoord : 0.0);
        double deltaZ = (box.minZ + box.maxZ) / 2.0 - eye.zCoord;
        double horizontal = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float yaw = (float) (Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI) - 90.0F;
        float pitch = horizontal == 0.0 ? 0.0F : (float) (-Math.atan2(deltaY, horizontal) * 180.0 / Math.PI);
        return new float[]{MathHelper.wrapAngleTo180_float(yaw), MathHelper.clamp_float(pitch, -90.0F, 90.0F)};
    }

    private static float stepToward(float from, float to, float maxStep) {
        float delta = MathHelper.wrapAngleTo180_float(to - from);
        if (delta > maxStep) {
            delta = maxStep;
        } else if (delta < -maxStep) {
            delta = -maxStep;
        }
        return MathHelper.wrapAngleTo180_float(from + delta);
    }

    private void cancelSprint() {
        if (mc.thePlayer.isSprinting()) {
            mc.thePlayer.setSprinting(false);
        }
    }

    private void sendQueuedAttack() {
        if (this.target == null || mc.thePlayer == null || mc.thePlayer.isDead) {
            return;
        }
        PacketUtil.sendPacket(new C02PacketUseEntity(this.target, C02PacketUseEntity.Action.ATTACK));
        mc.thePlayer.swingItem();
    }

    private void startBlink() {
        if (!this.useBlink.getValue().booleanValue() || Myau.blinkManager.isBlinking()) {
            return;
        }
        this.blinkHeld = Myau.blinkManager.setBlinkState(true, BlinkModules.HIT_FLICK);
    }

    private void flushBlink() {
        if (!this.blinkHeld) {
            return;
        }
        this.blinkHeld = false;
        Myau.blinkManager.setBlinkState(false, BlinkModules.HIT_FLICK);
    }

    private void reset() {
        this.flushBlink();
        this.state = IDLE;
        this.stateTicks = 0;
        this.attackQueued = false;
        this.target = null;
        this.flickOffset = 0.0F;
    }
}
