# Keiko

Keiko is a simple queueing library originally built for Spinnaker's Orca 
µservice.

## Using

Include `com.netflix.spinnaker.keiko:keiko-redis:<version>` in your `build.gradle` file.

Implement message types and handlers extending `com.netflix.spinnaker.q.Message` and `com.netflix.spinnaker.q.MessageHandler` respectively.

## Implementing a queue