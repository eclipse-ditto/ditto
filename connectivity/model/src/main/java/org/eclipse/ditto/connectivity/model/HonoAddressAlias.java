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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Possible address aliases used by connections of type 'Hono'
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
     * command&#038;control address alias
     */
    COMMAND("command"),

    /**
     * command response address alias
     */
    COMMAND_RESPONSE("command_response");

    private final String name;

    private static final Map<String, HonoAddressAlias> HONO_ADDRESS_ALIAS_MAP;

    static {
        Map<String, HonoAddressAlias> map = new ConcurrentHashMap<>();
        for (HonoAddressAlias alias : HonoAddressAlias.values()) {
            map.put(alias.getName(), alias);
        }
        HONO_ADDRESS_ALIAS_MAP = Collections.unmodifiableMap(map);
    }

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
     * Returns all defined HonoAddressAlias names
     *
     * @return A list with HonoAddressAlias names
     */
    public static List<String> names() {
        return new ArrayList<>(HONO_ADDRESS_ALIAS_MAP.keySet());
    }

    /**
     * Returns the HonoAddressAlias to which the given name is mapped
     *
     * @param name of HonoAddressAlias
     * @return the HonoAddressAlias to which the given name is mapped
     */
    public static Optional<HonoAddressAlias> fromName(String name) {
        try {
            return Optional.of(HONO_ADDRESS_ALIAS_MAP.get(name));
        } catch (NullPointerException | ClassCastException ex) {
            return Optional.empty();
        }
    }

    /**
     * Resolves the input as a potential address alias or returns empty string if not an existing alias.
     *
     * @param alias the alias name to resolve
     * @param tenantId the tenantId - used in the resolve pattern
     * @param thingSuffix if true, adds '/{{thing:id}}' suffix on resolve - needed for replyTarget addresses
     * if false - does not add suffix
     * @return the resolved alias or empty if not an alias
     */
    public static String resolve(String alias, String tenantId, boolean thingSuffix) {
        return fromName(alias)
                .map(found -> "hono." + found.getName() + "." + tenantId + (thingSuffix ? "/{{thing:id}}" : ""))
                .orElse(alias);
    }

    /**
     * Resolves the input as a potential address alias or returns empty string if not an existing aliass.
     *
     * @param alias the alias name to resolve
     * @param tenantId the tenantId - used in the resolve pattern
     * @return the resolved alias or empty if not an alias
     */
    public static String resolve(String alias, String tenantId) throws IllegalArgumentException {
        return resolve(alias, tenantId, false);
    }

}
