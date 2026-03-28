package ch.zorty;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientMovementClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("ClientMovement");

	@Override
	public void onInitializeClient() {
		LOGGER.info("Client Movement loaded");
	}
}