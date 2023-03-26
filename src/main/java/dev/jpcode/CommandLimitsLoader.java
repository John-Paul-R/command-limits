package dev.jpcode;

import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.util.Identifier;

import java.io.FileNotFoundException;
import java.io.IOException;

public class CommandLimitsLoader {

    private static final Identifier COMMAND_REGISTRATION_PHASE_ID = new Identifier("command_limits", "register_commands_with_limits_phase");
    private static final CommandsProvider commandsProvider = new CommandsProvider();
    public static void registerCommandWithLimits() {
        // CommandLimits must perform registration after all other mods, so that mod-added commands can be referenced
        // in Aliases. We add our own phase that must execute after the default phase to achieve this.
        CommandRegistrationCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, COMMAND_REGISTRATION_PHASE_ID);
        CommandRegistrationCallback.EVENT.register(
                COMMAND_REGISTRATION_PHASE_ID,
                (dispatcher, ign) -> {
                    try {
                        commandsProvider.loadOrInitConfig(dispatcher);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    commandsProvider.registerCommandLimitsCommands(dispatcher);
//                    this.commandsProvider.loadCommandAliases();
                    try {
                        commandsProvider.reregisterCommands(dispatcher);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

    }

}
