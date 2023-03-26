package dev.jpcode;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayersDataModel {
    private Map<UUID, CommandLimitsPlayerModel> players = new HashMap<>();

    public Map<UUID, CommandLimitsPlayerModel> getPlayers() {
        return players;
    }

    public void setPlayers(Map<UUID, CommandLimitsPlayerModel> players) {
        this.players = players;
    }
}
