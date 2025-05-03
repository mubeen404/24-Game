package common;
import java.io.Serializable;
import java.util.List;

public class LeaderboardUpdate implements Serializable {
    private static final long serialVersionUID = 1L;
    private List<String> leaderboardEntries;

    public LeaderboardUpdate(List<String> leaderboardEntries) {
        this.leaderboardEntries = leaderboardEntries;
    }

    public List<String> getLeaderboardEntries() {
        return leaderboardEntries;
    }
} 