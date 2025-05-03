package common;
import java.io.Serializable;
import java.util.Map;

public class GameResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private Map<String, Boolean> playerResults; // username -> win/loss
    private String winner;
    private String message;

    public GameResult(Map<String, Boolean> playerResults, String winner, String message) {
        this.playerResults = playerResults;
        this.winner = winner;
        this.message = message;
    }

    public Map<String, Boolean> getPlayerResults() {
        return playerResults;
    }

    public String getWinner() {
        return winner;
    }

    public String getMessage() {
        return message;
    }
} 