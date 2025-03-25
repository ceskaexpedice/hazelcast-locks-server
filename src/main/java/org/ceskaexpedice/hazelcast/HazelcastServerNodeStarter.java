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

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;
/*
c:\tmp\t>java -cp ".;hazelcast-all-3.11.2.jar;hazelcast-locks-server-1.0-SNAPSHOT.jar" org.ceskaexpedice.hazelcast.HazelcastServerNodeStarter
 */
public class HazelcastServerNodeStarter {

    private static final Logger LOGGER = Logger.getLogger(HazelcastServerNodeStarter.class.getName());
    private static final CountDownLatch shutdownLatch = new CountDownLatch(1);

    public static void main(String[] args) throws IOException {
        startServer();

        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down Hazelcast...");
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
        /*
        File hazelcastConfigFile = KConfiguration.getInstance().findConfigFile("hazelcast.config");
        String hazelcastConfigFileS = (hazelcastConfigFile != null && hazelcastConfigFile.exists()) ? hazelcastConfigFile.getAbsolutePath() : null;
        String hazelcastInstance = KConfiguration.getInstance().getConfiguration().getString("hazelcast.instance");
        String hazelcastUser = KConfiguration.getInstance().getConfiguration().getString("hazelcast.user");

         */
        File hazelcastConfigFile = null;
        String hazelcastConfigFileS = (hazelcastConfigFile != null && hazelcastConfigFile.exists()) ? hazelcastConfigFile.getAbsolutePath() : null;
        String hazelcastInstance = "akubrasync";
        String hazelcastUser = "dev";

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
}