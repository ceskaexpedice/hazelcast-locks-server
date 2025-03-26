/*
 * Copyright (C) 2025 Inovatika
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.ceskaexpedice.hazelcast;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

/**
 * Starts Hazelcast server from given configuration (env variables)
 */
/* TODO
c:\tmp\t>java -cp ".;hazelcast-all-3.11.2.jar;hazelcast-locks-server-1.0-SNAPSHOT.jar" org.ceskaexpedice.hazelcast.HazelcastServerNodeStarter
 */
public class HazelcastServerNodeStarter {
    private static final String ENV_HAZELCAST_CONFIG_FILE = "HAZELCAST_CONFIG_FILE";
    private static final String ENV_HAZELCAST_INSTANCE = "HAZELCAST_INSTANCE";
    private static final String ENV_HAZELCAST_USER = "HAZELCAST_USER";

    private static final String DEFAULT_HAZELCAST_CONFIG_FILE = null;
    private static final String DEFAULT_HAZELCAST_INSTANCE = "akubrasync";
    private static final String DEFAULT_HAZELCAST_USER = "dev";

    private static final Logger LOGGER = Logger.getLogger(HazelcastServerNodeStarter.class.getName());
    private static final CountDownLatch shutdownLatch = new CountDownLatch(1);

    public static void main(String[] args) {
        startServer();
        LOGGER.info("Hazelcast server node started successfully");

        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down Hazelcast..."); // do not use Logger here!
            stopServer();
            shutdownLatch.countDown();
        }));

        // Wait until shutdown signal is received
        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void startServer() {
        String hazelcastConfigFileS = getEnvOrDefault(ENV_HAZELCAST_CONFIG_FILE, DEFAULT_HAZELCAST_CONFIG_FILE);
        String hazelcastInstance = getEnvOrDefault(ENV_HAZELCAST_INSTANCE, DEFAULT_HAZELCAST_INSTANCE);
        String hazelcastUser = getEnvOrDefault(ENV_HAZELCAST_USER, DEFAULT_HAZELCAST_USER);

        HazelcastConfiguration hazelcastConfig = new HazelcastConfiguration.Builder()
                .hazelcastConfigFile(hazelcastConfigFileS)
                .hazelcastInstance(hazelcastInstance)
                .hazelcastUser(hazelcastUser)
                .build();

        HazelcastServerNode.ensureHazelcastNode(hazelcastConfig);
    }

    public static void stopServer() {
        HazelcastServerNode.shutdown();
    }

    private static String getEnvOrDefault(String envVar, String defaultValue) {
        String value = System.getenv(envVar);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }
}