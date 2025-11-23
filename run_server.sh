#!/bin/bash

# Get the absolute path to the current directory
CURRENT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Make sure we're in the right directory
cd "${CURRENT_DIR}"

# Source the environment
source env.sh

# Set classpath with MySQL JDBC driver and JMS libraries
CLASSPATH="${CURRENT_DIR}/bin:${CURRENT_DIR}/lib/mysql-connector-j-8.0.31.jar:${CURRENT_DIR}/lib/kafka-clients-3.5.1.jar:${CURRENT_DIR}/lib/slf4j-api-1.7.36.jar:${CURRENT_DIR}/lib/lz4-java-1.8.0.jar:${CURRENT_DIR}/lib/snappy-java-1.1.10.5.jar:${CURRENT_DIR}/lib/zstd-jni-1.5.5-5.jar:${CURRENT_DIR}/lib/jedis-4.4.5.jar:${CURRENT_DIR}/lib/commons-pool2-2.11.1.jar:../glassfish5/mq/lib/jms.jar:../glassfish5/glassfish/lib/gf-client.jar"

# Run the server with explicit codebase and hostname
java -Dfile.encoding=UTF-8 \
  -classpath "${CLASSPATH}" \
  -Djava.rmi.server.codebase="file:${CURRENT_DIR}/bin/" \
  -Djava.rmi.server.hostname=localhost \
  -Djava.security.policy="${CURRENT_DIR}/server.policy" \
  -Djava.rmi.server.useCodebaseOnly=false \
  -Djava.security.manager \
  server.JPoker24GameServer 