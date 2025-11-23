# 24 Game – Distributed System with JMS, Kafka, Redis, Hadoop, and Java Swing

## Overview

This project implements the classic “24 Game” as a distributed application. Gameplay runs over Java Messaging Service (JMS) with GlassFish 5, statistics are persisted in MySQL, and a Java Swing client delivers the UI. The platform now adds an end-to-end analytics pipeline: gameplay events stream through Apache Kafka, cached in Redis for live leaderboards, and periodically recomputed via Apache Hadoop for historical accuracy.

---

## Features

- **Modern GUI:** Intuitive Swing interface for login, registration, gameplay, leaderboard, and profile views.
- **Distributed Game Logic:** Server coordinates lobby matchmaking, game rounds, and answer validation entirely via JMS.
- **Persistent Storage:** MySQL keeps per-user win/loss counts and aggregate timing metrics.
- **Real-Time Analytics:** Kafka events are pushed to Redis to keep leaderboards instantly fresh.
- **Batch Analytics:** Hadoop Streaming processes Kafka archives to rebuild long-term statistics and reconcile drift.
- **Production-Ready Scripts:** Shell helpers compile, run, ingest Kafka data, launch Hadoop jobs, and apply results.

---

## Directory Structure

```
analytics/
  kafka_to_hdfs_ingest.sh       # Streams Kafka topic into HDFS
  run_hadoop_leaderboard.sh     # Hadoop Streaming wrapper
  mr_leaderboard_mapper.py      # Streaming mapper (Python)
  mr_leaderboard_reducer.py     # Streaming reducer (Python)
  apply_leaderboard_results.sh  # Loads Hadoop output into MySQL & Redis
docs/
  data_pipeline.md              # Detailed architecture and runbook
lib/
  mysql-connector-j-8.0.31.jar
  kafka-clients-3.5.1.jar
  jedis-4.4.5.jar
  commons-pool2-2.11.1.jar
  slf4j-api-1.7.36.jar
  lz4-java-1.8.0.jar
  snappy-java-1.1.10.5.jar
  zstd-jni-1.5.5-5.jar
src/
  client/JPoker24Game.java      # Main Swing client
  client/cards/                 # Card assets
  server/JPoker24GameServer.java# Game server
  server/DBUtil.java            # MySQL helper
  common/                       # Shared JMS payloads
  model/                        # POJOs for users/stats
README.md
```

---

## Setup Instructions

### 1. Prerequisites

- **Java 8+**
- **MySQL 8+**
- **GlassFish 5** (full platform, [download here](https://javaee.github.io/glassfish/download))
- **Apache Kafka 3.5.x**
- **Redis 7+** (Docker `redis24` container is fine)
- **Apache Hadoop 3.2.x** (HDFS + YARN)

> See [`docs/data_pipeline.md`](docs/data_pipeline.md) for environment variables, service start commands, and a full runbook.

### 2. MySQL Database

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

Create these resources via the GlassFish Admin Console or `asadmin`:

- **Connection Factory:** `jms/JPoker24GameConnectionFactory`
- **Queue:** `jms/JPoker24GameQueue`
- **Topic:** `jms/JPoker24GameTopic`
- **Stats Queue:** `jms/JPoker24StatsQueue`

### 4. Build the Project

From the repository root:

```sh
./compile.sh
```

---

## Running the Application

### Core Gameplay Loop

1. **Start GlassFish & MySQL** – ensure the JMS resources above exist.
2. **Launch the server**
   ```sh
   ./run_server.sh
   ```
3. **Launch one or more clients**
   ```sh
   ./run_jms_client.sh
   ```
4. **Play** – matches automatically start when enough distinct players join the lobby.

### Analytics Pipeline (Kafka ➜ HDFS ➜ Hadoop ➜ MySQL/Redis)

1. Start Kafka, Redis, HDFS, and YARN (commands in `docs/data_pipeline.md`).
2. Mirror Kafka events into HDFS:
   ```sh
   nohup ./analytics/kafka_to_hdfs_ingest.sh \
     > analytics/logs/kafka_to_hdfs_ingest.log 2>&1 &
   ```
3. Play several games to emit analytics events.
4. Run the Hadoop aggregation:
   ```sh
   ./analytics/run_hadoop_leaderboard.sh
   ```
5. Apply results back to the serving layer:
   ```sh
   HDFS_OUTPUT_PATH=/game24/analytics/leaderboard-<timestamp> \
     ./analytics/apply_leaderboard_results.sh
   ```
6. Inspect the reconciled leaderboard:
   ```sh
   mysql -N -uroot -p12345678 game24db \
     -e "SELECT * FROM user_stats_hadoop;"
   ```

The “End-to-End Runbook” in `docs/data_pipeline.md` walks through these steps (and shutdown commands) in detail.

---

## Analytics Architecture

- **Real-time stats:** `JPoker24GameServer` publishes `GAME_FINISHED` events to Kafka and updates Redis caches for instant leaderboards.
- **Batch reconciliation:** Kafka events are archived to HDFS; Hadoop Streaming (Python mapper/reducer) produces authoritative aggregates.
- **Serving sync:** `apply_leaderboard_results.sh` upserts Hadoop output into MySQL (`user_stats_hadoop`) and mirrors it into Redis.

---

## Program Organization

- `client.JPoker24Game` – Swing UI, JMS consumer/producer logic.
- `server.JPoker24GameServer` – Lobby management, game lifecycle, persistence, Kafka/Redis publishing.
- `common` – Shared JMS DTOs.
- `model` – Persistent entities (`User`, `UserStats`).
- `analytics` – Kafka ingestion, Hadoop job, and synchronization scripts.
- `docs` – Architecture notes, runbook, troubleshooting.

---

## Notes

- Core gameplay remains JMS-only; Kafka/Redis/Hadoop operate on analytics data.
- Use the provided shell scripts (`compile.sh`, `run_server.sh`, `run_jms_client.sh`) for correct classpaths and security policy configuration.
- The repository ignores generated artifacts (`bin/`, analytics logs). Commit only source, scripts, and configuration.

Enjoy showcasing an end-to-end distributed system with both real-time and batch analytics! 🚀
