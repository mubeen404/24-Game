# Game24 Analytics Pipeline

This directory documents how Kafka, Redis, and Hadoop integrate with the
Game24 assignment so you can demonstrate the full distributed stack.

## Overview

1. **Runtime telemetry**  
   The Java server publishes a JSON event to the Kafka topic `game-analytics`
   at the end of every match (`publishAnalyticsEvent`).

2. **Real-time cache**  
   Redis receives live updates from the server (`updateRedisCaches`), allowing
   the Swing client to serve the latest leaderboard without touching Kafka or
   Hadoop.

3. **Batch analytics**  
   Kafka events are copied to HDFS for long-term storage. Hadoop Streaming jobs
   compute historical aggregates that can be compared against the real-time data
   or exported to SQL.

## Hadoop Setup & Usage

Hadoop powers the batch side of the analytics workflow: it scans the raw Kafka
events stored in HDFS, aggregates long-term player stats, and produces a clean
dataset that can be loaded back into Redis/MySQL.

### 1. Start HDFS and YARN

```bash
export HADOOP_HOME=/Users/mubeen/hadoop/hadoop-3.2.3
export PATH=$HADOOP_HOME/bin:$HADOOP_HOME/sbin:$PATH

start-dfs.sh
start-yarn.sh
```

Check `jps` to confirm `NameNode`, `DataNode`, `ResourceManager`, and
`NodeManager` are active. If they are already running, Hadoop prints warnings—
those can be ignored.

### 2. Ingest Kafka events into HDFS

The server publishes a JSON summary to Kafka at the end of each game. The helper
script mirrors those events into HDFS for archival analytics:

```bash
./analytics/kafka_to_hdfs_ingest.sh
```

The raw logs land in `/game24/raw-events/YYYY/MM/DD/HH/`.

### 3. Run the Hadoop Streaming job

```bash
./analytics/run_hadoop_leaderboard.sh
```

Behind the scenes the script:

- Stages the mapper and reducer into a temp directory (avoids spaces in paths),
- Invokes Hadoop Streaming with `/usr/bin/python3`,
- Enumerates every `events-*` file in `/game24/raw-events`, and
- Writes the aggregated leaderboard to `/game24/analytics/leaderboard-<timestamp>`.

Inspect the output with:

```bash
hdfs dfs -ls /game24/analytics/leaderboard-<timestamp>
hdfs dfs -cat /game24/analytics/leaderboard-<timestamp>/part-00000
```

Each reducer line is `username,games_played,games_won,avg_time_seconds`.

### 4. Sync results back to serving stores

```bash
HDFS_OUTPUT_PATH=/game24/analytics/leaderboard-<timestamp> \
  ./analytics/apply_leaderboard_results.sh
```

The script downloads the reducer output, upserts it into the MySQL table
`user_stats_hadoop`, and updates Redis keys (`leaderboard:hadoop` plus per-user
hashes). Ensure MySQL and Redis are running locally before executing it.

### Why Hadoop here?

- **Historical accuracy** – Redis caches live updates, but Hadoop can recompute
  the truth from the append-only event log.
- **Wide time windows** – HDFS retains the entire gameplay history, enabling
  longer trend analysis than the in-memory cache.
- **Portfolio story** – Demonstrates end-to-end data engineering: JMS gameplay,
  Kafka streaming, Hadoop batch ETL, and a hybrid serving layer.

## End-to-End Runbook

Follow these steps after cloning the repo to bring the full stack online and run
through a batch analytics cycle.

1. **Set environment variables (new terminal session)**
   ```bash
   export JAVA_HOME=$(/usr/libexec/java_home -v 1.8+)
   export HADOOP_HOME=/Users/mubeen/hadoop/hadoop-3.2.3
   export KAFKA_HOME=/Users/mubeen/kafka_2.13-3.5.1
   export PATH=$HADOOP_HOME/bin:$HADOOP_HOME/sbin:$KAFKA_HOME/bin:$PATH
   ```

2. **Start infrastructure services**
   ```bash
   start-dfs.sh
   start-yarn.sh
   $KAFKA_HOME/bin/kafka-server-start.sh $KAFKA_HOME/config/kraft/server.properties
   docker start redis24    # or docker run ... (if not created yet)
   ```
   Leave Kafka running in its own terminal. If Hadoop daemons were already up,
   the start scripts will print “already running” warnings—safe to ignore.

3. **Ensure Kafka topic exists**
   ```bash
   kafka-topics.sh --bootstrap-server localhost:9092 --create \
     --topic game-analytics --partitions 1 --replication-factor 1
   ```
   If the topic already exists, Kafka reports it and exits without error.

4. **Launch Kafka ➜ HDFS ingestion**
   ```bash
   cd /Users/mubeen/Library/CloudStorage/OneDrive-TheUniversityofHongKong/Msc\ sem2/Distributed\ Learning/Assignment/Assignment3/24_game_assignment
   nohup ./analytics/kafka_to_hdfs_ingest.sh \
     > analytics/logs/kafka_to_hdfs_ingest.log 2>&1 &
   ```
   The script continuously batches Kafka events into `/game24/raw-events/...`.

5. **Build and start the game server**
   ```bash
   ./compile.sh
   ./run_server.sh        # keep this terminal open
   ```

6. **Run two Swing clients to generate matches**
   ```bash
   ./run_jms_client.sh    # launch twice, use distinct usernames
   ```
   Play several games so analytics events are produced.

7. **Run the Hadoop leaderboard job**
   ```bash
   ./analytics/run_hadoop_leaderboard.sh
   ```
   When it finishes, note the output path it prints
   (e.g. `/game24/analytics/leaderboard-20251123-195134`).

8. **Inspect the reducer output (optional)**
   ```bash
   hdfs dfs -cat /game24/analytics/leaderboard-<timestamp>/part-00000
   ```

9. **Apply Hadoop results back to MySQL/Redis**
   ```bash
   HDFS_OUTPUT_PATH=/game24/analytics/leaderboard-<timestamp> \
     ./analytics/apply_leaderboard_results.sh
   ```
   Confirm the MySQL table:
   ```bash
   mysql -N -uroot -p12345678 game24db \
     -e "SELECT * FROM user_stats_hadoop;"
   ```

10. **Shut down services (when done)**
    ```bash
    pkill -f kafka_to_hdfs_ingest.sh
    kafka-server-stop.sh
    docker stop redis24
    stop-yarn.sh
    stop-dfs.sh
    ```

That sequence demonstrates the full flow: live gameplay ➜ Kafka ➜ HDFS ➜ Hadoop
Streaming ➜ MySQL/Redis sync.

## Components

### Kafka ➜ HDFS Ingestion

Run the helper script while Kafka, Hadoop DFS, and YARN are up:

```bash
./analytics/kafka_to_hdfs_ingest.sh
```

Environment variables (optional):

| Variable | Default | Description |
| --- | --- | --- |
| `KAFKA_BOOTSTRAP` | `localhost:9092` | Kafka bootstrap servers |
| `KAFKA_TOPIC` | `game-analytics` | Topic emitted by the game server |
| `HDFS_TARGET` | `/game24/raw-events` | HDFS directory for raw events |
| `FLUSH_EVERY` | `100` | Records per HDFS flush |
| `BATCH_SECONDS` | `60` | Max seconds between flushes |

The script batches messages and appends them to hour-based files in HDFS:
`/game24/raw-events/YYYY/MM/DD/HH/events-<host>.log`.

### Hadoop Leaderboard Job

Use Hadoop Streaming to aggregate the raw events into per-player statistics:

```bash
./analytics/run_hadoop_leaderboard.sh
```

Key files:

* `analytics/mr_leaderboard_mapper.py`
* `analytics/mr_leaderboard_reducer.py`

The reducer emits CSV records: `username,games_played,games_won,avg_time_seconds`.

### Load Results into Serving Layer

After the Hadoop job finishes, sync the results to MySQL/Redis so the rest of
the system can consume them:

```bash
HDFS_OUTPUT_PATH=/game24/analytics/leaderboard-<timestamp> \
  ./analytics/apply_leaderboard_results.sh
```

Environment variables:

| Variable | Default | Description |
| --- | --- | --- |
| `MYSQL_USER` | `root` | MySQL username |
| `MYSQL_PASS` | `12345678` | MySQL password |
| `MYSQL_DB` | `game24db` | Database |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |

The script:

* Ensures a `user_stats_hadoop` table exists in MySQL,
* Upserts the aggregated rows,
* Mirrors the values into Redis keys (`leaderboard:hadoop` and `userstats:<user>:hadoop`).

## Suggested Demo Flow

1. Start Kafka, Redis (Docker), HDFS, and YARN.
2. Launch `kafka_to_hdfs_ingest.sh` in the background.
3. Run the game server and two clients to generate matches.
4. Run `run_hadoop_leaderboard.sh` to compute aggregates.
5. Sync results back via `apply_leaderboard_results.sh`.
6. Show data comparison between live Redis stats and Hadoop-derived table.

This demonstrates:

* Real-time streaming (Kafka ➜ Redis),
* Batch analytics (Kafka ➜ HDFS ➜ Hadoop),
* Hybrid serving (MySQL/Redis fed by Hadoop job).


