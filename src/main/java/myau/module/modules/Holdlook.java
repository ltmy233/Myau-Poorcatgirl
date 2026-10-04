package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.IntProperty;
import myau.util.KeyBindUtil;
import net.minecraft.client.Minecraft;

public class Holdlook extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final IntProperty rearCamKey = new IntProperty("rear-cam-key", 0, -100, 255);
    public final IntProperty frontCamKey = new IntProperty("front-cam-key", 0, -100, 255);

    private boolean rearActive;
    private boolean frontActive;
    private int savedPerspective;

    public Holdlook() {
        super("Holdlook", false);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        if (event.getType() != EventType.PRE) {
            return;
        }

        if (mc.currentScreen != null) {
            if (this.rearActive || this.frontActive) {
                mc.gameSettings.thirdPersonView = this.savedPerspective;
                this.rearActive = false;
                this.frontActive = false;
            }
            return;
        }

        boolean rearDown = isPressed(this.rearCamKey.getValue());
        boolean frontDown = isPressed(this.frontCamKey.getValue());

        if (rearDown && !this.rearActive) {
            this.savedPerspective = mc.gameSettings.thirdPersonView;
            mc.gameSettings.thirdPersonView = 1;
            this.rearActive = true;
        } else if (!rearDown && this.rearActive) {
            mc.gameSettings.thirdPersonView = this.frontActive ? 2 : this.savedPerspective;
            this.rearActive = false;
        }

        if (frontDown && !this.frontActive) {
            if (!this.rearActive) {
                this.savedPerspective = mc.gameSettings.thirdPersonView;
            }
            mc.gameSettings.thirdPersonView = 2;
            this.frontActive = true;
        } else if (!frontDown && this.frontActive) {
            mc.gameSettings.thirdPersonView = this.rearActive ? 1 : this.savedPerspective;
            this.frontActive = false;
        }
    }

    private static boolean isPressed(int key) {
        return key != 0 && KeyBindUtil.isKeyDown(key);
    }

    @Override
    public void onDisabled() {
        if (this.rearActive || this.frontActive) {
            mc.gameSettings.thirdPersonView = 0;
            this.rearActive = false;
            this.frontActive = false;
        }
    }
}
