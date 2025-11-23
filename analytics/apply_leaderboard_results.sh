#!/bin/bash
#
# Apply results produced by the Hadoop leaderboard job back into MySQL/Redis.
# Prerequisites:
#   - mysql CLI installed and reachable
#   - redis-cli available
#   - HDFS output path produced by run_hadoop_leaderboard.sh
#
# Environment variables:
#   HDFS_OUTPUT_PATH - Required; HDFS directory with reducer output
#   MYSQL_USER       - MySQL username (default root)
#   MYSQL_PASS       - MySQL password (default 12345678)
#   MYSQL_DB         - Database name (default game24db)
#   REDIS_HOST       - Redis host (default localhost)
#   REDIS_PORT       - Redis port (default 6379)
#
set -euo pipefail

HDFS_OUTPUT_PATH="${HDFS_OUTPUT_PATH:?Set HDFS_OUTPUT_PATH to the Hadoop job output directory}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-12345678}"
MYSQL_DB="${MYSQL_DB:-game24db}"
REDIS_HOST="${REDIS_HOST:-localhost}"
REDIS_PORT="${REDIS_PORT:-6379}"

if ! command -v hdfs >/dev/null 2>&1; then
  echo "[apply] hdfs CLI not found" >&2
  exit 1
fi

if ! command -v mysql >/dev/null 2>&1; then
  echo "[apply] mysql CLI not found" >&2
  exit 1
fi

if ! command -v redis-cli >/dev/null 2>&1; then
  echo "[apply] redis-cli not found" >&2
  exit 1
fi

TMP_FILE="$(mktemp -t leaderboard-XXXXXX.csv)"
trap 'rm -f "${TMP_FILE}"' EXIT

echo "[apply] Fetching Hadoop output from ${HDFS_OUTPUT_PATH}"
PART_FILES=()
while IFS= read -r path; do
  [[ -n "${path}" ]] && PART_FILES+=("${path}")
done < <(hdfs dfs -ls "${HDFS_OUTPUT_PATH}" 2>/dev/null | awk '{print $8}' | grep 'part-')

if [[ ${#PART_FILES[@]} -eq 0 ]]; then
  echo "[apply] No part files found in ${HDFS_OUTPUT_PATH}" >&2
  exit 1
fi

hdfs dfs -cat "${PART_FILES[@]}" > "${TMP_FILE}"

echo "[apply] Ensuring MySQL summary table exists"
mysql -N -u"${MYSQL_USER}" -p"${MYSQL_PASS}" "${MYSQL_DB}" <<'SQL'
CREATE TABLE IF NOT EXISTS user_stats_hadoop (
  username VARCHAR(255) PRIMARY KEY,
  games_played INT NOT NULL DEFAULT 0,
  games_won INT NOT NULL DEFAULT 0,
  avg_time DOUBLE NOT NULL DEFAULT 0
);
SQL

echo "[apply] Applying results to MySQL and Redis"
while IFS=',' read -r username played won avg_time; do
  username="$(echo "${username}" | xargs)"
  [[ -z "${username}" ]] && continue

  mysql -N -u"${MYSQL_USER}" -p"${MYSQL_PASS}" "${MYSQL_DB}" <<SQL
INSERT INTO user_stats_hadoop (username, games_played, games_won, avg_time)
VALUES ('${username}', ${played}, ${won}, ${avg_time})
ON DUPLICATE KEY UPDATE games_played=VALUES(games_played),
                        games_won=VALUES(games_won),
                        avg_time=VALUES(avg_time);
SQL

  redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" ZADD leaderboard:hadoop "${won}" "${username}" >/dev/null
  redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" HSET "userstats:${username}:hadoop" games_played "${played}" games_won "${won}" avg_time "${avg_time}" >/dev/null
done < "${TMP_FILE}"

echo "[apply] Done."


