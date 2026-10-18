package myau.ui.components;

import myau.Myau;
import myau.module.modules.HUD;
import myau.property.properties.IntProperty;
import myau.ui.Component;
import myau.ui.Lang;
import myau.ui.dataset.BindStage;
import myau.util.KeyBindUtil;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

import java.util.concurrent.atomic.AtomicInteger;

public class KeyPropertyComponent implements Component {
    private final IntProperty property;
    private final ModuleComponent parentModule;
    private boolean binding;
    private static KeyPropertyComponent escapeOwner;
    private int offsetY;
    private int x;
    private int y;

    public KeyPropertyComponent(IntProperty property, ModuleComponent parentModule, int offsetY) {
        this.property = property;
        this.parentModule = parentModule;
        this.offsetY = offsetY;
    }

    @Override
    public void draw(AtomicInteger offset) {
        GL11.glPushMatrix();
        GL11.glScaled(0.5D, 0.5D, 0.5D);
        String text = this.binding
                ? Lang.t(BindStage.binding)
                : Lang.t(this.property.getName()).replace("-", " ") + ": " + KeyBindUtil.getKeyName(this.property.getValue());
        int color = ((HUD) Myau.moduleManager.modules.get(HUD.class))
                .getColor(System.currentTimeMillis(), offset.get()).getRGB();
        Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(text,
                (float) ((this.parentModule.category.getX() + 4) * 2),
                (float) ((this.parentModule.category.getY() + this.offsetY + 3) * 2), color);
        GL11.glPopMatrix();
    }

    @Override
    public void update(int mousePosX, int mousePosY) {
        this.x = this.parentModule.category.getX();
        this.y = this.parentModule.category.getY() + this.offsetY;
    }

    @Override
    public void mouseDown(int mouseX, int mouseY, int button) {
        if (!this.parentModule.panelExpand) return;
        if (this.isHovered(mouseX, mouseY) && button == 0) {
            this.binding = !this.binding;
        } else if (this.binding) {
            this.property.setValue(button - 100);
            this.binding = false;
        }
        if (this.binding) {
            escapeOwner = this;
        } else if (escapeOwner == this) {
            escapeOwner = null;
        }
    }

    @Override
    public void mouseReleased(int x, int y, int button) {
    }

    @Override
    public void keyTyped(char chatTyped, int keyCode) {
        if (!this.binding) return;
        if (keyCode == 1) {
            this.property.setValue(0);
            this.binding = false;
        } else {
            this.property.setValue(keyCode == 11 ? 0 : keyCode);
            this.binding = false;
        }
        if (escapeOwner == this) {
            escapeOwner = null;
        }
    }

    public static boolean consumeEscape(char typedChar, int key) {
        KeyPropertyComponent owner = escapeOwner;
        if (owner == null) {
            return false;
        }
        escapeOwner = null;
        owner.keyTyped(typedChar, key);
        return true;
    }

    @Override
    public void setComponentStartAt(int newOffsetY) {
        this.offsetY = newOffsetY;
    }

    @Override
    public int getHeight() {
        return 12;
    }

    @Override
    public boolean isVisible() {
        return this.property.isVisible();
    }

    private boolean isHovered(int mouseX, int mouseY) {
        return mouseX > this.x && mouseX < this.x + this.parentModule.category.getWidth()
                && mouseY > this.y - 1 && mouseY < this.y + 12;
    }
}
