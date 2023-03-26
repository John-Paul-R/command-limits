package dev.jpcode;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.apache.logging.log4j.core.lookup.StrSubstitutor;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CommandsProvider {

    public void registerCommandLimitsCommands(
        CommandDispatcher<ServerCommandSource> dispatcher) {

        var commandNames = getCommandNames(dispatcher);

        var rootCommandName = CONFIG.getRootCommandName();

        var clBuilder = CommandManager.literal(rootCommandName)
            .then(CommandManager.literal("reload")
                .requires(Permissions.require("commandlimits.reload", 3))
                .executes(ctx -> {
                    try {
                        loadOrInitConfig(dispatcher);
                        reregisterCommands(dispatcher);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return 0;
                })
            )
            .then(CommandManager.literal("player")
                .then(CommandManager.argument("target_player", EntityArgumentType.player())
                    .then(CommandManager.literal("info")
                        .requires(Permissions.require("commandlimits.player.info", 2))
                        .executes(ctx -> {
                            var targetPlayer = EntityArgumentType.getPlayer(ctx, "target_player");

                            var titleBase = Text.of("Command Limits info for ");
                            var targetPlayerName = targetPlayer.getDisplayName();

                            var playerData = getPlayerData(targetPlayer);

                            var titleText = Text.literal("")
                                .append(titleBase)
                                .append(targetPlayerName)
                                .append("\n");

                            var finalText = Text.literal("")
                                .append(titleText);

                            playerData.getCommandExecutionsMap().forEach((key, value) -> {
                                finalText.append(Text.literal("")
                                    .append(Text.literal(key))
                                    .append(Text.literal(": "))
                                    .append(Text.literal("(Execution Count: "))
                                    .append(Text.literal(value.toString()))
                                    .append(")\n"));
                            });

                            ctx.getSource().sendFeedback(finalText, false);

                            return 0;
                        }))
                    .then(CommandManager.literal("addExecutions")
                        .requires(Permissions.require("commandlimits.player.modifyExecutionsCount", 2))
                        .then(CommandManager.argument("command", StringArgumentType.string())
                            .suggests((ctx, builder) -> {
                                commandNames.forEach(builder::suggest);
                                return builder.buildFuture();
                            })
                            .then(CommandManager.argument("count", IntegerArgumentType.integer())
                                .executes(ctx -> {
                                    var targetPlayer = EntityArgumentType.getPlayer(ctx, "target_player");
                                    var specifiedCommand = StringArgumentType.getString(ctx, "command");
                                    var count = IntegerArgumentType.getInteger(ctx, "count");

                                    var playerData = getPlayerData(targetPlayer);
                                    int currentExecutions = playerData.getCommandExecutions(specifiedCommand);
                                    playerData.setCommandExecutions(specifiedCommand, currentExecutions + count);

                                    return 0;
                                }))
                        )
                    )
                )
            );

        dispatcher.register(clBuilder);
    }

    private static final File configFile = FabricLoader.getInstance().getConfigDir().resolve("command-limits-config.json").toFile();
    // TODO, this should be in player-specific files in `world`, but I'm moving quickly
    private static final File playersDataFile = FabricLoader.getInstance().getConfigDir().resolve("command-limits-playerdata.json").toFile();

    private static CommandLimitsConfig CONFIG;
    private static PlayersDataModel PLAYERS_DATA;
    private Field commandNodeCommandField;
    private Field commandNodeRequirementField;

    private static CommandLimitsPlayerModel getPlayerData(ServerPlayerEntity player) {
        var playerUuid = player.getUuid();
        var data = PLAYERS_DATA.getPlayers().get(playerUuid);
        if (data == null) {
            PLAYERS_DATA.getPlayers().put(playerUuid, data = new CommandLimitsPlayerModel());
        }
        return data;
    }

    private static CommandLimitsPlayerModel getPlayerData(ServerCommandSource serverCommandSource) {
        return getPlayerData(serverCommandSource.getPlayer());
    }

    public void reregisterCommands(
        CommandDispatcher<ServerCommandSource> dispatcher) throws IOException {

        if (PLAYERS_DATA == null) {
            loadOrInitPlayerData();
        }

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

    private static void loadOrInitPlayerData() throws IOException {
        // playerdata
        Gson gson2 = new GsonBuilder()
            .setPrettyPrinting()
            .create();

        var playersDataSerializer = new PlayersDataModelSerializer();
        var playersData = playersDataFile.exists() && playersDataFile.isFile() && playersDataFile.length() >= 2
            ? playersDataSerializer.fromJson(JsonParser.parseReader(new JsonReader(new FileReader(playersDataFile))).getAsJsonObject())
            : new PlayersDataModel();

        var outPlayersJsonObject = new JsonObject();
        playersDataSerializer.toJson(outPlayersJsonObject, playersData);
        var playersFileWriter = new FileWriter(playersDataFile);
        gson2.toJson(outPlayersJsonObject, new JsonWriter(playersFileWriter));
        playersFileWriter.flush();
        playersFileWriter.close();

        PLAYERS_DATA = playersData;
    }

    private static Set<String> getCommandNames(CommandDispatcher<ServerCommandSource> dispatcher) {
        return dispatcher.getRoot().getChildren().stream()
            .map(CommandNode::getName)
            .collect(Collectors.toCollection(TreeSet::new));
    }

    public void loadOrInitConfig(CommandDispatcher<ServerCommandSource> dispatcher) throws IOException {

        var commandNames = getCommandNames(dispatcher);

        var configSerializer = new ConfigSerializer();

        Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
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
    }

    private Predicate<ServerCommandSource> createMaxExecutionsPredicate(
        String rootCommandName,
        CommandNode<ServerCommandSource> commandNode,
        CommandLimitsModel commandConfig) {
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

    private Command<ServerCommandSource> createWrappedCommand(
        String rootCommandName,
        CommandNode<ServerCommandSource> commandNode,
        CommandLimitsModel commandConfig)
    {
        var baseCommand = commandNode.getCommand();
        return (CommandContext<ServerCommandSource> ctx) -> {
            if (ctx.getSource().isExecutedByPlayer()) {
                var executorPlayer = ctx.getSource().getPlayer();
                var playerData = getPlayerData(executorPlayer);
                var feedbackTemplate = CONFIG.getCommandLimitFeedbackTemplate();
                if (feedbackTemplate.isPresent()) {
                    int currentExecutions = playerData.getCommandExecutions(rootCommandName) + 1; // +1 to account for _this_ execution
                    int maxExecutions = commandConfig.getMaxExecutions();
                    int remainingExecutions = maxExecutions - currentExecutions;
                    Map<String, String> substitutions = new HashMap<>();
                    substitutions.put("commandName", rootCommandName);
                    substitutions.put("remainingExecutions", Integer.toString(remainingExecutions));
                    substitutions.put("currentExecutions", Integer.toString(currentExecutions));
                    substitutions.put("maxExecutions", Integer.toString(maxExecutions));

                    var messageString = new StrSubstitutor(substitutions).replace(feedbackTemplate.get());
                    ctx.getSource().sendFeedback(Text.of(messageString), false);
                }
                playerData.incrementExecutions(rootCommandName);
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
        CommandLimitsModel commandConfig) {
        try {
            commandNodeRequirementField.set(existingNode, createMaxExecutionsPredicate(rootCommandName, existingNode, commandConfig));
            if (existingNode.getCommand() != null) {
                commandNodeCommandField.set(existingNode, createWrappedCommand(rootCommandName, existingNode, commandConfig));
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
        FileWriter playersFileWriter;
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
