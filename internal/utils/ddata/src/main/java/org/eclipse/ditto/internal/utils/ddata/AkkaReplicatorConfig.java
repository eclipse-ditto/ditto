/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.internal.utils.ddata;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

import com.typesafe.config.Config;

/**
 * Provides configuration settings for the Akka {@link akka.cluster.ddata.Replicator}.
 */
@Immutable
public interface AkkaReplicatorConfig {

    /**
     * Returns the name of the replicator.
     *
     * @return the name.
     */
    String getName();

    /**
     * Returns the cluster role of members with replicas of the distributed collection.
     *
     * @return cluster role.
     */
    String getRole();

    /**
     * Returns the interval with which cluster members initiate gossip.
     *
     * @return the gossip interval.
     */
    Duration getGossipInterval();

    /**
     * Returns the interval subscribers are notified.
     *
     * @return the duration.
     */
    Duration getNotifySubscribersInterval();

    /**
     * Gets the complete Akka {@code distributed-data} config to use for the {@link akka.cluster.ddata.Replicator}.
     *
     * @return the complete config.
     */
    Config getCompleteConfig();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code AkkaReplicatorConfig}.
     */
    enum AkkaReplicatorConfigValue implements KnownConfigValue {

        /**
         * The name to be used for the {@link akka.cluster.ddata.Replicator}.
         */
        NAME("name", "ddataReplicator"),

        /**
         * The role to be used for the {@link akka.cluster.ddata.Replicator}.
         */
        ROLE("role", ""),

        /**
         * How often distributed data changes are deseminated in the cluster.
         */
        GOSSIP_INTERVAL("gossip-interval", "2 s"),

        /**
         * the interval subscribers are notified.
         */
        NOTIFY_SUBSCRIBERS_INTERVAL("notify-subscribers-interval", "500 ms");

        private final String path;
        private final Object defaultValue;

        AkkaReplicatorConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

    }

}
