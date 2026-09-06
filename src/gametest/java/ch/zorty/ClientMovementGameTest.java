package ch.zorty;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

public class ClientMovementGameTest implements FabricClientGameTest {
    private static final Identifier SPRINTING = Identifier.withDefaultNamespace("sprinting");
    private static final EntityDataAccessor<Byte> FLAGS = new EntityDataAccessor<>(0, EntityDataSerializers.BYTE);

    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            context.runOnClient(client -> {
                // Network decoding produces mutable snapshots; integrated servers can deliver immutable ones.
                checkSprintAttributes(client, true);
                checkSprintAttributes(client, false);
                checkMetadata(client);
                checkRemotePlayer(client);
            });
            context.takeScreenshot("clientmovement-26.2");
            LoggerFactory.getLogger("clientmovement-test").info("Sprint, FOV, pose, attribute, and remote-player regression checks passed.");
        }
    }

    private static void checkSprintAttributes(Minecraft client, boolean decode) {
        var player = client.player;
        var movement = player.getAttribute(Attributes.MOVEMENT_SPEED);
        var serverMovement = new AttributeInstance(Attributes.MOVEMENT_SPEED, ignored -> {});
        serverMovement.setBaseValue(0.1);
        movement.setBaseValue(0.1);
        player.setSprinting(true);
        double sprintSpeed = movement.getValue();
        float sprintFov = player.getFieldOfViewModifier(false, 1.0F);

        receiveAttributes(client, new ClientboundUpdateAttributesPacket(player.getId(), List.of(serverMovement)), decode);
        require(player.isSprinting(), "A stale server update must not stop local sprinting");
        require(movement.hasModifier(SPRINTING), "Local sprint speed must survive a server update");
        require(Math.abs(movement.getValue() - sprintSpeed) < 1.0E-7, "Sprint speed must stay constant");
        require(player.getFieldOfViewModifier(false, 1.0F) == sprintFov, "A stale sprint update must not wobble the FOV");

        var effect = new AttributeModifier(Identifier.fromNamespaceAndPath("clientmovement", "test_speed"),
                0.05, AttributeModifier.Operation.ADD_VALUE);
        serverMovement.setBaseValue(0.2);
        serverMovement.addTransientModifier(effect);
        receiveAttributes(client, new ClientboundUpdateAttributesPacket(player.getId(), List.of(serverMovement)), decode);
        require(movement.getBaseValue() == 0.2, "Server base speed changes must still apply");
        require(movement.hasModifier(effect.id()), "Other movement modifiers must still apply");
        require(movement.hasModifier(SPRINTING), "Other modifiers must not remove local sprint speed");

        player.setSprinting(false);
        serverMovement.addTransientModifier(sprintModifier());
        receiveAttributes(client, new ClientboundUpdateAttributesPacket(player.getId(), List.of(serverMovement)), decode);
        require(!movement.hasModifier(SPRINTING), "A stale server update must not restart local sprinting");
        require(movement.hasModifier(effect.id()), "Stopping sprint must preserve other modifiers");

        var health = new AttributeInstance(Attributes.MAX_HEALTH, ignored -> {});
        health.setBaseValue(24);
        receiveAttributes(client, new ClientboundUpdateAttributesPacket(player.getId(), List.of(health)), decode);
        require(player.getAttribute(Attributes.MAX_HEALTH).getBaseValue() == 24, "Unrelated attributes must still update");
        movement.removeModifiers();
        movement.setBaseValue(0.1);
    }

    private static void checkMetadata(Minecraft client) {
        var player = client.player;
        player.setPose(Pose.CROUCHING);
        player.setSprinting(true);
        receiveMetadata(client, player, (byte) 0x42, Pose.STANDING);
        require(player.isSprinting(), "Metadata must not stop local sprinting");
        require(player.getPose() == Pose.CROUCHING, "Metadata must not replace the local pose");
        require(player.getEntityData().get(FLAGS) == (byte) 0x4A, "Non-sprint flags must still apply");

        player.setSprinting(false);
        receiveMetadata(client, player, (byte) 0x08, Pose.SWIMMING);
        require(!player.isSprinting(), "Metadata must not start local sprinting");
        require(player.getEntityData().get(FLAGS) == (byte) 0, "Other metadata flags must still clear");
        require(player.getPose() == Pose.CROUCHING, "Server swimming metadata must not replace the local pose");
        player.setPose(Pose.STANDING);
    }

    private static void checkRemotePlayer(Minecraft client) {
        var remote = new RemotePlayer(client.level, new GameProfile(UUID.randomUUID(), "RemoteTest"));
        remote.setId(Integer.MAX_VALUE);
        client.level.addEntity(remote);
        try {
            receiveMetadata(client, remote, (byte) 0x08, Pose.SWIMMING);
            require(remote.isSprinting(), "Remote sprint metadata must still apply");
            require(remote.getPose() == Pose.SWIMMING, "Remote pose metadata must still apply");
            var movement = new AttributeInstance(Attributes.MOVEMENT_SPEED, ignored -> {});
            movement.setBaseValue(0.15);
            movement.addTransientModifier(sprintModifier());
            receiveAttributes(client, new ClientboundUpdateAttributesPacket(remote.getId(), List.of(movement)), true);
            require(remote.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(SPRINTING), "Remote sprint speed must still update");
            require(remote.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue() == 0.15, "Remote base speed must still update");
            movement.removeModifiers();
            receiveAttributes(client, new ClientboundUpdateAttributesPacket(remote.getId(), List.of(movement)), true);
            require(!remote.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(SPRINTING), "Remote sprint speed must still clear");
        } finally {
            client.level.removeEntity(remote.getId(), Entity.RemovalReason.DISCARDED);
        }
    }

    private static void receiveAttributes(Minecraft client, ClientboundUpdateAttributesPacket packet, boolean decode) {
        if (decode) {
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), client.level.registryAccess());
            try {
                ClientboundUpdateAttributesPacket.STREAM_CODEC.encode(buffer, packet);
                packet = ClientboundUpdateAttributesPacket.STREAM_CODEC.decode(buffer);
            } finally {
                buffer.release();
            }
        }
        client.getConnection().handleUpdateAttributes(packet);
    }

    private static void receiveMetadata(Minecraft client, Entity entity, byte flags, Pose pose) {
        client.getConnection().handleSetEntityData(new ClientboundSetEntityDataPacket(entity.getId(), List.of(
                new SynchedEntityData.DataValue<>(0, EntityDataSerializers.BYTE, flags),
                new SynchedEntityData.DataValue<>(6, EntityDataSerializers.POSE, pose))));
    }

    private static AttributeModifier sprintModifier() {
        return new AttributeModifier(SPRINTING, 0.3F, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
