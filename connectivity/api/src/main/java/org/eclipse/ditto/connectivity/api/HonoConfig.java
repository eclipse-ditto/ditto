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

import java.util.List;

import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.UserPasswordCredentials;
import org.eclipse.ditto.internal.utils.akka.AkkaClassLoader;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

import akka.actor.AbstractExtensionId;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.actor.Extension;

/**
 * Configuration interface for connection type 'Hono' parameters
 * Via actor system extension, it enables different implementations per service type (Ditto/Things)
 */
public interface HonoConfig extends Extension {

    /**
     * Prefix in .conf files
     */
    String PREFIX = "ditto.connectivity.hono-connection";

    /**
     * Gets the Base URI configuration value
     *
     * @return the connection URI
     */
    String getBaseUri();

    /**
     * Gets validateCertificates boolean property
     *
      * @return validateCertificates boolean property
     */
    Boolean getValidateCertificates();
    /**
     * Gets the SASL mechanism of Hono-connection (Kafka specific property)
     *
     * @return {@link SaslMechanism}
     */
    SaslMechanism getSaslMechanism();

    /**
     * Gets bootstrap servers
     *
     * @return {@link String} containing comma separated bootstrap server list
     */
    String getBootstrapServers();

    /**
     * Gets the credentials for specified Hono-connection
     *
     * @param connectionId The connection ID of the connection
     * @return The credentials of the connection
     */
    UserPasswordCredentials getCredentials(ConnectionId connectionId);

    /**
     * Gets Hub tenant_id property
     *
     * @param connectionId The connection ID of the connection
     * @return hubTenantId
     */
    default String getTenantId(ConnectionId connectionId) {
        return "";
    }

    enum HonoConfigValue implements KnownConfigValue {

        /**
         * Base URI, including port number
         */
        BASE_URI("base-uri", ""),

        /**
         * validateCertificates boolean property
         */
        VALIDATE_CERTIFICATES("validate-certificates", false),

        /**
         * SASL mechanism for connections of type Hono
          */
        SASL_MECHANISM("sasl-mechanism", "plain"),

        /**
         * Bootstrap servers, comma separated
         */
        BOOTSTRAP_SERVERS("bootstrap-servers", ""),

        /**
         * Username
         */
        USERNAME("username", ""),

        /**
         * Password
         */
        PASSWORD("password", "");

        private final String path;
        private final Object defaultValue;

        HonoConfigValue(final String thePath, final Object theDefaultValue) {
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

    /**
     * Load the {@code HonoConfig} extension.
     *
     * @param actorSystem The actor system in which to load the configuration.
     * @return the {@code HonoConfig}.
     */
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

    enum SaslMechanism {

        PLAIN("plain");

        final String value;

        SaslMechanism(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}