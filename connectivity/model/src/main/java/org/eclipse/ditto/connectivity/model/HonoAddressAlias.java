/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import javax.annotation.Nullable;

/**
 * Possible address aliases used by connections of type 'Hono'
 */
public enum HonoAddressAlias {

    /**
     * telemetry address alias.
     */
    TELEMETRY("telemetry"),

    /**
     * event address alias.
     */
    EVENT("event"),

    /**
     * command&#038;control address alias.
     */
    COMMAND("command"),

    /**
     * command response address alias.
     */
    COMMAND_RESPONSE("command_response");

    private final String value;

    private HonoAddressAlias(final String value) {
        this.value = value;
    }

    /**
     * Returns all defined HonoAddressAlias values.
     *
     * @return a stream with HonoAddressAlias values.
     */
    public static Stream<String> aliasValues() {
        return Stream.of(values()).map(HonoAddressAlias::getAliasValue);
    }

    /**
     * Returns the HonoAddressAlias to which the given alias value is mapped.
     * This method is fault-tolerant for its parameter to some degree:
     * <ul>
     *     <li>it accepts {@code null},</li>
     *     <li>it trims white spaces and</li>
     *     <li>it converts the specified string to lower case.</li>
     * </ul>
     *
     * @param aliasValue the aliasValue of the supposed HonoAddressAlias.
     * @return an Optional containing the HonoAddressAlias which matches {@code aliasValue} or an empty Optional if none
     * matches.
     */
    public static Optional<HonoAddressAlias> forAliasValue(@Nullable final String aliasValue) {
        return Stream.of(values())
                .filter(alias -> null != aliasValue &&
                        Objects.equals(alias.getAliasValue(), aliasValue.trim().toLowerCase(Locale.ENGLISH)))
                .findAny();
    }

    /**
     * Gets the value of the alias.
     *
     * @return the value of the alias.
     */
    public String getAliasValue() {
        return value;
    }

}
