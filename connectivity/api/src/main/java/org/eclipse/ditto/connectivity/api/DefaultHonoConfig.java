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
package org.eclipse.ditto.connectivity.api;
import com.typesafe.config.Config;
import akka.actor.ActorSystem;
public final class DefaultHonoConfig implements HonoConfig {

    private final String baseUri;

    private final String telemetryAddress;
    private final String eventAddress;
    private final String commandAndControlAddress;
    private final String commandResponseAddress;

    private final String username;
    private final String password;

    public DefaultHonoConfig(final ActorSystem actorSystem) {
        final Config config = actorSystem.settings().config().getConfig(PREFIX);
        this.baseUri = config.getString(ConfigValues.BASE_URI.getConfigPath());
        this.telemetryAddress = config.getString(ConfigValues.TELEMETRY_ADDRESS.getConfigPath());
        this.eventAddress = config.getString(ConfigValues.EVENT_ADDRESS.getConfigPath());
        this.commandAndControlAddress = config.getString(ConfigValues.COMMAND_AND_CONTROL_ADDRESS.getConfigPath());
        this.commandResponseAddress = config.getString(ConfigValues.COMMAND_RESPONSE_ADDRESS.getConfigPath());
        this.username = config.getString(ConfigValues.USERNAME.getConfigPath());
        this.password = config.getString(ConfigValues.PASSWORD.getConfigPath());
    }

    @Override
    public String getBaseUri() {
        return baseUri;
    }

    public String getTelemetryAddress() {
        return telemetryAddress;
    }

    public String getEventAddress() {
        return eventAddress;
    }

    public String getCommandAndControlAddress() {
        return commandAndControlAddress;
    }

    public String getCommandResponseAddress() {
        return commandResponseAddress;
    }
    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}