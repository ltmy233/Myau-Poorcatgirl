package poorcatgirl.module.blockin;

import java.util.function.BooleanSupplier;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import poorcatgirl.event.EventManager;
import poorcatgirl.events.RightClickMouseEvent;
import poorcatgirl.management.RotationState;
import poorcatgirl.util.BlockUtil;
import poorcatgirl.util.RotationUtil;

public final class BlockInMappedApi {
    private final Minecraft mc=Minecraft.getMinecraft();

    public boolean placeBlock(BlockPos support,EnumFacing face,Vec3 hit,double range,BooleanSupplier allowed) {
        if(support==null || face==null || hit==null || !allowed.getAsBoolean())return false;
        EntityPlayerSP player=mc.thePlayer;
        WorldClient world=mc.theWorld;
        PlayerControllerMP controller=mc.playerController;
        if(player==null || world==null || controller==null)return false;
        int slot=player.inventory.currentItem;
        ItemStack stack=player.getHeldItem();
        float yaw=player.rotationYaw,pitch=player.rotationPitch;
        if(!valid(player,world,controller,stack,support,face,hit,range))return false;

        RightClickMouseEvent event=new RightClickMouseEvent();
        EventManager.call(event);
        if(!allowed.getAsBoolean() || event.isCancelled())return false;
        if(mc.thePlayer!=player || mc.theWorld!=world || mc.playerController!=controller
                || player.inventory.currentItem!=slot || player.getHeldItem()!=stack
                || player.rotationYaw!=yaw || player.rotationPitch!=pitch)return false;
        if(!valid(player,world,controller,stack,support,face,hit,range))return false;
        boolean placed=controller.onPlayerRightClick(player,world,stack,support,face,hit);
        if(placed && mc.thePlayer==player && mc.theWorld==world)player.swingItem();
        return placed;
    }

    private boolean valid(EntityPlayerSP player,WorldClient world,PlayerControllerMP controller,
                          ItemStack stack,BlockPos support,EnumFacing face,Vec3 hit,double range) {
        if(mc.currentScreen!=null || player.isDead || player.getHealth()<=0 || player.isSpectator()
                || player.isRiding() || !player.capabilities.allowEdit || RotationState.isActived()
                || controller.getIsHittingBlock() || player.isUsingItem() || stack==null
                || stack.stackSize<=0 || !(stack.getItem() instanceof ItemBlock))return false;
        double reach=Math.min(range,controller.getBlockReachDistance());
        if(!Double.isFinite(reach) || reach<=0 || player.getPositionEyes(1).distanceTo(hit)>reach)return false;
        BlockPos goal=support.offset(face);
        if(!world.isBlockLoaded(goal) || !world.isBlockLoaded(support) || !world.getWorldBorder().contains(goal))return false;
        Block backing=world.getBlockState(support).getBlock();
        Block target=world.getBlockState(goal).getBlock();
        if(!backing.getMaterial().blocksMovement() || BlockUtil.isInteractable(backing)
                || !target.getMaterial().isReplaceable())return false;
        AxisAlignedBB box=new AxisAlignedBB(goal,goal.add(1,1,1));
        if(box.intersectsWith(player.getEntityBoundingBox()) || !world.checkNoEntityCollision(box,player))return false;
        MovingObjectPosition trace=RotationUtil.rayTrace(player.rotationYaw,player.rotationPitch,reach,1);
        return trace!=null && trace.typeOfHit==MovingObjectPosition.MovingObjectType.BLOCK
                && support.equals(trace.getBlockPos()) && face==trace.sideHit
                && trace.hitVec!=null && trace.hitVec.distanceTo(hit)<=0.05D;
    }
}
