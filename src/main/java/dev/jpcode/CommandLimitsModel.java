package dev.jpcode;

public class CommandLimitsModel {
    public CommandLimitsModel() {}

    public CommandLimitsModel(int maxExecutions) {
        this.maxExecutions = maxExecutions;
    }

    private int maxExecutions;

    public int getMaxExecutions() {
        return maxExecutions;
    }

    public void setMaxExecutions(int maxExecutions) {
        this.maxExecutions = maxExecutions;
    }
}
