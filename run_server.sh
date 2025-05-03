#!/bin/bash

# Get the absolute path to the current directory
CURRENT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Make sure we're in the right directory
cd "${CURRENT_DIR}"

# Source the environment
source env.sh

# Set classpath with MySQL JDBC driver
CLASSPATH="${CURRENT_DIR}/bin:${CURRENT_DIR}/lib/mysql-connector-j-8.0.31.jar"

# Run the server with explicit codebase and hostname
java -Dfile.encoding=UTF-8 \
  -classpath "${CLASSPATH}" \
  -Djava.rmi.server.codebase="file:${CURRENT_DIR}/bin/" \
  -Djava.rmi.server.hostname=localhost \
  -Djava.security.policy="${CURRENT_DIR}/server.policy" \
  -Djava.rmi.server.useCodebaseOnly=false \
  -Djava.security.manager \
  server.AuthServer 