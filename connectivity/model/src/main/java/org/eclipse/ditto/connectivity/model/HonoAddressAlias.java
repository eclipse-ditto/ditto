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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;


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
    COMMAND_RESPONSE("command_response");

    private final String name;

    private static final Map<String, HonoAddressAlias> HONO_ADDRESS_ALIAS_MAP;

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

    static {
        Map<String, HonoAddressAlias> map = new ConcurrentHashMap<>();
        for (HonoAddressAlias alias : HonoAddressAlias.values()) {
            map.put(alias.getName(), alias);
        }
        HONO_ADDRESS_ALIAS_MAP = Collections.unmodifiableMap(map);
    }

    /**
     * Returns all defined HonoAddressAlias names
     *
     * @return A list with HonoAddressAlias names
     */
    public static List<String> names() {
        return List.copyOf(HONO_ADDRESS_ALIAS_MAP.keySet());
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

}
