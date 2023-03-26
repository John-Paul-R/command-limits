package dev.jpcode;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommandLimitsMod implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("command_limits");

	@Override
	public void onInitialize() {

		CommandLimitsLoader.registerCommandWithLimits();

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> CommandsProvider.savePlayerData());
	}
}