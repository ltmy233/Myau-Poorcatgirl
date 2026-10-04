package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.TickEvent;
import myau.mixin.IAccessorShaderGroup;
import myau.module.Module;
import myau.property.properties.FloatProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Shader;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.client.shader.ShaderUniform;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.util.List;

public class Saturation extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final ResourceLocation SHADER_LOCATION = new ResourceLocation("minecraft:shaders/post/color_convolve.json");

    public static ShaderGroup shaderGroup;

    public final FloatProperty saturation = new FloatProperty("saturation", 1.0F, 0.0F, 5.0F);

    private float lastSaturation = 1.0F;

    public Saturation() {
        super("Saturation", false);
    }

    @Override
    public void onEnabled() {
        loadShader();
    }

    @Override
    public void onDisabled() {
        removeShader();
    }

    @EventTarget
    public void onUpdate(TickEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        if (event.getType() != EventType.PRE) {
            return;
        }
        float current = this.saturation.getValue();
        if (!isShaderActive()) {
            loadShader();
        }
        if (current != lastSaturation) {
            lastSaturation = current;
            applySaturation();
        }
    }

    private void loadShader() {
        if (mc.theWorld == null) {
            return;
        }
        if (isShaderActive()) {
            return;
        }
        try {
            ShaderGroup group = new ShaderGroup(mc.getTextureManager(), mc.getResourceManager(), mc.getFramebuffer(), SHADER_LOCATION);
            group.createBindFramebuffers(mc.displayWidth, mc.displayHeight);
            shaderGroup = group;
            lastSaturation = this.saturation.getValue();
            applySaturation();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void removeShader() {
        if (shaderGroup == null) {
            return;
        }
        shaderGroup.deleteShaderGroup();
        shaderGroup = null;
    }

    private void applySaturation() {
        if (shaderGroup == null) {
            return;
        }
        List<Shader> shaders = ((IAccessorShaderGroup) shaderGroup).getListShaders();
        if (shaders == null) {
            return;
        }
        for (Shader shader : shaders) {
            ShaderUniform shaderUniform = shader.getShaderManager().getShaderUniform("Saturation");
            if (shaderUniform != null) {
                shaderUniform.set(lastSaturation);
            }
        }
    }

    private boolean isShaderActive() {
        return shaderGroup != null;
    }
}
