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

import java.util.Arrays;

/**
 * Possible Aliases for Address used by connections of type 'Hono'
 */
public enum HonoAddressAlias {
    /**
     * telemetry address alias
     */
    TELEMETRY("telemetry"),

    /**
     * event address alias
     */
    EVENT("event"),

    /**
     * command&control address alias
     */
    COMMAND("command"),

    /**
     * command response address alias
     */
    COMMAND_RESPONSE("command_response"),

    /**
     * unknown alias
     */
    UNKNOWN("");

    private final String name;

    HonoAddressAlias(String name) {
        this.name = name;
    }

    /**
     * Gets the name of the alias
     *
     * @return The name of the alias
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the alias enum that have the given name.
     *
     * @param alias the alias name to get
     * @return {@link HonoAddressAlias}
     */
    public static HonoAddressAlias fromName(String alias) {
        return Arrays.stream(HonoAddressAlias.values())
                .filter(v -> v.name.equals(alias))
                .findFirst()
                .orElse(UNKNOWN);
    }

    /**
     * Resolves the input as a potential address alias or returns the same value if not an existing alias.
     *
     * @param alias the alias name to resolve
     * @param tenantId the tenantId - used in the resolve pattern
     * @param thingSuffix if true, adds '/{{thing:id}}' suffix on resolve - needed for replyTarget addresses
     *                    if false - does not add suffix
     * @return the resolved alias or the same value if not an alias
     */
    public static String resolve(String alias, String tenantId, boolean thingSuffix) throws IllegalArgumentException {
        return Arrays.stream(HonoAddressAlias.values())
                .filter(v -> v.name.equals(alias))
                .findFirst()
                .map(found -> "hono." + found + "." + tenantId + (thingSuffix ? "/{{thing:id}}" : ""))
                .orElse(alias);
    }

    /**
     * Resolves the input as a potential address alias or returns the same value if not an existing alias.
     *
     * @param alias the alias name to resolve
     * @param tenantId the tenantId - used in the resolve pattern
     * @return the resolved alias or the same value if not an alias
     */
    public static String resolve(String alias, String tenantId) throws IllegalArgumentException {
        return resolve(alias, tenantId, false);
    }

}
