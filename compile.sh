#!/bin/bash

# Source the environment
source env.sh

# Create bin directory if it doesn't exist
mkdir -p bin

# Clean the bin directory
rm -rf bin/*

# Set classpath with MySQL JDBC driver and JMS libraries
CLASSPATH=.:bin:lib/mysql-connector-j-8.0.31.jar:lib/kafka-clients-3.5.1.jar:lib/slf4j-api-1.7.36.jar:lib/lz4-java-1.8.0.jar:lib/snappy-java-1.1.10.5.jar:lib/zstd-jni-1.5.5-5.jar:lib/jedis-4.4.5.jar:lib/commons-pool2-2.11.1.jar:../glassfish5/mq/lib/jms.jar:../glassfish5/glassfish/lib/gf-client.jar

# Compile in the correct order
echo "Compiling model classes..."
javac -d bin -cp $CLASSPATH src/model/*.java

echo "Compiling common interfaces..."
javac -d bin -cp $CLASSPATH src/common/*.java

echo "Compiling server implementation..."
javac -d bin -cp $CLASSPATH src/server/*.java

echo "Compiling client classes..."
javac -d bin -cp $CLASSPATH src/client/*.java

mkdir -p bin/client/cards
cp src/client/cards/*.png bin/client/cards/

echo "Compilation complete. Class files are in the bin directory." 