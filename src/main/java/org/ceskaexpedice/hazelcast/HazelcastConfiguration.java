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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents the configuration settings for Hazelcast nodes (both server and client).
 * <p>
 * This class holds configuration information needed for initializing Hazelcast server and client nodes,
 * including paths to configuration files and user credentials. It uses the Builder design pattern to
 * provide a flexible way to construct configuration instances.
 * </p>
 *
 * <p>
 * The configuration includes:
 * <ul>
 *     <li>Path to the Hazelcast server configuration file</li>
 *     <li>Path to the Hazelcast client configuration file</li>
 *     <li>Instance name for the Hazelcast node</li>
 *     <li>Group name for the Hazelcast cluster</li>
 * </ul>
 * </p>
 *
 * <p>
 * This class is immutable once built, ensuring thread safety for the configuration values.
 * </p>
 *
 * @author pavels
 */
public class HazelcastConfiguration {

    private final String hazelcastConfigFile;
    private final String hazelcastClientConfigFile;
    private final String hazelcastInstance;
    private final String hazelcastUser;

    // File less configuration
    private List<String> addresses = new ArrayList<>();

    /**
     * Private constructor to create a new HazelcastConfiguration instance using the Builder.
     *
     * @param builder The Builder instance used to construct the HazelcastConfiguration object.
     */
    private HazelcastConfiguration(Builder builder) {
        this.hazelcastConfigFile = builder.hazelcastConfigFile;
        this.hazelcastClientConfigFile = builder.hazelcastClientConfigFile;
        this.hazelcastInstance = builder.hazelcastInstance;
        this.hazelcastUser = builder.hazelcastUser;
        this.addresses = builder.adresses;
    }

    /**
     * Returns the path to the Hazelcast server configuration file.
     *
     * @return The path to the Hazelcast server configuration file.
     */
    public String getHazelcastConfigFile() {
        return hazelcastConfigFile;
    }

    /**
     * Returns the path to the Hazelcast client configuration file.
     *
     * @return The path to the Hazelcast client configuration file.
     */
    public String getHazelcastClientConfigFile() {
        return hazelcastClientConfigFile;
    }

    /**
     * Returns the name of the Hazelcast instance.
     *
     * @return The name of the Hazelcast instance.
     */
    public String getHazelcastInstance() {
        return hazelcastInstance;
    }

    /**
     * Returns the name of the user or group for the Hazelcast cluster.
     *
     * @return The name of the Hazelcast user or group.
     */
    public String getHazelcastUser() {
        return hazelcastUser;
    }


    //===== File less configuration

    /**
     * Returns the explicitly defined list of Hazelcast server addresses
     * that the client should connect to.
     *
     * @return list of Hazelcast server addresses (e.g. "localhost:5701").
     */
    public List<String> getAddresses() {
        return addresses;
    }

    /**
     * Builder class for constructing HazelcastConfiguration instances.
     * <p>
     * The Builder design pattern is used to allow flexible and incremental construction of a
     * HazelcastConfiguration instance with various configuration options.
     * </p>
     */
    public static class Builder {
        private String hazelcastConfigFile;
        private String hazelcastClientConfigFile;
        private String hazelcastInstance;
        private String hazelcastUser;

        private List<String> adresses = new ArrayList<>();

        /**
         * Sets the path to the Hazelcast server configuration file.
         *
         * @param hazelcastConfigFile The path to the Hazelcast server configuration file.
         * @return This Builder instance for method chaining.
         */
        public Builder hazelcastConfigFile(String hazelcastConfigFile) {
            this.hazelcastConfigFile = hazelcastConfigFile;
            return this;
        }

        /**
         * Sets the path to the Hazelcast client configuration file.
         *
         * @param hazelcastClientConfigFile The path to the Hazelcast client configuration file.
         * @return This Builder instance for method chaining.
         */
        public Builder hazelcastClientConfigFile(String hazelcastClientConfigFile) {
            this.hazelcastClientConfigFile = hazelcastClientConfigFile;
            return this;
        }

        /**
         * Sets the name of the Hazelcast instance.
         *
         * @param hazelcastInstance The name of the Hazelcast instance.
         * @return This Builder instance for method chaining.
         */
        public Builder hazelcastInstance(String hazelcastInstance) {
            this.hazelcastInstance = hazelcastInstance;
            return this;
        }

        /**
         * Sets the name of the user or group for the Hazelcast cluster.
         *
         * @param hazelcastUser The name of the Hazelcast user or group.
         * @return This Builder instance for method chaining.
         */
        public Builder hazelcastUser(String hazelcastUser) {
            this.hazelcastUser = hazelcastUser;
            return this;
        }

        public Builder addHazelcastServer(String server) {
            this.adresses.add(server);
            return this;
        }

        public Builder removeHazelcastServer(String server) {
            this.adresses.remove(server);
            return this;
        }

        public Builder setHazelcastServers(String ... server) {
            Arrays.stream(server).forEach(this.adresses::add);
            return this;
        }

        /**
         * Builds and returns a new HazelcastConfiguration instance.
         *
         * @return A new HazelcastConfiguration instance.
         */
        public HazelcastConfiguration build() {
            return new HazelcastConfiguration(this);
        }
    }
}
