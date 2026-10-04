package myau.mixin;

import net.minecraft.client.gui.GuiOptionSlider;
import net.minecraft.client.settings.GameSettings;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@SideOnly(Side.CLIENT)
@Mixin({GuiOptionSlider.class})
public interface IAccessorGuiOptionSlider {
    @Accessor("sliderValue")
    float getSliderValue();

    @Accessor("options")
    GameSettings.Options getOptions();
}
