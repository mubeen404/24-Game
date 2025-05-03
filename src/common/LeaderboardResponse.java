package common;
import java.io.Serializable;
import java.util.List;
import model.UserStats;

public class LeaderboardResponse implements Serializable {
    private static final long serialVersionUID = 1L;
    private List<UserStats> leaderboard;
    public LeaderboardResponse(List<UserStats> leaderboard) {
        this.leaderboard = leaderboard;
    }
    public List<UserStats> getLeaderboard() {
        return leaderboard;
    }
} 