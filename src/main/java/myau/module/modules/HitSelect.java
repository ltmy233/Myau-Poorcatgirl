package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.PacketEvent;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityLargeFireball;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.util.Vec3;

public class HitSelect extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"SECOND", "CRITICALS", "W_TAP"});
    
    private boolean sprintState = false;
    private boolean set = false;
    private double savedSlowdown = 0.0;
    
    private int blockedHits = 0;
    private int allowedHits = 0;

    public HitSelect() {
        super("HitSelect", false);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        
        if (event.getType() == EventType.POST) {
            this.resetMotion();
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.SEND || event.isCancelled()) {
            return;
        }

        if (event.getPacket() instanceof C0BPacketEntityAction) {
            C0BPacketEntityAction packet = (C0BPacketEntityAction) event.getPacket();
            switch (packet.getAction()) {
                case START_SPRINTING:
                    this.sprintState = true;
                    break;
                case STOP_SPRINTING:
                    this.sprintState = false;
                    break;
            }
            return;
        }

        if (event.getPacket() instanceof C02PacketUseEntity) {
            C02PacketUseEntity use = (C02PacketUseEntity) event.getPacket();
            
            if (use.getAction() != C02PacketUseEntity.Action.ATTACK) {
                return;
            }

            Entity target = use.getEntityFromWorld(mc.theWorld);
            if (target == null || target instanceof EntityLargeFireball) {
                return;
            }

            if (!(target instanceof EntityLivingBase)) {
                return;
            }

            EntityLivingBase living = (EntityLivingBase) target;
            boolean allow = true;

            switch (this.mode.getValue()) {
                case 0: // SECOND
                    allow = this.prioritizeSecondHit(mc.thePlayer, living);
                    break;
                case 1: // CRITICALS
                    allow = this.prioritizeCriticalHits(mc.thePlayer);
                    break;
                case 2: // WTAP
                    allow = this.prioritizeWTapHits(mc.thePlayer, this.sprintState);
                    break;
            }

            if (!allow) {
                event.setCancelled(true);
                this.blockedHits++;
            } else {
                this.allowedHits++;
            }
        }
    }

    private boolean prioritizeSecondHit(EntityLivingBase player, EntityLivingBase target) {
        // 对方已经在硬直了，这一下让它打出去
        if (target.hurtTime != 0) {
            return true;
        }

        // 自己还没从受击硬直里缓过来，也放行
        if (player.hurtTime <= player.maxHurtTime - 1) {
            return true;
        }

        // 贴得太近了，别拦
        double dist = player.getDistanceToEntity(target);
        if (dist < 2.5) {
            return true;
        }

        // 双方都不是朝着对方走的，说明这下不算，放行
        if (!this.isMovingTowards(target, player, 60.0)) {
            return true;
        }

        if (!this.isMovingTowards(player, target, 60.0)) {
            return true;
        }

        // 该拦了，顺手把位移修正回去
        this.fixMotion();
        return false;
    }

    private boolean prioritizeCriticalHits(EntityLivingBase player) {
        // 脚踩在地上，出不了暴击，放行
        if (player.onGround) {
            return true;
        }

        // 正在受击硬直中，放行
        if (player.hurtTime != 0) {
            return true;
        }

        // 正在下落，这是暴击的条件，放行
        if (player.fallDistance > 0.0f) {
            return true;
        }

        // 既不在下落也没硬直，拦下来并修正位移
        this.fixMotion();
        return false;
    }

    private boolean prioritizeWTapHits(EntityLivingBase player, boolean sprinting) {
        // 撞在墙上了，位移本来就出不去，放行
        if (player.isCollidedHorizontally) {
            return true;
        }

        // 前进键没按着，放行
        if (!mc.gameSettings.keyBindForward.isKeyDown()) {
            return true;
        }

        // 已经在疾跑了，这下不归我管，放行
        if (sprinting) {
            return true;
        }

        // 拦下来，顺便把位移修正回去
        this.fixMotion();
        return false;
    }

    private void fixMotion() {
        if (this.set) {
            return;
        }

        KeepSprint keepSprint = (KeepSprint) Myau.moduleManager.modules.get(KeepSprint.class);
        if (keepSprint == null) {
            return;
        }

        try {
            // 先把当前的减速值记下来，等会儿要还原
            this.savedSlowdown = keepSprint.slowdown.getValue().doubleValue();
            
            // 开启 KeepSprint 并把减速归零，这样被拦下也不会掉速
            if (!keepSprint.isEnabled()) {
                keepSprint.toggle();
            }
            keepSprint.slowdown.setValue(0);
            
            this.set = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void resetMotion() {
        if (!this.set) {
            return;
        }

        KeepSprint keepSprint = (KeepSprint) Myau.moduleManager.modules.get(KeepSprint.class);
        if (keepSprint == null) {
            return;
        }

        try {
            // 把减速值放回原来的
            keepSprint.slowdown.setValue((int) this.savedSlowdown);
            
            // 要是我们刚才顺手打开的 KeepSprint，现在关掉
            if (keepSprint.isEnabled()) {
                keepSprint.toggle();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        this.set = false;
        this.savedSlowdown = 0.0;
    }

    private boolean isMovingTowards(EntityLivingBase source, EntityLivingBase target, double maxAngle) {
        Vec3 currentPos = source.getPositionVector();
        Vec3 lastPos = new Vec3(source.lastTickPosX, source.lastTickPosY, source.lastTickPosZ);
        Vec3 targetPos = target.getPositionVector();

        // 用这一帧和上一帧的位置差，算出实际走的方向
        double mx = currentPos.xCoord - lastPos.xCoord;
        double mz = currentPos.zCoord - lastPos.zCoord;
        double movementLength = Math.sqrt(mx * mx + mz * mz);

        // 一动没动，谈不上朝哪走
        if (movementLength == 0.0) {
            return false;
        }

        // 归一化，后面只看方向不看快慢
        mx /= movementLength;
        mz /= movementLength;

        // 从自己指向对方的那个向量
        double tx = targetPos.xCoord - currentPos.xCoord;
        double tz = targetPos.zCoord - currentPos.zCoord;
        double targetLength = Math.sqrt(tx * tx + tz * tz);

        // 就站在同一个点上，没有夹角可算
        if (targetLength == 0.0) {
            return false;
        }

        tx /= targetLength;
        tz /= targetLength;

        // 点乘就是两个方向夹角的余弦
        double dotProduct = mx * tx + mz * tz;

        // 夹角没超过阈值，就算朝着对方走
        return dotProduct >= Math.cos(Math.toRadians(maxAngle));
    }

    @Override
    public void onDisabled() {
        this.resetMotion();
        this.sprintState = false;
        this.set = false;
        this.savedSlowdown = 0.0;
        this.blockedHits = 0;
        this.allowedHits = 0;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }
}