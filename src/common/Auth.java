package common;

import model.UserStats;
import java.util.Map;
import java.util.List;

/**
 * Stub Auth interface for GUI compilation. RMI removed; methods unused in JMS-only client.
 */
public interface Auth {
    boolean login(String username, String password);
    boolean register(String username, String password);
    boolean logout(String username);
    boolean updateUserProfile(String username, Map<String, String> data);
    UserStats getUserStats(String username);
    List<UserStats> getLeaderboardList();
} 