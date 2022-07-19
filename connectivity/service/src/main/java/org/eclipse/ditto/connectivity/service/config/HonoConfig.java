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
package org.eclipse.ditto.connectivity.service.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.UserPasswordCredentials;
import org.eclipse.ditto.internal.utils.akka.AkkaClassLoader;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

import akka.actor.AbstractExtensionId;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.actor.Extension;

/**
 * This interface provides access to the configuration properties Hono connections.
 * The actual configuration can be obtained via actor system extension.
 */
public interface HonoConfig extends Extension {

    /**
     * Prefix in .conf files
     */
    String PREFIX = "ditto.connectivity.hono";

    /**
     * Gets the Base URI configuration value.
     *
     * @return the connection base URI.
     */
    URI getBaseUri();

    /**
     * Indicates whether the certificates should be validated.
     *
     * @return {@code true} if the certificates should be validated, {@code false} else.
     */
    boolean isValidateCertificates();

    /**
     * Gets the SASL mechanism of Hono-connection (Kafka specific property).
     *
     * @return the configured SaslMechanism.
     */
    SaslMechanism getSaslMechanism();

    /**
     * Returns the URIs of bootstrap servers.
     *
     * @return an unmodifiable unsorted Set containing the URIs of bootstrap servers.
     */
    Set<URI> getBootstrapServerUris();

    /**
     * Gets the credentials for the specified Hono connection.
     *
     * @param connectionId the ID of the connection.
     * @return the credentials of the connection.
     * @throws NullPointerException if {@code connectionId} is {@code null}.
     */
    // TODO jff delete connection ID parameter because for Ditto it does not make sense.
    UserPasswordCredentials getUserPasswordCredentials(ConnectionId connectionId);

    /**
     * Gets the Hub tenant ID property for the specified Hono connection.
     *
     * @param connectionId the ID of the connection.
     * @return the Hub tenant ID, {@code ""} by default.
     * @throws NullPointerException if {@code connectionId} is {@code null}.
     */
    // TODO jff delete method after obtaining the tenant ID is moved to another place.
    default String getTenantId(final ConnectionId connectionId) {
        return "";
    }

    enum HonoConfigValue implements KnownConfigValue {

        /**
         * Base URL, including port number.
         */
        BASE_URI("base-uri", "tcp://localhost:30092"),

        /**
         * validateCertificates boolean property.
         */
        VALIDATE_CERTIFICATES("validate-certificates", false),

        /**
         * SASL mechanism for connections of type Hono.
         */
        SASL_MECHANISM("sasl-mechanism", SaslMechanism.PLAIN.name()),

        /**
         * Bootstrap servers, comma separated.
         */
        BOOTSTRAP_SERVERS("bootstrap-servers", "bootstrap.server:9999"),

        /**
         * The Hono credentials username.
         */
        USERNAME("username", ""),

        /**
         * The Hono credentials password.
         */
        PASSWORD("password", "");

        private final String path;
        private final Object defaultValue;

        private HonoConfigValue(final String thePath, final Object theDefaultValue) {
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
        return HonoConfig.ExtensionId.INSTANCE.get(ConditionChecker.checkNotNull(actorSystem, "actorSystem"));
    }

    /**
     * Validates and gets URI from a string
     *
     * @param uri A {@link String} to be validated
     * @return New {@link URI} from specified string
     * @throws DittoConfigError if given string is not a valid URI
     */
    static URI getUri(final String uri) throws DittoConfigError {
        try {
            return new URI(uri);
        } catch (final URISyntaxException e) {
            throw new DittoConfigError(e);
        }
    }

    /**
     * ID of the actor system extension to provide a Hono-connections configuration.
     */
    final class ExtensionId extends AbstractExtensionId<HonoConfig> {

        private static final String CONFIG_PATH = PREFIX + ".config-provider";

        private static final ExtensionId INSTANCE = new ExtensionId();

        @Override
        public HonoConfig createExtension(final ExtendedActorSystem system) {
            ConditionChecker.checkNotNull(system, "system");
            return AkkaClassLoader.instantiate(system,
                    HonoConfig.class,
                    system.settings().config().getString(CONFIG_PATH),
                    List.of(ActorSystem.class),
                    List.of(system));
        }

    }

    enum SaslMechanism {

        PLAIN("plain");

        private final String value;

        private SaslMechanism(final String value) {
            this.value = value;
        }

        /**
         * Returns the value of this SaslMechanism.
         *
         * @return the value.
         */
        @Override
        public String toString() {
            return value;
        }

    }

}