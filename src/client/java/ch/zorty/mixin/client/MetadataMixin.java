package ch.zorty.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SyncedDataHolder;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SynchedEntityData.class)
public class MetadataMixin {

	@Shadow
	private SyncedDataHolder entity;

	@Inject(method = "assignValue", at = @At("HEAD"), cancellable = true)
	private <T> void onAssignValue(
			SynchedEntityData.DataItem<T> dataItem,
			SynchedEntityData.DataValue<?> item,
			CallbackInfo ci
	) {

		// Check if player = entity
		Minecraft mc = Minecraft.getInstance();
		if(mc.player == null) return;
		if(entity != mc.player) return;

		// Cancel all pose updates
		if(dataItem.getAccessor().id() == 6) {
			ci.cancel();
		} else if(dataItem.getAccessor().id() == 0) {

			byte flags = (Byte) item.value();
			boolean sprinting = mc.player.isSprinting();

			// Set sprint flag to current sprinting value
			flags &= ~0x08;
			if (sprinting) { flags |= 0x08; }

			// Safe since index 0 metadata is always a byte
			((SynchedEntityData.DataItem<Byte>) dataItem).setValue(flags);

			ci.cancel();
		}

	}
}
