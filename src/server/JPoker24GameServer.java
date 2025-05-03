package server;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import java.util.*;
import java.util.concurrent.*;
import common.JoinGameRequest;
import common.GameStartNotification;
import common.CardDrawMessage;
import common.AnswerSubmission;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import common.GameResult;
import server.DBUtil;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import model.UserStats;
import common.LeaderboardRequest;
import common.LeaderboardResponse;
import common.UserStatsRequest;
import common.UserStatsResponse;

public class JPoker24GameServer {
    private static final int MAX_PLAYERS = 4;
    private static final int MIN_PLAYERS = 2;
    private static final int WAIT_TIME_SECONDS = 10;
    private static final int ANSWER_TIMEOUT_SECONDS = 60;

    private final List<String> waitingPlayers = new ArrayList<>();
    private ScheduledFuture<?> timerFuture = null;
    private ScheduledFuture<?> answerTimeoutFuture = null;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private Session session;
    private MessageProducer topicProducer;

    // Store answers for the current game
    private final List<AnswerSubmission> currentGameAnswers = new ArrayList<>();
    private List<Integer> currentGameCards = null;
    private boolean collectingAnswers = false;
    private boolean gameFinished = false;
    private String gameWinner = null;
    private List<String> currentGamePlayers = new ArrayList<>();
    private long currentGameStartTime;

    private boolean timerRunning = false;

    public static void main(String[] args) throws Exception {
        new JPoker24GameServer().run();
    }

    public void run() throws Exception {
        Properties props = new Properties();
        props.setProperty(Context.INITIAL_CONTEXT_FACTORY, "com.sun.enterprise.naming.SerialInitContextFactory");
        props.setProperty(Context.PROVIDER_URL, "iiop://localhost:3700");
        Context ctx = new InitialContext(props);

        ConnectionFactory factory = (ConnectionFactory) ctx.lookup("jms/JPoker24GameConnectionFactory");
        javax.jms.Queue queue = (javax.jms.Queue) ctx.lookup("jms/JPoker24GameQueue");
        Topic topic = (Topic) ctx.lookup("jms/JPoker24GameTopic");
        javax.jms.Queue statsQueue = (javax.jms.Queue) ctx.lookup("jms/JPoker24StatsQueue");
        Connection connection = factory.createConnection();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageConsumer consumer = session.createConsumer(queue);
        MessageProducer topicProducer = session.createProducer(topic);
        MessageConsumer statsConsumer = session.createConsumer(statsQueue);
        connection.start();

        this.session = session;
        this.topicProducer = topicProducer;

        System.out.println("[Server] Waiting for JoinGameRequest messages...");
        // Start a thread to listen for leaderboard requests
        new Thread(() -> listenForLeaderboardRequests(statsConsumer, session)).start();
        while (true) {
            Message msg = consumer.receive();
            System.out.println("[Server] Received LeaderboardRequest");
            // System.out.println("[Server] Sending LeaderboardResponse with " + leaderboard.size() + " users");
            if (msg instanceof ObjectMessage) {
                ObjectMessage objMsg = (ObjectMessage) msg;
                Object obj = objMsg.getObject();
                if (obj instanceof JoinGameRequest) {
                    String username = ((JoinGameRequest) obj).getUsername();
                    System.out.println("[Server] Received JoinGameRequest from: " + username);
                    handleJoinRequest(username);
                } else if (obj instanceof AnswerSubmission) {
                    handleAnswerSubmission((AnswerSubmission) obj);
                }
            }
        }
    }

    private synchronized void handleJoinRequest(String username) {
        System.out.println("[Server] handleJoinRequest called by: " + username);
        System.out.println("[Server] Current waitingPlayers: " + waitingPlayers + ", timerRunning: " + timerRunning);
        if (waitingPlayers.contains(username)) {
            System.out.println("[Server] Player already waiting: " + username);
            return;
        }
        waitingPlayers.add(username);
        System.out.println("[Server] Player joined: " + username + " | Waiting list: " + waitingPlayers);
        if (!timerRunning) {
            timerRunning = true;
            timerFuture = scheduler.schedule(this::timerExpired, WAIT_TIME_SECONDS, TimeUnit.SECONDS);
            System.out.println("[Server] Timer started for 10 seconds.");
        }
        if (waitingPlayers.size() == MAX_PLAYERS) {
            startGame();
        }
        System.out.println("[Server] handleJoinRequest END. waitingPlayers: " + waitingPlayers + ", timerRunning: " + timerRunning);
    }

    private synchronized void timerExpired() {
        System.out.println("[Server] Timer expired. Players waiting: " + waitingPlayers);
        timerRunning = false;
        if (waitingPlayers.size() >= MIN_PLAYERS) {
            startGame();
        } else {
            System.out.println("[Server] Not enough players to start the game after timer expired.");
            waitingPlayers.clear();
            System.out.println("[Server] Waiting list cleared: " + waitingPlayers);
        }
        System.out.println("[Server] timerExpired END. waitingPlayers: " + waitingPlayers + ", timerRunning: " + timerRunning);
    }

    private synchronized void startGame() {
        // Record the timestamp for computing game duration
        long startTime = System.currentTimeMillis();
        this.currentGameStartTime = startTime;
        // Cancel timer if still running
        if (timerFuture != null && !timerFuture.isDone()) {
            timerFuture.cancel(false);
        }
        // Schedule answer-collection timeout
        answerTimeoutFuture = scheduler.schedule(this::finishGame, ANSWER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        System.out.println("[Server] Answer timeout scheduled for " + ANSWER_TIMEOUT_SECONDS + " seconds.");
        System.out.println("[Server] Game starting with players: " + waitingPlayers);
        // Save current game players
        currentGamePlayers = new ArrayList<>(waitingPlayers);
        // Notify clients via JMS topic
        try {
            GameStartNotification notification = new GameStartNotification(new ArrayList<>(waitingPlayers), startTime);
            ObjectMessage msg = session.createObjectMessage(notification);
            topicProducer.send(msg);
            System.out.println("[Server] Sent GameStartNotification to topic.");

            // Draw 4 unique cards (values 1-13)
            List<Integer> cards = drawUniqueCards(4, 1, 13);
            currentGameCards = new ArrayList<>(cards);
            System.out.println("[Server] Drawn cards: " + cards);
            CardDrawMessage cardMsg = new CardDrawMessage(cards);
            ObjectMessage cardObjMsg = session.createObjectMessage(cardMsg);
            topicProducer.send(cardObjMsg);
            System.out.println("[Server] Sent CardDrawMessage to topic.");

            // Reset game finish state
            gameFinished = false;
            gameWinner = null;
            collectingAnswers = true;
            currentGameAnswers.clear();
            System.out.println("[Server] Now collecting answers for this game...");
        } catch (Exception e) {
            System.err.println("[Server] Failed to send GameStartNotification or CardDrawMessage: " + e.getMessage());
            e.printStackTrace();
        }
        waitingPlayers.clear();
        timerRunning = false;
        System.out.println("[Server] Game started. Waiting list cleared and timerRunning set to false.");
    }

    // Helper method to draw n unique cards in a given range
    private List<Integer> drawUniqueCards(int n, int min, int max) {
        List<Integer> deck = new ArrayList<>();
        for (int i = min; i <= max; i++) deck.add(i);
        Collections.shuffle(deck);
        return new ArrayList<>(deck.subList(0, n));
    }

    private synchronized void handleAnswerSubmission(AnswerSubmission answer) {
        if (!collectingAnswers || gameFinished) {
            System.out.println("[Server] Not accepting answers (game finished or not started). Ignoring submission from: " + answer.getUsername());
            return;
        }
        currentGameAnswers.add(answer);
        System.out.println("[Server] Received answer from " + answer.getUsername() + ": " + answer.getExpression());
        // Step 1.1: Validate card usage
        if (currentGameCards == null) {
            System.out.println("[Server] No cards drawn for current game. Cannot validate answer.");
            return;
        }
        List<Integer> usedNumbers = extractNumbers(answer.getExpression());
        List<Integer> drawnCards = new ArrayList<>(currentGameCards);
        Collections.sort(usedNumbers);
        Collections.sort(drawnCards);
        boolean correctCards = usedNumbers.equals(drawnCards);
        if (correctCards) {
            // Cancel answer timeout on first correct answer
            if (answerTimeoutFuture != null && !answerTimeoutFuture.isDone()) {
                answerTimeoutFuture.cancel(false);
            }
            System.out.println("[Server] Answer uses correct cards.");
            // Step 1.2: Evaluate expression
            try {
                ScriptEngine engine = new ScriptEngineManager().getEngineByName("JavaScript");
                String expr = answer.getExpression().replaceAll("÷", "/").replaceAll("×", "*");
                Object resultObj = engine.eval(expr);
                double result = Double.parseDouble(resultObj.toString());
                if (Math.abs(result - 24.0) < 1e-6) {
                    System.out.println("[Server] Answer is CORRECT! Expression evaluates to 24.");
                    // Winner logic: first correct answer wins
                    gameFinished = true;
                    gameWinner = answer.getUsername();
                    collectingAnswers = false;
                    // Build results map
                    Map<String, Boolean> results = new LinkedHashMap<>();
                    for (String player : drawnPlayers()) {
                        results.put(player, player.equals(gameWinner));
                    }
                    // Broadcast game result
                    GameResult gameResult = new GameResult(results, gameWinner, answer.getExpression());
                    try {
                        ObjectMessage resultMsg = session.createObjectMessage(gameResult);
                        topicProducer.send(resultMsg);
                        System.out.println("[Server] Sent GameResult: winner=" + gameWinner);
                        // Persist stats for this game
                        persistGameResult(results);
                    } catch (Exception ex) {
                        System.err.println("[Server] Failed to send GameResult: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                } else {
                    System.out.println("[Server] Answer is INCORRECT. Expression evaluates to: " + result);
                }
            } catch (Exception e) {
                System.out.println("[Server] Error evaluating expression: " + e.getMessage());
            }
        } else {
            System.out.println("[Server] Answer does NOT use correct cards. Used: " + usedNumbers + ", Drawn: " + drawnCards);
        }
        // If all players have submitted and no winner yet, finish the game
        if (currentGameAnswers.size() == currentGamePlayers.size() && !gameFinished) {
            finishGame();
        }
    }

    // Helper to extract all integer numbers from an expression string
    private List<Integer> extractNumbers(String expr) {
        List<Integer> numbers = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(expr);
        while (m.find()) {
            numbers.add(Integer.parseInt(m.group()));
        }
        return numbers;
    }

    // Helper to get list of players in the current game
    private List<String> drawnPlayers() {
        return new ArrayList<>(currentGamePlayers);
    }

    // Finish game when answer timeout expires (no correct answers)
    private synchronized void finishGame() {
        if (gameFinished) return;
        gameFinished = true;
        collectingAnswers = false;
        System.out.println("[Server] Answer timeout reached, finishing game with no correct submissions.");
        Map<String, Boolean> results = new LinkedHashMap<>();
        for (String player : drawnPlayers()) {
            results.put(player, false);
        }
        GameResult gameResult = new GameResult(results, null, "Time up, no correct answers.");
        try {
            ObjectMessage resultMsg = session.createObjectMessage(gameResult);
            topicProducer.send(resultMsg);
            System.out.println("[Server] Sent GameResult on timeout: no winner.");
            // Persist stats for timeout game (no winner)
            persistGameResult(results);
        } catch (Exception e) {
            System.err.println("[Server] Failed to send GameResult on timeout: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Persist game results (games played/won and durations) to the database.
     */
    private void persistGameResult(Map<String, Boolean> results) {
        try (java.sql.Connection conn = DBUtil.getConnection()) {
            conn.setAutoCommit(false);
            String sql = "INSERT INTO user_stats (username, games_played, games_won, total_time) VALUES (?, 1, ?, ?) " +
                         "ON DUPLICATE KEY UPDATE games_played = games_played + 1, games_won = games_won + ?, total_time = total_time + ?;";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Map.Entry<String, Boolean> entry : results.entrySet()) {
                    ps.setString(1, entry.getKey());
                    int won = entry.getValue() ? 1 : 0;
                    ps.setInt(2, won);
                    long time = entry.getValue() ? (System.currentTimeMillis() - currentGameStartTime) : 0;
                    ps.setLong(3, time);
                    ps.setInt(4, won);
                    ps.setLong(5, time);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            System.err.println("[Server] DB persistence error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Listen for LeaderboardRequest messages and reply with LeaderboardResponse
    private void listenForLeaderboardRequests(MessageConsumer statsConsumer, Session session) {
        System.out.println("[Server] Leaderboard listener started, waiting for requests...");
        try {
            while (true) {
                Message msg = statsConsumer.receive();
                if (msg instanceof ObjectMessage) {
                    ObjectMessage objMsg = (ObjectMessage) msg;
                    Object obj = objMsg.getObject();
                    if (obj instanceof LeaderboardRequest) {
                        System.out.println("[Server] Received LeaderboardRequest");
                        List<UserStats> leaderboard = getLeaderboardFromDB();
                        System.out.println("[Server] Sending LeaderboardResponse with " + leaderboard.size() + " users");
                        LeaderboardResponse response = new LeaderboardResponse(leaderboard);
                        Destination replyDest = msg.getJMSReplyTo();
                        if (replyDest != null) {
                            ObjectMessage respMsg = session.createObjectMessage(response);
                            session.createProducer(replyDest).send(respMsg);
                        }
                    } else if (obj instanceof UserStatsRequest) {
                        String username = ((UserStatsRequest) obj).getUsername();
                        System.out.println("[Server] Received UserStatsRequest for: " + username);
                        UserStats stats = getUserStatsFromDB(username);
                        UserStatsResponse response = new UserStatsResponse(stats);
                        Destination replyDest = msg.getJMSReplyTo();
                        if (replyDest != null) {
                            ObjectMessage respMsg = session.createObjectMessage(response);
                            session.createProducer(replyDest).send(respMsg);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Server] Error in leaderboard listener: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Query leaderboard from DB
    private List<UserStats> getLeaderboardFromDB() {
        List<UserStats> stats = new ArrayList<>();
        String sql = "SELECT username, games_played, games_won, " +
                     "CASE WHEN games_won > 0 THEN total_time / games_won / 1000 ELSE 0 END AS avg_time " +
                     "FROM user_stats ORDER BY games_won DESC, avg_time ASC";
        try (java.sql.Connection conn = DBUtil.getConnection();
             java.sql.Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery(sql)) {
            int rank = 1;
            while (rs.next()) {
                String username = rs.getString("username");
                int gamesPlayed = rs.getInt("games_played");
                int gamesWon = rs.getInt("games_won");
                double avgTime = rs.getDouble("avg_time");
                stats.add(new UserStats(username, gamesPlayed, gamesWon, avgTime, rank++));
            }
        } catch (Exception e) {
            System.err.println("[Server] Error reading leaderboard from DB: " + e.getMessage());
        }
        return stats;
    }

    // Query a single user's stats from DB
    private UserStats getUserStatsFromDB(String username) {
        String sql = "SELECT username, games_played, games_won, CASE WHEN games_won > 0 THEN total_time / games_won / 1000 ELSE 0 END AS avg_time FROM user_stats WHERE username = ?";
        try (java.sql.Connection conn = DBUtil.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int gamesPlayed = rs.getInt("games_played");
                    int gamesWon = rs.getInt("games_won");
                    double avgTime = rs.getDouble("avg_time");
                    // Rank is not calculated here, set to 0
                    return new UserStats(username, gamesPlayed, gamesWon, avgTime, 0);
                }
            }
        } catch (Exception e) {
            System.err.println("[Server] Error reading user stats from DB: " + e.getMessage());
        }
        return new UserStats(username, 0, 0, 0.0, 0);
    }
} 