package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.LoadWorldEvent;
import myau.events.PacketEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C03PacketPlayer.C05PacketPlayerLook;
import net.minecraft.network.play.client.C03PacketPlayer.C06PacketPlayerPosLook;
import net.minecraft.util.MathHelper;

import java.util.Random;

public class KBDisplacer extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Random rnd = new Random();

    public final FloatProperty angle = new FloatProperty("angle", 90.0F, -180.0F, 180.0F);
    public final FloatProperty random = new FloatProperty("random", 0.0F, 0.0F, 180.0F);
    public final BooleanProperty sprintOnly = new BooleanProperty("sprint-only", true);

    private boolean pendingRestore;
    private boolean rotationKnown;
    private float serverYaw;
    private float serverPitch;
    private int lastAttackTick = Integer.MIN_VALUE;

    public KBDisplacer() {
        super("KBDisplacer", false);
    }

    @EventTarget(Priority.LOWEST)
    public void onPacket(PacketEvent event) {
        if (event.isCancelled() || event.getType() != EventType.SEND
                || mc.thePlayer == null || mc.theWorld == null || mc.getNetHandler() == null) return;

        if (event.getPacket() instanceof C03PacketPlayer) {
            C03PacketPlayer movement = (C03PacketPlayer) event.getPacket();
            if (movement.getRotating()) {
                serverYaw = movement.getYaw();
                serverPitch = movement.getPitch();
                rotationKnown = true;
                pendingRestore = false;
            } else if (pendingRestore && rotationKnown) {
                // 顶掉一个普通移动包就行，还原本身不多发包。
                event.setCancelled(true);
                C03PacketPlayer restored = movement.isMoving()
                        ? new C06PacketPlayerPosLook(movement.getPositionX(), movement.getPositionY(),
                        movement.getPositionZ(), serverYaw, serverPitch, movement.isOnGround())
                        : new C05PacketPlayerLook(serverYaw, serverPitch, movement.isOnGround());
                PacketUtil.sendPacketNoEvent(restored);
                pendingRestore = false;
            }
            return;
        }

        if (!isEnabled() || pendingRestore || !rotationKnown || mc.thePlayer.isDead
                || !(event.getPacket() instanceof C02PacketUseEntity)) return;
        C02PacketUseEntity attack = (C02PacketUseEntity) event.getPacket();
        if (attack.getAction() != C02PacketUseEntity.Action.ATTACK
                || (sprintOnly.getValue() && !mc.thePlayer.isSprinting())
                || lastAttackTick == mc.thePlayer.ticksExisted) return;
        Entity target = attack.getEntityFromWorld(mc.theWorld);
        if (target == null || target == mc.thePlayer || target.isDead) return;

        float displacement = angle.getValue();
        float spread = random.getValue();
        if (!Float.isFinite(displacement) || !Float.isFinite(spread)) return;
        if (spread > 0.0F) displacement += (rnd.nextFloat() * 2.0F - 1.0F) * spread;
        displacement = MathHelper.wrapAngleTo180_float(displacement);
        if (Math.abs(displacement) < 0.001F) return;

        PacketUtil.sendPacketNoEvent(new C05PacketPlayerLook(
                MathHelper.wrapAngleTo180_float(serverYaw + displacement), serverPitch,
                mc.thePlayer.onGround));
        pendingRestore = true;
        lastAttackTick = mc.thePlayer.ticksExisted;
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        pendingRestore = false;
        rotationKnown = false;
        lastAttackTick = Integer.MIN_VALUE;
    }

    @Override
    public void onDisabled() {
        // 待恢复的位置由下一次正常移动顺带补上，模块关了也一样
        lastAttackTick = Integer.MIN_VALUE;
    }
}
