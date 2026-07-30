package myau.module.modules;

import myau.module.Module;
import myau.ui.impl.clickgui.normal.ClickGuiScreen;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

public class GuiModule extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public GuiModule() {
        super("ClickGui", false);
        setKey(Keyboard.KEY_RSHIFT);
    }

    @Override
    public void onEnabled() {
        setEnabled(false);
        ClickGuiScreen gui = ClickGuiScreen.getInstance();
        mc.displayGuiScreen(gui);
    }
}
