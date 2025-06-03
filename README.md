![Build](https://img.shields.io/github/actions/workflow/status/ceskaexpedice/hazelcast-locks-server/gradle-push.yml?branch=main)

# Hazelcast
Hazelcast Locks Server is used by [`ceskaexpedice/akubra`](https://github.com/ceskaexpedice/akubra) and possibly other clients for synchronization using read/write locks in concurrent environment.
When this module is started it serves as a server and applications using it are considered as clients. 

## Start/Configuration
The component is typicaly started in Docker container and it s configured using environment variables. You can check the main class collecting configuration properties here:
[`HazelcastServerNodeStarter`](src/main/java/org/ceskaexpedice/hazelcast/HazelcastServerNodeStarter.java)