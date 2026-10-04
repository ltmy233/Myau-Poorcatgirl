package myau.ui.dataset.impl;

import myau.enums.ChatColors;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.ui.dataset.Slider;
import myau.util.KeyBindUtil;

public class IntSlider extends Slider {
    private final IntProperty property;

    public IntSlider(IntProperty property) {
        this.property = property;
    }

    @Override
    public double getInput() {
        return property.getValue();
    }

    @Override
    public double getMin() {
        return property.getMinimum();
    }

    @Override
    public double getMax() {
        return property.getMaximum();
    }

    @Override
    public void setValue(double value) {
        property.setValue(new Double(value).intValue());
    }

    @Override
    public void setValueString(String value) {
        try {
            property.setValue(Integer.parseInt(value));
        } catch (Exception ignore) {
        }
    }

    @Override
    public String getName() {
        return property.getName().replace("-", " ");
    }

    @Override
    public String getValueString() {
        if (this.property.getName().endsWith("-key")) {
            return KeyBindUtil.getKeyName(this.property.getValue());
        }
        return property.getValue().toString();
    }

    @Override
    public String getValueColorString() {
        if (this.property.getName().endsWith("-key")) {
            return KeyBindUtil.getKeyName(this.property.getValue());
        }
        return ChatColors.formatColor(property.formatValue());
    }

    @Override
    public double getIncrement() {
        return 1;
    }

    @Override
    public boolean isVisible() {
        return property.isVisible();
    }

    @Override
    public void stepping(boolean increment) {
        if (increment) {
            if (property.getValue() >= property.getMaximum()) return;
            property.setValue(property.getValue() + 1);
        } else {
            if (property.getValue() <= property.getMinimum()) return;
            property.setValue(property.getValue() - 1);
        }
    }
}
