package dev.jpcode;

import java.util.HashMap;

public class CommandLimitsConfig {

    private HashMap<String, CommandLimitsModel> commands = new HashMap<>();
    private final String defaultRootCommandName = "commandlimits";
    private String rootCommandName = defaultRootCommandName;

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
}
