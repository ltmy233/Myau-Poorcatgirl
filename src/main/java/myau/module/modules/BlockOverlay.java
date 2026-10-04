package myau.module.modules;

import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ColorProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.ModeProperty;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDeadBush;
import net.minecraft.block.BlockDoublePlant;
import net.minecraft.block.BlockFlower;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockTallGrass;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

public class BlockOverlay extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final String[] RENDER_MODES = {"Hidden", "Vanilla", "Side", "Full"};
    private static final String[] COLOR_MODES = {"Static", "Gradient", "Fade", "Chroma"};
    private static final double PADDING = 0.002;
    private static final int OVERLAY_ALPHA = 100;
    private static final int OUTLINE_ALPHA = 255;

    public final ModeProperty renderMode = new ModeProperty("mode", 2, RENDER_MODES);
    public final BooleanProperty overlayVisible = new BooleanProperty("overlay-visible", true);
    public final ModeProperty overlayColorMode = new ModeProperty("overlay-color-mode", 0, COLOR_MODES);
    public final ColorProperty overlayColor = new ColorProperty("overlay-color", 0x000000);
    public final ColorProperty overlayColor2 = new ColorProperty("overlay-color-2", 0xFFFFFF, () -> this.overlayColorMode.getValue() == 1 || this.overlayColorMode.getValue() == 2);
    public final FloatProperty overlayFadeSpeed = new FloatProperty("overlay-fade-speed", 5.5F, 1.0F, 10.0F, () -> this.overlayColorMode.getValue() == 2);
    public final FloatProperty overlayChromaSpeed = new FloatProperty("overlay-chroma-speed", 5.5F, 1.0F, 10.0F, () -> this.overlayColorMode.getValue() == 3);
    public final BooleanProperty outlineVisible = new BooleanProperty("outline-visible", true);
    public final ModeProperty outlineColorMode = new ModeProperty("outline-color-mode", 0, COLOR_MODES);
    public final ColorProperty outlineColor = new ColorProperty("outline-color", 0x000000);
    public final ColorProperty outlineColor2 = new ColorProperty("outline-color-2", 0xFFFFFF, () -> this.outlineColorMode.getValue() == 1 || this.outlineColorMode.getValue() == 2);
    public final FloatProperty outlineFadeSpeed = new FloatProperty("outline-fade-speed", 5.5F, 1.0F, 10.0F, () -> this.outlineColorMode.getValue() == 2);
    public final FloatProperty outlineChromaSpeed = new FloatProperty("outline-chroma-speed", 5.5F, 1.0F, 10.0F, () -> this.outlineColorMode.getValue() == 3);
    public final FloatProperty thickness = new FloatProperty("thickness", 2.0F, 1.0F, 10.0F);
    public final BooleanProperty persistence = new BooleanProperty("persistence", false);
    public final BooleanProperty depthless = new BooleanProperty("depthless", false);
    public final BooleanProperty barriers = new BooleanProperty("barriers", false);
    public final BooleanProperty hidePlants = new BooleanProperty("hide-plants", false);

    public BlockOverlay() {
        super("BlockOverlay", false);
    }

    @Override
    public void onEnabled() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void onDisabled() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(this);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{RENDER_MODES[this.renderMode.getValue()]};
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDrawBlockHighlight(DrawBlockHighlightEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        int mode = this.renderMode.getValue();
        if (mode == 0) {
            event.setCanceled(true);
            return;
        }
        if (mode == 1) {
            return;
        }
        event.setCanceled(true);
        if (!nullCheck()) {
            return;
        }
        if (!this.persistence.getValue() && mc.thePlayer.isSpectator()) {
            return;
        }
        BlockPos pos = getFocusedBlock();
        if (pos == null) {
            return;
        }
        boolean showOverlay = this.overlayVisible.getValue();
        boolean showOutline = this.outlineVisible.getValue();
        if (!showOverlay && !showOutline) {
            return;
        }

        EnumFacing side = (mode == 2) ? mc.objectMouseOver.sideHit : null;
        renderCustomBlockOverlay(pos, side, showOverlay, showOutline);
    }

    private void renderCustomBlockOverlay(BlockPos pos, EnumFacing side, boolean showOverlay, boolean showOutline) {
        AxisAlignedBB box = getBlockSelectionBox(pos);
        if (box == null) {
            return;
        }
        box = box.expand(PADDING, PADDING, PADDING);
        double vx = mc.getRenderManager().viewerPosX, vy = mc.getRenderManager().viewerPosY, vz = mc.getRenderManager().viewerPosZ;
        AxisAlignedBB renderBox = box.offset(-vx, -vy, -vz);

        int overlayStart = 0, overlayEnd = 0, outlineStart = 0, outlineEnd = 0;
        if (showOverlay) {
            int color1 = mergeAlpha(this.overlayColor.getValue(), OVERLAY_ALPHA);
            int color2 = mergeAlpha(this.overlayColor2.getValue(), OVERLAY_ALPHA);
            overlayStart = computeStart(this.overlayColorMode.getValue(), color1, color2, this.overlayFadeSpeed.getValue(), this.overlayChromaSpeed.getValue());
            overlayEnd = computeEnd(this.overlayColorMode.getValue(), color1, color2, this.overlayFadeSpeed.getValue(), this.overlayChromaSpeed.getValue());
        }
        if (showOutline) {
            int color1 = mergeAlpha(this.outlineColor.getValue(), OUTLINE_ALPHA);
            int color2 = mergeAlpha(this.outlineColor2.getValue(), OUTLINE_ALPHA);
            outlineStart = computeStart(this.outlineColorMode.getValue(), color1, color2, this.outlineFadeSpeed.getValue(), this.outlineChromaSpeed.getValue());
            outlineEnd = computeEnd(this.outlineColorMode.getValue(), color1, color2, this.outlineFadeSpeed.getValue(), this.outlineChromaSpeed.getValue());
        }

        GL11.glPushMatrix();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D();
        GlStateManager.depthMask(false);
        boolean depthDisabled = this.depthless.getValue();
        if (depthDisabled) {
            GlStateManager.disableDepth();
        }
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        if (showOutline) {
            GL11.glLineWidth(this.thickness.getValue().floatValue());
        }
        GL11.glShadeModel(GL11.GL_SMOOTH);

        try {
            IBlockState state = mc.theWorld.getBlockState(pos);
            if (state.getBlock() instanceof BlockStairs) {
                drawStairs(pos, state, box, side, vx, vy, vz, overlayStart, overlayEnd, outlineStart, outlineEnd, showOverlay, showOutline);
            } else {
                if (side != null) {
                    drawFace(renderBox, side, overlayStart, overlayEnd, outlineStart, outlineEnd, showOverlay, showOutline);
                } else {
                    for (EnumFacing face : EnumFacing.values()) {
                        drawFace(renderBox, face, overlayStart, overlayEnd, outlineStart, outlineEnd, showOverlay, showOutline);
                    }
                }
            }
        } finally {
            GL11.glShadeModel(GL11.GL_FLAT);
            GL11.glLineWidth(2.0f);
            GL11.glDisable(GL11.GL_LINE_SMOOTH);
            if (depthDisabled) {
                GlStateManager.enableDepth();
            }
            GlStateManager.depthMask(true);
            GlStateManager.enableTexture2D();
            GlStateManager.enableCull();
            GlStateManager.disableBlend();
            GL11.glPopMatrix();
        }
    }

    private BlockPos getFocusedBlock() {
        if (mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return null;
        }
        BlockPos pos = mc.objectMouseOver.getBlockPos();
        if (pos == null) {
            return null;
        }
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        if (block == Blocks.air) {
            return null;
        }
        if (block == Blocks.barrier && !this.barriers.getValue()) {
            return null;
        }
        if (this.hidePlants.getValue() && (block instanceof BlockTallGrass || block instanceof BlockFlower || block instanceof BlockDeadBush || block instanceof BlockDoublePlant)) {
            return null;
        }
        return pos;
    }

    private static AxisAlignedBB getBlockSelectionBox(BlockPos pos) {
        if (mc.theWorld == null || pos == null) {
            return null;
        }
        IBlockState state = mc.theWorld.getBlockState(pos);
        Block block = state.getBlock();
        block.setBlockBoundsBasedOnState(mc.theWorld, pos);
        AxisAlignedBB box = block.getSelectedBoundingBox(mc.theWorld, pos);
        if (box == null) {
            box = new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
        }
        return box;
    }

    private static boolean nullCheck() {
        return mc.thePlayer != null && mc.theWorld != null;
    }

    private static int mergeAlpha(int color, int alpha) {
        return (color & 0xFFFFFF) | alpha << 24;
    }

    private static int interpolate(int c1, int c2, double pct) {
        Color a = new Color(c1, true), b = new Color(c2, true);
        double inv = 1.0 - pct;
        return new Color((int) (a.getRed() * pct + b.getRed() * inv), (int) (a.getGreen() * pct + b.getGreen() * inv), (int) (a.getBlue() * pct + b.getBlue() * inv), (int) (a.getAlpha() * pct + b.getAlpha() * inv)).getRGB();
    }

    private static int getChroma(long speed) {
        long time = System.currentTimeMillis();
        return Color.getHSBColor((float) (time % (15000L / speed)) / (15000.0F / (float) speed), 1.0F, 1.0F).getRGB();
    }

    private static int computeStart(int colorMode, int color1, int color2, double fadeSpeed, double chromaSpeed) {
        switch (colorMode) {
            case 1:
                return color1;
            case 2:
                double pct = Math.sin(System.currentTimeMillis() / (1100.0 - fadeSpeed * 100.0)) * 0.5 + 0.5;
                return interpolate(color1, color2, pct);
            case 3:
                int alpha = (color1 >> 24) & 0xFF;
                return mergeAlpha(getChroma((long) chromaSpeed), alpha);
            default:
                return color1;
        }
    }

    private static int computeEnd(int colorMode, int color1, int color2, double fadeSpeed, double chromaSpeed) {
        switch (colorMode) {
            case 1:
                return color2;
            case 2:
                double pct = Math.sin((System.currentTimeMillis() + 500L) / (1100.0 - fadeSpeed * 100.0)) * 0.5 + 0.5;
                return interpolate(color1, color2, pct);
            default:
                return computeStart(colorMode, color1, color2, fadeSpeed, chromaSpeed);
        }
    }

    private static void drawFace(AxisAlignedBB box, EnumFacing face, int os, int oe, int ls, int le, boolean overlay, boolean outline) {
        Tessellator ts = Tessellator.getInstance();
        WorldRenderer wr = ts.getWorldRenderer();
        if (overlay) {
            wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
            addFaceVertices(wr, face, box, os, oe);
            ts.draw();
        }
        if (outline) {
            wr.begin(2, DefaultVertexFormats.POSITION_COLOR);
            addFaceVertices(wr, face, box, ls, le);
            ts.draw();
        }
    }

    private static void v(WorldRenderer wr, double x, double y, double z, int color) {
        wr.pos(x, y, z).color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >> 24) & 0xFF).endVertex();
    }

    private static void addFaceVertices(WorldRenderer wr, EnumFacing face, AxisAlignedBB box, int start, int end) {
        switch (face) {
            case UP:
                v(wr, box.minX, box.maxY, box.maxZ, start);
                v(wr, box.maxX, box.maxY, box.maxZ, end);
                v(wr, box.maxX, box.maxY, box.minZ, start);
                v(wr, box.minX, box.maxY, box.minZ, end);
                break;
            case DOWN:
                v(wr, box.maxX, box.minY, box.maxZ, start);
                v(wr, box.minX, box.minY, box.maxZ, end);
                v(wr, box.minX, box.minY, box.minZ, start);
                v(wr, box.maxX, box.minY, box.minZ, end);
                break;
            case NORTH:
                v(wr, box.maxX, box.maxY, box.minZ, start);
                v(wr, box.maxX, box.minY, box.minZ, end);
                v(wr, box.minX, box.minY, box.minZ, start);
                v(wr, box.minX, box.maxY, box.minZ, end);
                break;
            case SOUTH:
                v(wr, box.minX, box.maxY, box.maxZ, start);
                v(wr, box.minX, box.minY, box.maxZ, end);
                v(wr, box.maxX, box.minY, box.maxZ, start);
                v(wr, box.maxX, box.maxY, box.maxZ, end);
                break;
            case EAST:
                v(wr, box.maxX, box.maxY, box.maxZ, start);
                v(wr, box.maxX, box.minY, box.maxZ, end);
                v(wr, box.maxX, box.minY, box.minZ, start);
                v(wr, box.maxX, box.maxY, box.minZ, end);
                break;
            case WEST:
                v(wr, box.minX, box.maxY, box.minZ, start);
                v(wr, box.minX, box.minY, box.minZ, end);
                v(wr, box.minX, box.minY, box.maxZ, start);
                v(wr, box.minX, box.maxY, box.maxZ, end);
                break;
            default:
                break;
        }
    }

    private static void drawStairs(BlockPos blockPos, IBlockState blockState, AxisAlignedBB box, EnumFacing side, double viewerX, double viewerY, double viewerZ, int overlayStartColor, int overlayEndColor, int outlineStartColor, int outlineEndColor, boolean overlay, boolean outline) {
        EnumFacing blockFacing = blockState.getValue(BlockStairs.FACING);
        BlockStairs.EnumHalf blockHalf = blockState.getValue(BlockStairs.HALF);
        int blockX = blockPos.getX();
        int blockY = blockPos.getY();
        int blockZ = blockPos.getZ();
        int angleX = (blockHalf == BlockStairs.EnumHalf.TOP) ? 270 : 0;
        int angleY = 0;
        switch (blockFacing) {
            case NORTH:
                angleY = 180;
                break;
            case EAST:
                angleY = 90;
                break;
            case WEST:
                angleY = 270;
                break;
            default:
                break;
        }
        GL11.glPushMatrix();
        GL11.glTranslated(-viewerX, -viewerY, -viewerZ);
        GL11.glTranslated(blockX + 0.5, blockY, blockZ + 0.5);
        GL11.glRotated(angleY, 0.0, 1.0, 0.0);
        GL11.glTranslated(0.0, 0.5, 0.0);
        GL11.glRotated(angleX, 1.0, 0.0, 0.0);
        GL11.glTranslated(-blockX - 0.5, -blockY - 0.5, -blockZ - 0.5);
        if (side == null) {
            drawStairsFull(box, overlayStartColor, overlayEndColor, outlineStartColor, outlineEndColor, overlay, outline);
        } else {
            drawStairsSide(box, blockHalf, blockFacing, side, overlayStartColor, overlayEndColor, outlineStartColor, outlineEndColor, overlay, outline);
        }
        GL11.glPopMatrix();
    }

    private static void drawStairsFull(AxisAlignedBB box, int overlayStart, int overlayEnd, int outlineStart, int outlineEnd, boolean overlay, boolean outline) {
        drawStairsTop(box, overlayStart, overlayEnd, outlineStart, outlineEnd, overlay, outline);
        drawStairsBottom(box, overlayStart, overlayEnd, outlineStart, outlineEnd, overlay, outline);
        drawStairsNorth(box, overlayStart, overlayEnd, outlineStart, outlineEnd, overlay, outline);
        drawStairsEast(box, overlayStart, overlayEnd, outlineStart, outlineEnd, overlay, outline);
        drawStairsSouth(box, overlayStart, overlayEnd, outlineStart, outlineEnd, overlay, outline);
        drawStairsWest(box, overlayStart, overlayEnd, outlineStart, outlineEnd, overlay, outline);
    }

    private static void drawStairsSide(AxisAlignedBB box, BlockStairs.EnumHalf blockHalf, EnumFacing blockFacing, EnumFacing side, int overlayStart, int overlayEnd, int outlineStart, int outlineEnd, boolean overlay, boolean outline) {
        EnumFacing mapped = getSide(blockHalf, blockFacing, side);
        switch (mapped) {
            case UP:
                drawStairsTop(box, overlayStart, overlayEnd, outlineStart, outlineEnd, overlay, outline);
                break;
            case DOWN:
                drawStairsBottom(box, overlayStart, overlayEnd, outlineStart, outlineEnd, overlay, outline);
                break;
            case NORTH:
                drawStairsNorth(box, overlayStart, overlayEnd, outlineStart, outlineEnd, overlay, outline);
                break;
            case EAST:
                drawStairsEast(box, overlayStart, overlayEnd, outlineStart, outlineEnd, overlay, outline);
                break;
            case SOUTH:
                drawStairsSouth(box, overlayStart, overlayEnd, outlineStart, outlineEnd, overlay, outline);
                break;
            case WEST:
                drawStairsWest(box, overlayStart, overlayEnd, outlineStart, outlineEnd, overlay, outline);
                break;
            default:
                break;
        }
    }

    private static void drawStairsTop(AxisAlignedBB box, int os, int oe, int ls, int le, boolean overlay, boolean outline) {
        AxisAlignedBB b1 = box.contract(0.0, 0.0, 0.25).offset(0.0, 0.0, 0.25);
        AxisAlignedBB b2 = box.contract(0.0, 0.0, 0.25).offset(0.0, -0.5, -0.25);
        drawFace(b1, EnumFacing.UP, os, oe, ls, le, overlay, outline);
        drawFace(b2, EnumFacing.UP, os, oe, ls, le, overlay, outline);
    }

    private static void drawStairsBottom(AxisAlignedBB box, int os, int oe, int ls, int le, boolean overlay, boolean outline) {
        drawFace(box, EnumFacing.DOWN, os, oe, ls, le, overlay, outline);
    }

    private static void drawStairsNorth(AxisAlignedBB box, int os, int oe, int ls, int le, boolean overlay, boolean outline) {
        drawFace(box.contract(0.0, 0.252, 0.0).offset(0.0, 0.252, 0.5), EnumFacing.NORTH, os, oe, ls, le, overlay, outline);
        drawFace(box.contract(0.0, 0.25, 0.0).offset(0.0, -0.25, 0.0), EnumFacing.NORTH, os, oe, ls, le, overlay, outline);
    }

    private static void drawStairsEast(AxisAlignedBB box, int os, int oe, int ls, int le, boolean overlay, boolean outline) {
        drawFace(box.contract(0.0, 0.252, 0.25).offset(0.0, 0.252, 0.25), EnumFacing.EAST, os, oe, ls, le, overlay, outline);
        drawFace(box.contract(0.0, 0.25, 0.0).offset(0.0, -0.25, 0.0), EnumFacing.EAST, os, oe, ls, le, overlay, outline);
    }

    private static void drawStairsSouth(AxisAlignedBB box, int os, int oe, int ls, int le, boolean overlay, boolean outline) {
        drawFace(box, EnumFacing.SOUTH, os, oe, ls, le, overlay, outline);
    }

    private static void drawStairsWest(AxisAlignedBB box, int os, int oe, int ls, int le, boolean overlay, boolean outline) {
        drawFace(box.contract(0.0, 0.252, 0.25).offset(0.0, 0.252, 0.25), EnumFacing.WEST, os, oe, ls, le, overlay, outline);
        drawFace(box.contract(0.0, 0.25, 0.0).offset(0.0, -0.25, 0.0), EnumFacing.WEST, os, oe, ls, le, overlay, outline);
    }

    private static EnumFacing getSide(BlockStairs.EnumHalf blockHalf, EnumFacing blockFacing, EnumFacing side) {
        if (blockHalf == BlockStairs.EnumHalf.TOP) {
            switch (blockFacing) {
                case NORTH:
                    side = side.rotateAround(EnumFacing.Axis.X);
                    side = side.rotateAround(EnumFacing.Axis.Y);
                    side = side.rotateAround(EnumFacing.Axis.Y);
                    break;
                case EAST:
                    side = side.rotateAround(EnumFacing.Axis.Z);
                    side = side.rotateAround(EnumFacing.Axis.Y);
                    break;
                case SOUTH:
                    side = side.rotateAround(EnumFacing.Axis.X);
                    side = side.rotateAround(EnumFacing.Axis.X);
                    side = side.rotateAround(EnumFacing.Axis.X);
                    break;
                case WEST:
                    side = side.rotateAround(EnumFacing.Axis.Z);
                    side = side.rotateAround(EnumFacing.Axis.Y);
                    side = side.rotateAround(EnumFacing.Axis.Z);
                    side = side.rotateAround(EnumFacing.Axis.Z);
                    break;
                default:
                    break;
            }
        } else if (side != EnumFacing.UP && side != EnumFacing.DOWN) {
            switch (blockFacing) {
                case NORTH:
                    side = side.getOpposite();
                    break;
                case EAST:
                    side = side.rotateYCCW();
                    break;
                case WEST:
                    side = side.rotateYCCW();
                    break;
                default:
                    break;
            }
        }
        return side;
    }
}
