package myau.module.modules;

import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ColorProperty;
import myau.property.properties.IntProperty;
import net.minecraft.entity.EntityLivingBase;

public class DamageTint extends Module {
    public static DamageTint instance;

    public final ColorProperty tintColor = new ColorProperty("tint-color", 16711680);
    public final IntProperty tintAlpha = new IntProperty("tint-alpha", 76, 0, 255);
    public final BooleanProperty fade = new BooleanProperty("fade-out", false);

    public DamageTint() {
        super("DamageTint", false);
    }

    @Override
    public void onEnabled() {
        instance = this;
    }

    @Override
    public void onDisabled() {
        instance = null;
    }

    public static float computeAlpha(EntityLivingBase entity) {
        if (instance == null) {
            return 1.0F;
        }
        if (!instance.fade.getValue()) {
            return instance.tintAlpha.getValue() / 255.0F;
        }
        float maxHurt = entity.maxHurtTime;
        if (maxHurt <= 0.0F) {
            return instance.tintAlpha.getValue() / 255.0F;
        }
        float percent = 1.0F - (float) entity.hurtTime / maxHurt;
        percent = (percent < 0.5F) ? (percent / 0.5F) : ((1.0F - percent) / 0.5F);
        return (instance.tintAlpha.getValue() / 255.0F) * percent;
    }
}
