package myau.module.modules;

import myau.module.Module;
import myau.property.properties.ModeProperty;
import myau.ui.impl.clickgui.rise.RiseClickGUI;
import net.minecraft.client.Minecraft;

import java.awt.*;

public class RiseClickGUIModule extends Module {

    // Accent color palette used by the Rise GUI theme screen.
    public static final int[] COLORS = {
            0xFF4FC3F7, // Sky Blue
            0xFF81C784, // Green
            0xFFFF8A65, // Orange
            0xFFBA68C8, // Purple
            0xFFFFD54F, // Yellow
            0xFFFF6B6B, // Red
            0xFF4DB6AC, // Teal
            0xFFFFFFFF, // White
    };
    public static final String[] COLOR_NAMES = {
            "Sky Blue", "Green", "Orange", "Purple", "Yellow", "Red", "Teal", "White"
    };

    public ModeProperty accentColor = new ModeProperty("Color", 0, COLOR_NAMES);

    public Color getAccentColor() {
        int idx = accentColor.getValue();
        if (idx < 0 || idx >= COLORS.length) idx = 0;
        return new Color(COLORS[idx], true);
    }

    public RiseClickGUIModule() {
        super("RiseClickGUI", false, false, "Rise v6 style ClickGUI");
    }

    @Override
    public void onEnabled() {
        super.onEnabled();
        if (Minecraft.getMinecraft().theWorld == null) {
            this.setEnabled(false);
            return;
        }
        Minecraft.getMinecraft().displayGuiScreen(RiseClickGUI.getInstance());
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        Minecraft.getMinecraft().displayGuiScreen(null);
        if (Minecraft.getMinecraft().currentScreen == null) {
            Minecraft.getMinecraft().setIngameFocus();
        }
    }
}
