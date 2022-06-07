/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.connectivity.api;
import java.util.List;
import org.eclipse.ditto.internal.utils.akka.AkkaClassLoader;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;
import akka.actor.AbstractExtensionId;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.actor.Extension;
public interface HonoConfig extends Extension {

    String PREFIX = "hono-connection";

    String getBaseUri();
    enum ConfigValues implements KnownConfigValue {

        BASE_URI("base-uri", "my.hono.host:1234"),
        TELEMETRY_ADDRESS("telemetry", "telemetry/..."),
        EVENT_ADDRESS("event", "event/..."),
        COMMAND_AND_CONTROL_ADDRESS("commandAndControl", "command/..."),
        COMMAND_RESPONSE_ADDRESS("commandResponse", "response/..."),
        USERNAME("username", ""),
        PASSWORD("password", "");

        private final String path;
        private final Object defaultValue;
        ConfigValues(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }

    }

    static HonoConfig get(final ActorSystem actorSystem) {
        return HonoConfig.ExtensionId.INSTANCE.get(actorSystem);
    }

    /**
     * ID of the actor system extension to provide a Hono-connections configuration.
     */
    final class ExtensionId extends AbstractExtensionId<HonoConfig> {

        private static final String CONFIG_PATH = PREFIX + ".config-provider";
        private static final ExtensionId INSTANCE = new ExtensionId();
        @Override
        public HonoConfig createExtension(final ExtendedActorSystem system) {

            final String implementation = system.settings().config().getString(CONFIG_PATH);
            return AkkaClassLoader.instantiate(system, HonoConfig.class,
                    implementation,
                    List.of(ActorSystem.class),
                    List.of(system));
        }
    }
}