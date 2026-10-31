package poorcatgirl.module.modules;

import poorcatgirl.Poorcatgirl;
import poorcatgirl.event.EventTarget;
import poorcatgirl.event.types.EventType;
import poorcatgirl.event.types.Priority;
import poorcatgirl.events.LoadWorldEvent;
import poorcatgirl.events.LivingUpdateEvent;
import poorcatgirl.events.MoveInputEvent;
import poorcatgirl.events.PlayerUpdateEvent;
import poorcatgirl.events.TickEvent;
import poorcatgirl.events.UpdateEvent;
import poorcatgirl.management.RotationState;
import poorcatgirl.mixin.IAccessorMinecraft;
import poorcatgirl.module.Module;
import poorcatgirl.module.blockin.BlockInInventory;
import poorcatgirl.module.blockin.BlockInMappedApi;
import poorcatgirl.property.properties.BooleanProperty;
import poorcatgirl.property.properties.FloatProperty;
import poorcatgirl.property.properties.IntProperty;
import poorcatgirl.property.properties.ModeProperty;
import poorcatgirl.ui.Lang;
import poorcatgirl.util.BlockUtil;
import poorcatgirl.util.KeyBindUtil;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AutoBlockIn extends Module {
    private static final Minecraft mc=Minecraft.getMinecraft();

    private static final double INSET=0.05;
    private static final double STEP=0.2;
    private static final double JIT=STEP*0.1;
    private static final int CONFIRM_GRACE_TICKS=20;
    private static final int NO_MATERIAL_TIMEOUT=100;
    private static final int IDLE_TIMEOUT=140;
    private static final int ATTEMPT_TIMEOUT=700;
    private static final int JUMP_TIMEOUT=34;
    private static final int ROOF_AIM_TICKS=3;
    private static final float MAX_ROTATION_STEP=90f;
    private static final float ROTATION_QUANTUM=0.05f;

    public final FloatProperty range=new FloatProperty("range",4.5f,3f,6f);
    public final ModeProperty execution=new ModeProperty("execution",0,new String[]{"MAPPED_API","VANILLA_CLICK"});
    public final IntProperty placeDelay=new IntProperty("place-delay",0,0,200);
    public final BooleanProperty itemSpoof=new BooleanProperty("item-spoof",false);
    public final IntProperty placeTicks=new IntProperty("place-ticks",1,1,10);
    public final FloatProperty rotateStep=new FloatProperty("rotate-step",90f,5f,90f);
    public final IntProperty triggerKey=new IntProperty("trigger-key",0,-100,255);
    public final ModeProperty material=new ModeProperty("block-material",0,
            new String[]{"AUTO_HARDEST","WOOL","PLANKS","END_STONE","CLAY","OBSIDIAN"});
    public final IntProperty rotationTolerance=new IntProperty("rotation-tolerance",25,5,100);
    public final ModeProperty moveFix=new ModeProperty("move-fix",0,new String[]{"NONE","SILENT"});

    private final BlockInInventory inventory=new BlockInInventory();
    private final BlockInMappedApi mappedApi=new BlockInMappedApi();
    private int mappedPreparedTick=-10000;
    private int lastMappedAttemptTick=-10000;
    private EntityPlayerSP mappedAttemptPlayer;
    private WorldClient mappedAttemptWorld;
    private boolean mappedInFlight;
    private long attemptGeneration;
    private EntityPlayerSP attemptPlayer;
    private WorldClient attemptWorld;
    private EntityPlayerSP mappedPlayer;
    private WorldClient mappedWorld;
    private final Map<BlockPos,Integer> recentPlacements=new HashMap<>();
    private String status="PAUSED";
    private float aimYaw;
    private float aimPitch;
    private BlockPos targetGoal;
    private BlockPos targetSupport;
    private EnumFacing targetFace;
    private Vec3 targetHitVec;
    private long lastPlaceTime;
    private int lastPlaceTick=-10000;
    private boolean clickedBeforeUpdate;
    private int lastScanTick=-10000;
    private int completeTicks;
    private int noMaterialTicks;
    private int idleTicks;
    private int attemptTicks;
    private boolean active;
    private boolean prevTriggerDown;
    private boolean jumping;
    private boolean jumpUsed;
    private boolean jumpPending;
    private boolean jumpAirborne;
    private boolean mappedJumpMissed;
    private boolean roofPrewarming;
    private int jumpStartTick=-1;
    private BlockPos jumpBase;
    private int jumpRoofY;
    private int roofAimTicks;
    private EntityPlayerSP cameraPlayer;
    private WorldClient cameraWorld;
    private float cameraYaw;
    private float cameraPitch;

    private static AutoBlockIn cameraOwner(Entity entity) {
        if(entity==null || Poorcatgirl.moduleManager==null)return null;
        AutoBlockIn owner=(AutoBlockIn)Poorcatgirl.moduleManager.modules.get(AutoBlockIn.class);
        return owner!=null && owner.isEnabled() && owner.cameraPlayer==entity
                && entity==mc.thePlayer && owner.cameraWorld==mc.theWorld ? owner : null;
    }

    public static boolean holdsCamera(Entity entity) {return entity!=null && cameraOwner(entity)!=null;}

    public static float renderCameraYaw(Entity entity,float ordinaryYaw) {
        AutoBlockIn owner=cameraOwner(entity);
        return owner==null ? ordinaryYaw : owner.cameraYaw;
    }

    public static float renderCameraPitch(Entity entity,float ordinaryPitch) {
        AutoBlockIn owner=cameraOwner(entity);
        return owner==null ? ordinaryPitch : owner.cameraPitch;
    }

    public static boolean consumeCameraMouse(Minecraft minecraft) {
        if(!minecraft.inGameHasFocus || !holdsCamera(minecraft.getRenderViewEntity()))return false;
        minecraft.mouseHelper.mouseXYChange();
        return true;
    }

    private void holdCamera() {
        if(cameraPlayer==mc.thePlayer && cameraWorld==mc.theWorld)return;
        restoreCamera();
        cameraPlayer=mc.thePlayer;
        cameraWorld=mc.theWorld;
        cameraYaw=cameraPlayer.rotationYaw;
        cameraPitch=cameraPlayer.rotationPitch;
    }

    private void restoreCamera() {
        if(cameraPlayer!=null && cameraPlayer==mc.thePlayer && cameraWorld==mc.theWorld) {
            float restoredYaw=cameraPlayer.rotationYaw
                    +MathHelper.wrapAngleTo180_float(cameraYaw-cameraPlayer.rotationYaw);
            cameraPlayer.rotationYaw=cameraPlayer.prevRotationYaw=restoredYaw;
            cameraPlayer.rotationPitch=cameraPlayer.prevRotationPitch=cameraPitch;
            cameraPlayer.renderArmYaw=cameraPlayer.prevRenderArmYaw=restoredYaw;
            cameraPlayer.renderArmPitch=cameraPlayer.prevRenderArmPitch=cameraPitch;
        }
        cameraPlayer=null;
        cameraWorld=null;
    }

    public AutoBlockIn() {super("AutoBlockIn",false);}

    public int getSlot() {return -1;}

    public boolean ownsAutomaticAction() {return isRunning() && inventory.ownsSlot();}

    public boolean isRunning() {return isEnabled() && active && triggerDown();}

    @Override public boolean toggle() {
        setEnabled(!isEnabled());
        return false;
    }

    @Override public void onEnabled() {
        resetRuntime();
        prevTriggerDown=false;
        if(mc.thePlayer!=null) {
            aimYaw=mc.thePlayer.rotationYaw;
            aimPitch=mc.thePlayer.rotationPitch;
        }
        status="READY";
    }

    @Override public void onDisabled() {
        resetRuntime();
        prevTriggerDown=false;
    }

    private void resetRuntime() {
        attemptGeneration++;
        attemptPlayer=null;
        attemptWorld=null;
        restoreCamera();
        active=false;
        jumping=false;
        jumpUsed=false;
        jumpPending=false;
        mappedJumpMissed=false;
        roofPrewarming=false;
        jumpStartTick=-1;
        jumpBase=null;
        roofAimTicks=0;
        inventory.reset();
        recentPlacements.clear();
        clearTarget();
        lastPlaceTime=0L;
        lastPlaceTick=-10000;
        clickedBeforeUpdate=false;
        lastScanTick=-10000;
        completeTicks=0;
        noMaterialTicks=0;
        idleTicks=0;
        attemptTicks=0;
        status="PAUSED";
    }

    private void startAttempt() {
        attemptGeneration++;
        attemptPlayer=mc.thePlayer;
        attemptWorld=mc.theWorld;
        restoreCamera();
        clearTarget();
        inventory.release();
        recentPlacements.clear();
        lastPlaceTime=0L;
        lastPlaceTick=-10000;
        clickedBeforeUpdate=false;
        lastScanTick=-10000;
        completeTicks=0;
        noMaterialTicks=0;
        idleTicks=0;
        attemptTicks=0;
        jumping=false;
        jumpUsed=false;
        jumpPending=false;
        mappedJumpMissed=false;
        roofPrewarming=false;
        jumpStartTick=-1;
        jumpBase=null;
        roofAimTicks=0;
        active=true;
        status="ENCLOSE";
        if(mc.thePlayer!=null) {
            aimYaw=mc.thePlayer.rotationYaw;
            aimPitch=mc.thePlayer.rotationPitch;
        }
    }

    private void finishAttempt() {
        attemptGeneration++;
        attemptPlayer=null;
        attemptWorld=null;
        restoreCamera();
        clickedBeforeUpdate=false;
        active=false;
        jumping=false;
        jumpPending=false;
        roofPrewarming=false;
        jumpBase=null;
        status="READY";
        inventory.release();
        clearTarget();
    }

    private void clearTarget() {
        mappedPreparedTick=-10000;
        mappedPlayer=null;
        mappedWorld=null;
        targetGoal=null;
        targetSupport=null;
        targetFace=null;
        targetHitVec=null;
    }

    private boolean conflict() {
        if(Poorcatgirl.moduleManager==null)return true;
        Telly telly=(Telly)Poorcatgirl.moduleManager.modules.get(Telly.class);
        return (telly!=null && telly.isAutomationRunning())
                || (Poorcatgirl.blinkManager!=null && Poorcatgirl.blinkManager.isBlinking())
                || (Poorcatgirl.lagManager!=null && !Poorcatgirl.lagManager.packetQueue.isEmpty());
    }

    private boolean validContext() {
        return mc.thePlayer!=null && mc.theWorld!=null && mc.playerController!=null
                && !mc.thePlayer.isDead && mc.thePlayer.getHealth()>0
                && !mc.thePlayer.isSpectator() && !mc.thePlayer.isRiding()
                && mc.thePlayer.capabilities.allowEdit
                && mc.currentScreen==null && inventory.safeContainer() && !conflict();
    }

    private double reach() {return Math.min(range.getValue(),mc.playerController.getBlockReachDistance());}

    private boolean triggerDown() {
        if(mc.currentScreen!=null || mc.theWorld==null || mc.thePlayer==null)return false;
        int key=triggerKey.getValue();
        if(key==0)key=mc.gameSettings.keyBindUseItem.getKeyCode();
        return key!=0 && KeyBindUtil.isKeyDown(key);
    }

    private boolean updateTrigger() {
        boolean down=isEnabled() && triggerDown();
        if(active && (attemptPlayer!=mc.thePlayer || attemptWorld!=mc.theWorld)) {
            finishAttempt();
            if(down)startAttempt();
        }
        if(down && !prevTriggerDown)startAttempt();
        if(!down && active)finishAttempt();
        prevTriggerDown=down;
        return down && active;
    }

    @EventTarget(Priority.HIGH)
    public void onTriggerTick(TickEvent event) {
        updateTrigger();
    }

    private BlockPos currentBase() {
        if(jumpBase!=null && (jumping || jumpUsed))return jumpBase;
        return new BlockPos(MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.getEntityBoundingBox().minY+1.0E-7D),
                MathHelper.floor_double(mc.thePlayer.posZ));
    }

    @EventTarget(Priority.HIGHEST)
    public void onUpdate(UpdateEvent event) {
        if(event.getType()==EventType.POST) {
            clickedBeforeUpdate=false;
            return;
        }
        if(event.getType()!=EventType.PRE)return;
        if(!updateTrigger())return;
        if(mc.currentScreen!=null)return;
        inventory.tick();
        if(!validContext()) {finishAttempt();return;}
        if(event.isRotated() || RotationState.isActived())return;

        if(clickedBeforeUpdate)return;

        BlockPos base=currentBase();
        if(enclosedNow(base)) {
            restoreCamera();
            clearTarget();
            return;
        }

        int slot=selectMaterialSlot();
        if(slot<0) {
            clearTarget();
            return;
        }

        Vec3 eye=mc.thePlayer.getPositionEyes(1f);
        if(execution.getValue()==0 && triggerKey.getValue()!=0 && !jumpUsed && mc.thePlayer.onGround
                && !mappedJumpMissed && roofGoals(base).size()==1 && !hasRoofSupport(base)) {
            startJump(base);
        }
        if(!prepareRoofJump(base,eye)) {
            if(targetHitVec==null) {
                if(mc.thePlayer.ticksExisted-lastScanTick>=(execution.getValue()==0?1:2)) {
                    findBestPlacement(base);
                    lastScanTick=mc.thePlayer.ticksExisted;
                }
            } else if(!targetStillValid() || !candidateGoal(targetGoal) || !targetReachable(eye)) {
                findBestPlacement(base);
                lastScanTick=mc.thePlayer.ticksExisted;
            }
        }

        if(targetHitVec!=null && targetGoal!=null && targetSupport!=null && targetFace!=null) {
            holdCamera();
            float[] exact=lookAt(eye,targetHitVec);
            applyAim(exact);
            mc.thePlayer.rotationYaw=aimYaw;
            mc.thePlayer.rotationPitch=aimPitch;
            inventory.acquire(slot);
            if(execution.getValue()==0) {
                mappedPreparedTick=mc.thePlayer.ticksExisted;
                mappedPlayer=mc.thePlayer;
                mappedWorld=mc.theWorld;
            }
        } else {
            aimYaw=mc.thePlayer.rotationYaw;
            aimPitch=mc.thePlayer.rotationPitch;
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onLivingUpdate(LivingUpdateEvent event) {
        if(!jumpPending)return;
        if(!updateTrigger() || !validContext())return;
        if(!jumpPending)return;
        jumpPending=false;
        if(jumpStartTick!=mc.thePlayer.ticksExisted || !mc.thePlayer.onGround
                || RotationState.isActived())return;
        mc.thePlayer.movementInput.jump=true;
    }

    private boolean prepareRoofJump(BlockPos base,Vec3 eye) {
        if(jumping || jumpUsed || !mc.thePlayer.onGround || !wallsClosed(base)) {
            roofPrewarming=false;
            return false;
        }
        List<BlockPos> roofs=roofGoals(base);
        boolean needsRoof=false;
        for(BlockPos roof:roofs)if(isOpen(roof))needsRoof=true;
        if(!needsRoof) {
            roofPrewarming=false;
            return false;
        }
        for(BlockPos roof:roofs)for(EnumFacing face:EnumFacing.values()) {
            if(isSolidSupport(roof.offset(face))) {
                roofPrewarming=false;
                return false;
            }
        }
        BlockPos tipGoal=null;
        BlockPos support=null;
        for(BlockPos goal:wallGoals(base,roofY(base)-base.getY())) {
            if(!isOpen(goal) || !candidateGoal(goal))continue;
            BlockPos below=goal.down();
            if(!isSolidSupport(below))continue;
            tipGoal=goal;
            support=below;
            break;
        }
        if(tipGoal==null) {
            roofPrewarming=true;
            if(++roofAimTicks>ROOF_AIM_TICKS)startJump(base);
            return false;
        }
        targetGoal=tipGoal;
        targetSupport=support;
        targetFace=EnumFacing.UP;
        targetHitVec=new Vec3(support.getX()+0.5D,support.getY()+1.0D-INSET,support.getZ()+0.5D);
        roofPrewarming=true;
        roofAimTicks++;
        boolean aligned=aimAligned(eye,targetHitVec,3.0F);
        if(roofAimTicks>=ROOF_AIM_TICKS && (aligned || execution.getValue()!=0 && roofAimTicks>=ROOF_AIM_TICKS*3)) {
            startJump(base);
        }
        return true;
    }

    private boolean aimAligned(Vec3 eye,Vec3 hit,float tolerance) {
        float[] exact=lookAt(eye,hit);
        float dy=Math.abs(MathHelper.wrapAngleTo180_float(exact[0]-mc.thePlayer.rotationYaw));
        float dp=Math.abs(exact[1]-mc.thePlayer.rotationPitch);
        return dy<=tolerance && dp<=tolerance;
    }

    private void applyAim(float[] exact) {
        float maxStep=Math.min(rotateStep.getValue(),MAX_ROTATION_STEP);
        float yawDelta=MathHelper.wrapAngleTo180_float(exact[0]-mc.thePlayer.rotationYaw);
        float pitchDelta=MathHelper.clamp_float(exact[1]-mc.thePlayer.rotationPitch,-90f,90f);
        float yawStep=stepToward(yawDelta,maxStep);
        float pitchStep=stepToward(pitchDelta,maxStep);
        aimYaw=mc.thePlayer.rotationYaw+quantize(yawStep);
        aimPitch=MathHelper.clamp_float(mc.thePlayer.rotationPitch+quantize(pitchStep),-90f,90f);
    }

    private static float stepToward(float delta,float maxStep) {
        float remaining=Math.abs(delta);
        if(remaining<=0.001f)return 0f;
        float step=Math.min(remaining,maxStep);
        if(remaining<maxStep) {
            step=Math.max(2.0f,remaining*0.7f);
        }
        step*=0.92f+0.08f*(float)Math.random();
        step=Math.min(step,remaining);
        return Math.copySign(step,delta);
    }

    private static float quantize(float angle) {
        return Math.round(angle/ROTATION_QUANTUM)*ROTATION_QUANTUM;
    }

    @EventTarget
    public void onMove(MoveInputEvent event) {
    }

    @EventTarget(Priority.HIGHEST)
    public void onTick(TickEvent event) {
        if(event.getType()!=EventType.PRE)return;
        if(!updateTrigger())return;
        if(mc.currentScreen!=null)return;
        if(!validContext()) {finishAttempt();return;}

        if(++attemptTicks>ATTEMPT_TIMEOUT) {
            finishAttempt();
            return;
        }

        updateJumpState();

        BlockPos base=currentBase();
        if(enclosedNow(base)) {
            restoreCamera();
            noMaterialTicks=0;
            idleTicks=0;
            attemptTicks=0;
            completeTicks++;
            status="READY";
            if(!jumping && mc.thePlayer.onGround) {
                jumpUsed=false;
                jumpBase=null;
                jumpStartTick=-1;
                roofPrewarming=false;
                roofAimTicks=0;
            }
            inventory.release();
            clearTarget();
            return;
        }
        completeTicks=0;
        status="ENCLOSE";

        int slot=selectMaterialSlot();
        if(slot<0) {
            if(++noMaterialTicks>NO_MATERIAL_TIMEOUT) {
                finishAttempt();
            }
            return;
        }
        noMaterialTicks=0;

        if(execution.getValue()==0)return;

        if(roofPrewarming)return;

        if(targetHitVec==null) {
            if(++idleTicks>IDLE_TIMEOUT) {
                finishAttempt();
            }
            return;
        }

        if(RotationState.isActived())return;
        if(!targetStillValid() || !candidateGoal(targetGoal)) {clearTarget();return;}

        long now=System.currentTimeMillis();
        if(now-lastPlaceTime<placeDelay.getValue())return;
        if(mc.thePlayer.ticksExisted-lastPlaceTick<placeTicks.getValue())return;
        IAccessorMinecraft input=(IAccessorMinecraft)mc;
        if(input.getRightClickDelayTimer()>0 || mc.playerController.getIsHittingBlock()
                || mc.thePlayer.isUsingItem())return;
        if(mc.gameSettings.keyBindUseItem.isKeyDown())return;

        ItemStack stack=mc.thePlayer.inventory.getStackInSlot(slot);
        if(stack==null || !(stack.getItem() instanceof ItemBlock))return;
        Block block=((ItemBlock)stack.getItem()).getBlock();
        if(!isUsableMaterial(block))return;

        MovingObjectPosition mop=rayTraceBlock(mc.thePlayer.rotationYaw,mc.thePlayer.rotationPitch,reach());
        if(mop==null || mop.typeOfHit!=MovingObjectPosition.MovingObjectType.BLOCK
                || !targetSupport.equals(mop.getBlockPos()) || mop.sideHit!=targetFace) {
            if(++idleTicks>IDLE_TIMEOUT) {
                finishAttempt();
            }
            return;
        }

        MovingObjectPosition crosshair=mc.objectMouseOver;
        if(crosshair==null || crosshair.typeOfHit!=MovingObjectPosition.MovingObjectType.BLOCK
                || !targetSupport.equals(crosshair.getBlockPos()) || crosshair.sideHit!=targetFace
                || crosshair.hitVec==null || crosshair.hitVec.distanceTo(mop.hitVec)>0.05D)return;

        if(inventory.acquire(slot)<0)return;
        ItemStack held=mc.thePlayer.getHeldItem();
        if(held==null || !(held.getItem() instanceof ItemBlock)) {
            inventory.release();
            return;
        }
        BlockPos goal=targetGoal;
        input.callRightClickMouse();
        clickedBeforeUpdate=true;
        lastPlaceTime=now;
        lastPlaceTick=mc.thePlayer.ticksExisted;
        clearTarget();
        if(goal!=null && !isOpen(goal)) {
            idleTicks=0;
            recentPlacements.put(goal,mc.thePlayer.ticksExisted+CONFIRM_GRACE_TICKS);
        }
    }

    private boolean mappedAllowed() {
        if(!updateTrigger())return false;
        if(!validContext()) {finishAttempt();return false;}
        return execution.getValue()==0 && mappedPlayer==mc.thePlayer && mappedWorld==mc.theWorld
                && targetHitVec!=null && targetStillValid() && candidateGoal(targetGoal)
                && !RotationState.isActived() && !mc.gameSettings.keyBindUseItem.isKeyDown();
    }

    @EventTarget(Priority.LOWEST)
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        executeMappedUpdate();
    }

    private void executeMappedUpdate() {
        if(mappedInFlight)return;
        if(!mappedAllowed() || roofPrewarming || triggerKey.getValue()==0)return;
        int tick=mc.thePlayer.ticksExisted;
        if(mappedPreparedTick!=tick || lastMappedAttemptTick==tick
                && mappedAttemptPlayer==mc.thePlayer && mappedAttemptWorld==mc.theWorld)return;
        long now=System.currentTimeMillis();
        if(now-lastPlaceTime<placeDelay.getValue() || tick-lastPlaceTick<placeTicks.getValue())return;
        MovingObjectPosition hit=rayTraceBlock(mc.thePlayer.rotationYaw,mc.thePlayer.rotationPitch,reach());
        if(hit==null || hit.typeOfHit!=MovingObjectPosition.MovingObjectType.BLOCK
                || !targetSupport.equals(hit.getBlockPos()) || targetFace!=hit.sideHit)return;
        int slot=selectMaterialSlot();
        if(slot<0 || inventory.acquire(slot)<0)return;
        BlockPos goal=targetGoal;
        long generation=attemptGeneration;
        lastMappedAttemptTick=tick;
        mappedAttemptPlayer=mc.thePlayer;
        mappedAttemptWorld=mc.theWorld;
        boolean placed;
        mappedInFlight=true;
        try {
            placed=mappedApi.placeBlock(targetSupport,targetFace,hit.hitVec,reach(),
                    ()->generation==attemptGeneration && mappedAllowed());
        } finally {
            mappedInFlight=false;
        }
        if(generation!=attemptGeneration)return;
        if(attemptPlayer!=mc.thePlayer || attemptWorld!=mc.theWorld) {
            updateTrigger();
            return;
        }
        lastPlaceTime=now;
        lastPlaceTick=tick;
        if(placed)idleTicks=0;
        clearTarget();
        if(placed && mc.theWorld!=null && !isOpen(goal))recentPlacements.put(goal,tick+CONFIRM_GRACE_TICKS);
    }

    private void updateJumpState() {
        if(!jumping)return;
        if(!mc.thePlayer.onGround && mc.thePlayer.motionY>0D)jumpAirborne=true;
        int since=mc.thePlayer.ticksExisted-jumpStartTick;
        if(mc.thePlayer.onGround && since>2) {
            if(!jumpAirborne || (execution.getValue()==0 && !hasRoofSupport(currentBase()))) {
                jumpUsed=false;
                if(execution.getValue()==0)mappedJumpMissed=true;
                jumpBase=null;
                jumpStartTick=-1;
                roofAimTicks=0;
            }
            jumping=false;
            jumpPending=false;
            roofPrewarming=false;
            clearTarget();
            return;
        }
        if(since>JUMP_TIMEOUT) {
            jumping=false;
            jumpPending=false;
            roofPrewarming=false;
            clearTarget();
        }
    }

    private void startJump(BlockPos base) {
        jumpRoofY=roofY(base);
        jumpUsed=true;
        jumpPending=true;
        jumpAirborne=false;
        jumping=true;
        roofPrewarming=false;
        jumpBase=base;
        jumpStartTick=mc.thePlayer.ticksExisted;
        clearTarget();
    }

    private boolean enclosedNow(BlockPos base) {
        if(!wallsClosed(base))return false;
        for(BlockPos roof:roofGoals(base))if(isOpen(roof))return false;
        return true;
    }

    private boolean wallsClosed(BlockPos base) {
        for(int y=0;y<roofY(base)-base.getY();y++)for(BlockPos goal:wallGoals(base,y))if(isOpen(goal))return false;
        return true;
    }

    private int roofY(BlockPos base) {
        if(jumpBase!=null && (jumping || jumpUsed))return jumpRoofY;
        return -MathHelper.floor_double(-mc.thePlayer.getEntityBoundingBox().maxY+1.0E-7D);
    }

    private int[] footprint() {
        AxisAlignedBB box=mc.thePlayer.getEntityBoundingBox();
        return new int[]{MathHelper.floor_double(box.minX+1.0E-7D),
                MathHelper.floor_double(box.maxX-1.0E-7D),
                MathHelper.floor_double(box.minZ+1.0E-7D),
                MathHelper.floor_double(box.maxZ-1.0E-7D)};
    }

    private List<BlockPos> wallGoals(BlockPos base,int y) {
        int[] f=footprint();
        List<BlockPos> result=new ArrayList<>();
        for(int z=f[2];z<=f[3];z++)result.add(new BlockPos(f[1]+1,base.getY()+y,z));
        for(int x=f[0];x<=f[1];x++)result.add(new BlockPos(x,base.getY()+y,f[3]+1));
        for(int z=f[2];z<=f[3];z++)result.add(new BlockPos(f[0]-1,base.getY()+y,z));
        for(int x=f[0];x<=f[1];x++)result.add(new BlockPos(x,base.getY()+y,f[2]-1));
        if(y==roofY(base)-base.getY())result.sort((a,b)->Double.compare(horizontalDistanceSquared(b),horizontalDistanceSquared(a)));
        return result;
    }

    private List<BlockPos> roofGoals(BlockPos base) {
        int[] f=footprint();
        List<BlockPos> result=new ArrayList<>();
        for(int x=f[0];x<=f[1];x++)for(int z=f[2];z<=f[3];z++)
            result.add(new BlockPos(x,roofY(base),z));
        result.sort((a,b)->Double.compare(horizontalDistanceSquared(b),horizontalDistanceSquared(a)));
        return result;
    }

    private double horizontalDistanceSquared(BlockPos pos) {
        double dx=pos.getX()+0.5D-mc.thePlayer.posX;
        double dz=pos.getZ()+0.5D-mc.thePlayer.posZ;
        return dx*dx+dz*dz;
    }

    private void findBestPlacement(BlockPos base) {
        clearTarget();
        Vec3 eye=mc.thePlayer.getPositionEyes(1f);
        double reach=reach();

        if(execution.getValue()==0) {
            for(BlockPos roof:roofGoals(base))if(isOpen(roof) && tryPlaceRoof(roof,eye,reach))return;
            if((jumping || jumpUsed) && !hasRoofSupport(base) && tryPlaceTip(base,eye,reach))return;
            if(!wallsClosed(base))sidesAim(eye,reach,base);
            return;
        }

        if(!wallsClosed(base)) {
            sidesAim(eye,reach,base);
            return;
        }

        boolean missingRoof=false;
        for(BlockPos roof:roofGoals(base))if(isOpen(roof)) {
            missingRoof=true;
            if(tryPlaceRoof(roof,eye,reach))return;
        }
        if(!missingRoof)return;

        if(jumping || jumpUsed) {
            if(tryPlaceTip(base,eye,reach))return;
        }

        if(!jumpUsed && mc.thePlayer.onGround) {
            startJump(base);
        }
    }

    private boolean hasRoofSupport(BlockPos base) {
        for(BlockPos roof:roofGoals(base))for(EnumFacing face:EnumFacing.values())
            if(isSolidSupport(roof.offset(face)))return true;
        return false;
    }

    private boolean tryPlaceRoof(BlockPos roof,Vec3 eye,double reach) {
        if(!candidateGoal(roof))return false;
        for(EnumFacing face:EnumFacing.values()) {
            BlockPos support=roof.offset(face);
            if(!isSolidSupport(support))continue;
            if(tryPlaceOnBlock(support,eye,reach,roof))return true;
        }
        return false;
    }

    private boolean tryPlaceTip(BlockPos base,Vec3 eye,double reach) {
        for(BlockPos goal:wallGoals(base,roofY(base)-base.getY())) {
            if(!isOpen(goal) || !candidateGoal(goal))continue;
            for(EnumFacing face:EnumFacing.values()) {
                BlockPos support=goal.offset(face);
                if(!isSolidSupport(support))continue;
                if(tryPlaceOnBlock(support,eye,reach,goal))return true;
            }
        }
        return false;
    }

    private boolean isSolidSupport(BlockPos pos) {
        if(isOpen(pos))return false;
        Block block=mc.theWorld.getBlockState(pos).getBlock();
        return block.getMaterial().blocksMovement() && !BlockUtil.isInteractable(block);
    }

    private void sidesAim(Vec3 eye,double reach,BlockPos base) {
        List<BlockPos> goals=new ArrayList<>();
        for(int y=roofY(base)-base.getY()-1;y>=0;y--) {
            for(BlockPos goal:wallGoals(base,y)) {
                if(candidateGoal(goal))goals.add(goal);
            }
        }
        goals.sort((a,b)->Double.compare(turnCost(eye,a),turnCost(eye,b)));
        for(BlockPos goal:goals) {
            for(EnumFacing face:EnumFacing.values()) {
                BlockPos support=goal.offset(face);
                if(isOpen(support))continue;
                if(tryPlaceOnBlock(support,eye,reach,goal))return;
            }
        }
    }

    private double turnCost(Vec3 eye,BlockPos goal) {
        float[] angles=lookAt(eye,new Vec3(goal.getX()+0.5D,goal.getY()+0.5D,goal.getZ()+0.5D));
        float yaw=MathHelper.wrapAngleTo180_float(angles[0]-mc.thePlayer.rotationYaw);
        float pitch=angles[1]-mc.thePlayer.rotationPitch;
        return (double)yaw*yaw+(double)pitch*pitch;
    }

    private boolean targetReachable(Vec3 eye) {
        float[] angles=lookAt(eye,targetHitVec);
        MovingObjectPosition trace=rayTraceBlock(angles[0],angles[1],reach());
        return trace!=null && trace.typeOfHit==MovingObjectPosition.MovingObjectType.BLOCK
                && targetSupport.equals(trace.getBlockPos()) && targetFace==trace.sideHit
                && quantizedAimReaches(angles,targetSupport,targetFace,reach());
    }

    private boolean quantizedAimReaches(float[] angles,BlockPos support,EnumFacing face,double range) {
        float yaw=mc.thePlayer.rotationYaw+quantize(MathHelper.wrapAngleTo180_float(angles[0]-mc.thePlayer.rotationYaw));
        float pitch=MathHelper.clamp_float(mc.thePlayer.rotationPitch+quantize(angles[1]-mc.thePlayer.rotationPitch),-90f,90f);
        MovingObjectPosition trace=rayTraceBlock(yaw,pitch,range);
        return trace!=null && trace.typeOfHit==MovingObjectPosition.MovingObjectType.BLOCK
                && support.equals(trace.getBlockPos()) && face==trace.sideHit;
    }

    private boolean tryPlaceOnBlock(BlockPos support,Vec3 eye,double reach,BlockPos goal) {
        if(!candidateGoal(goal))return false;
        if(isOpen(support))return false;
        Block supportBlock=mc.theWorld.getBlockState(support).getBlock();
        if(!supportBlock.getMaterial().blocksMovement())return false;
        if(BlockUtil.isInteractable(supportBlock))return false;
        for(EnumFacing face:EnumFacing.values()) {
            if(!support.offset(face).equals(goal))continue;
            double[] mappedSamples={0.5D,0.3D,0.7D,0.1D,0.9D};
            boolean mapped=execution.getValue()==0;
            int n=mapped?mappedSamples.length-1:(int)Math.round(1/STEP);
            for(int r=0;r<=n;r++) {
                double v=mapped?mappedSamples[r]:r*STEP+(Math.random()*JIT*2-JIT);
                v=clamp(v,0,1);
                for(int c=0;c<=n;c++) {
                    double u=mapped?mappedSamples[c]:c*STEP+(Math.random()*JIT*2-JIT);
                    u=clamp(u,0,1);
                    Vec3 hit=getHitPosOnFace(support,face,u,v);
                    float[] rot=lookAt(eye,hit);
                    MovingObjectPosition mop=rayTraceBlock(rot[0],rot[1],reach);
                    if(mop==null || mop.typeOfHit!=MovingObjectPosition.MovingObjectType.BLOCK)continue;
                    if(!mop.getBlockPos().equals(support) || mop.sideHit!=face)continue;
                    if(!quantizedAimReaches(rot,support,face,reach))continue;
                    targetGoal=goal;
                    targetSupport=support;
                    targetFace=face;
                    targetHitVec=mop.hitVec;
                    return true;
                }
            }
        }
        return false;
    }

    private boolean candidateGoal(BlockPos pos) {
        if(!isOpen(pos))return false;
        if(!mc.theWorld.isBlockLoaded(pos))return false;
        if(!mc.theWorld.getWorldBorder().contains(pos))return false;
        Integer until=recentPlacements.get(pos);
        if(until!=null) {
            if(until>mc.thePlayer.ticksExisted)return false;
            recentPlacements.remove(pos);
        }
        AxisAlignedBB box=new AxisAlignedBB(pos,pos.add(1,1,1));
        if(box.intersectsWith(mc.thePlayer.getEntityBoundingBox()))return false;
        return mc.theWorld.checkNoEntityCollision(box,mc.thePlayer);
    }

    private boolean targetStillValid() {
        if(targetGoal==null || targetSupport==null || targetFace==null)return false;
        if(!isOpen(targetGoal))return false;
        if(isOpen(targetSupport))return false;
        Block block=mc.theWorld.getBlockState(targetSupport).getBlock();
        return block.getMaterial().blocksMovement() && !BlockUtil.isInteractable(block);
    }

    private Vec3 getHitPosOnFace(BlockPos block,EnumFacing face,double u,double v) {
        double x=block.getX()+0.5;
        double y=block.getY()+0.5;
        double z=block.getZ()+0.5;
        switch(face) {
            case DOWN:
                y=block.getY()+INSET;
                x=block.getX()+u;
                z=block.getZ()+v;
                break;
            case UP:
                y=block.getY()+1.0-INSET;
                x=block.getX()+u;
                z=block.getZ()+v;
                break;
            case NORTH:
                z=block.getZ()+INSET;
                x=block.getX()+u;
                y=block.getY()+v;
                break;
            case SOUTH:
                z=block.getZ()+1.0-INSET;
                x=block.getX()+u;
                y=block.getY()+v;
                break;
            case WEST:
                x=block.getX()+INSET;
                z=block.getZ()+u;
                y=block.getY()+v;
                break;
            case EAST:
            default:
                x=block.getX()+1.0-INSET;
                z=block.getZ()+u;
                y=block.getY()+v;
                break;
        }
        return new Vec3(x,y,z);
    }

    private boolean isOpen(BlockPos pos) {
        Block block=mc.theWorld.getBlockState(pos).getBlock();
        return block==Blocks.air || block==Blocks.water || block==Blocks.flowing_water
                || block==Blocks.lava || block==Blocks.flowing_lava || block==Blocks.fire;
    }

    private MovingObjectPosition rayTraceBlock(float yaw,float pitch,double range) {
        float yawRad=(float)Math.toRadians(yaw);
        float pitchRad=(float)Math.toRadians(pitch);
        double x=-Math.sin(yawRad)*Math.cos(pitchRad);
        double y=-Math.sin(pitchRad);
        double z=Math.cos(yawRad)*Math.cos(pitchRad);
        Vec3 start=mc.thePlayer.getPositionEyes(1f);
        Vec3 end=start.addVector(x*range,y*range,z*range);
        return mc.theWorld.rayTraceBlocks(start,end);
    }

    private static float[] lookAt(Vec3 eye,Vec3 hit) {
        double dx=hit.xCoord-eye.xCoord;
        double dy=hit.yCoord-eye.yCoord;
        double dz=hit.zCoord-eye.zCoord;
        double horizontal=Math.sqrt(dx*dx+dz*dz);
        float yaw=(float)(Math.atan2(dz,dx)*180.0/Math.PI)-90f;
        float pitch=(float)(-Math.atan2(dy,horizontal)*180.0/Math.PI);
        return new float[]{MathHelper.wrapAngleTo180_float(yaw),
                MathHelper.clamp_float(pitch,-90f,90f)};
    }

    private static double clamp(double value,double low,double high) {
        return value<low?low:(value>high?high:value);
    }

    private int materialId(Block block) {
        if(block==Blocks.wool)return 1;
        if(block==Blocks.planks)return 2;
        if(block==Blocks.end_stone)return 3;
        if(block==Blocks.hardened_clay || block==Blocks.stained_hardened_clay)return 4;
        return block==Blocks.obsidian?5:0;
    }

    private static boolean isUsableMaterial(Block block) {
        if(block==null || block==Blocks.air)return false;
        if(block.getMaterial()==net.minecraft.block.material.Material.air)return false;
        if(!block.isFullCube())return false;
        if(block instanceof net.minecraft.block.BlockFalling)return false;
        if(block==Blocks.tnt)return false;
        return !BlockUtil.isInteractable(block);
    }

    private int selectMaterialSlot() {
        int mode=material.getValue();
        int preferred=-1;
        float preferredStrength=-1f;
        int fallback=-1;
        float fallbackStrength=-1f;
        for(int i=0;i<9;i++) {
            ItemStack stack=mc.thePlayer.inventory.getStackInSlot(i);
            if(stack==null || stack.stackSize<=0 || !(stack.getItem() instanceof ItemBlock))continue;
            Block block=((ItemBlock)stack.getItem()).getBlock();
            if(!isUsableMaterial(block))continue;
            float hardness=block.getBlockHardness(mc.theWorld,new BlockPos(mc.thePlayer));
            if(!Float.isFinite(hardness) || hardness<0f)continue;
            if(fallback<0 || hardness>fallbackStrength) {fallback=i;fallbackStrength=hardness;}
            if(mode!=0 && materialId(block)!=mode)continue;
            if(preferred<0 || hardness>preferredStrength) {preferred=i;preferredStrength=hardness;}
        }
        return mode!=0 && preferred>=0?preferred:fallback;
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        resetRuntime();
        prevTriggerDown=false;
        if(isEnabled())status="READY";
    }

    @Override public String[] getSuffix() {return new String[]{Lang.t(status)};}
}
