package myau.mixin;

import net.minecraft.client.gui.GuiSlider;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@SideOnly(Side.CLIENT)
@Mixin({GuiSlider.class})
public interface IAccessorGuiSlider {
    @Accessor("sliderPosition")
    float getSliderPosition();

    @Invoker("getDisplayString")
    String callGetDisplayString();
}
