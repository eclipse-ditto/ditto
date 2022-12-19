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

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

/**
 * Possible address aliases used by connections of type 'Hono'.
 *
 * @since 3.2.0
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

    private static final Map<String, HonoAddressAlias> HONO_ADDRESS_ALIASES_BY_ALIAS_VALUE =
            Collections.unmodifiableMap(
                    Stream.of(HonoAddressAlias.values())
                            .collect(Collectors.toMap(HonoAddressAlias::getAliasValue, Function.identity()))
            );

    private final String value;

    HonoAddressAlias(final String value) {
        this.value = value;
    }

    /**
     * Returns the HonoAddressAlias to which the given alias value is mapped.
     *
     * @param aliasValue the aliasValue of the supposed HonoAddressAlias.
     * @return an Optional containing the HonoAddressAlias which matches {@code aliasValue} or an empty Optional if none
     * matches.
     */
    public static Optional<HonoAddressAlias> forAliasValue(@Nullable final String aliasValue) {
        return Optional.ofNullable(HONO_ADDRESS_ALIASES_BY_ALIAS_VALUE.get(aliasValue));
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
