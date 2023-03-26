package dev.jpcode;

import java.util.HashMap;
import java.util.Map;

public class CommandLimitsPlayerModel {
    private Map<String, Integer> commandExecutions = new HashMap<>();

    public Map<String, Integer> getCommandExecutionsMap() {
        return commandExecutions;
    }

    public void setCommandExecutionsMap(Map<String, Integer> commandExecutions) {
        this.commandExecutions = commandExecutions;
    }

    public int getCommandExecutions(String command) {
        return commandExecutions.getOrDefault(command, 0);
    }

    public void setCommandExecutions(String command, int numExecutions) {
        commandExecutions.put(command, numExecutions);
    }

    public int incrementExecutions(String command) {
        var current = commandExecutions.getOrDefault(command, 0);
        var newValue = current + 1;
        commandExecutions.put(command, newValue);
        return newValue;
    }

}
