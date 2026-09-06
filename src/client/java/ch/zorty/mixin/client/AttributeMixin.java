package ch.zorty.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;

import java.util.ArrayList;
import java.util.List;


@Mixin(ClientPacketListener.class)
public class AttributeMixin {

    @Unique
    private final Identifier SPRINT_ID = Identifier.withDefaultNamespace("sprinting");

    @Inject(
            method = "handleUpdateAttributes(Lnet/minecraft/network/protocol/game/ClientboundUpdateAttributesPacket;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;removeModifiers()V"
            )
    )
    private void beforeAddTransientModifiers(ClientboundUpdateAttributesPacket packet, CallbackInfo ci,
                                             @Local Entity entity,
                                             @Local AttributeInstance instance,
                                             @Local LocalRef<ClientboundUpdateAttributesPacket.AttributeSnapshot> attribute) {
        Minecraft mc = Minecraft.getInstance();

        // Check if client = entity and only edit movement_speed attributes
        if (mc.player == null || mc.level == null) return;
        if (entity != mc.player) return;
        if (instance.getAttribute() != Attributes.MOVEMENT_SPEED) return;

        boolean sprint = instance.hasModifier(SPRINT_ID);
        // Integrated-server packets can contain immutable modifiers. Replace the local snapshot
        // instead of mutating the packet, while preserving the client's sprint modifier.
        AttributeSnapshot snapshot = attribute.get();
        List<AttributeModifier> modifiers = new ArrayList<>(snapshot.modifiers());
        // Use the already existing client side sprinting modifier instead of the vanilla client one to stop this mod
        // from acting like an 1e-8 speedhack on certain server implementations (looking at you minestom)
        if (!sprint) {
            modifiers.removeIf(modifier -> modifier.is(SPRINT_ID));
        } else if (modifiers.stream().noneMatch(modifier -> modifier.is(SPRINT_ID))) {
            modifiers.add(instance.getModifier(SPRINT_ID));
        }
        attribute.set(new ClientboundUpdateAttributesPacket.AttributeSnapshot(snapshot.attribute(), snapshot.base(), modifiers));


    }
}
