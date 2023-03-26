package dev.jpcode;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.ServerCommandSource;

import java.io.*;
import java.lang.reflect.Field;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CommandsProvider {

    public void registerCommandLimitsCommands(
            CommandDispatcher<ServerCommandSource> dispatcher,
            CommandRegistryAccess registryAccess) {

    }

    private static final File configFile = FabricLoader.getInstance().getConfigDir().resolve("command-limits-config.json").toFile();
    // TODO, this should be in player-specific files in `world`, but I'm moving quickly
    private static final File playersDataFile = FabricLoader.getInstance().getConfigDir().resolve("command-limits-playerdata.json").toFile();

    private static CommandLimitsConfig CONFIG;
    private static PlayersDataModel PLAYERS_DATA;
    private Field commandNodeCommandField;
    private Field commandNodeRequirementField;

    private static CommandLimitsPlayerModel getPlayerData(ServerCommandSource serverCommandSource) {
        var playerUuid = serverCommandSource.getPlayer().getUuid();
        var data = PLAYERS_DATA.getPlayers().get(playerUuid);
        if (data == null) {
            PLAYERS_DATA.getPlayers().put(playerUuid, data = new CommandLimitsPlayerModel());
        }
        return data;
    }

    public void reregisterCommands(
            CommandDispatcher<ServerCommandSource> dispatcher,
            CommandRegistryAccess registryAccess) throws IOException {

        var commands = dispatcher.getRoot().getChildren();

        var commandNames = commands.stream().map(CommandNode::getName).collect(Collectors.toSet());

        var configSerializer = new ConfigSerializer();

        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
        var baseConfig = configFile.exists() && configFile.isFile() && configFile.length() >= 2
                ? configSerializer.fromJson(JsonParser.parseReader(new JsonReader(new FileReader(configFile))).getAsJsonObject())
                : new CommandLimitsConfig();
        var commandsConfigValues = baseConfig.getCommands();
        commandNames.forEach(cmdName -> commandsConfigValues.putIfAbsent(cmdName, new CommandLimitsModel(-1)));

        baseConfig.setCommands(commandsConfigValues);

        var outJsonObject = new JsonObject();
        configSerializer.toJson(outJsonObject, baseConfig);
        var configFileWriter = new FileWriter(configFile);

        gson.toJson(outJsonObject, configFileWriter);
        configFileWriter.flush();
        configFileWriter.close();

        CONFIG = baseConfig;

        // playerdata
        var playersDataSerializer = new PlayersDataModelSerializer();
        var playersData = playersDataFile.exists() && playersDataFile.isFile() && playersDataFile.length() >= 2
                ? playersDataSerializer.fromJson(JsonParser.parseReader(new JsonReader(new FileReader(playersDataFile))).getAsJsonObject())
                : new PlayersDataModel();
//        var players = playersData.getPlayers();
//        commandNames.forEach(cmdName -> players.putIfAbsent(cmdName, new CommandLimitsModel(-1)));

//        playersData.setCommands(players);

        var outPlayersJsonObject = new JsonObject();
        playersDataSerializer.toJson(outPlayersJsonObject, playersData);
        var playersFileWriter = new FileWriter(playersDataFile);
        gson.toJson(outPlayersJsonObject, new JsonWriter(playersFileWriter));
        playersFileWriter.flush();
        playersFileWriter.close();

        PLAYERS_DATA = playersData;

        // --- hook the modified commands

        try {
            commandNodeCommandField = CommandNode.class.getDeclaredField("command");
            commandNodeCommandField.setAccessible(true);
            commandNodeRequirementField = CommandNode.class.getDeclaredField("requirement");
            commandNodeRequirementField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }

        CONFIG.getCommands().forEach((commandName, commandConfig) -> {
            if (commandConfig.getMaxExecutions() < 0) {
                return;
            }

            var command = dispatcher.getRoot().getChild(commandName);

            processCommandNode(command, commandConfig);
        });
    }

    private Predicate<ServerCommandSource> createMaxExecutionsPredicate(
            String rootCommandName,
            CommandNode<ServerCommandSource> commandNode,
            CommandLimitsModel commandConfig)
    {
        var existingRequirement = commandNode.getRequirement();
        return serverCommandSource -> {
            if (!existingRequirement.test(serverCommandSource)) {
                return false;
            }
            if (!serverCommandSource.isExecutedByPlayer()) {
                return true;
            }
            var playerData = getPlayerData(serverCommandSource);
            int executionCount = playerData == null ? 0 : playerData.getCommandExecutions(rootCommandName);
            return executionCount < commandConfig.getMaxExecutions();
        };
    }

    private Command<ServerCommandSource> createWrappedCommand(String rootCommandName, CommandNode<ServerCommandSource> commandNode) {
        var baseCommand = commandNode.getCommand();
        return (CommandContext<ServerCommandSource> ctx) -> {
            if (ctx.getSource().isExecutedByPlayer()) {
                getPlayerData(ctx.getSource()).incrementExecutions(rootCommandName);
            }
            return baseCommand.run(ctx);
        };
    }

    private void processCommandNode(CommandNode<ServerCommandSource> existingNode, CommandLimitsModel commandConfig) {
        processCommandNode(existingNode.getName(), existingNode, commandConfig);
    }

    private void processCommandNode(
            String rootCommandName,
            CommandNode<ServerCommandSource> existingNode,
            CommandLimitsModel commandConfig)
    {
        try {
            commandNodeRequirementField.set(existingNode, createMaxExecutionsPredicate(rootCommandName, existingNode, commandConfig));
            if (existingNode.getCommand() != null) {
                commandNodeCommandField.set(existingNode, createWrappedCommand(rootCommandName, existingNode));
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        existingNode.getChildren().forEach(cmd -> processCommandNode(rootCommandName, cmd, commandConfig));
    }

    public static void savePlayerData() {
        Gson gson = new Gson();
        var playersDataSerializer = new PlayersDataModelSerializer();

        var outPlayersJsonObject = new JsonObject();
        playersDataSerializer.toJson(outPlayersJsonObject, PLAYERS_DATA);
        FileWriter playersFileWriter = null;
        try {
            playersFileWriter = new FileWriter(playersDataFile);
            gson.toJson(outPlayersJsonObject, new JsonWriter(playersFileWriter));
            playersFileWriter.flush();
            playersFileWriter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
