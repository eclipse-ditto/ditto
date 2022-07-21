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

import org.eclipse.ditto.base.model.common.ConditionChecker;

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

    /**
     * Resolves the source or target address of this address alias for the specified tenant ID.
     *
     * @param tenantId the tenant ID to resolve the address of this alias for.
     * @return the resolved address of this address alias for {@code tenantId}.
     * @throws NullPointerException if {@code tenantId} is {@code null}.
     */
    public String resolveAddress(final CharSequence tenantId) {
        ConditionChecker.checkNotNull(tenantId, "tenantId");

        final String prefix = "hono.";
        final String aliasValue = getAliasValue();
        final int tenantIdLength = tenantId.length();
        final StringBuilder sb = new StringBuilder(prefix.length() + aliasValue.length() + 1 + tenantIdLength);
        sb.append(prefix).append(aliasValue);
        if (0 < tenantIdLength) {
            sb.append(".").append(tenantId);
        }
        return sb.toString();
    }

    /**
     * Resolves the source or target address of this address alias for the specified tenant ID and appends a suffix
     * for thing ID.
     * This is mainly needed for reply target addresses.
     *
     * @param tenantId the tenant ID to resolve the address of this alias for.
     * @return the resolved address of this address alias for {@code tenantId} with {@code "/{{thing:id}}"} appended.
     * @throws NullPointerException if {@code tenantId} is {@code null}.
     */
    public String resolveAddressWithThingIdSuffix(final CharSequence tenantId) {
        return resolveAddress(tenantId) + "/{{thing:id}}";
    }

}
