package dev.jpcode;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Optional;

public class CommandLimitsConfig {

    private HashMap<String, CommandLimitsModel> commands = new HashMap<>();
    private final String defaultRootCommandName = "commandlimits";
    private String rootCommandName = defaultRootCommandName;
    private final String defaultCommandLimitFeedbackTemplate = "Executing limited command (${commandName}). Uses remaining: ${remainingExecutions} (${currentExecutions} / ${maxExecutions})";
    private @Nullable String commandLimitFeedbackTemplate = defaultCommandLimitFeedbackTemplate;

    public HashMap<String, CommandLimitsModel> getCommands() {
        return commands;
    }

    public void setCommands(HashMap<String, CommandLimitsModel> commands) {
        this.commands = commands;
    }

    public void setCommandExecutionLimit(String commandName, int maxExecutions) {
        var existing = this.commands.get(commandName);
        existing.setMaxExecutions(maxExecutions);
    }

    public String getRootCommandName() {
        return rootCommandName;
    }

    public void setRootCommandName(String rootCommandName) {
        if (rootCommandName == null) {
            rootCommandName = defaultRootCommandName;
        }
        this.rootCommandName = rootCommandName;
    }

    public Optional<String> getCommandLimitFeedbackTemplate() {
        return Optional.ofNullable(commandLimitFeedbackTemplate);
    }

    public void setCommandLimitFeedbackTemplate(@Nullable String commandLimitFeedbackTemplate) {
        this.commandLimitFeedbackTemplate = commandLimitFeedbackTemplate;
    }

    public void setCommandLimitFeedbackTemplateToDefault() {
        this.commandLimitFeedbackTemplate = defaultCommandLimitFeedbackTemplate;
    }
}
