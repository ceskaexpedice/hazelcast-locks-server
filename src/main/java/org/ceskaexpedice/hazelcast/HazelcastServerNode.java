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

import com.hazelcast.config.Config;
import com.hazelcast.config.GroupConfig;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Manages the Hazelcast server node for distributed locking.
 * This class ensures that a Hazelcast instance is created and started with the appropriate configuration.
 * It provides methods to initialize and shut down the Hazelcast instance.
 *
 * <p>
 * The Hazelcast configuration can either be loaded from a file or created programmatically.
 * This class is responsible for ensuring that only one instance of the Hazelcast server is running at a time.
 * </p>
 *
 * <p>
 * This class is thread-safe and ensures the Hazelcast instance is only created once.
 * </p>
 *
 * @author pavels
 */
public class HazelcastServerNode {

    private static final ILogger LOGGER = Logger.getLogger(HazelcastServerNode.class);
    private static HazelcastInstance hzInstance;

    /**
     * Ensures that a Hazelcast node is started, creating an instance if it doesn't already exist.
     * <p>
     * This method is synchronized to ensure that only one Hazelcast instance is created even in a multi-threaded environment.
     * If a Hazelcast instance is already running, it does nothing.
     * </p>
     *
     * @param configuration The configuration containing details such as the Hazelcast config file and user credentials.
     */
    public static synchronized void ensureHazelcastNode(HazelcastConfiguration configuration) {
        if (hzInstance != null) {
            return;
        }
        Config config = createHazelcastConfig(configuration);
        hzInstance = Hazelcast.getOrCreateHazelcastInstance(config);
    }

    /**
     * Creates the Hazelcast configuration based on the provided configuration details.
     * <p>
     * This method either loads the configuration from an XML file if provided, or constructs the configuration
     * programmatically if no file is specified.
     * </p>
     *
     * @param configuration The configuration containing details such as the config file path and user credentials.
     * @return The Hazelcast configuration object.
     */
    private static Config createHazelcastConfig(HazelcastConfiguration configuration) {
        Config config = null;
        File configFile = configuration.getHazelcastConfigFile() == null ? null : new File(configuration.getHazelcastConfigFile());
        if (configFile != null) {
            try (FileInputStream configStream = new FileInputStream(configFile)) {
                config = new XmlConfigBuilder(configStream).build();
            } catch (IOException ex) {
                LOGGER.warning("Could not load Hazelcast config file " + configFile, ex);
            }
        } else {
            config = new Config(configuration.getHazelcastInstance());
            GroupConfig groupConfig = config.getGroupConfig();
            groupConfig.setName(configuration.getHazelcastUser());
        }
        return config;
    }

    /**
     * Shuts down the running Hazelcast instance, if one is currently active.
     * <p>
     * This method ensures that the Hazelcast instance is properly shut down and its resources are released.
     * </p>
     */
    public static void shutdown() {
        if (hzInstance != null) {
            hzInstance.shutdown();
        }
    }
}

