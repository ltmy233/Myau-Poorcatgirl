package myau.util;

import net.minecraft.block.Block;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import org.lwjgl.opengl.GL11;

import java.util.Set;

public final class BlockShapeRenderer {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static boolean isInViewFrustum(AxisAlignedBB box) {
        return box != null && RenderUtil.isInViewFrustum(box, 0.0);
    }

    public static AxisAlignedBB getBlockSelectionBox(BlockPos pos) {
        if (mc.theWorld == null || pos == null) {
            return null;
        }
        IBlockState state = mc.theWorld.getBlockState(pos);
        Block block = state.getBlock();
        block.setBlockBoundsBasedOnState(mc.theWorld, pos);
        AxisAlignedBB box = block.getSelectedBoundingBox(mc.theWorld, pos);
        if (box == null) {
            box = new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
        }
        return box;
    }

    public static void renderBlockShape(BlockPos pos, IBlockState state, int color, boolean outline, boolean shade, Set<EnumFacing> visibleFaces) {
        AxisAlignedBB box = getBlockSelectionBox(pos);
        if (box == null) {
            return;
        }
        double viewerX = mc.getRenderManager().viewerPosX;
        double viewerY = mc.getRenderManager().viewerPosY;
        double viewerZ = mc.getRenderManager().viewerPosZ;
        int overlayColor = (color & 0x00FFFFFF) | (63 << 24);
        int outlineColor = color | 0xFF000000;

        GL11.glPushMatrix();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GL11.glLineWidth(2.0F);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);

        if (state.getBlock() instanceof BlockStairs) {
            StairsUtils.drawStairs(pos, state, box, null, viewerX, viewerY, viewerZ, overlayColor, outlineColor, outlineColor, outlineColor, shade, outline, (faceBox, face, overlayStart, overlayEnd, lineStart, lineEnd, doOverlay, doOutline) -> drawBoxFace(faceBox, face, overlayColor, outlineColor, doOverlay, doOutline));
        } else {
            AxisAlignedBB renderBox = box.offset(-viewerX, -viewerY, -viewerZ);
            for (EnumFacing face : visibleFaces) {
                drawBoxFace(renderBox, face, overlayColor, outlineColor, shade, outline);
            }
        }

        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GL11.glPopMatrix();
    }

    public static void drawBoxFace(AxisAlignedBB box, EnumFacing face, int overlayColor, int outlineColor, boolean overlay, boolean outline) {
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();
        if (overlay) {
            worldRenderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
            drawBoxFaceVertices(worldRenderer, face, box, overlayColor, overlayColor);
            tessellator.draw();
        }
        if (outline) {
            worldRenderer.begin(2, DefaultVertexFormats.POSITION_COLOR);
            drawBoxFaceVertices(worldRenderer, face, box, outlineColor, outlineColor);
            tessellator.draw();
        }
    }

    private static void drawBoxFaceVertices(WorldRenderer worldRenderer, EnumFacing face, AxisAlignedBB box, int start, int end) {
        switch (face) {
            case UP:
                drawBoxFaceVertex(worldRenderer, box.minX, box.maxY, box.maxZ, start);
                drawBoxFaceVertex(worldRenderer, box.maxX, box.maxY, box.maxZ, end);
                drawBoxFaceVertex(worldRenderer, box.maxX, box.maxY, box.minZ, start);
                drawBoxFaceVertex(worldRenderer, box.minX, box.maxY, box.minZ, end);
                break;
            case DOWN:
                drawBoxFaceVertex(worldRenderer, box.maxX, box.minY, box.maxZ, start);
                drawBoxFaceVertex(worldRenderer, box.minX, box.minY, box.maxZ, end);
                drawBoxFaceVertex(worldRenderer, box.minX, box.minY, box.minZ, start);
                drawBoxFaceVertex(worldRenderer, box.maxX, box.minY, box.minZ, end);
                break;
            case NORTH:
                drawBoxFaceVertex(worldRenderer, box.maxX, box.maxY, box.minZ, start);
                drawBoxFaceVertex(worldRenderer, box.maxX, box.minY, box.minZ, end);
                drawBoxFaceVertex(worldRenderer, box.minX, box.minY, box.minZ, start);
                drawBoxFaceVertex(worldRenderer, box.minX, box.maxY, box.minZ, end);
                break;
            case SOUTH:
                drawBoxFaceVertex(worldRenderer, box.minX, box.maxY, box.maxZ, start);
                drawBoxFaceVertex(worldRenderer, box.minX, box.minY, box.maxZ, end);
                drawBoxFaceVertex(worldRenderer, box.maxX, box.minY, box.maxZ, start);
                drawBoxFaceVertex(worldRenderer, box.maxX, box.maxY, box.maxZ, end);
                break;
            case EAST:
                drawBoxFaceVertex(worldRenderer, box.maxX, box.maxY, box.minZ, start);
                drawBoxFaceVertex(worldRenderer, box.maxX, box.maxY, box.maxZ, end);
                drawBoxFaceVertex(worldRenderer, box.maxX, box.minY, box.maxZ, start);
                drawBoxFaceVertex(worldRenderer, box.maxX, box.minY, box.minZ, end);
                break;
            case WEST:
                drawBoxFaceVertex(worldRenderer, box.minX, box.maxY, box.maxZ, start);
                drawBoxFaceVertex(worldRenderer, box.minX, box.maxY, box.minZ, end);
                drawBoxFaceVertex(worldRenderer, box.minX, box.minY, box.minZ, start);
                drawBoxFaceVertex(worldRenderer, box.minX, box.minY, box.maxZ, end);
                break;
            default:
                break;
        }
    }

    private static void drawBoxFaceVertex(WorldRenderer worldRenderer, double x, double y, double z, int color) {
        worldRenderer.pos(x, y, z).color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >> 24) & 0xFF).endVertex();
    }

    private BlockShapeRenderer() {
    }
}
