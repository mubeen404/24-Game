#!/bin/bash

# Source the environment
source env.sh

# Create bin directory if it doesn't exist
mkdir -p bin

# Clean the bin directory
rm -rf bin/*

# Set classpath with MySQL JDBC driver
CLASSPATH=".:bin:lib/mysql-connector-j-8.0.31.jar"

# Compile in the correct order
echo "Compiling model classes..."
javac -d bin -cp $CLASSPATH src/model/*.java

echo "Compiling common interfaces..."
javac -d bin -cp $CLASSPATH src/common/*.java

echo "Compiling server implementation..."
javac -d bin -cp $CLASSPATH src/server/*.java

echo "Compiling client classes..."
javac -d bin -cp $CLASSPATH src/client/*.java

echo "Compilation complete. Class files are in the bin directory." 