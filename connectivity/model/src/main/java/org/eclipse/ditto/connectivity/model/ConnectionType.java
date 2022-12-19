/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.Optional;

/**
 * An enumeration of connection types of a {@link Connection}.
 */
public enum ConnectionType implements CharSequence {

    /**
     * Indicates an AMQP 0.9.1 connection.
     */
    AMQP_091("amqp-091"),

    /**
     * Indicates an AMQP 1.0 connection.
     */
    AMQP_10("amqp-10"),

    /**
     * Indicates a MQTT connection.
     */
    MQTT("mqtt"),

    /**
     * Indicates a Kafka connection.
     */
    KAFKA("kafka"),

    /**
     * Indicates an HTTP-connection with targets only.
     */
    HTTP_PUSH("http-push"),

    /**
     * Indicates a MQTT 5 connection.
     */
    MQTT_5("mqtt-5"),

    /**
     * Indicates a connection to Eclipse Hono.
     * @since 3.2.0
     */
    HONO("hono");

    private final String name;

    ConnectionType(final String name) {

        this.name = name;
    }

    /**
     * Returns the name of this {@code ConnectionStatus}.
     *
     * @return the name.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the {@code ConnectionType} for the given {@code name} if it exists.
     *
     * @param name the name.
     * @return the ConnectionType or an empty optional.
     */
    public static Optional<ConnectionType> forName(final CharSequence name) {
        checkNotNull(name, "Name");
        return Arrays.stream(values())
                .filter(c -> c.name.contentEquals(name))
                .findFirst();
    }

    @Override
    public int length() {
        return name.length();
    }

    @Override
    public char charAt(final int index) {
        return name.charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return name.subSequence(start, end);
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * Whether the passed in {@code connectionType} supports headers on the protocol level or not.
     *
     * @param connectionType the connection type to check for header support.
     * @return {@code true} when headers are supported by the passed in {@code connectionType}, {@code false} if not.
     */
    static boolean supportsHeaders(final ConnectionType connectionType) {
        return connectionType != MQTT;
    }

}
