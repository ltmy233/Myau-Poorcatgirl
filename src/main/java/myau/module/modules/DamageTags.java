package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.Render3DEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.DataWatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public class DamageTags extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final String[] DEPTH_MODES = { "Vanilla", "Visible", "Through walls" };
    private static final int HEALTH_WATCHER_ID = 6;
    private static final int ABSORPTION_WATCHER_ID = 17;
    private static final double MAX_RENDER_DISTANCE_SQ = 4096.0D;
    private static final double RISE_DISTANCE = 1.0D;
    private static final double FRUSTUM_AABB_EXPAND = 0.35D;
    private static final double STACK_RADIUS_SQ = 0.36D;
    private static final float HIDDEN_TEXT_ALPHA = 32.0F / 255.0F;
    private static final float FADE_START = 0.72F;
    private static final int MIN_FONT_ALPHA = 4;

    public final IntProperty duration = new IntProperty("duration", 1200, 0, 2000);
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 3.0F);
    public final ModeProperty depthMode = new ModeProperty("depth", 0, DEPTH_MODES);
    public final BooleanProperty textShadow = new BooleanProperty("text-shadow", false);
    public final BooleanProperty background = new BooleanProperty("background", true);
    public final PercentProperty backgroundOpacity = new PercentProperty("background-opacity", 25, () -> this.background.getValue());

    private final List<DamageTag> activeTags = new ArrayList<DamageTag>();
    private final Map<Integer, HealthSnapshot> trackedStates = new HashMap<Integer, HealthSnapshot>();
    private final ConcurrentLinkedQueue<QueuedMetadataUpdate> pendingUpdates = new ConcurrentLinkedQueue<QueuedMetadataUpdate>();
    private World lastWorld;

    private static class QueuedMetadataUpdate {
        private final int entityId;
        private final boolean hasHealth;
        private final float health;
        private final boolean hasAbsorption;
        private final float absorption;

        private QueuedMetadataUpdate(int entityId, boolean hasHealth, float health, boolean hasAbsorption, float absorption) {
            this.entityId = entityId;
            this.hasHealth = hasHealth;
            this.health = health;
            this.hasAbsorption = hasAbsorption;
            this.absorption = absorption;
        }
    }

    private static class HealthSnapshot {
        private final float health;
        private final float absorption;

        private HealthSnapshot(float health, float absorption) {
            this.health = health;
            this.absorption = absorption;
        }

        private float total() {
            return this.health + this.absorption;
        }
    }

    private static class DamageTag {
        private final String text;
        private final int color;
        private final double x;
        private final double y;
        private final double z;
        private final long createdAt;
        private final long durationMs;
        private final AxisAlignedBB frustumAabb;
        private final int textHalfWidth;

        private DamageTag(String text, int color, double x, double y, double z, long createdAt, long durationMs, int textHalfWidth) {
            this.text = text;
            this.color = color;
            this.x = x;
            this.y = y;
            this.z = z;
            this.createdAt = createdAt;
            this.durationMs = durationMs;
            this.textHalfWidth = textHalfWidth;
            double e = FRUSTUM_AABB_EXPAND;
            this.frustumAabb = new AxisAlignedBB(x - e, y - e, z - e, x + e, y + RISE_DISTANCE + e, z + e);
        }
    }

    public DamageTags() {
        super("DamageTags", false);
    }

    @Override
    public void onEnabled() {
        resetState();
        this.lastWorld = mc.theWorld;
    }

    @Override
    public void onDisabled() {
        resetState();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        if (event.getType() != EventType.RECEIVE) {
            return;
        }
        if (!(event.getPacket() instanceof S1CPacketEntityMetadata) || mc.theWorld == null) {
            return;
        }

        S1CPacketEntityMetadata packet = (S1CPacketEntityMetadata) event.getPacket();
        List<DataWatcher.WatchableObject> watchedObjects = packet.func_149376_c();
        if (watchedObjects == null || watchedObjects.isEmpty()) {
            return;
        }

        boolean hasHealth = false;
        float health = 0.0F;
        boolean hasAbsorption = false;
        float absorption = 0.0F;

        for (DataWatcher.WatchableObject watchedObject : watchedObjects) {
            if (watchedObject == null || watchedObject.getObject() == null) {
                continue;
            }

            int dataValueId = watchedObject.getDataValueId();
            if (dataValueId == HEALTH_WATCHER_ID && watchedObject.getObject() instanceof Float) {
                hasHealth = true;
                health = (Float) watchedObject.getObject();
            } else if (dataValueId == ABSORPTION_WATCHER_ID && watchedObject.getObject() instanceof Float) {
                hasAbsorption = true;
                absorption = (Float) watchedObject.getObject();
            }
        }

        if (!hasHealth && !hasAbsorption) {
            return;
        }

        this.pendingUpdates.offer(new QueuedMetadataUpdate(packet.getEntityId(), hasHealth, health, hasAbsorption, absorption));
    }

    @EventTarget
    public void onRenderWorld(Render3DEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        if (mc.theWorld != this.lastWorld) {
            resetState();
            this.lastWorld = mc.theWorld;
        }

        long now = System.currentTimeMillis();
        flushPendingUpdates(event.getPartialTicks(), now);
        updateTrackedStates();
        pruneExpiredTags(now);

        if (this.activeTags.isEmpty()) {
            return;
        }

        FontRenderer fontRenderer = mc.fontRendererObj;
        RenderManager renderManager = mc.getRenderManager();
        if (fontRenderer == null || renderManager == null) {
            return;
        }

        double viewerX = renderManager.viewerPosX;
        double viewerY = renderManager.viewerPosY;
        double viewerZ = renderManager.viewerPosZ;

        int depthOrdinal = this.depthMode.getValue();
        float scaleMul = this.scale.getValue();
        boolean bgEnabled = this.background.getValue();
        float bgOpacitySlider = this.backgroundOpacity.getValue() / 100.0F;

        GlStateManager.pushAttrib();
        try {
            for (DamageTag tag : this.activeTags) {
                double dx = tag.x - viewerX;
                double dy = tag.y - viewerY;
                double dz = tag.z - viewerZ;
                if (dx * dx + dy * dy + dz * dz > MAX_RENDER_DISTANCE_SQ) {
                    continue;
                }
                if (!RenderUtil.isInViewFrustum(tag.frustumAabb, 0.0D)) {
                    continue;
                }
                renderTag(tag, now, renderManager, fontRenderer, viewerX, viewerY, viewerZ, depthOrdinal, scaleMul, bgEnabled, bgOpacitySlider);
            }
        } finally {
            GlStateManager.popAttrib();
        }
    }

    private void flushPendingUpdates(float partialTicks, long nowMillis) {
        if (mc.theWorld == null) {
            this.pendingUpdates.clear();
            return;
        }

        QueuedMetadataUpdate update;
        while ((update = this.pendingUpdates.poll()) != null) {
            Entity entity = mc.theWorld.getEntityByID(update.entityId);
            if (!(entity instanceof EntityLivingBase) || entity instanceof EntityArmorStand || entity == mc.thePlayer) {
                this.trackedStates.remove(update.entityId);
                continue;
            }

            EntityLivingBase living = (EntityLivingBase) entity;
            HealthSnapshot previous = this.trackedStates.get(update.entityId);

            float newHealth = update.hasHealth ? update.health : getHealth(living);
            float newAbsorption = update.hasAbsorption ? update.absorption : getAbsorption(living);

            HealthSnapshot current = new HealthSnapshot(newHealth, newAbsorption);
            if (previous == null) {
                this.trackedStates.put(update.entityId, current);
                continue;
            }

            float delta = current.total() - previous.total();
            this.trackedStates.put(update.entityId, current);

            if (Math.abs(delta) <= 0.01F) {
                continue;
            }

            spawnTag(living, delta, partialTicks, nowMillis);
        }
    }

    private void updateTrackedStates() {
        if (mc.theWorld == null) {
            this.trackedStates.clear();
            return;
        }

        this.trackedStates.entrySet().removeIf(entry -> {
            Entity entity = mc.theWorld.getEntityByID(entry.getKey());
            return !(entity instanceof EntityLivingBase) || entity instanceof EntityArmorStand || entity == mc.thePlayer;
        });

        List<Entity> loaded = mc.theWorld.loadedEntityList;
        for (int i = 0, n = loaded.size(); i < n; i++) {
            Entity entity = loaded.get(i);
            if (!(entity instanceof EntityLivingBase) || entity instanceof EntityArmorStand || entity == mc.thePlayer) {
                continue;
            }

            EntityLivingBase living = (EntityLivingBase) entity;
            this.trackedStates.put(entity.getEntityId(), new HealthSnapshot(getHealth(living), getAbsorption(living)));
        }
    }

    private void pruneExpiredTags(long now) {
        Iterator<DamageTag> iterator = this.activeTags.iterator();
        while (iterator.hasNext()) {
            DamageTag tag = iterator.next();
            if (now - tag.createdAt >= tag.durationMs) {
                iterator.remove();
            }
        }
    }

    private void spawnTag(EntityLivingBase living, float delta, float partialTicks, long nowMillis) {
        double x = interpolate(living.lastTickPosX, living.posX, partialTicks);
        double y = interpolate(living.lastTickPosY, living.posY, partialTicks) + living.height + 0.5D;
        double z = interpolate(living.lastTickPosZ, living.posZ, partialTicks);
        y += getStackOffset(x, y, z, nowMillis);

        long durationMs = Math.max(1L, this.duration.getValue().longValue());
        String text = (delta > 0.0F ? "+" : "-") + fastOneDecimal(Math.abs(delta));
        int color = delta > 0.0F ? 0xFF55FF55 : 0xFFFF5555;
        FontRenderer fr = mc.fontRendererObj;
        int halfW = fr != null ? fr.getStringWidth(text) >> 1 : 0;

        this.activeTags.add(new DamageTag(text, color, x, y, z, nowMillis, durationMs, halfW));
        if (this.activeTags.size() > 256) {
            this.activeTags.remove(0);
        }
    }

    private void renderTag(DamageTag tag, long now, RenderManager renderManager, FontRenderer fontRenderer,
                           double viewerX, double viewerY, double viewerZ,
                           int depthOrdinal, float scaleMul,
                           boolean bgEnabled, float bgOpacitySlider) {
        float progress = getProgress(tag, now);
        if (progress >= 1.0F) {
            return;
        }

        float rise = expoOut(progress);
        float alpha = getAlpha(progress);
        if (alpha <= 0.0F) {
            return;
        }

        double x = tag.x - viewerX;
        double y = tag.y + rise * RISE_DISTANCE - viewerY;
        double z = tag.z - viewerZ;
        float renderScale = 0.02666667F * scaleMul;
        int halfWidth = tag.textHalfWidth;

        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y, (float) z);
        GL11.glNormal3f(0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(renderManager.playerViewX * (mc.gameSettings.thirdPersonView == 2 ? -1 : 1), 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-renderScale, -renderScale, renderScale);

        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

        switch (depthOrdinal) {
            case 1:
                renderVisibleTag(fontRenderer, tag, halfWidth, alpha, bgEnabled, bgOpacitySlider);
                break;
            case 2:
                renderThroughWallsTag(fontRenderer, tag, halfWidth, alpha, bgEnabled, bgOpacitySlider);
                break;
            default:
                renderVanillaDepthTag(fontRenderer, tag, halfWidth, alpha, bgEnabled, bgOpacitySlider);
                break;
        }

        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    private void renderVanillaDepthTag(FontRenderer fontRenderer, DamageTag tag, int halfWidth, float alpha,
                                       boolean bgEnabled, float bgOpacitySlider) {
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        drawBackground(halfWidth, alpha, bgEnabled, bgOpacitySlider);
        fontRenderer.drawString(tag.text, -halfWidth, 0, applyFontAlpha(tag.color, HIDDEN_TEXT_ALPHA * alpha), false);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        fontRenderer.drawString(tag.text, -halfWidth, 0, applyFontAlpha(tag.color, alpha), this.textShadow.getValue());
    }

    private void renderVisibleTag(FontRenderer fontRenderer, DamageTag tag, int halfWidth, float alpha,
                                  boolean bgEnabled, float bgOpacitySlider) {
        GlStateManager.depthMask(false);
        GlStateManager.enableDepth();
        drawBackground(halfWidth, alpha, bgEnabled, bgOpacitySlider);
        fontRenderer.drawString(tag.text, -halfWidth, 0, applyFontAlpha(tag.color, alpha), this.textShadow.getValue());
    }

    private void renderThroughWallsTag(FontRenderer fontRenderer, DamageTag tag, int halfWidth, float alpha,
                                       boolean bgEnabled, float bgOpacitySlider) {
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        drawBackground(halfWidth, alpha, bgEnabled, bgOpacitySlider);
        fontRenderer.drawString(tag.text, -halfWidth, 0, applyFontAlpha(tag.color, alpha), this.textShadow.getValue());
    }

    private void drawBackground(int halfWidth, float alpha, boolean bgEnabled, float bgOpacitySlider) {
        if (!bgEnabled) {
            return;
        }

        float bgAlpha = bgOpacitySlider * alpha;
        if (bgAlpha <= 0.0F) {
            return;
        }

        GlStateManager.disableTexture2D();
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();
        worldRenderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        worldRenderer.pos(-halfWidth - 1, -1, 0.0D).color(0.0F, 0.0F, 0.0F, bgAlpha).endVertex();
        worldRenderer.pos(-halfWidth - 1, 8, 0.0D).color(0.0F, 0.0F, 0.0F, bgAlpha).endVertex();
        worldRenderer.pos(halfWidth + 1, 8, 0.0D).color(0.0F, 0.0F, 0.0F, bgAlpha).endVertex();
        worldRenderer.pos(halfWidth + 1, -1, 0.0D).color(0.0F, 0.0F, 0.0F, bgAlpha).endVertex();
        tessellator.draw();
        GlStateManager.enableTexture2D();
    }

    private float getProgress(DamageTag tag, long now) {
        return MathHelper.clamp_float((float) (now - tag.createdAt) / (float) tag.durationMs, 0.0F, 1.0F);
    }

    private float getAlpha(float progress) {
        if (progress <= FADE_START) {
            return 1.0F;
        }

        float fadeProgress = MathHelper.clamp_float((progress - FADE_START) / (1.0F - FADE_START), 0.0F, 1.0F);
        return 1.0F - fadeProgress;
    }

    private double getStackOffset(double x, double y, double z, long nowMillis) {
        int overlaps = 0;
        for (DamageTag tag : this.activeTags) {
            if (nowMillis - tag.createdAt >= tag.durationMs) {
                continue;
            }

            double dx = tag.x - x;
            double dz = tag.z - z;
            if ((dx * dx + dz * dz) <= STACK_RADIUS_SQ && Math.abs(tag.y - y) <= 1.5D) {
                overlaps++;
            }
        }
        return overlaps * 0.15D;
    }

    private float getHealth(EntityLivingBase living) {
        return Math.max(0.0F, living.getHealth());
    }

    private float getAbsorption(EntityLivingBase living) {
        if (living instanceof EntityPlayer) {
            return Math.max(0.0F, ((EntityPlayer) living).getAbsorptionAmount());
        }
        return 0.0F;
    }

    private double interpolate(double start, double end, float partialTicks) {
        return start + (end - start) * partialTicks;
    }

    private int applyFontAlpha(int color, float alpha) {
        int alphaChannel = MathHelper.clamp_int(Math.round(alpha * 255.0F), 0, 255);
        if (alpha > 0.0F && alphaChannel < MIN_FONT_ALPHA) {
            alphaChannel = MIN_FONT_ALPHA;
        }
        return (alphaChannel << 24) | (color & 0x00FFFFFF);
    }

    private String fastOneDecimal(float value) {
        int tenths = Math.round(value * 10.0F);
        int intPart = tenths / 10;
        int fracPart = Math.abs(tenths % 10);
        if (fracPart == 0) {
            return String.valueOf(intPart);
        }
        return intPart + "." + fracPart;
    }

    private float expoOut(float t) {
        if (t <= 0.0F) {
            return 0.0F;
        }
        if (t >= 1.0F) {
            return 1.0F;
        }
        return 1.0F - (float) Math.pow(2.0D, -10.0D * t);
    }

    private void resetState() {
        this.activeTags.clear();
        this.trackedStates.clear();
        this.pendingUpdates.clear();
    }
}
