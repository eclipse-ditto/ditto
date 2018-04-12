/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.amqpbridge.util;

import javax.annotation.concurrent.Immutable;

/**
 * This class encloses everything regarding configuration keys.
 */
public final class ConfigKeys {

    private static final String AMQP_BRIDGE_PREFIX = "ditto.amqp-bridge.";

    /**
     * Key of the uri for mongodb.
     */
    public static final String MONGO_URI = "akka.contrib.persistence.mongodb.mongo.mongouri";

    /**
     * Configuration keys for Connection.
     */
    @Immutable
    public static final class Connection {

        private static final String PREFIX = AMQP_BRIDGE_PREFIX + "connection.";

        private static final String SUPERVISOR_PREFIX = PREFIX + "supervisor.";

        private static final String SUPERVISOR_EXPONENTIAL_BACKOFF = SUPERVISOR_PREFIX + "exponential-backoff.";

        /**
         * The random factor of the exponential back-off strategy.
         */
        public static final String
                SUPERVISOR_EXPONENTIAL_BACKOFF_RANDOM_FACTOR = SUPERVISOR_EXPONENTIAL_BACKOFF + "random-factor";

        /**
         * The maximal exponential back-off duration.
         */
        public static final String SUPERVISOR_EXPONENTIAL_BACKOFF_MAX = SUPERVISOR_EXPONENTIAL_BACKOFF + "max";

        /**
         * The minimal exponential back-off duration.
         */
        public static final String SUPERVISOR_EXPONENTIAL_BACKOFF_MIN = SUPERVISOR_EXPONENTIAL_BACKOFF + "min";

        private static final String SNAPSHOT_PREFIX = PREFIX + "snapshot.";

        /**
         * Every amount of changes (configured by this key), this Actor will create a snapshot of the connectionStatus.
         */
        public static final String SNAPSHOT_THRESHOLD = SNAPSHOT_PREFIX + "threshold";

        private Connection() {
            throw new AssertionError();
        }
    }

    /**
     * Configuration keys for Reconnect.
     */
    @Immutable
    public static final class Reconnect {

        private static final String PREFIX = AMQP_BRIDGE_PREFIX + "reconnect.";

        private static final String SNAPSHOT_PREFIX = PREFIX + "snapshot.";

        /**
         * Every amount of changes (configured by this key), this Actor will create a snapshot of the connection ids.
         */
        public static final String SNAPSHOT_THRESHOLD = SNAPSHOT_PREFIX + "threshold";

        private Reconnect() {
            throw new AssertionError();
        }
    }

    /*
     * This class is not designed for instantiation.
     */
    private ConfigKeys() {
        throw new AssertionError();
    }

}
