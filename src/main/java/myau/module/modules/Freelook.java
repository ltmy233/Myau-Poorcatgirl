package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.LoadWorldEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.util.KeyBindUtil;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

public class Freelook extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static Freelook instance;
    public static boolean perspectiveToggled;
    public static float cameraYaw;
    public static float cameraPitch;

    public final IntProperty freelookKey = new IntProperty("freelook-key", 56, -100, 255);
    public final FloatProperty sensitivity = new FloatProperty("sensitivity", 1.0F, 0.1F, 3.0F);
    public final FloatProperty stretchSpeed = new FloatProperty("stretch-speed", 1.0F, 0.1F, 3.0F);
    public final FloatProperty distance = new FloatProperty("camera-distance", 4.0F, 1.0F, 8.0F);
    public final FloatProperty height = new FloatProperty("camera-height", 0.15F, -1.0F, 1.0F);
    public final FloatProperty lookPitch = new FloatProperty("look-pitch", -45.0F, -90.0F, 90.0F);
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"HOLD", "TOGGLE"});

    private boolean prevKeyState;
    private int previousPerspective;
    private float lastFov;
    private float playerYaw;

    public Freelook() {
        super("Freelook", false);
    }

    @Override
    public void setKey(int key) {
        super.setKey(0);
    }

    @Override
    public void onEnabled() {
        instance = this;
        // 快捷键归内部逻辑管，解掉模块自身绑定，免得同一键把模块和视角一起翻面
        this.setKey(0);
        this.prevKeyState = false;
    }

    @Override
    public void onDisabled() {
        instance = null;
        this.prevKeyState = false;
        if (perspectiveToggled) {
            perspectiveToggled = false;
            mc.gameSettings.thirdPersonView = 0;
            if (mc.currentScreen == null && mc.inGameHasFocus) {
                mc.mouseHelper.grabMouseCursor();
            }
            mc.gameSettings.fovSetting = lastFov;
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        if (event.getType() != EventType.POST) {
            return;
        }
        if (mc.currentScreen != null) {
            if (perspectiveToggled && this.mode.getValue() == 0) {
                resetPerspective();
            }
            this.prevKeyState = false;
            return;
        }
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        boolean down = this.freelookKey.getValue() != 0 && KeyBindUtil.isKeyDown(this.freelookKey.getValue());
        if (this.mode.getValue() == 0) {
            if (down && !perspectiveToggled) {
                enterPerspective();
            } else if (!down && perspectiveToggled) {
                resetPerspective();
            }
            this.prevKeyState = down;
            return;
        }
        if (down != this.prevKeyState) {
            onPressed(down);
            this.prevKeyState = down;
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        if (perspectiveToggled) {
            resetPerspective();
        }
        this.prevKeyState = false;
    }

    private void onPressed(boolean state) {
        if (state) {
            cameraYaw = mc.thePlayer.rotationYaw;
            cameraPitch = mc.thePlayer.rotationPitch;
            if (perspectiveToggled) {
                resetPerspective();
            } else {
                enterPerspective();
            }
        } else if (this.mode.getValue() == 0) {
            resetPerspective();
        }
    }

    private void enterPerspective() {
        perspectiveToggled = true;
        previousPerspective = mc.gameSettings.thirdPersonView;
        mc.gameSettings.thirdPersonView = 1;
        if (mc.entityRenderer != null) mc.entityRenderer.loadEntityShader(null);
        if (mc.renderGlobal != null) mc.renderGlobal.setDisplayListEntitiesDirty();
        lastFov = mc.gameSettings.fovSetting;
        this.playerYaw = mc.thePlayer.rotationYaw;
        cameraYaw = this.playerYaw;
        cameraPitch = this.lookPitch.getValue();
    }

    public void resetPerspective() {
        perspectiveToggled = false;
        mc.gameSettings.thirdPersonView = previousPerspective;
        if (mc.entityRenderer != null && previousPerspective == 0) mc.entityRenderer.loadEntityShader(mc.getRenderViewEntity());
        if (mc.renderGlobal != null) mc.renderGlobal.setDisplayListEntitiesDirty();
        if (mc.currentScreen == null && mc.inGameHasFocus) {
            mc.mouseHelper.grabMouseCursor();
        }
        mc.gameSettings.fovSetting = lastFov;
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled() && perspectiveToggled;
    }

    public static boolean overrideMouse(Minecraft minecraft) {
        if (!minecraft.inGameHasFocus) {
            return false;
        }
        if (instance == null || !instance.isEnabled() || !perspectiveToggled) {
            return true;
        }
        minecraft.mouseHelper.mouseXYChange();
        float sens = minecraft.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        float mult = sens * sens * sens * 8.0F * instance.sensitivity.getValue();
        float deltaScrollX = minecraft.mouseHelper.deltaX * mult;
        float deltaScrollY = minecraft.mouseHelper.deltaY * mult;
        cameraYaw += deltaScrollX * 0.15F;
        cameraPitch += deltaScrollY * 0.15F;
        cameraPitch = Math.max(-90.0F, Math.min(90.0F, cameraPitch));
        return false;
    }

}
