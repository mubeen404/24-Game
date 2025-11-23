#!/bin/bash
#
# Continuously read analytics events from Kafka and land them in HDFS.
# Requires kafka-console-consumer and the hdfs CLI to be available on PATH.
#
# Environment variables:
#   KAFKA_BOOTSTRAP - Kafka bootstrap servers (default localhost:9092)
#   KAFKA_TOPIC     - Kafka topic to consume (default game-analytics)
#   HDFS_TARGET     - HDFS base directory (default /game24/raw-events)
#   FLUSH_EVERY     - Number of records per HDFS flush (default 100)
#   BATCH_SECONDS   - Max seconds between flushes (default 60)

set -euo pipefail

KAFKA_BOOTSTRAP="${KAFKA_BOOTSTRAP:-localhost:9092}"
KAFKA_TOPIC="${KAFKA_TOPIC:-game-analytics}"
HDFS_TARGET="${HDFS_TARGET:-/game24/raw-events}"
FLUSH_EVERY="${FLUSH_EVERY:-100}"
BATCH_SECONDS="${BATCH_SECONDS:-60}"

if ! command -v kafka-console-consumer.sh >/dev/null 2>&1; then
  echo "[ingest] kafka-console-consumer.sh not found in PATH" >&2
  exit 1
fi

if ! command -v hdfs >/dev/null 2>&1; then
  echo "[ingest] hdfs CLI not found in PATH" >&2
  exit 1
fi

TMP_DIR="$(mktemp -d -t kafka-ingest-XXXXXX)"
TMP_BATCH="${TMP_DIR}/batch.log"
trap 'rm -rf "${TMP_DIR}"' EXIT

echo "[ingest] Starting Kafka->HDFS ingestion"
echo "[ingest] Kafka bootstrap: ${KAFKA_BOOTSTRAP}"
echo "[ingest] Topic: ${KAFKA_TOPIC}"
echo "[ingest] HDFS target: ${HDFS_TARGET}"

mkdir -p "${TMP_DIR}"
touch "${TMP_BATCH}"
COUNT=0
LAST_FLUSH=$(date +%s)

flush_batch() {
  local now path hour_path file_path
  now=$(date +%s)
  if [[ ! -s "${TMP_BATCH}" ]]; then
    LAST_FLUSH="${now}"
    return
  fi

  hour_path="$(date -u +%Y/%m/%d/%H)"
  path="${HDFS_TARGET}/${hour_path}"
  file_path="${path}/events-${HOSTNAME}.log"

  hdfs dfs -mkdir -p "${path}"
  if ! hdfs dfs -test -e "${file_path}"; then
    hdfs dfs -touchz "${file_path}"
  fi
  hdfs dfs -appendToFile "${TMP_BATCH}" "${file_path}"
  : > "${TMP_BATCH}"
  COUNT=0
  LAST_FLUSH="${now}"
  echo "[ingest] Flushed batch to ${file_path}"
}

kafka-console-consumer.sh \
  --bootstrap-server "${KAFKA_BOOTSTRAP}" \
  --topic "${KAFKA_TOPIC}" \
  --property print.value=true \
  --property print.timestamp=true \
  --from-beginning |
while IFS= read -r line; do
  printf '%s\n' "${line}" >> "${TMP_BATCH}"
  COUNT=$((COUNT + 1))
  now=$(date +%s)
  if [[ "${COUNT}" -ge "${FLUSH_EVERY}" || $((now - LAST_FLUSH)) -ge "${BATCH_SECONDS}" ]]; then
    flush_batch
  fi
done

flush_batch


