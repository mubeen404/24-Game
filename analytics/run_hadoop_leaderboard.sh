#!/bin/bash
#
# Wrapper script to launch the Hadoop Streaming job that computes the
# leaderboard aggregates from the Kafka->HDFS raw events.
#
# Environment variables:
#   HDFS_INPUT   - HDFS path containing raw events (default /game24/raw-events)
#   HDFS_OUTPUT  - HDFS output directory (default /game24/analytics/leaderboard-$(date))
#   HADOOP_BIN   - Path to hadoop executable (default hadoop)
#
set -euo pipefail

HDFS_INPUT="${HDFS_INPUT:-/game24/raw-events}"
TIMESTAMP="$(date -u +%Y%m%d-%H%M%S)"
HDFS_OUTPUT="${HDFS_OUTPUT:-/game24/analytics/leaderboard-${TIMESTAMP}}"
HADOOP_BIN="${HADOOP_BIN:-hadoop}"
STREAMING_JAR="${HADOOP_STREAMING_JAR:-$HADOOP_HOME/share/hadoop/tools/lib/hadoop-streaming-3.2.3.jar}"
PYTHON_BIN="${PYTHON_BIN:-/usr/bin/python3}"
HDFS_CMD="${HDFS_CMD:-hdfs}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
MAPPER_ORIG="${SCRIPT_DIR}/mr_leaderboard_mapper.py"
REDUCER_ORIG="${SCRIPT_DIR}/mr_leaderboard_reducer.py"
TMP_STAGE="$(mktemp -d -t leaderboard-stage-XXXXXX)"
trap 'rm -rf "'"${TMP_STAGE}"'"' EXIT
MAPPER="${TMP_STAGE}/mr_leaderboard_mapper.py"
REDUCER="${TMP_STAGE}/mr_leaderboard_reducer.py"
cp "${MAPPER_ORIG}" "${MAPPER}"
cp "${REDUCER_ORIG}" "${REDUCER}"
chmod +x "${MAPPER}" "${REDUCER}"

if ! command -v "${HADOOP_BIN}" >/dev/null 2>&1; then
  echo "[hadoop-job] hadoop binary not found (${HADOOP_BIN})" >&2
  exit 1
fi

if [[ ! -f "${STREAMING_JAR}" ]]; then
  echo "[hadoop-job] Hadoop streaming jar not found at ${STREAMING_JAR}" >&2
  exit 1
fi

INPUT_FILES=()
while IFS= read -r line; do
  [[ -n "${line}" ]] && INPUT_FILES+=("${line}")
done < <("${HDFS_CMD}" dfs -find "${HDFS_INPUT}" -name 'events-*' 2>/dev/null)

if [[ ${#INPUT_FILES[@]} -eq 0 ]]; then
  echo "[hadoop-job] No event files found under ${HDFS_INPUT}" >&2
  exit 1
fi

echo "[hadoop-job] Launching leaderboard job"
echo "[hadoop-job] Input: ${HDFS_INPUT}"
echo "[hadoop-job] Output: ${HDFS_OUTPUT}"

"${HADOOP_BIN}" jar "${STREAMING_JAR}" \
  -D mapreduce.job.name="game24-leaderboard" \
  -D mapreduce.framework.name=local \
  -files "${MAPPER},${REDUCER}" \
  -mapper "${PYTHON_BIN} ${MAPPER}" \
  -reducer "${PYTHON_BIN} ${REDUCER}" \
  $(for f in "${INPUT_FILES[@]}"; do printf ' -input %q' "$f"; done) \
  -output "${HDFS_OUTPUT}"

echo "[hadoop-job] Completed. Results in ${HDFS_OUTPUT}"


