package myau.module.modules;

import myau.module.Module;
import myau.ui.ClickGui;
import myau.ui.Lang;
import myau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

public class GuiModule extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private ClickGui clickGui;
    public final ModeProperty language = new ModeProperty("language", 0, new String[]{"中文", "EN"});

    public GuiModule() {
        super("ClickGui", false);
        setKey(Keyboard.KEY_RSHIFT);
        Lang.load();
        this.language.setValue(Lang.isChinese() ? 0 : 1);
    }

    @Override
    public void onEnabled() {
        setEnabled(false);
        if(clickGui == null){
            clickGui = new ClickGui();
        }
        mc.displayGuiScreen(clickGui);
    }

    @Override
    public void verifyValue(String name) {
        if (this.language.getName().equals(name)) {
            Lang.setLanguage(this.language.getValue() == 0 ? Lang.ZH : Lang.EN);
        }
    }
}
