package myau.module.modules;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.LoadWorldEvent;
import myau.events.PacketEvent;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.mixin.IAccessorRenderManager;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.util.Vec3;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

public class Blink extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int FAKE_ID = -1337;
    private static final int MAX_TRAIL = 200;
    private static final int MAX_INBOUND = 4096;

    public final ModeProperty direction = new ModeProperty("direction", 0, new String[]{"OUTGOING", "BIDIRECTIONAL"});
    public final BooleanProperty spawnFake = new BooleanProperty("spawn-fake", true);
    public final IntProperty autoSend = new IntProperty("auto-send", 20, 0, 500);
    public final BooleanProperty breadcrumbs = new BooleanProperty("breadcrumbs", false);

    private final Deque<Packet<INetHandlerPlayClient>> pendingInbound = new ConcurrentLinkedDeque<>();
    private final ArrayDeque<Vec3> trail = new ArrayDeque<>();
    private boolean fakeSpawned;

    public Blink() {
        super("Blink", false);
    }

    @Override
    public void onEnabled() {
        this.trail.clear();
        this.flushInbound();
        Myau.blinkManager.setBlinkState(false, Myau.blinkManager.getBlinkingModule());
        Myau.blinkManager.setBlinkState(true, BlinkModules.BLINK);
        this.createFake();
    }

    @Override
    public void onDisabled() {
        Myau.blinkManager.setBlinkState(false, BlinkModules.BLINK);
        this.flushInbound();
        this.removeFake();
        this.trail.clear();
    }

    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.POST) {
            return;
        }
        if (!Myau.blinkManager.isBlinking() || Myau.blinkManager.getBlinkingModule() != BlinkModules.BLINK) {
            this.setEnabled(false);
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (this.direction.getValue() != 1) {
            this.flushInbound();
        }
        if (this.breadcrumbs.getValue().booleanValue()) {
            this.trail.addLast(new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ));
            while (this.trail.size() > MAX_TRAIL) {
                this.trail.removeFirst();
            }
        }
        int threshold = this.autoSend.getValue().intValue();
        if (threshold > 0 && Myau.blinkManager.countMovement() >= (long) threshold) {
            this.pulse();
        }
    }

    @SuppressWarnings("unchecked")
    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.RECEIVE || event.isCancelled()) {
            return;
        }
        if (this.direction.getValue() != 1 || mc.thePlayer == null) {
            return;
        }
        Packet<?> packet = event.getPacket();
        if (packet instanceof S14PacketEntity || packet instanceof S18PacketEntityTeleport) {
            this.pendingInbound.offer((Packet<INetHandlerPlayClient>) packet);
            event.setCancelled(true);
            if (this.pendingInbound.size() > MAX_INBOUND) {
                this.flushInbound();
            }
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled() || !this.breadcrumbs.getValue().booleanValue() || this.trail.size() < 2) {
            return;
        }
        IAccessorRenderManager renderManager = (IAccessorRenderManager) mc.getRenderManager();
        double renderX = renderManager.getRenderPosX();
        double renderY = renderManager.getRenderPosY();
        double renderZ = renderManager.getRenderPosZ();
        HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);
        Color color = hud != null ? hud.getColor(System.currentTimeMillis()) : new Color(255, 255, 255);
        float red = (float) color.getRed() / 255.0F;
        float green = (float) color.getGreen() / 255.0F;
        float blue = (float) color.getBlue() / 255.0F;
        RenderUtil.enableRenderState();
        Vec3 previous = null;
        for (Vec3 point : this.trail) {
            if (previous != null) {
                RenderUtil.drawLine3D(
                        new Vec3(previous.xCoord - renderX, previous.yCoord - renderY, previous.zCoord - renderZ),
                        point.xCoord,
                        point.yCoord,
                        point.zCoord,
                        red,
                        green,
                        blue,
                        0.85F,
                        2.0F
                );
            }
            previous = point;
        }
        RenderUtil.disableRenderState();
    }

    @EventTarget
    public void onWorldLoad(LoadWorldEvent event) {
        this.fakeSpawned = false;
        this.pendingInbound.clear();
        this.trail.clear();
        this.setEnabled(false);
    }

    private void pulse() {
        Myau.blinkManager.setBlinkState(false, BlinkModules.BLINK);
        Myau.blinkManager.setBlinkState(true, BlinkModules.BLINK);
        this.flushInbound();
        this.trail.clear();
        this.removeFake();
        this.createFake();
    }

    private void createFake() {
        if (!this.spawnFake.getValue().booleanValue() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        this.removeFake();
        EntityOtherPlayerMP fake = new EntityOtherPlayerMP(mc.theWorld, mc.thePlayer.getGameProfile());
        fake.setPositionAndRotation(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ,
                mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        fake.prevPosX = mc.thePlayer.posX;
        fake.prevPosY = mc.thePlayer.posY;
        fake.prevPosZ = mc.thePlayer.posZ;
        fake.lastTickPosX = mc.thePlayer.posX;
        fake.lastTickPosY = mc.thePlayer.posY;
        fake.lastTickPosZ = mc.thePlayer.posZ;
        fake.prevRotationYaw = mc.thePlayer.rotationYaw;
        fake.prevRotationPitch = mc.thePlayer.rotationPitch;
        fake.setSneaking(mc.thePlayer.isSneaking());
        fake.setSprinting(mc.thePlayer.isSprinting());
        fake.inventory.copyInventory(mc.thePlayer.inventory);
        fake.setHealth(Math.min(mc.thePlayer.getHealth(), fake.getMaxHealth()));
        ((WorldClient) mc.theWorld).addEntityToWorld(FAKE_ID, fake);
        this.fakeSpawned = true;
    }

    private void removeFake() {
        if (this.fakeSpawned && mc.theWorld != null) {
            ((WorldClient) mc.theWorld).removeEntityFromWorld(FAKE_ID);
        }
        this.fakeSpawned = false;
    }

    private void flushInbound() {
        if (this.pendingInbound.isEmpty() || mc.getNetHandler() == null) {
            this.pendingInbound.clear();
            return;
        }
        while (true) {
            Packet<INetHandlerPlayClient> packet = this.pendingInbound.poll();
            if (packet == null) {
                this.pendingInbound.clear();
                break;
            }
            packet.processPacket(mc.getNetHandler());
        }
    }
}
