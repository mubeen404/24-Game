# 24 Game – Distributed System with JMS, MySQL, and Java Swing

## Overview

This project is a complete implementation of the "24 Game" as a distributed system using Java Messaging Service (JMS) for all client-server communication, a MySQL database for persistent user statistics and leaderboard, and a modern Java Swing GUI. The system is designed to run with GlassFish 5 as the JMS provider.

---

## Features

- **Modern GUI:** Intuitive Swing interface for login, registration, gameplay, leaderboard, and profile.
- **Distributed Game Logic:** Server matches players, manages game state, and validates answers.
- **JMS-Only Communication:** All interactions use JMS queues and topics (no RMI, no direct DB access from client).
- **Persistent Leaderboard:** MySQL database tracks games played, games won, and average winning time.
- **Robust Multiplayer Queue:** Handles all edge cases for joining and starting games, with clear feedback.
- **Real-Time Stats:** Leaderboard and profile update instantly after each game.
- **Card Images:** Realistic card visuals for an engaging experience.

---

## Directory Structure

```
src/
  client/
    JPoker24Game.java         # Main client GUI
    cards/                    # Card images (PNG)
  server/
    JPoker24GameServer.java   # Main server logic
    DBUtil.java               # Database utility
  common/
    ...                       # Shared message and model classes
  model/
    UserStats.java, User.java # Data models
lib/
  mysql-connector-j-8.0.31.jar
README.md
```

---

## Setup Instructions

### 1. Prerequisites

- **Java 8+**
- **MySQL**
- **GlassFish 5** (full platform, [download here](https://javaee.github.io/glassfish/download))

### 2. MySQL Database

Create the database and table:
```sql
CREATE DATABASE IF NOT EXISTS game24db;
USE game24db;
CREATE TABLE IF NOT EXISTS user_stats (
  username VARCHAR(255) PRIMARY KEY,
  games_played INT NOT NULL DEFAULT 0,
  games_won INT NOT NULL DEFAULT 0,
  total_time BIGINT NOT NULL DEFAULT 0
);
```

### 3. GlassFish JMS Resources

Create these resources in the GlassFish Admin Console or via `asadmin`:
- **Connection Factory:** `jms/JPoker24GameConnectionFactory`
- **Queue:** `jms/JPoker24GameQueue`
- **Topic:** `jms/JPoker24GameTopic`
- **Stats Queue:** `jms/JPoker24StatsQueue`

### 4. Compile the Project

From the project root:
```sh
javac -d bin -cp "lib/mysql-connector-j-8.0.31.jar:glassfish5/mq/lib/jms.jar:glassfish5/glassfish/lib/gf-client.jar" src/common/*.java src/model/*.java src/server/*.java src/client/*.java
```

---

## Running the Application

### 1. Start GlassFish and MySQL

Make sure both are running and JMS resources are configured.

### 2. Start the Server

```sh
java -cp "bin:lib/mysql-connector-j-8.0.31.jar:glassfish5/mq/lib/jms.jar:glassfish5/glassfish/lib/gf-client.jar" server.JPoker24GameServer
```

### 3. Start the Client

Open a new terminal for each client:
```sh
java -cp "bin:lib/mysql-connector-j-8.0.31.jar:glassfish5/mq/lib/jms.jar:glassfish5/glassfish/lib/gf-client.jar" client.JPoker24Game
```

---

## Game Flow

1. **Login/Register:** User logs in or creates a new account.
2. **Join Game:** Click "Join Game" to enter the queue. The server starts a game when 2+ players join within 10 seconds, or immediately with 4.
3. **Game Play:** Four cards are drawn. Players enter an expression using +, -, ×, ÷, and parentheses to make 24.
4. **Game Over:** Server validates answers, determines the winner, updates stats, and broadcasts results.
5. **Leaderboard/Profile:** View real-time stats and rankings.

---


## Program Organization

- **client.JPoker24Game:** Main GUI client, handles all user interaction and JMS communication.
- **server.JPoker24GameServer:** Main server, manages game logic, player matching, answer validation, and stats.
- **common:** Shared message and model classes for JMS.
- **model:** Data models for user stats and user info.
- **lib:** MySQL JDBC driver (and any other required libraries).

---

## Notes

- All communication is via JMS (no RMI or direct DB access from client).
- The queue/timer logic is robust and handles all edge cases for joining and starting games.
- The leaderboard and profile are always up-to-date and reflect the latest database state.


