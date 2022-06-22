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
    COMMAND("commandAndControl"),

    /**
     * command response address alias
     */
    COMMAND_RESPONSE("commandResponse");

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

}
