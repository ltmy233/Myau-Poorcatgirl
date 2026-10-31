package poorcatgirl.module.modules;

import poorcatgirl.module.Module;
import poorcatgirl.property.properties.PercentProperty;

public class NoHurtCam extends Module {
    public final PercentProperty multiplier = new PercentProperty("multiplier", 0);

    public NoHurtCam() {
        super("NoHurtCam", false, true);
    }
}
