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
package org.eclipse.ditto.services.gateway.endpoints.config;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides configuration settings of the web socket endpoint.
 */
@Immutable
public interface WebSocketConfig {

    /**
     * Returns the max queue size of how many inflight commands a single web socket client can have.
     *
     * @return the queue size.
     */
    int getSubscriberBackpressureQueueSize();

    /**
     * Returns the max buffer size of how many outstanding command responses and events a single web socket client
     * can have.
     * Additional command responses and events are dropped if this size is reached.
     *
     * @return the buffer size.
     */
    int getPublisherBackpressureBufferSize();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code WebSocketConfig}.
     */
    enum WebSocketConfigValue implements KnownConfigValue {

        /**
         * The max queue size of how many inflight commands a single web socket client can have.
         */
        SUBSCRIBER_BACKPRESSURE_QUEUE_SIZE("subscriber.backpressure-queue-size", 100),

        /**
         * The max buffer size of how many outstanding command responses and events a single web socket client can have.
         */
        PUBLISHER_BACKPRESSURE_BUFFER_SIZE("publisher.backpressure-buffer-size", 200);

        private final String path;
        private final Object defaultValue;

        private WebSocketConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }

    }

}
