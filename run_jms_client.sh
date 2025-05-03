#!/bin/bash

# Run JMS Client with the proper classpath
cd src
java -cp .:../glassfish5/mq/lib/jms.jar:../glassfish5/glassfish/lib/gf-client.jar client.JMSClient 