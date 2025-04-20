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

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.XmlClientConfigBuilder;
import com.hazelcast.config.GroupConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Manages the Hazelcast client node for connecting to a Hazelcast cluster.
 * This class ensures that a Hazelcast client instance is created and connected to the Hazelcast cluster.
 * It provides methods to initialize the client, retrieve the instance, and shut it down.
 *
 * <p>
 * The Hazelcast client configuration can either be loaded from a file or created programmatically.
 * This class ensures that only one instance of the Hazelcast client is created at a time.
 * </p>
 *
 * <p>
 * This class is thread-safe and ensures the Hazelcast client instance is only created once.
 * </p>
 *
 * @author pavels
 */
public class HazelcastClientNode {

    private static final ILogger LOGGER = Logger.getLogger(HazelcastClientNode.class);
    private HazelcastInstance hzInstance;

    /**
     * Ensures that a Hazelcast client node is created and connected to the Hazelcast cluster.
     * <p>
     * This method creates a new Hazelcast client instance if one does not already exist. It loads the configuration
     * from the specified configuration file, or constructs it programmatically if no file is provided.
     * </p>
     *
     * @param configuration The configuration containing details such as the Hazelcast client config file and user credentials.
     */
    public void ensureHazelcastNode(HazelcastConfiguration configuration) {
        ClientConfig config = createHazelcastConfig(configuration);
        hzInstance = HazelcastClient.newHazelcastClient(config);
    }

    /**
     * Creates the Hazelcast client configuration based on the provided configuration details.
     * <p>
     * This method either loads the configuration from an XML file if provided, or constructs the configuration
     * programmatically if no file is specified.
     * </p>
     *
     * @param configuration The configuration containing details such as the config file path and user credentials.
     * @return The Hazelcast client configuration object.
     */
    private ClientConfig createHazelcastConfig(HazelcastConfiguration configuration) {
        ClientConfig config = null;
        File configFile = configuration.getHazelcastClientConfigFile() == null ? null : new File(configuration.getHazelcastClientConfigFile());
        if (configFile != null) {
            try (FileInputStream configStream = new FileInputStream(configFile)) {
                config = new XmlClientConfigBuilder(configStream).build();
            } catch (IOException ex) {
                LOGGER.warning("Could not load Hazelcast config file " + configFile, ex);
            }
        } else {
            config = new ClientConfig();
            config.setInstanceName(configuration.getHazelcastInstance());
            /* TODO the following config can be used for monitor thread
            config.setConnectionAttemptLimit(1);
            config.setConnectionAttemptPeriod(1000);
             */
            GroupConfig groupConfig = config.getGroupConfig();
            groupConfig.setName(configuration.getHazelcastUser());
        }
        return config;
    }

    /**
     * Retrieves the Hazelcast client instance.
     *
     * @return The Hazelcast client instance.
     */
    public HazelcastInstance getHzInstance() {
        return hzInstance;
    }

    /**
     * Shuts down the running Hazelcast client instance, if one is currently active.
     * <p>
     * This method ensures that the Hazelcast client instance is properly shut down and its resources are released.
     * </p>
     */
    public void shutdown() {
        if (hzInstance != null) {
            hzInstance.shutdown();
        }
    }
}
