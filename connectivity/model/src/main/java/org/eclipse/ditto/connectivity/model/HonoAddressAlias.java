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
     * Gets the value of the alias.
     *
     * @return the value of the alias.
     */
    public String getAliasValue() {
        return value;
    }

}
