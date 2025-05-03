package common;
import java.io.Serializable;
import java.util.List;

public class GameStartNotification implements Serializable {
    private static final long serialVersionUID = 1L;
    private List<String> players;
    private long startTime;

    public GameStartNotification(List<String> players, long startTime) {
        this.players = new java.util.ArrayList<>(players);
        this.startTime = startTime;
    }

    public List<String> getPlayers() {
        return players;
    }

    public long getStartTime() {
        return startTime;
    }
} 