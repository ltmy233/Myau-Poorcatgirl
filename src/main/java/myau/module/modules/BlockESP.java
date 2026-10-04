package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.LoadWorldEvent;
import myau.events.PacketEvent;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.mixin.IAccessorRenderManager;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.TextProperty;
import myau.util.BlockESPCache;
import myau.util.BlockShapeRenderer;
import myau.util.RenderUtil;
import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S26PacketMapChunkBulk;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BlockESP extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final TextProperty blockList = new TextProperty("blocks", "");
    public final BooleanProperty outline = new BooleanProperty("outline", true);
    public final BooleanProperty shade = new BooleanProperty("shade", false);
    public final FloatProperty range = new FloatProperty("range", 64.0F, 8.0F, 256.0F);
    public final IntProperty maxRenders = new IntProperty("max-renders", 2048, 128, 8192);
    public final IntProperty scanSpeed = new IntProperty("scan-speed", 8, 1, 32);

    private final List<String> blocks = new ArrayList<>();
    private BlockESPCache cache;
    private int prevListHash;

    public BlockESP() {
        super("BlockESP", false);
    }

    @Override
    public void onEnabled() {
        this.blocks.clear();
        this.blocks.addAll(BlockESPCache.parseBlockList(this.blockList.getValue()));
        this.prevListHash = this.blockList.getValue().hashCode();
        this.cache = new BlockESPCache(this.blocks);
        if (!this.blocks.isEmpty()) {
            this.cache.enqueueLoadedChunks();
        }
    }

    @Override
    public void onDisabled() {
        if (this.cache != null) {
            this.cache.clear();
            this.cache = null;
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{String.valueOf(this.cache == null ? 0 : this.cache.totalCached())};
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.cache == null || event.getType() != EventType.POST) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        int hash = this.blockList.getValue().hashCode();
        if (hash != this.prevListHash) {
            this.prevListHash = hash;
            this.blocks.clear();
            this.blocks.addAll(BlockESPCache.parseBlockList(this.blockList.getValue()));
            this.cache.onSettingsChanged();
        }
        this.cache.tickScan(this.scanSpeed.getValue());
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (this.cache == null || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        double rangeValue = this.range.getValue();
        double rangeSq = rangeValue * rangeValue;
        double px = mc.thePlayer.posX;
        double py = mc.thePlayer.posY;
        double pz = mc.thePlayer.posZ;
        boolean doOutline = this.outline.getValue();
        boolean doShade = this.shade.getValue();
        int max = this.maxRenders.getValue();
        int rendered = 0;

            for (Map.Entry<Long, Set<BlockPos>> entry : this.cache.entries()) {
                if (rendered >= max) {
                    break;
                }
                for (BlockPos pos : entry.getValue()) {
                    if (rendered >= max) {
                        break;
                    }
                    double dx = pos.getX() + 0.5 - px;
                    double dy = pos.getY() + 0.5 - py;
                    double dz = pos.getZ() + 0.5 - pz;
                    if (dx * dx + dy * dy + dz * dz > rangeSq) {
                        continue;
                    }
                    AxisAlignedBB box = BlockShapeRenderer.getBlockSelectionBox(pos);
                    if (box == null || !BlockShapeRenderer.isInViewFrustum(box)) {
                        continue;
                    }
                    EnumSet<EnumFacing> visibleFaces = EnumSet.noneOf(EnumFacing.class);
                    for (EnumFacing facing : EnumFacing.VALUES) {
                        if (!this.cache.contains(pos.offset(facing))) {
                            visibleFaces.add(facing);
                        }
                    }
                    if (visibleFaces.isEmpty()) {
                        continue;
                    }
                    IBlockState state = mc.theWorld.getBlockState(pos);
                    MapColor mapColor = state.getBlock().getMapColor(state);
                    int rgb = mapColor != null ? (0xFF << 24 | mapColor.colorValue) : 0xFFFF0000;
                    BlockShapeRenderer.renderBlockShape(pos, state, rgb, doOutline, doShade, visibleFaces);
                    rendered++;
                }
            }
    }

    private static void emitGradientVertex(double vx, double vy, double minX, double width, int colorA, int colorB) {
        float t = (float) ((vx - minX) / Math.max(width, 0.0001D));
        if (t < 0.0F) {
            t = 0.0F;
        } else if (t > 1.0F) {
            t = 1.0F;
        }
        float ra = (colorA >> 16 & 255);
        float ga = (colorA >> 8 & 255);
        float ba = (colorA & 255);
        float aa = (colorA >>> 24);
        float rb = (colorB >> 16 & 255);
        float gb = (colorB >> 8 & 255);
        float bb = (colorB & 255);
        float ab = (colorB >>> 24);
        GL11.glColor4f((ra + (rb - ra) * t) / 255.0F, (ga + (gb - ga) * t) / 255.0F, (ba + (bb - ba) * t) / 255.0F, (aa + (ab - aa) * t) / 255.0F);
        GL11.glVertex2d(vx, vy);
    }

    private static void drawGradientOutline(double x, double y, double width, double height, double radius, float lineWidth, int colorA, int colorB) {
        if (width <= 0.0D || height <= 0.0D || (colorA >>> 24) == 0) {
            return;
        }
        radius = Math.max(0.0D, Math.min(radius, Math.min(width, height) / 2.0D));
        if (radius <= 0.0D) {
            return;
        }
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GL11.glLineWidth(lineWidth);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 180; i <= 270; i += 3) {
            double rad = Math.toRadians(i);
            double vx = x + radius + Math.cos(rad) * radius;
            double vy = y + radius + Math.sin(rad) * radius;
            emitGradientVertex(vx, vy, x, width, colorA, colorB);
        }
        for (int i = 270; i <= 360; i += 3) {
            double rad = Math.toRadians(i);
            double vx = x + width - radius + Math.cos(rad) * radius;
            double vy = y + radius + Math.sin(rad) * radius;
            emitGradientVertex(vx, vy, x, width, colorA, colorB);
        }
        for (int i = 0; i <= 90; i += 3) {
            double rad = Math.toRadians(i);
            double vx = x + width - radius + Math.cos(rad) * radius;
            double vy = y + height - radius + Math.sin(rad) * radius;
            emitGradientVertex(vx, vy, x, width, colorA, colorB);
        }
        for (int i = 90; i <= 180; i += 3) {
            double rad = Math.toRadians(i);
            double vx = x + radius + Math.cos(rad) * radius;
            double vy = y + height - radius + Math.sin(rad) * radius;
            emitGradientVertex(vx, vy, x, width, colorA, colorB);
        }
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(1.0F);
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        if (this.cache == null) {
            return;
        }
        this.cache.clear();
        this.cache.enqueueLoadedChunks();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (this.cache == null || event.getType() != EventType.RECEIVE) {
            return;
        }
        Object packet = event.getPacket();
        if (packet instanceof S23PacketBlockChange) {
            S23PacketBlockChange blockChange = (S23PacketBlockChange) packet;
            this.cache.onBlockChange(blockChange.getBlockPosition(), blockChange.getBlockState());
        } else if (packet instanceof S22PacketMultiBlockChange) {
            S22PacketMultiBlockChange multiBlockChange = (S22PacketMultiBlockChange) packet;
            for (S22PacketMultiBlockChange.BlockUpdateData data : multiBlockChange.getChangedBlocks()) {
                this.cache.onBlockChange(data.getPos(), data.getBlockState());
            }
        } else if (packet instanceof S21PacketChunkData) {
            S21PacketChunkData chunkData = (S21PacketChunkData) packet;
            if (chunkData.getExtractedSize() == 0) {
                this.cache.removeChunk(chunkData.getChunkX(), chunkData.getChunkZ());
            } else {
                this.cache.enqueueChunk(chunkData.getChunkX(), chunkData.getChunkZ());
            }
        } else if (packet instanceof S26PacketMapChunkBulk) {
            S26PacketMapChunkBulk chunkBulk = (S26PacketMapChunkBulk) packet;
            for (int i = 0; i < chunkBulk.getChunkCount(); i++) {
                this.cache.enqueueChunk(chunkBulk.getChunkX(i), chunkBulk.getChunkZ(i));
            }
        }
    }
}
