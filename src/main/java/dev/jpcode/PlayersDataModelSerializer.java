package dev.jpcode;

import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayersDataModelSerializer {
    public void toJson(JsonObject json, PlayersDataModel object) {
        var playersObj = new JsonObject();
        object.getPlayers().forEach((key, value) -> {
            var obj = new JsonObject();
            var commandExecutions = new JsonObject();
            value.getCommandExecutionsMap().forEach(commandExecutions::addProperty);
            obj.add("commandExecutions", commandExecutions);
            playersObj.add(key.toString(), obj);
        });

        json.add("players", playersObj);
        json.addProperty("_DO_NOT_TOUCH_PLAYERS_FILE_VERSION", 1);
    }

    public PlayersDataModel fromJson(JsonObject json) {
        var playersData = new PlayersDataModel();

        Map<UUID, CommandLimitsPlayerModel> players = new HashMap<>();
        json.get("players").getAsJsonObject()
            .entrySet()
            .forEach((playerEntry) -> {
                var playerData = new CommandLimitsPlayerModel();
                playerEntry.getValue().getAsJsonObject()
                    .get("commandExecutions").getAsJsonObject()
                    .entrySet()
                    .forEach(commandEntry -> playerData.setCommandExecutions(commandEntry.getKey(), commandEntry.getValue().getAsInt()));
                players.put(
                    UUID.fromString(playerEntry.getKey()),
                    playerData);
            });

        playersData.setPlayers(players);

        return playersData;
    }
}
