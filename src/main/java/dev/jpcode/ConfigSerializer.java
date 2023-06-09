package dev.jpcode;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.HashMap;

public class ConfigSerializer {
    public void toJson(JsonObject json, CommandLimitsConfig object) {
        json.addProperty("rootCommandName", object.getRootCommandName());

        var feedbackTemplate = object.getCommandLimitFeedbackTemplate();
        json.add(
            "commandLimitFeedbackTemplate",
            feedbackTemplate.isEmpty() ? JsonNull.INSTANCE : new JsonPrimitive(feedbackTemplate.get()));

        var commandsObj = new JsonObject();
        object.getCommands().forEach((key, value) -> {
            var obj = new JsonObject();
            obj.addProperty("maxExecutions", value.getMaxExecutions());
            commandsObj.add(key, obj);
        });

        json.add("commands", commandsObj);
        json.addProperty("_DO_NOT_TOUCH_CONFIG_VERSION", 1);
    }

    public CommandLimitsConfig fromJson(JsonObject json) {
        var config = new CommandLimitsConfig();

        var rootCommandElem = json.get("rootCommandName");
        config.setRootCommandName(rootCommandElem == null || rootCommandElem.isJsonNull()
            ? null
            : rootCommandElem.getAsString());

        var feedbackTemplate = json.get("commandLimitFeedbackTemplate");
        if (feedbackTemplate == null) {
            config.setCommandLimitFeedbackTemplateToDefault();
        } else {
            config.setCommandLimitFeedbackTemplate(feedbackTemplate.isJsonNull()
                ? null
                : feedbackTemplate.getAsString());
        }


        var commands = new HashMap<String, CommandLimitsModel>();
        json.getAsJsonObject("commands")
            .entrySet()
            .forEach(cmdEntry -> commands.put(
                cmdEntry.getKey(),
                new CommandLimitsModel(cmdEntry.getValue().getAsJsonObject().get("maxExecutions").getAsInt())));

        config.setCommands(commands);

        return config;
    }
}
