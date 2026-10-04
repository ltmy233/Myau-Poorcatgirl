package myau.module.modules;

import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import net.minecraft.client.Minecraft;

public class BetterChat extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final BooleanProperty clear = new BooleanProperty("clear", false);
    public final BooleanProperty rounded = new BooleanProperty("rounded", true);
    public final BooleanProperty timestamps = new BooleanProperty("timestamps", true);
    public final BooleanProperty history = new BooleanProperty("history", false);
    public final BooleanProperty smooth = new BooleanProperty("smooth", true);
    public final IntProperty xOffset = new IntProperty("x-offset", 0, -300, 300);
    public final IntProperty yOffset = new IntProperty("y-offset", 0, -300, 300);
    public final FloatProperty scale = new FloatProperty("scale", mc.gameSettings.chatScale, 0.5F, 2.0F);
    public final FloatProperty width = new FloatProperty("width", mc.gameSettings.chatWidth, 0.0F, 1.0F);

    public BetterChat() {
        super("BetterChat", true);
    }

    @Override
    public void verifyValue(String name) {
        if (mc.gameSettings == null) {
            return;
        }
        if ("scale".equals(name)) {
            mc.gameSettings.chatScale = this.scale.getValue();
        } else if ("width".equals(name)) {
            mc.gameSettings.chatWidth = this.width.getValue();
        }
    }
}
