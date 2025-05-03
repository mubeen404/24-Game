package client;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.awt.Color;
import java.awt.Dimension;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import javax.swing.ListSelectionModel;
import javax.swing.ImageIcon;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import common.GameResult;
import common.JoinGameRequest;
import common.GameStartNotification;
import common.CardDrawMessage;
import common.AnswerSubmission;
import model.UserStats;
import common.Auth;
import common.LeaderboardRequest;
import common.LeaderboardResponse;
import common.UserStatsRequest;
import common.UserStatsResponse;

/**
 * GUI for the RMI Client
 */
public class JPoker24Game extends JFrame {
    private static final long serialVersionUID = 1L;
    
    // JMS-only: no RMI client or auth
    
    // GUI Components
    private JPanel cardPanel;
    private CardLayout cardLayout;
    
    // Login Panel Components
    private JPanel loginPanel;
    private JTextField loginUsernameField;
    private JPasswordField loginPasswordField;
    private JButton loginButton;
    private JButton switchToRegisterButton;
    
    // Register Panel Components
    private JPanel registerPanel;
    private JTextField registerUsernameField;
    private JPasswordField registerPasswordField;
    private JButton registerButton;
    private JButton switchToLoginButton;
    
    // Main Panel Components (after login)
    private JPanel mainPanel;
    private JLabel welcomeLabel;
    private JButton logoutButton;
    
    // New panel components
    private JPanel profilePanel;
    private JPanel leaderboardPanel;
    private JLabel scoreLabel;
    private JTextField bioField;
    private JButton saveProfileButton;
    private JButton backToMainButton;
    private JTable leaderboardTable;
    
    // Card Names
    private static final String LOGIN_PANEL = "Login Panel";
    private static final String REGISTER_PANEL = "Register Panel";
    private static final String MAIN_PANEL = "Main Panel";
    private static final String PROFILE_PANEL = "Profile Panel";
    private static final String LEADERBOARD_PANEL = "Leaderboard Panel";
    private static final String GAME_PANEL = "Game Panel";
    
    // Current user
    private String currentUser;
    
    // Game state
    private boolean gameInProgress = false;
    private List<Integer> currentCards = new ArrayList<>();
    private List<String> currentPlayers = new ArrayList<>();
    
    // Game Panel Components
    private JPanel gamePanel;
    private JPanel cardsPanel;
    private JPanel playersPanel;
    private JLabel gameStatusLabel;
    private JTextField expressionField;
    private JButton submitAnswerButton;
    private JButton joinGameButton;
    private JButton backFromGameButton;
    
    private JPanel statsPanel;
    private JLabel gamesPlayedLabel;
    private JLabel winsLabel;
    private JLabel avgTimeLabel;
    private JLabel rankLabel;
    
    private Context jmsContext;
    private Connection jmsConnection;
    private Session jmsSession;
    private MessageProducer queueProducer;
    private MessageConsumer topicConsumer;
    
    private JPanel operationButtonsPanel;
    private javax.swing.Timer gameTimer;
    private JLabel timerLabel;
    private long gameStartTime;
    
    // Waiting/timeout UI
    private JButton exitWaitingButton;
    private JLabel waitingTimerLabel;
    private javax.swing.Timer waitingTimer;
    private int waitingTimeLeft;
    
    // JMS leaderboard
    private MessageProducer statsProducer;
    private TemporaryQueue statsReplyQueue;
    private MessageConsumer statsReplyConsumer;
    
    // JMS user stats
    private TemporaryQueue userStatsReplyQueue;
    private MessageConsumer userStatsReplyConsumer;
    
    private Auth authService = new Auth() {
        @Override
        public boolean login(String username, String password) { return true; }
        @Override
        public boolean register(String username, String password) { return true; }
        @Override
        public boolean logout(String username) { return true; }
        @Override
        public boolean updateUserProfile(String username, Map<String, String> data) { return true; }
        @Override
        public UserStats getUserStats(String username) { return new UserStats(username); }
        @Override
        public java.util.List<UserStats> getLeaderboardList() { return new ArrayList<>(); }
    };
    
    /**
     * Constructor for JMS-only client (skips RMI/login)
     */
    public JPoker24Game() {
        setupUI();
        setupGameResultListener();
        setupJMSConnection();
        // Start with login panel for submission
        // currentUser = "TestUser_" + (System.currentTimeMillis() % 1000);
        // cardLayout.show(cardPanel, GAME_PANEL);
        // resetGameUI();
    }
    
    /**
     * Set up the UI components
     */
    private void setupUI() {
        setTitle("24-Game Authentication System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(400, 300);
        setLocationRelativeTo(null);
        
        // Create the card layout and panel
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        
        // Create panels
        createLoginPanel();
        createRegisterPanel();
        createMainPanel();
        createProfilePanel();
        createLeaderboardPanel();
        createGamePanel();
        
        // Add panels to the card layout
        cardPanel.add(loginPanel, LOGIN_PANEL);
        cardPanel.add(registerPanel, REGISTER_PANEL);
        cardPanel.add(mainPanel, MAIN_PANEL);
        cardPanel.add(profilePanel, PROFILE_PANEL);
        cardPanel.add(leaderboardPanel, LEADERBOARD_PANEL);
        cardPanel.add(gamePanel, GAME_PANEL);
        
        // Show login panel by default
        cardLayout.show(cardPanel, LOGIN_PANEL);
        System.out.println("Initial UI setup complete: Starting with login panel");
        
        // Add card panel to the frame
        getContentPane().add(cardPanel, BorderLayout.CENTER);
        
        // Debug information
        System.out.println("CardLayout components:");
        System.out.println("- Login panel: " + loginPanel);
        System.out.println("- Register panel: " + registerPanel);
        System.out.println("- Main panel: " + mainPanel);
        System.out.println("- Profile panel: " + profilePanel);
        System.out.println("- Leaderboard panel: " + leaderboardPanel);
        System.out.println("- Game panel: " + gamePanel);
    }
    
    /**
     * Create the login panel
     */
    private void createLoginPanel() {
        // Created to test
        loginPanel = new JPanel();
        loginPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Username label and field
        gbc.gridx = 0;
        gbc.gridy = 0;
        loginPanel.add(new JLabel("Username:"), gbc);
        
        gbc.gridx = 1;
        gbc.gridy = 0;
        loginUsernameField = new JTextField(15);
        loginPanel.add(loginUsernameField, gbc);
        
        // Password label and field
        gbc.gridx = 0;
        gbc.gridy = 1;
        loginPanel.add(new JLabel("Password:"), gbc);
        
        gbc.gridx = 1;
        gbc.gridy = 1;
        loginPasswordField = new JPasswordField(15);
        loginPanel.add(loginPasswordField, gbc);
        
        // Login button
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        loginButton = new JButton("Login");
        loginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleLogin();
            }
        });
        loginPanel.add(loginButton, gbc);
        
        // Switch to register button
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        switchToRegisterButton = new JButton("Register New User");
        switchToRegisterButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cardLayout.show(cardPanel, REGISTER_PANEL);
            }
        });
        loginPanel.add(switchToRegisterButton, gbc);
    }
    
    /**
     * Create the registration panel
     */
    private void createRegisterPanel() {
        // Created to test
        registerPanel = new JPanel();
        registerPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Username label and field
        gbc.gridx = 0;
        gbc.gridy = 0;
        registerPanel.add(new JLabel("New Username:"), gbc);
        
        gbc.gridx = 1;
        gbc.gridy = 0;
        registerUsernameField = new JTextField(15);
        registerPanel.add(registerUsernameField, gbc);
        
        // Password label and field
        gbc.gridx = 0;
        gbc.gridy = 1;
        registerPanel.add(new JLabel("New Password:"), gbc);
        
        gbc.gridx = 1;
        gbc.gridy = 1;
        registerPasswordField = new JPasswordField(15);
        registerPanel.add(registerPasswordField, gbc);
        
        // Register button
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        registerButton = new JButton("Register");
        registerButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleRegister();
            }
        });
        registerPanel.add(registerButton, gbc);
        
        // Switch to login button
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        switchToLoginButton = new JButton("Back to Login");
        switchToLoginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cardLayout.show(cardPanel, LOGIN_PANEL);
            }
        });
        registerPanel.add(switchToLoginButton, gbc);
    }
    
    /**
     * Create the main panel (after login)
     */
    private void createMainPanel() {
        mainPanel = new JPanel();
        mainPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Welcome label
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        welcomeLabel = new JLabel("Welcome!");
        mainPanel.add(welcomeLabel, gbc);
        
        // Play Game button
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        JButton playGameButton = new JButton("Play 24 Game");
        playGameButton.addActionListener(e -> {
            cardLayout.show(cardPanel, GAME_PANEL);
            resetGameUI();
        });
        mainPanel.add(playGameButton, gbc);
        
        // Profile button
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        JButton profileButton = new JButton("View Profile");
        profileButton.addActionListener(e -> cardLayout.show(cardPanel, PROFILE_PANEL));
        mainPanel.add(profileButton, gbc);
        
        // Leaderboard button
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        JButton leaderboardButton = new JButton("View Leaderboard");
        leaderboardButton.addActionListener(e -> {
            updateLeaderboard();
            cardLayout.show(cardPanel, LEADERBOARD_PANEL);
        });
        mainPanel.add(leaderboardButton, gbc);
        
        // Logout button
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        logoutButton = new JButton("Logout");
        logoutButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleLogout();
            }
        });
        mainPanel.add(logoutButton, gbc);
    }
    
    /**
     * Create the profile panel
     */
    private void createProfilePanel() {
        profilePanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // User Stats Section
        statsPanel = new JPanel(new GridLayout(5, 1, 5, 5));
        statsPanel.setBorder(BorderFactory.createTitledBorder("Player Statistics"));

        gamesPlayedLabel = new JLabel("Games Played: 0");
        winsLabel = new JLabel("Wins: 0");
        avgTimeLabel = new JLabel("Average Time to Win: 0.0s");
        rankLabel = new JLabel("Rank: -");

        statsPanel.add(new JLabel("Username: " + currentUser));
        statsPanel.add(gamesPlayedLabel);
        statsPanel.add(winsLabel);
        statsPanel.add(avgTimeLabel);
        statsPanel.add(rankLabel);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        profilePanel.add(statsPanel, gbc);

        // Back button
        JButton backButton = new JButton("Back to Main");
        backButton.addActionListener(e -> cardLayout.show(cardPanel, MAIN_PANEL));
        gbc.gridy = 1;
        profilePanel.add(backButton, gbc);

        // Add a refresh button
        JButton refreshButton = new JButton("Refresh Stats");
        refreshButton.addActionListener(e -> {
            System.out.println("Refreshing stats for: " + currentUser);
            updateUserStats();
        });
        gbc.gridy = 2;
        profilePanel.add(refreshButton, gbc);

        // Update stats when profile is shown
        profilePanel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                System.out.println("Profile panel shown for user: " + currentUser);
                updateUserStats();
            }
        });
    }
    
    /**
     * Create the leaderboard panel
     */
    private void createLeaderboardPanel() {
        leaderboardPanel = new JPanel(new BorderLayout(10, 10));
        leaderboardPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Create table model with columns
        String[] columnNames = {"Rank", "Username", "Games Won", "Games Played", "Avg Time"};
        DefaultTableModel model = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Make table read-only
            }
        };
        
        leaderboardTable = new JTable(model);
        leaderboardTable.setFillsViewportHeight(true);
        
        // Style the table
        leaderboardTable.getTableHeader().setReorderingAllowed(false);
        leaderboardTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        JScrollPane scrollPane = new JScrollPane(leaderboardTable);
        leaderboardPanel.add(scrollPane, BorderLayout.CENTER);

        // Back button
        JButton backButton = new JButton("Back to Main");
        backButton.addActionListener(e -> cardLayout.show(cardPanel, MAIN_PANEL));
        leaderboardPanel.add(backButton, BorderLayout.SOUTH);

        // Refresh button
        JButton refreshButton = new JButton("Refresh Leaderboard");
        refreshButton.addActionListener(e -> updateLeaderboard());
        leaderboardPanel.add(refreshButton, BorderLayout.NORTH);
    }
    
    /**
     * Create the game panel for playing the 24 Game
     */
    private void createGamePanel() {
        gamePanel = new JPanel(new BorderLayout(10, 10));
        gamePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Create top panel with vertical layout for status, instruction, and back button
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        // Game status label
        gameStatusLabel = new JLabel("Game Status: Ready to join");
        gameStatusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        gameStatusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        topPanel.add(gameStatusLabel);
        // Instruction label
        JLabel instructionLabel = new JLabel("Make 24 using all four cards and any of +, -, ×, ÷ operations");
        instructionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        instructionLabel.setFont(new Font("Arial", Font.ITALIC, 12));
        topPanel.add(instructionLabel);
        // Back button
        backFromGameButton = new JButton("Back to Main");
        backFromGameButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        backFromGameButton.addActionListener(e -> {
            gameInProgress = false;
            currentCards.clear();
            gameTimer.stop();
            cardLayout.show(cardPanel, MAIN_PANEL);
        });
        topPanel.add(Box.createVerticalStrut(5));
        topPanel.add(backFromGameButton);
        gamePanel.add(topPanel, BorderLayout.NORTH);
        
        // Create cards panel to display the 4 cards (with images and hover effect)
        cardsPanel = new JPanel(new GridLayout(1, 4, 10, 0));
        cardsPanel.setBorder(BorderFactory.createTitledBorder("Cards"));
        for (int i = 0; i < 4; i++) {
            JLabel cardLabel = new JLabel();
            cardLabel.setPreferredSize(new Dimension(100, 150));
            cardLabel.setHorizontalAlignment(JLabel.CENTER);
            cardLabel.setVerticalAlignment(JLabel.CENTER);
            cardLabel.setOpaque(true);
            cardLabel.setBackground(Color.WHITE);
            cardLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
            // Hover effect
            cardLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    cardLabel.setBorder(BorderFactory.createLineBorder(new Color(30, 144, 255), 3));
                    cardLabel.setBackground(new Color(240, 248, 255));
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    cardLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
                    cardLabel.setBackground(Color.WHITE);
                }
            });
            cardsPanel.add(cardLabel);
        }
        
        // Create players panel
        playersPanel = new JPanel();
        playersPanel.setLayout(new BoxLayout(playersPanel, BoxLayout.Y_AXIS));
        playersPanel.setBorder(BorderFactory.createTitledBorder("Players"));
        playersPanel.setPreferredSize(new Dimension(150, 200));
        
        // Create center panel to hold cards and players
        JPanel centerPanel = new JPanel(new BorderLayout(10, 0));
        centerPanel.add(cardsPanel, BorderLayout.CENTER);
        centerPanel.add(playersPanel, BorderLayout.EAST);
        gamePanel.add(centerPanel, BorderLayout.CENTER);
        
        // Create bottom panel for expression input, operations and buttons
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 0));
        
        // Create timer label
        timerLabel = new JLabel("Time: 0s");
        timerLabel.setFont(new Font("Arial", Font.BOLD, 14));
        timerLabel.setHorizontalAlignment(JLabel.CENTER);
        
        // Create game timer
        gameTimer = new javax.swing.Timer(1000, e -> updateTimer());
        
        // Waiting timer label
        waitingTimerLabel = new JLabel("");
        waitingTimerLabel.setFont(new Font("Arial", Font.BOLD, 12));
        waitingTimerLabel.setHorizontalAlignment(JLabel.CENTER);
        waitingTimerLabel.setForeground(Color.BLUE);
        waitingTimerLabel.setVisible(false);
        
        // Create join game button
        joinGameButton = new JButton("Join Game");
        joinGameButton.addActionListener(e -> joinGame());
        
        // Exit waiting button
        exitWaitingButton = new JButton("Exit Waiting");
        exitWaitingButton.setEnabled(false);
        exitWaitingButton.setVisible(false);
        exitWaitingButton.addActionListener(e -> {
            stopWaitingTimer();
            resetGameUI();
            cardLayout.show(cardPanel, MAIN_PANEL);
        });
        
        // Create top part of bottom panel (join button and timer)
        JPanel joinTimerPanel = new JPanel(new BorderLayout(5, 0));
        joinTimerPanel.add(joinGameButton, BorderLayout.WEST);
        joinTimerPanel.add(timerLabel, BorderLayout.CENTER);
        joinTimerPanel.add(waitingTimerLabel, BorderLayout.EAST);
        joinTimerPanel.add(exitWaitingButton, BorderLayout.SOUTH);
        
        // Create operation buttons panel
        operationButtonsPanel = new JPanel(new GridLayout(1, 6, 5, 0));
        String[] operations = {"+", "-", "×", "÷", "(", ")"};
        for (String op : operations) {
            JButton opButton = new JButton(op);
            opButton.addActionListener(e -> {
                if (expressionField.isEnabled()) {
                    expressionField.setText(expressionField.getText() + op);
                    expressionField.requestFocus();
                }
            });
            operationButtonsPanel.add(opButton);
        }
        
        // Create expression panel
        JPanel expressionPanel = new JPanel(new BorderLayout(5, 0));
        expressionPanel.add(new JLabel("Expression: "), BorderLayout.WEST);
        expressionField = new JTextField();
        expressionField.setEnabled(false); // Initially disabled until game starts
        expressionPanel.add(expressionField, BorderLayout.CENTER);
        
        submitAnswerButton = new JButton("Submit");
        submitAnswerButton.setEnabled(false); // Initially disabled until game starts
        submitAnswerButton.addActionListener(e -> submitAnswer());
        expressionPanel.add(submitAnswerButton, BorderLayout.EAST);
        
        // Add all components to bottom panel
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        bottomPanel.add(joinTimerPanel);
        bottomPanel.add(Box.createVerticalStrut(5));
        bottomPanel.add(operationButtonsPanel);
        bottomPanel.add(Box.createVerticalStrut(5));
        bottomPanel.add(expressionPanel);
        
        gamePanel.add(bottomPanel, BorderLayout.SOUTH);
    }
    
    /**
     * Handle login
     */
    private void handleLogin() {
        // Skip authentication for JMS-only testing
        if (authService == null) {
            currentUser = loginUsernameField.getText().trim();
            welcomeLabel.setText("Welcome, " + currentUser + "!");
            cardLayout.show(cardPanel, MAIN_PANEL);
            JOptionPane.showMessageDialog(this, "Login bypassed for JMS testing!");
            return;
        }
        
        String username = loginUsernameField.getText().trim();
        String password = new String(loginPasswordField.getPassword());
        System.out.println("Attempting login for user: '" + username + "' with password: '" + password + "'");
        
        if (username.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Username cannot be empty");
            return;
        }
        
        boolean success = authService.login(username, password);
        if (success) {
            currentUser = username;
            System.out.println("Login successful for: " + currentUser);
            welcomeLabel.setText("Welcome, " + currentUser + "!");
            // Update the username label in the profile panel
            for (Component comp : statsPanel.getComponents()) {
                if (comp instanceof JLabel && ((JLabel) comp).getText().startsWith("Username:")) {
                    ((JLabel) comp).setText("Username: " + currentUser);
                    break;
                }
            }
            // Switch to main panel and show success message
            cardLayout.show(cardPanel, MAIN_PANEL);
            JOptionPane.showMessageDialog(this, "Login successful!");
        } else {
            System.out.println("Login failed for: " + username);
            JOptionPane.showMessageDialog(this, "Login failed. Please check your credentials.");
        }
    }
    
    /**
     * Handle registration
     */
    private void handleRegister() {
        // Skip authentication for JMS-only testing
        if (authService == null) {
            currentUser = registerUsernameField.getText().trim();
            welcomeLabel.setText("Welcome, " + currentUser + "!");
            cardLayout.show(cardPanel, MAIN_PANEL);
            JOptionPane.showMessageDialog(this, "Registration bypassed for JMS testing!");
            return;
        }
        
        String username = registerUsernameField.getText();
        String password = new String(registerPasswordField.getPassword());
        boolean success = authService.register(username, password);
        if (success) {
            currentUser = username;
            welcomeLabel.setText("Welcome, " + currentUser + "!");
            // Update the username label in the profile panel
            for (Component comp : statsPanel.getComponents()) {
                if (comp instanceof JLabel && ((JLabel) comp).getText().startsWith("Username:")) {
                    ((JLabel) comp).setText("Username: " + currentUser);
                    break;
                }
            }
            cardLayout.show(cardPanel, MAIN_PANEL);
            JOptionPane.showMessageDialog(this, "Registration successful!");
        } else {
            JOptionPane.showMessageDialog(this, "Registration failed. Username may already exist.");
        }
    }
    
    /**
     * Handle logout
     */
    private void handleLogout() {
        // Skip authentication for JMS-only testing
        if (authService == null) {
            currentUser = null;
            cardLayout.show(cardPanel, LOGIN_PANEL);
            return;
        }
        
        System.out.println("Logout button clicked for user: " + currentUser);
        
        try {
            // Store current user before logout attempt
            String username = currentUser;
            
            if (username == null || username.isEmpty()) {
                System.out.println("Error: No user is currently logged in");
                return;
            }
            
            System.out.println("Calling server logout for: " + username);
            
            // Call the server's logout method
            boolean success = authService.logout(username);
            System.out.println("Server logout result: " + success);
            
            // First switch to login panel without any delays
            System.out.println("Attempting to switch to login panel");
            cardLayout.show(cardPanel, LOGIN_PANEL);
            
            // Reset UI state after switching
            currentUser = null;
            loginUsernameField.setText("");
            loginPasswordField.setText("");
            
            // Force UI to update
            revalidate();
            repaint();
            
            // Show message after UI is updated
            System.out.println("Showing result dialog");
            if (success) {
                JOptionPane.showMessageDialog(this, "Logout successful!");
            } else {
                JOptionPane.showMessageDialog(this, "Logout failed on server, but UI has been reset.");
            }
            
            // Double check we're still on login panel after the dialog
            System.out.println("Final check to ensure we're on the login panel");
            cardLayout.show(cardPanel, LOGIN_PANEL);
            revalidate();
            repaint();
            
        } catch (Exception e) {
            System.err.println("Error during logout: " + e.getMessage());
            // Even if server communication fails, go back to login screen
            cardLayout.show(cardPanel, LOGIN_PANEL);
            JOptionPane.showMessageDialog(this, "Error during logout: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void handleSaveProfile() {
        Map<String, String> profileData = new HashMap<>();
        profileData.put("bio", bioField.getText());
        
        boolean success = authService.updateUserProfile(currentUser, profileData);
        if (success) {
            JOptionPane.showMessageDialog(this, "Profile updated successfully!");
        } else {
            JOptionPane.showMessageDialog(this, "Failed to update profile.");
        }
    }
    
    /**
     * Update user stats
     */
    private void updateUserStats() {
        try {
            if (currentUser == null || currentUser.trim().isEmpty()) {
                System.err.println("Error: No user is currently logged in");
                return;
            }
            System.out.println("[Client] Sending UserStatsRequest for: " + currentUser);
            UserStatsRequest req = new UserStatsRequest(currentUser);
            ObjectMessage msg = jmsSession.createObjectMessage(req);
            msg.setJMSReplyTo(userStatsReplyQueue);
            statsProducer.send(msg);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error requesting user stats: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void updateProfilePanel(UserStats stats) {
        if (stats == null) return;
        gamesPlayedLabel.setText("Games Played: " + stats.getGamesPlayed());
        winsLabel.setText("Wins: " + stats.getGamesWon());
        avgTimeLabel.setText(String.format("Average Time to Win: %.1fs", stats.getAvgTimeToWin()));
        rankLabel.setText("Rank: " + (stats.getRank() > 0 ? stats.getRank() : "-"));
        // Update username label in statsPanel
        for (Component comp : statsPanel.getComponents()) {
            if (comp instanceof JLabel && ((JLabel) comp).getText().startsWith("Username:")) {
                ((JLabel) comp).setText("Username: " + stats.getUsername());
                break;
            }
        }
    }
    
    /**
     * Update leaderboard
     */
    private void updateLeaderboard() {
        try {
            System.out.println("[Client] Sending LeaderboardRequest");
            LeaderboardRequest req = new LeaderboardRequest();
            ObjectMessage msg = jmsSession.createObjectMessage(req);
            msg.setJMSReplyTo(statsReplyQueue);
            statsProducer.send(msg);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error requesting leaderboard: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Set up the GameResult listener to receive game results from the server
     */
    private void setupGameResultListener() {
        try {
            // Set up JMS connection properties
            Properties props = new Properties();
            props.setProperty(Context.INITIAL_CONTEXT_FACTORY, "com.sun.enterprise.naming.SerialInitContextFactory");
            props.setProperty(Context.PROVIDER_URL, "iiop://localhost:3700");
            Context context = new InitialContext(props);
            
            // Create a connection to the JMS provider
            ConnectionFactory connectionFactory = (ConnectionFactory) context.lookup("jms/JPoker24GameConnectionFactory");
            Connection connection = connectionFactory.createConnection();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            
            // Lookup the JMS topic
            Topic gameResultTopic = (Topic) context.lookup("jms/JPoker24GameTopic");
            
            // Create a consumer for the topic
            MessageConsumer consumer = session.createConsumer(gameResultTopic);
            
            // Set the message listener
            consumer.setMessageListener(new MessageListener() {
                @Override
                public void onMessage(Message message) {
                    try {
                        if (message instanceof ObjectMessage) {
                            ObjectMessage objectMessage = (ObjectMessage) message;
                            Object content = objectMessage.getObject();
                            
                            if (content instanceof GameResult) {
                                GameResult result = (GameResult) content;
                                
                                // Update the GUI with the game result
                                SwingUtilities.invokeLater(() -> {
                                    displayGameResult(result);
                                });
                            }
                        }
                    } catch (JMSException e) {
                        e.printStackTrace();
                        JOptionPane.showMessageDialog(JPoker24Game.this, 
                            "Error receiving game result: " + e.getMessage(),
                            "Communication Error", 
                            JOptionPane.ERROR_MESSAGE);
                    }
                }
            });
            
            // Start the connection
            connection.start();
            System.out.println("GameResult listener set up successfully");
            
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, 
                "Error setting up game result listener: " + e.getMessage(),
                "Communication Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Display the game result in a dialog
     */
    private void displayGameResult(GameResult result) {
        gameTimer.stop();
        stopWaitingTimer();
        
        // Create a dialog to show the result
        JDialog resultDialog = new JDialog(this, "Game Result", true);
        resultDialog.setLayout(new BorderLayout());
        
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        // Add winner information
        JLabel winnerLabel = new JLabel("Winner: " + result.getWinner());
        winnerLabel.setFont(new Font("Arial", Font.BOLD, 16));
        winnerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(winnerLabel);
        panel.add(Box.createVerticalStrut(10));
        
        // Add winning expression
        JLabel expressionLabel = new JLabel("Winning Expression: " + result.getMessage());
        expressionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(expressionLabel);
        panel.add(Box.createVerticalStrut(20));
        
        // Add player results
        JLabel playersLabel = new JLabel("Player Results:");
        playersLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(playersLabel);
        
        JPanel resultsPanel = new JPanel(new GridLayout(0, 1, 5, 5));
        for (Map.Entry<String, Boolean> entry : result.getPlayerResults().entrySet()) {
            JLabel playerLabel = new JLabel(entry.getKey() + ": " + (entry.getValue() ? "Win" : "Loss"));
            resultsPanel.add(playerLabel);
        }
        panel.add(resultsPanel);
        panel.add(Box.createVerticalStrut(20));
        
        // Add a button to close the dialog
        JButton closeButton = new JButton("OK");
        closeButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        closeButton.addActionListener(e -> resultDialog.dispose());
        panel.add(closeButton);
        
        resultDialog.add(panel, BorderLayout.CENTER);
        resultDialog.setSize(400, 300);
        resultDialog.setLocationRelativeTo(this);
        resultDialog.setVisible(true);
        
        // Update leaderboard and user stats after a game
        updateLeaderboard();
        updateUserStats();
        
        // Reset game state after displaying results
        gameInProgress = false;
        joinGameButton.setEnabled(true);
        expressionField.setText("");
        expressionField.setEnabled(false);
        submitAnswerButton.setEnabled(false);
        
        if (result.getWinner() == null || result.getWinner().isEmpty()) {
            // Timeout or not enough players
            JOptionPane.showMessageDialog(this, "Not enough players joined. Please try again.", "Timeout", JOptionPane.WARNING_MESSAGE);
            resetGameUI();
            return;
        }
        
        if (currentUser != null && currentUser.equals(result.getWinner())) {
            gameStatusLabel.setText("Game Status: You won! Join a new game.");
        } else {
            gameStatusLabel.setText("Game Status: Game over. Winner: " + result.getWinner());
        }
    }
    
    /**
     * Reset the game UI to its initial state
     */
    private void resetGameUI() {
        gameStatusLabel.setText("Game Status: Ready to join");
        expressionField.setText("");
        expressionField.setEnabled(false);
        submitAnswerButton.setEnabled(false);
        joinGameButton.setEnabled(true);
        currentCards.clear();
        currentPlayers.clear();
        gameInProgress = false;
        stopWaitingTimer();
        
        // Reset card placeholders
        for (Component card : cardsPanel.getComponents()) {
            if (card instanceof JLabel) {
                JLabel cardLabel = (JLabel) card;
                cardLabel.setIcon(null);
                cardLabel.setText("");
            }
        }
        
        // Reset players list
        playersPanel.removeAll();
        playersPanel.revalidate();
        playersPanel.repaint();
        
        // Reset timer
        gameTimer.stop();
        timerLabel.setText("Time: 0s");
    }

    /**
     * Set up JMS connection for game communication
     */
    private void setupJMSConnection() {
        try {
            // Set up JMS connection properties
            Properties props = new Properties();
            props.setProperty(Context.INITIAL_CONTEXT_FACTORY, "com.sun.enterprise.naming.SerialInitContextFactory");
            props.setProperty(Context.PROVIDER_URL, "iiop://localhost:3700");
            jmsContext = new InitialContext(props);
            
            // Create a connection to the JMS provider
            ConnectionFactory connectionFactory = (ConnectionFactory) jmsContext.lookup("jms/JPoker24GameConnectionFactory");
            jmsConnection = connectionFactory.createConnection();
            jmsSession = jmsConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            
            // Set up queue for sending requests
            javax.jms.Queue queue = (javax.jms.Queue) jmsContext.lookup("jms/JPoker24GameQueue");
            queueProducer = jmsSession.createProducer(queue);
            
            // Set up topic for receiving notifications
            Topic topic = (Topic) jmsContext.lookup("jms/JPoker24GameTopic");
            topicConsumer = jmsSession.createConsumer(topic);
            
            // Set the message listener for game events
            topicConsumer.setMessageListener(new MessageListener() {
                @Override
                public void onMessage(Message message) {
                    try {
                        if (message instanceof ObjectMessage) {
                            ObjectMessage objectMessage = (ObjectMessage) message;
                            Object content = objectMessage.getObject();
                            
                            if (content instanceof GameStartNotification) {
                                SwingUtilities.invokeLater(() -> handleGameStart((GameStartNotification) content));
                            } else if (content instanceof CardDrawMessage) {
                                SwingUtilities.invokeLater(() -> handleCardDraw((CardDrawMessage) content));
                            }
                        }
                    } catch (JMSException e) {
                        e.printStackTrace();
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(JPoker24Game.this, 
                                "Error receiving game message: " + e.getMessage(),
                                "Communication Error", 
                                JOptionPane.ERROR_MESSAGE);
                        });
                    }
                }
            });
            
            // --- Leaderboard JMS setup ---
            javax.jms.Queue statsQueue = (javax.jms.Queue) jmsContext.lookup("jms/JPoker24StatsQueue");
            statsProducer = jmsSession.createProducer(statsQueue);
            statsReplyQueue = jmsSession.createTemporaryQueue();
            statsReplyConsumer = jmsSession.createConsumer(statsReplyQueue);
            statsReplyConsumer.setMessageListener(new MessageListener() {
                @Override
                public void onMessage(Message message) {
                    try {
                        if (message instanceof ObjectMessage) {
                            ObjectMessage objMsg = (ObjectMessage) message;
                            Object obj = objMsg.getObject();
                            if (obj instanceof LeaderboardResponse) {
                                List<UserStats> stats = ((LeaderboardResponse) obj).getLeaderboard();
                                SwingUtilities.invokeLater(() -> updateLeaderboardTable(stats));
                            }
                        }
                    } catch (JMSException e) {
                        e.printStackTrace();
                    }
                }
            });
            
            // --- User Stats JMS setup ---
            userStatsReplyQueue = jmsSession.createTemporaryQueue();
            userStatsReplyConsumer = jmsSession.createConsumer(userStatsReplyQueue);
            userStatsReplyConsumer.setMessageListener(new MessageListener() {
                @Override
                public void onMessage(Message message) {
                    try {
                        if (message instanceof ObjectMessage) {
                            ObjectMessage objMsg = (ObjectMessage) message;
                            Object obj = objMsg.getObject();
                            if (obj instanceof UserStatsResponse) {
                                UserStats stats = ((UserStatsResponse) obj).getUserStats();
                                SwingUtilities.invokeLater(() -> updateProfilePanel(stats));
                            }
                        }
                    } catch (JMSException e) {
                        e.printStackTrace();
                    }
                }
            });
            
            // Start the connection
            jmsConnection.start();
            System.out.println("JMS connection set up successfully");
            
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, 
                "Error setting up JMS connection: " + e.getMessage(),
                "Communication Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Send a request to join a game
     */
    private void joinGame() {
        try {
            joinGameButton.setEnabled(false);
            gameStatusLabel.setText("Game Status: Joining game...");
            
            // Start waiting timer for 10 seconds
            waitingTimeLeft = 10;
            waitingTimerLabel.setText("Waiting: " + waitingTimeLeft + "s");
            waitingTimerLabel.setVisible(true);
            exitWaitingButton.setEnabled(true);
            exitWaitingButton.setVisible(true);
            waitingTimer = new javax.swing.Timer(1000, e -> {
                waitingTimeLeft--;
                if (waitingTimeLeft > 0) {
                    waitingTimerLabel.setText("Waiting: " + waitingTimeLeft + "s");
                } else {
                    waitingTimerLabel.setText("Timeout! Not enough players.");
                    stopWaitingTimer();
                    joinGameButton.setEnabled(true);
                    exitWaitingButton.setEnabled(false);
                }
            });
            waitingTimer.start();
            
            JoinGameRequest request = new JoinGameRequest(currentUser);
            ObjectMessage message = jmsSession.createObjectMessage(request);
            queueProducer.send(message);
            
            gameStatusLabel.setText("Game Status: Waiting for other players...");
            System.out.println("Sent join game request for user: " + currentUser);
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, 
                "Error joining game: " + e.getMessage(),
                "Communication Error", 
                JOptionPane.ERROR_MESSAGE);
            
            // Re-enable join button if there was an error
            joinGameButton.setEnabled(true);
            gameStatusLabel.setText("Game Status: Ready to join");
        }
    }

    /**
     * Submit an answer for the current game
     */
    private void submitAnswer() {
        try {
            if (!gameInProgress || currentCards.isEmpty()) {
                JOptionPane.showMessageDialog(this, 
                    "No game in progress or no cards drawn",
                    "Game Error", 
                    JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            String expression = expressionField.getText().trim();
            if (expression.isEmpty()) {
                JOptionPane.showMessageDialog(this, 
                    "Please enter an expression",
                    "Input Error", 
                    JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            // Disable submit button to prevent multiple submissions
            submitAnswerButton.setEnabled(false);
            expressionField.setEnabled(false);
            
            AnswerSubmission submission = new AnswerSubmission(currentUser, expression, new ArrayList<>(currentCards));
            ObjectMessage message = jmsSession.createObjectMessage(submission);
            queueProducer.send(message);
            
            gameStatusLabel.setText("Game Status: Answer submitted, waiting for results...");
            System.out.println("Submitted answer: " + expression);
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, 
                "Error submitting answer: " + e.getMessage(),
                "Communication Error", 
                JOptionPane.ERROR_MESSAGE);
            
            // Re-enable inputs if there was an error
            submitAnswerButton.setEnabled(true);
            expressionField.setEnabled(true);
        }
    }

    /**
     * Handle a game start notification
     */
    private void handleGameStart(GameStartNotification notification) {
        gameInProgress = true;
        currentPlayers = notification.getPlayers();
        currentCards.clear();
        
        gameStatusLabel.setText("Game Status: Game starting!");
        
        // Update players list
        playersPanel.removeAll();
        for (String player : currentPlayers) {
            JLabel playerLabel = new JLabel(player);
            playerLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            if (player.equals(currentUser)) {
                playerLabel.setFont(new Font(playerLabel.getFont().getName(), Font.BOLD, playerLabel.getFont().getSize()));
            }
            playersPanel.add(playerLabel);
            playersPanel.add(Box.createVerticalStrut(5));
        }
        playersPanel.revalidate();
        playersPanel.repaint();
        
        System.out.println("Game starting with players: " + currentPlayers);
        
        // Start the timer
        gameStartTime = notification.getStartTime();
        timerLabel.setText("Time: 0s");
        gameTimer.start();
    }

    /**
     * Handle a card draw message
     */
    private void handleCardDraw(CardDrawMessage message) {
        currentCards = new ArrayList<>(message.getCards());
        
        gameStatusLabel.setText("Game Status: Cards drawn! Make 24 using these cards.");
        
        // Update card display with images
        Component[] cardComponents = cardsPanel.getComponents();
        for (int i = 0; i < currentCards.size() && i < cardComponents.length; i++) {
            if (cardComponents[i] instanceof JLabel) {
                JLabel cardLabel = (JLabel) cardComponents[i];
                int cardValue = currentCards.get(i);
                String imageName = getCardImageFileName(cardValue);
                ImageIcon icon = new ImageIcon(getClass().getResource("/client/cards/" + imageName));
                cardLabel.setIcon(new ImageIcon(icon.getImage().getScaledInstance(90, 140, java.awt.Image.SCALE_SMOOTH)));
                cardLabel.setText("");
            }
        }
        
        // Enable input for answer
        expressionField.setEnabled(true);
        submitAnswerButton.setEnabled(true);
        
        System.out.println("Cards drawn: " + currentCards);
    }

    /**
     * Map card value to image file name (spades only for demo)
     */
    private String getCardImageFileName(int value) {
        switch (value) {
            case 1: return "ace_of_spades.png";
            case 11: return "jack_of_spades.png";
            case 12: return "queen_of_spades.png";
            case 13: return "king_of_spades.png";
            default: return value + "_of_spades.png";
        }
    }

    /**
     * Update the game timer display
     */
    private void updateTimer() {
        if (gameInProgress && gameStartTime > 0) {
            long elapsedSeconds = (System.currentTimeMillis() - gameStartTime) / 1000;
            timerLabel.setText("Time: " + elapsedSeconds + "s");
        }
    }

    /**
     * Close JMS resources
     */
    private void closeJMSResources() {
        try {
            if (queueProducer != null) queueProducer.close();
            if (topicConsumer != null) topicConsumer.close();
            if (jmsSession != null) jmsSession.close();
            if (jmsConnection != null) jmsConnection.close();
            if (jmsContext != null) jmsContext.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Override the dispose method to clean up resources
    @Override
    public void dispose() {
        closeJMSResources();
        super.dispose();
    }

    private void stopWaitingTimer() {
        if (waitingTimer != null) {
            waitingTimer.stop();
        }
        waitingTimerLabel.setVisible(false);
        exitWaitingButton.setEnabled(false);
        exitWaitingButton.setVisible(false);
    }

    // Actually update the leaderboard table with received stats
    private void updateLeaderboardTable(List<UserStats> stats) {
        System.out.println("[Client] Received leaderboard with " + (stats == null ? 0 : stats.size()) + " users");
        DefaultTableModel model = (DefaultTableModel) leaderboardTable.getModel();
        model.setRowCount(0); // Clear existing data
        if (stats != null) {
            for (UserStats stat : stats) {
                model.addRow(new Object[]{
                    stat.getRank(),
                    stat.getUsername(),
                    stat.getGamesWon(),
                    stat.getGamesPlayed(),
                    String.format("%.1fs", stat.getAvgTimeToWin())
                });
            }
        }
    }

    public static void main(String[] args) {
    javax.swing.SwingUtilities.invokeLater(() -> {
        new JPoker24Game().setVisible(true);
    });
    }


} 

