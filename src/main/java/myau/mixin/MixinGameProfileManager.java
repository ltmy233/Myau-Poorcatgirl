package myau.mixin;

import com.mojang.authlib.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.stream.Stream;

@Mixin(targets = "gg.essential.handlers.GameProfileManager", remap = false)
public class MixinGameProfileManager {

    @Redirect(
            method = "handleGameProfile(Lcom/mojang/authlib/GameProfile;Lgg/essential/mod/Skin;Ljava/lang/String;)Lcom/mojang/authlib/GameProfile;",
            at = @At(value = "INVOKE", target = "Ljava/util/Collection;->stream()Ljava/util/stream/Stream;"),
            remap = false
    )
    private static Stream<Property> myau$safeTextureStream(Collection<Property> textures) {
        try {
            ArrayList<Property> snapshot = new ArrayList<Property>(textures);
            return snapshot.stream();
        } catch (Throwable t) {
            return Stream.empty();
        }
    }

    @Redirect(
            method = "getSafeTexturesValue(Lcom/mojang/authlib/GameProfile;)Ljava/lang/String;",
            at = @At(value = "INVOKE", target = "Ljava/util/Collection;->iterator()Ljava/util/Iterator;"),
            remap = false
    )
    private static Iterator<Property> myau$safeTextureIterator(Collection<Property> textures) {
        try {
            return new ArrayList<Property>(textures).iterator();
        } catch (Throwable t) {
            return new ArrayList<Property>().iterator();
        }
    }
}
