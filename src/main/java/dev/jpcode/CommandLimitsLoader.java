package dev.jpcode;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.util.Identifier;

import java.io.FileNotFoundException;
import java.io.IOException;

public class CommandLimitsLoader {

    private static final Identifier COMMAND_REGISTRATION_PHASE_ID = Identifier.of("command_limits", "register_commands_with_limits_phase");
    private static final CommandsProvider commandsProvider = new CommandsProvider();
    public static void registerCommandWithLimits() {
        // CommandLimits must perform registration after all other mods, so that mod-added commands can be referenced
        // in Aliases. We add our own phase that must execute after the default phase to achieve this.
        CommandRegistrationCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, COMMAND_REGISTRATION_PHASE_ID);
        CommandRegistrationCallback.EVENT.register(
                COMMAND_REGISTRATION_PHASE_ID,
                (dispatcher, registryAccess, environment) -> {
                    commandsProvider.registerCommandLimitsCommands(dispatcher, registryAccess);
//                    this.commandsProvider.loadCommandAliases();
                    try {
                        commandsProvider.reregisterCommands(dispatcher, registryAccess);
                    } catch (FileNotFoundException e) {
                        throw new RuntimeException(e);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

    }

}
