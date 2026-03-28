package ch.zorty.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
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


@Mixin(ClientPacketListener.class)
public class AttributeMixin {

    @Unique
    private AttributeModifier SPRINTING_MODIFIER = new AttributeModifier(Identifier.withDefaultNamespace("sprinting"), 0.30000001192092896, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

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
                                             @Local ClientboundUpdateAttributesPacket.AttributeSnapshot attribute) {
        Minecraft mc = Minecraft.getInstance();

        // Check if client = entity and only edit movement_speed attributes
        if (mc.player == null || mc.level == null) return;
        if (entity != mc.player) return;
        if (instance.getAttribute() != Attributes.MOVEMENT_SPEED) return;

        boolean sprint = instance.hasModifier(Identifier.withDefaultNamespace("sprinting"));
        attribute.modifiers().removeIf(modifier -> modifier.is(Identifier.withDefaultNamespace("sprinting")));
        if(sprint) { attribute.modifiers().add(SPRINTING_MODIFIER); }


    }
}