package poorcatgirl.module.modules;

import poorcatgirl.module.Module;
import poorcatgirl.Poorcatgirl;
import poorcatgirl.ui.ClickGui;
import poorcatgirl.ui.Lang;
import poorcatgirl.property.properties.ModeProperty;
import poorcatgirl.property.properties.ColorProperty;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

public class GuiModule extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private ClickGui clickGui;
    public final ModeProperty language = new ModeProperty("language", 0, new String[]{"中文", "EN"});
    public final ColorProperty accentColor = new ColorProperty("accent-color", 0xFF66FF);

    public static int getAccentColor() {
        GuiModule gui = Poorcatgirl.moduleManager == null ? null
                : (GuiModule) Poorcatgirl.moduleManager.modules.get(GuiModule.class);
        return 0xFF000000 | (gui == null ? 0xFF66FF : gui.accentColor.getValue() & 0xFFFFFF);
    }

    public GuiModule() {
        super("ClickGui", false);
        setKey(Keyboard.KEY_RSHIFT);
        Lang.load();
        this.language.setValue(Lang.isChinese() ? 0 : 1);
    }

    @Override
    public void onEnabled() {
        setEnabled(false);
        if (Keyboard.isRepeatEvent()) {
            return;
        }
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
