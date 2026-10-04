package myau.module.modules;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventManager;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.*;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.util.ItemUtil;
import myau.util.PacketUtil;
import myau.util.PlayerUtil;
import myau.util.RotationUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityPigZombie;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class KillAuraV2 extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final String[] AUTO_BLOCK_MODES = {"MANUAL", "VANILLA", "FAKE", "PARTIAL", "HYPIXEL_1_8", "HYPIXEL_1_12"};
    private static final String[] ROTATION_MODES = {"SILENT", "LOCK_VIEW", "NONE"};
    private static final String[] SORT_MODES = {"DISTANCE", "HEALTH", "HURT_TIME", "YAW"};
    private static final String[] INFO_MODES = {"AUTOblock", "ROTATION", "APS", "TARGETS"};

    public final FloatProperty aps;
    public final ModeProperty autoBlock;
    public final IntProperty fov;
    public final FloatProperty attackRange;
    public final FloatProperty swingRange;
    public final FloatProperty blockRange;
    public final ModeProperty rotations;
    public final IntProperty smoothing;
    public final ModeProperty sort;
    public final IntProperty switchDelay;
    public final IntProperty targets;
    public final BooleanProperty targetInvis;
    public final BooleanProperty attackMobs;
    public final BooleanProperty disableInInventory;
    public final BooleanProperty disableWhileBlocking;
    public final BooleanProperty disableWhileMining;
    public final BooleanProperty hitThroughBlocks;
    public final BooleanProperty ignoreTeammates;
    public final BooleanProperty manualBlock;
    public final BooleanProperty prioritizeEnemies;
    public final BooleanProperty requireMouseDown;
    public final BooleanProperty silentSwing;
    public final BooleanProperty weaponOnly;
    public final ModeProperty info;
    public final ModeProperty showTarget;

    private static EntityLivingBase target;
    private static EntityLivingBase attackingEntity;
    private final HashMap<Integer, Integer> hitMap = new HashMap<>();
    private final Random rng = new Random();

    private long lastAttackTime = 0L;
    private long attackDelay = 62L;
    private boolean shouldAttack = false;
    private int delayTicks = 0;

    private boolean blockingClient = false;
    private int interactTicks = 0;
    private boolean firstCycle = false;
    private boolean swapped = false;
    private boolean partialDown = false;
    private int partialTicks = 0;
    private boolean lag = false;

    public KillAuraV2() {
        super("KillAuraV2", false);
        this.aps = new FloatProperty("aps", 16.0F, 1.0F, 20.0F);
        this.autoBlock = new ModeProperty("auto-block", 0, AUTO_BLOCK_MODES);
        this.fov = new IntProperty("fov", 360, 30, 360);
        this.attackRange = new FloatProperty("attack-range", 3.0F, 3.0F, 6.0F);
        this.swingRange = new FloatProperty("swing-range", 3.3F, 3.0F, 8.0F);
        this.blockRange = new FloatProperty("block-range", 6.0F, 3.0F, 12.0F);
        this.rotations = new ModeProperty("rotations", 0, ROTATION_MODES);
        this.smoothing = new IntProperty("smoothing", 0, 0, 10);
        this.sort = new ModeProperty("sort", 0, SORT_MODES);
        this.switchDelay = new IntProperty("switch-delay", 200, 50, 1000);
        this.targets = new IntProperty("targets", 3, 1, 10);
        this.targetInvis = new BooleanProperty("target-invis", true);
        this.attackMobs = new BooleanProperty("attack-mobs", false);
        this.disableInInventory = new BooleanProperty("disable-in-inventory", true);
        this.disableWhileBlocking = new BooleanProperty("disable-while-blocking", false);
        this.disableWhileMining = new BooleanProperty("disable-while-mining", false);
        this.hitThroughBlocks = new BooleanProperty("hit-through-blocks", true);
        this.ignoreTeammates = new BooleanProperty("ignore-teammates", true);
        this.manualBlock = new BooleanProperty("manual-block", false);
        this.prioritizeEnemies = new BooleanProperty("prioritize-enemies", false);
        this.requireMouseDown = new BooleanProperty("require-mouse-down", false);
        this.silentSwing = new BooleanProperty("silent-swing", false);
        this.weaponOnly = new BooleanProperty("weapon-only", false);
        this.info = new ModeProperty("info", 0, INFO_MODES);
        this.showTarget = new ModeProperty("show-target", 0, new String[]{"NONE", "DEFAULT", "HUD"});
    }

    @Override
    public void onEnabled() {
        this.hitMap.clear();
        this.shouldAttack = false;
        this.lastAttackTime = 0L;
        this.interactTicks = 0;
        this.firstCycle = false;
        this.swapped = false;
        this.partialDown = false;
        this.partialTicks = 0;
        this.lag = false;
        this.blockingClient = false;
        if (this.rotations.getValue() == 0 && this.autoBlock.getValue() <= 1) {
            this.delayTicks = 1;
        }
    }

    @Override
    public void onDisabled() {
        this.resetBlinkState();
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        if (this.blockingClient) {
            this.blockingClient = false;
            if (mc.thePlayer != null) mc.thePlayer.stopUsingItem();
        }
        this.shouldAttack = false;
        this.delayTicks = 0;
        this.interactTicks = 0;
        this.partialDown = false;
        target = null;
        attackingEntity = null;
    }

    @Override
    public String[] getSuffix() {
        switch (this.info.getValue()) {
            case 0:
                return new String[]{this.autoBlock.getModeString()};
            case 1:
                return new String[]{this.rotations.getModeString()};
            case 2:
                return new String[]{String.format(Locale.US, "%.1f", this.aps.getValue())};
            case 3: {
                int t = this.targets.getValue();
                String kind = t <= 2 ? "Single" : (t <= 5 ? "Switch" : "Multi");
                return new String[]{kind + " " + this.getTargetCount()};
            }
        }
        return new String[0];
    }

    private int getTargetCount() {
        if (mc.theWorld == null || mc.thePlayer == null) {
            return 0;
        }
        int count = 0;
        double maxRange = this.getMaxRange();
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity instanceof EntityLivingBase && this.isValidTarget((EntityLivingBase) entity)
                    && mc.thePlayer.getDistanceToEntity(entity) < maxRange + maxRange / 3.0) {
                count++;
            }
        }
        return count;
    }

    private double getMaxRange() {
        return Math.max(Math.max(this.swingRange.getValue(), this.attackRange.getValue()), this.blockRange.getValue());
    }

    private boolean blinkAutoBlock() {
        return this.autoBlock.getValue() == 4 || this.autoBlock.getValue() == 5;
    }

    private boolean autoBlockOverride() {
        return this.blinkAutoBlock() && ItemUtil.isHoldingSword();
    }

    private boolean manualBlockDown() {
        return (!this.manualBlock.getValue() || Mouse.isButtonDown(1)) && ItemUtil.isHoldingSword();
    }

    private boolean basicCondition() {
        return mc.theWorld != null && mc.thePlayer != null && !mc.thePlayer.isDead;
    }

    private boolean settingCondition() {
        if (this.requireMouseDown.getValue() && !Mouse.isButtonDown(0)) {
            return false;
        }
        if (this.weaponOnly.getValue() && !(ItemUtil.isHoldingSword() || ItemUtil.isHoldingTool())) {
            return false;
        }
        if (this.disableWhileMining.getValue() && this.isMining()) {
            return false;
        }
        if (this.disableInInventory.getValue() && mc.currentScreen != null) {
            return false;
        }
        if (Myau.playerStateManager.digging || Myau.playerStateManager.placing) {
            return false;
        }
        BedNuker bedNuker = (BedNuker) Myau.moduleManager.modules.get(BedNuker.class);
        if (bedNuker != null && bedNuker.isEnabled() && bedNuker.isReady()) {
            return false;
        }
        if (Myau.moduleManager.modules.get(Scaffold.class) != null && Myau.moduleManager.modules.get(Scaffold.class).isEnabled()) {
            return false;
        }
        AutoBlockIn autoBlockIn = (AutoBlockIn) Myau.moduleManager.modules.get(AutoBlockIn.class);
        if (autoBlockIn != null && autoBlockIn.isEnabled()) {
            return false;
        }
        return !this.requireMouseDown.getValue() || PlayerUtil.isAttacking();
    }

    private boolean isMining() {
        return Mouse.isButtonDown(0) && mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && mc.objectMouseOver.getBlockPos() != null;
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        if (this.isEnabled()) {
            this.hitMap.clear();
            target = null;
            attackingEntity = null;
        }
    }

    @EventTarget(Priority.LOW)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }
        if (this.delayTicks > 0) {
            this.delayTicks--;
            return;
        }
        if (!this.basicCondition() || !this.settingCondition()) {
            this.setTarget(null);
            return;
        }
        this.handleTarget();
        if (target == null) {
            this.handleBlocking(false);
            return;
        }

        float yaw = event.getYaw();
        float pitch = event.getPitch();

        if (this.rotations.getValue() != 2 && this.isInAttackRange(target)) {
            float[] raw = this.getRawRotations(target, yaw, pitch);
            float[] smoothed = this.getRotationsSmoothed(raw, yaw, pitch);
            event.setRotation(smoothed[0], smoothed[1], 1);
            event.setPervRotation(smoothed[0], 1);
            if (this.rotations.getValue() == 1) {
                mc.thePlayer.rotationYaw = smoothed[0];
                mc.thePlayer.rotationPitch = smoothed[1];
                Myau.rotationManager.setRotation(smoothed[0], smoothed[1], 1, true);
            }
        }

        double distanceToBox = this.getDistanceToBoundingBox(target);
        boolean inBlockRange = distanceToBox <= this.blockRange.getValue();

        if (!this.autoBlockOverride() || !inBlockRange || !this.manualBlockDown()) {
            this.handleSwingAndAttack(distanceToBox, false, event.getNewYaw(), event.getNewPitch());
            if (Myau.blinkManager.isBlinking() || this.lag) {
                this.resetBlinkState();
            }
            this.interactTicks = 0;
            this.firstCycle = false;
        } else {
            this.handleAutoBlock(distanceToBox, false, event.getNewYaw(), event.getNewPitch());
        }

        if (inBlockRange) {
            if (this.manualBlockDown()) {
                this.handleBlocking(true);
            }
            if (this.manualBlock.getValue()) {
                this.handleBlocking(this.manualBlockDown());
            }
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        if (target != null && this.basicCondition() && this.settingCondition()) {
            long now = System.currentTimeMillis();
            if (now - this.lastAttackTime >= this.attackDelay) {
                this.lastAttackTime = now;
                this.attackDelay = (long) (1000.0 / this.aps.getValue()) + this.rng.nextInt(9) - 4;
                if (this.attackDelay < 1L) {
                    this.attackDelay = 1L;
                }
                this.shouldAttack = true;
            }
        } else {
            this.shouldAttack = false;
        }
    }

    @EventTarget
    public void onRenderBox(Render3DEvent event) {
        if (!this.isEnabled() || target == null || this.showTarget.getValue() == 0) {
            return;
        }
        if (!TeamUtil.isEntityLoaded(target) || !this.settingCondition()) {
            return;
        }
        java.awt.Color color;
        if (this.showTarget.getValue() == 1) {
            color = new java.awt.Color(target.hurtTime > 0 ? 16733525 : 5635925);
        } else {
            color = ((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis());
        }
        myau.util.RenderUtil.enableRenderState();
        myau.util.RenderUtil.drawEntityBox(target, color.getRed(), color.getGreen(), color.getBlue());
        myau.util.RenderUtil.disableRenderState();
    }

    private boolean isValidTarget(EntityLivingBase entity) {
        if (mc.theWorld == null || !mc.theWorld.loadedEntityList.contains(entity)) {
            return false;
        }
        if (entity == mc.thePlayer || entity == mc.thePlayer.ridingEntity
                || entity == mc.getRenderViewEntity() || entity == mc.getRenderViewEntity().ridingEntity) {
            return false;
        }
        if (entity.isDead || entity.deathTime > 0 || entity.getHealth() <= 0.0F) {
            return false;
        }
        if (entity.isInvisible() && !this.targetInvis.getValue()) {
            return false;
        }
        if (this.fov.getValue() != 360 && RotationUtil.angleToEntity(entity) > this.fov.getValue()) {
            return false;
        }
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            if (player.deathTime != 0) {
                return false;
            }
            if (TeamUtil.isFriend(player)) {
                return false;
            }
            if (TeamUtil.isBot(player)) {
                return false;
            }
            if (this.ignoreTeammates.getValue() && TeamUtil.isSameTeam(player)) {
                return false;
            }
            return true;
        }
        if (entity instanceof EntityCreature) {
            if (!this.attackMobs.getValue()) {
                return false;
            }
            EntityCreature creature = (EntityCreature) entity;
            if (creature.tasks == null || creature.isAIDisabled() || creature.deathTime != 0) {
                return false;
            }
            String canonical = entity.getClass().getCanonicalName();
            if (canonical == null || !canonical.startsWith("net.minecraft.entity.monster.")) {
                return false;
            }
            return this.isHostile(creature);
        }
        return false;
    }

    private boolean isHostile(EntityCreature creature) {
        if (creature instanceof EntityPigZombie) {
            return creature.getAttackTarget() == mc.thePlayer;
        }
        if (creature instanceof EntitySilverfish || creature instanceof EntityIronGolem) {
            return !(this.ignoreTeammates.getValue() && TeamUtil.hasTeamColor(creature));
        }
        return true;
    }

    private static class TargetData {
        final double distance;
        final float health;
        final int hurtTime;
        final double yawDelta;
        final int entityId;
        final boolean isEnemy;

        TargetData(double distance, float health, int hurtTime, double yawDelta, int entityId, boolean isEnemy) {
            this.distance = distance;
            this.health = health;
            this.hurtTime = hurtTime;
            this.yawDelta = yawDelta;
            this.entityId = entityId;
            this.isEnemy = isEnemy;
        }
    }

    private void handleTarget() {
        if (mc.theWorld == null) {
            this.setTarget(null);
            return;
        }
        List<EntityLivingBase> availableTargets = new ArrayList<>();
        double maxRange = this.getMaxRange();
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity instanceof EntityLivingBase && this.isValidTarget((EntityLivingBase) entity)
                    && mc.thePlayer.getDistanceToEntity(entity) < maxRange + maxRange / 3.0) {
                availableTargets.add((EntityLivingBase) entity);
            }
        }

        List<TargetData> toClassTargets = new ArrayList<>();
        for (EntityLivingBase candidate : availableTargets) {
            double distanceRayCasted = this.getDistanceToBoundingBox(candidate);
            if (distanceRayCasted > maxRange) {
                continue;
            }
            if (!this.hitThroughBlocks.getValue()
                    && (RotationUtil.rayTrace(candidate) != null || distanceRayCasted > this.attackRange.getValue())) {
                continue;
            }
            boolean enemy = candidate instanceof EntityPlayer && TeamUtil.isTarget((EntityPlayer) candidate);
            toClassTargets.add(new TargetData(
                    distanceRayCasted,
                    candidate.getHealth(),
                    candidate.hurtTime,
                    RotationUtil.angleToEntity(candidate),
                    candidate.getEntityId(),
                    enemy
            ));
        }

        if (this.prioritizeEnemies.getValue()) {
            List<TargetData> enemies = new ArrayList<>();
            for (TargetData data : toClassTargets) {
                if (data.isEnemy) {
                    enemies.add(data);
                }
            }
            if (!enemies.isEmpty()) {
                toClassTargets = enemies;
            }
        }

        Comparator<TargetData> comparator;
        switch (this.sort.getValue()) {
            case 1:
                comparator = Comparator.comparingDouble(data -> (double) data.health);
                break;
            case 2:
                comparator = Comparator.comparingDouble(data -> (double) data.hurtTime);
                break;
            case 3:
                comparator = Comparator.comparingDouble(data -> data.yawDelta);
                break;
            default:
                comparator = Comparator.comparingDouble(data -> data.distance);
        }
        if (this.sort.getValue() != 0) {
            toClassTargets.sort(Comparator.comparingDouble(data -> data.distance));
        }
        toClassTargets.sort(comparator);

        List<TargetData> attackTargets = new ArrayList<>();
        for (TargetData data : toClassTargets) {
            if (data.distance <= this.attackRange.getValue() - 0.005) {
                attackTargets.add(data);
            }
        }

        if (!attackTargets.isEmpty()) {
            int ticksExisted = mc.thePlayer.ticksExisted;
            int switchDelayTicks = Math.max(1, this.switchDelay.getValue() / 50);
            long noHitTicks = (long) Math.min(attackTargets.size(), this.targets.getValue()) * (long) switchDelayTicks;

            for (TargetData data : attackTargets) {
                Integer firstHit = this.hitMap.get(data.entityId);
                if (firstHit == null || ticksExisted - firstHit >= switchDelayTicks) {
                    continue;
                }
                if (data.distance <= this.attackRange.getValue() - 0.005) {
                    Entity entity = mc.theWorld.getEntityByID(data.entityId);
                    if (entity instanceof EntityLivingBase) {
                        this.setTarget((EntityLivingBase) entity);
                        return;
                    }
                }
            }

            for (TargetData data : attackTargets) {
                Integer firstHit = this.hitMap.get(data.entityId);
                if (firstHit == null || (long) ticksExisted >= (long) firstHit + noHitTicks) {
                    this.hitMap.put(data.entityId, ticksExisted);
                    Entity entity = mc.theWorld.getEntityByID(data.entityId);
                    if (entity instanceof EntityLivingBase) {
                        this.setTarget((EntityLivingBase) entity);
                        return;
                    }
                }
            }
            return;
        }

        if (!toClassTargets.isEmpty()) {
            Entity entity = mc.theWorld.getEntityByID(toClassTargets.get(0).entityId);
            if (entity instanceof EntityLivingBase) {
                this.setTarget((EntityLivingBase) entity);
                return;
            }
        }
        this.setTarget(null);
    }

    private void setTarget(EntityLivingBase entity) {
        if (entity == null) {
            if (target != null || this.blockingClient) {
                this.handleBlocking(false);
                if (this.blinkAutoBlock()) {
                    this.resetBlinkState();
                }
            }
            this.swapped = false;
            this.partialTicks = 0;
            this.interactTicks = 0;
            this.firstCycle = false;
            target = null;
            attackingEntity = null;
        } else {
            target = entity;
        }
    }

    public EntityLivingBase getTarget() {
        return target;
    }

    private double getDistanceToBoundingBox(Entity entity) {
        if (mc.thePlayer == null || entity == null) {
            return 0.0;
        }
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
        AxisAlignedBB box = entity.getEntityBoundingBox();
        double x = MathHelper_clamp(eye.xCoord, box.minX, box.maxX);
        double y = MathHelper_clamp(eye.yCoord, box.minY, box.maxY);
        double z = MathHelper_clamp(eye.zCoord, box.minZ, box.maxZ);
        return eye.distanceTo(new Vec3(x, y, z));
    }

    private static double MathHelper_clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }

    private boolean isInAttackRange(EntityLivingBase entity) {
        return this.getDistanceToBoundingBox(entity) <= this.attackRange.getValue() - 0.005;
    }

    private boolean isInSwingRange(EntityLivingBase entity) {
        return this.getDistanceToBoundingBox(entity) <= this.swingRange.getValue();
    }

    private float[] getRawRotations(EntityLivingBase entity, float yaw, float pitch) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 clamped = RotationUtil.clampVecToBox(eye, entity.getEntityBoundingBox());
        return RotationUtil.getRotationsTo(
                clamped.xCoord - eye.xCoord,
                clamped.yCoord - eye.yCoord,
                clamped.zCoord - eye.zCoord,
                yaw,
                pitch
        );
    }

    private float unwrapYaw(float target, float current) {
        return current + ((target - current + 180.0F) % 360.0F + 360.0F) % 360.0F - 180.0F;
    }

    private float[] getRotationsSmoothed(float[] rotations, float currentYaw, float currentPitch) {
        float targetYaw = this.unwrapYaw(rotations[0], currentYaw);
        float deltaYaw = targetYaw - currentYaw;
        float deltaPitch = rotations[1] - currentPitch;

        float yawSmoothing = this.smoothing.getValue();
        float pitchSmoothing = yawSmoothing;

        float strafe = mc.thePlayer.moveStrafing;
        if ((strafe < 0.0F && deltaYaw < 0.0F) || (strafe > 0.0F && deltaYaw > 0.0F)) {
            yawSmoothing = Math.max(1.0F, yawSmoothing / 2.0F);
        }
        float motionY = (float) mc.thePlayer.motionY;
        if ((motionY > 0.0F && deltaPitch > 0.0F) || (motionY < 0.0F && deltaPitch < 0.0F)) {
            pitchSmoothing = Math.max(1.0F, pitchSmoothing / 2.0F);
        }

        return new float[]{
                currentYaw + deltaYaw / Math.max(1.0F, yawSmoothing),
                currentPitch + deltaPitch / Math.max(1.0F, pitchSmoothing)
        };
    }

    private void handleSwingAndAttack(double distance, boolean swung, float yaw, float pitch) {
        if (target == null) {
            return;
        }
        boolean inAttackDistance = this.isInAttackRange(target);
        boolean disabledByBlock = this.disableWhileBlocking.getValue() && mc.thePlayer.isBlocking();

        if ((distance <= this.swingRange.getValue() || inAttackDistance) && this.shouldAttack && !swung) {
            if (!disabledByBlock) {
                this.swingItem();
            }
        }
        if (inAttackDistance) {
            attackingEntity = target;
            if (this.shouldAttack && !disabledByBlock) {
                this.shouldAttack = false;
                if (this.rotations.getValue() != 2
                        && RotationUtil.rayTrace(target.getEntityBoundingBox(), yaw, pitch, this.attackRange.getValue() - 0.005) == null) {
                    return;
                }
                AttackEvent attackEvent = new AttackEvent(target);
                EventManager.call(attackEvent);
                ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
                PacketUtil.sendPacket(new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));
                PlayerUtil.attackEntity(target);
            }
        } else {
            attackingEntity = null;
        }
    }

    private void swingItem() {
        if (this.silentSwing.getValue() && mc.thePlayer.isBlocking()) {
            PacketUtil.sendPacket(new net.minecraft.network.play.client.C0APacketAnimation());
        } else {
            mc.thePlayer.swingItem();
        }
    }

    private void handleAutoBlock(double distance, boolean swung, float yaw, float pitch) {
        int maxTicks = this.autoBlock.getValue() == 4
                ? Math.max(1, (int) (20.0 / this.aps.getValue()))
                : 3;
        if (this.interactTicks >= maxTicks) {
            this.interactTicks = 0;
        }
        this.interactTicks++;

        if (this.firstCycle) {
            switch (this.interactTicks) {
                case 1: {
                    Myau.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
                    int bestSlot = this.getBestSwapSlot();
                    if (bestSlot != -1) {
                        PacketUtil.sendPacket(new C09PacketHeldItemChange(bestSlot));
                        this.swapped = true;
                    }
                    this.lag = false;
                    break;
                }
                case 2: {
                    PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                    this.swapped = false;
                    this.handleSwingAndAttack(distance, swung, yaw, pitch);
                    this.interactAt(yaw, pitch);
                    break;
                }
                case 3: {
                    this.sendBlockPacket();
                    this.releasePackets();
                    this.firstCycle = false;
                    this.lag = true;
                    break;
                }
                default:
                    this.interactTicks = 0;
            }
        } else {
            switch (this.interactTicks) {
                case 1:
                    break;
                case 2: {
                    this.lag = false;
                    int bestSlot = this.getBestSwapSlot();
                    if (bestSlot != -1) {
                        Myau.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
                        PacketUtil.sendPacket(new C09PacketHeldItemChange(bestSlot));
                        this.swapped = true;
                    }
                    break;
                }
                case 3: {
                    PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                    this.swapped = false;
                    this.handleSwingAndAttack(distance, swung, yaw, pitch);
                    this.interactAt(yaw, pitch);
                    this.sendBlockPacket();
                    this.releasePackets();
                    this.lag = true;
                    break;
                }
                default:
                    this.interactTicks = 0;
            }
        }
    }

    private int getBestSwapSlot() {
        int currentSlot = mc.thePlayer.inventory.currentItem;
        for (int i = 0; i < 9; i++) {
            if (i != currentSlot) {
                net.minecraft.item.ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                if (stack != null && stack.getItem() instanceof ItemSword) {
                    return i;
                }
            }
        }
        for (int i = 0; i < 9; i++) {
            if (i != currentSlot && mc.thePlayer.inventory.getStackInSlot(i) == null) {
                return i;
            }
        }
        return -1;
    }

    private void interactAt(float yaw, float pitch) {
        if (attackingEntity == null) {
            return;
        }
        MovingObjectPosition mop = RotationUtil.rayTrace(attackingEntity.getEntityBoundingBox(), yaw, pitch, 10.0);
        if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && mop.entityHit == attackingEntity) {
            Vec3 hitVec = mop.hitVec;
            Vec3 relative = new Vec3(
                    hitVec.xCoord - attackingEntity.posX,
                    hitVec.yCoord - attackingEntity.posY,
                    hitVec.zCoord - attackingEntity.posZ
            );
            PacketUtil.sendPacket(new C02PacketUseEntity(attackingEntity, relative));
            PacketUtil.sendPacket(new C02PacketUseEntity(attackingEntity, C02PacketUseEntity.Action.INTERACT));
        }
    }

    private void sendBlockPacket() {
        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
        if (mc.thePlayer.getHeldItem() != null) {
            mc.thePlayer.setItemInUse(mc.thePlayer.getHeldItem(), mc.thePlayer.getHeldItem().getMaxItemUseDuration());
        }
        this.blockingClient = true;
    }

    private void releasePackets() {
        Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
        if (this.swapped) {
            PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
            this.swapped = false;
        }
        if (this.blockingClient && ItemUtil.isHoldingSword()) {
            this.sendDigPacket();
            this.blockingClient = false;
        }
        this.interactTicks = 0;
        this.firstCycle = false;
        this.lag = false;
    }

    private void resetBlinkState() {
        Myau.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
        if (this.swapped && mc.thePlayer != null) {
            PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
            this.swapped = false;
        }
        if (this.blockingClient && mc.thePlayer != null) {
            this.sendDigPacket();
            this.blockingClient = false;
        }
        this.interactTicks = 0;
        this.firstCycle = false;
        this.lag = false;
        this.partialDown = false;
        this.partialTicks = 0;
    }

    private void sendDigPacket() {
        if (!ItemUtil.isHoldingSword()) {
            return;
        }
        PacketUtil.sendPacket(new C07PacketPlayerDigging(
                C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
    }

    private void rightClick(boolean state) {
        int keyCode = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(keyCode, state);
        if (state) {
            KeyBinding.onTick(keyCode);
        }
    }

    private void handleBlocking(boolean blockState) {
        if (!ItemUtil.isHoldingSword()) {
            if (this.blockingClient) {
                this.blockingClient = false;
                if (this.autoBlock.getValue() == 1) {
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                } else {
                    mc.thePlayer.stopUsingItem();
                }
            }
            return;
        }
        if (this.blockingClient == blockState && this.autoBlock.getValue() != 3) {
            return;
        }
        int keyCode = mc.gameSettings.keyBindUseItem.getKeyCode();
        switch (this.autoBlock.getValue()) {
            case 0:
                break;
            case 1:
                KeyBinding.setKeyBindState(keyCode, blockState);
                this.blockingClient = blockState;
                break;
            case 2:
                this.blockingClient = blockState;
                if (blockState) {
                    net.minecraft.item.ItemStack held = mc.thePlayer.getHeldItem();
                    if (held != null) {
                        mc.thePlayer.setItemInUse(held, held.getMaxItemUseDuration());
                    }
                } else {
                    mc.thePlayer.stopUsingItem();
                }
                break;
            case 3:
                if (!blockState) {
                    this.rightClick(this.partialDown = false);
                    this.partialTicks = 0;
                    break;
                }
                this.partialTicks++;
                if (this.partialDown) {
                    this.rightClick(this.partialDown = false);
                    this.partialTicks = 0;
                } else if (this.partialTicks == 2) {
                    this.rightClick(this.partialDown = true);
                }
                break;
            default:
                break;
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.isCancelled() || mc.thePlayer == null) {
            return;
        }
        if (event.getPacket() instanceof C07PacketPlayerDigging) {
            C07PacketPlayerDigging packet = (C07PacketPlayerDigging) event.getPacket();
            if (packet.getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) {
                this.blockingClient = false;
            }
        }
        if (event.getPacket() instanceof C09PacketHeldItemChange && event.getType() == EventType.SEND) {
            if (this.blockingClient) {
                this.blockingClient = false;
                if (this.autoBlock.getValue() != 1) {
                    mc.thePlayer.stopUsingItem();
                }
            }
        }
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (this.isEnabled() && this.rotations.getValue() == 0
                && target != null && (ItemUtil.isHoldingSword() || ItemUtil.isHoldingTool())) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (this.isEnabled() && this.rotations.getValue() == 0
                && target != null && (ItemUtil.isHoldingSword() || ItemUtil.isHoldingTool())) {
            event.setCancelled(true);
        }
    }

    @Override
    public void verifyValue(String value) {
        if (this.attackRange.getName().equals(value) && this.swingRange.getValue() < this.attackRange.getValue()) {
            this.swingRange.setValue(this.attackRange.getValue());
        } else if (this.swingRange.getName().equals(value) && this.swingRange.getValue() < this.attackRange.getValue()) {
            this.attackRange.setValue(this.swingRange.getValue());
        }
    }
}
