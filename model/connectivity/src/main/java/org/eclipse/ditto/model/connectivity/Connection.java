/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.connectivity;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * Represents a connection within the Connectivity service.
 */
@Immutable
public interface Connection extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * Returns the identifier of this {@code Connection}.
     *
     * @return the identifier.
     */
    String getId();

    /**
     * Returns the connection type of this {@code Connection}.
     *
     * @return the connection type
     */
    ConnectionType getConnectionType();

    /**
     * Returns the persisted/desired ConnectionStatus of this {@code Connection}.
     *
     * @return the persisted ConnectionStatus
     */
    ConnectionStatus getConnectionStatus();

    /**
     * Returns the Authorization Context of this {@code Connection}.
     *
     * @return the Authorization Context.
     */
    AuthorizationContext getAuthorizationContext();

    /**
     * Returns a set of the sources of this {@code Connection}.
     *
     * @return the sources
     */
    Set<Source> getSources();

    /**
     * Returns a set of targets of this {@code Connection}.
     */
    Set<Target> getTargets();

    /**
     * Returns how many clients on different cluster nodes should establish the {@code Connection}.
     * <p>
     * If greater than 1, the connection is created in a HA mode, running on at least 2 cluster nodes.
     * </p>
     *
     * @return the client count.
     */
    int getClientCount();

    /**
     * Returns whether or not failover is enabled for this {@code Connection}.
     *
     * @return {@code true} if failover is enabled, else {@code false}.
     */
    boolean isFailoverEnabled();

    /**
     * Returns the URI of this {@code Connection}.
     *
     * @return the URI.
     */
    String getUri();

    /**
     * Returns the protocol part of the URI of this {@code Connection}.
     *
     * @return the protocol.
     */
    String getProtocol();

    /**
     * Returns the username part of the URI of this {@code Connection}.
     *
     * @return the username.
     */
    Optional<String> getUsername();

    /**
     * Returns the password part of the URI of this {@code Connection}.
     *
     * @return the password.
     */
    Optional<String> getPassword();

    /**
     * Returns the hostname part of the URI of this {@code Connection}.
     *
     * @return the hostname.
     */
    String getHostname();

    /**
     * Returns the port part of the URI of this {@code Connection}.
     *
     * @return the port.
     */
    int getPort();

    /**
     * Returns the path part of the URI of this {@code Connection}.
     *
     * @return the path.
     */
    String getPath();

    /**
     * Whether to validate server certificates on connection establishment,
     *
     * @return {@code true} (default) if server certificates must be valid
     */
    boolean isValidateCertificates();

    /**
     * The size of the command processor pool i.e. how many processor actors.
     *
     * @return size of the command processor actor pool
     */
    int getProcessorPoolSize();

    /**
     * Returns configuration which is only applicable for a specific {@link ConnectionType}.
     *
     * @return an arbitrary map of config keys to config values
     */
    Map<String, String> getSpecificConfig();

    /**
     * Returns the MappingContext to apply in this connection containing either JavaScript scripts or a custom
     * implementation in Java mapping from external messages to internal Ditto Protocol messages.
     *
     * @return the MappingContext to apply for this connection
     */
    Optional<MappingContext> getMappingContext();

    /**
     * Returns a mutable builder with a fluent API for immutable {@code Connection}. The builder is initialised with the
     * entries of this instance.
     *
     * @return the new builder.
     */
    default ConnectionBuilder toBuilder() {
        return ConnectivityModelFactory.newConnectionBuilder(this);
    }

    /**
     * Returns all non hidden marked fields of this {@code Connection}.
     *
     * @return a JSON object representation of this Connection including only non hidden marked fields.
     */
    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    default JsonObject toJson(final JsonSchemaVersion schemaVersion, final JsonFieldSelector fieldSelector) {
        return toJson(schemaVersion, FieldType.notHidden()).get(fieldSelector);
    }

    /**
     * An enumeration of the known {@code JsonField}s of a {@code Connection}.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the {@code JsonSchemaVersion}.
         */
        public static final JsonFieldDefinition<Integer> SCHEMA_VERSION =
                JsonFactory.newIntFieldDefinition(JsonSchemaVersion.getJsonKey(), FieldType.SPECIAL, FieldType.HIDDEN,
                        JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Connection} identifier.
         */
        public static final JsonFieldDefinition<String> ID =
                JsonFactory.newStringFieldDefinition("id", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code ConnectionType}.
         */
        public static final JsonFieldDefinition<String> CONNECTION_TYPE =
                JsonFactory.newStringFieldDefinition("connectionType", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code ConnectionStatus}.
         */
        public static final JsonFieldDefinition<String> CONNECTION_STATUS =
                JsonFactory.newStringFieldDefinition("connectionStatus", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Connection} uri.
         */
        public static final JsonFieldDefinition<String> URI =
                JsonFactory.newStringFieldDefinition("uri", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Connection} authorization context (list of authorization subjects).
         */
        public static final JsonFieldDefinition<JsonArray> AUTHORIZATION_CONTEXT =
                JsonFactory.newJsonArrayFieldDefinition("authorizationContext", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Connection} sources configuration.
         */
        public static final JsonFieldDefinition<JsonArray> SOURCES =
                JsonFactory.newJsonArrayFieldDefinition("sources", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);
        /**
         * JSON field containing the {@code Connection} targets configuration.
         */
        public static final JsonFieldDefinition<JsonArray> TARGETS =
                JsonFactory.newJsonArrayFieldDefinition("targets", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Connection} client count.
         */
        public static final JsonFieldDefinition<Integer> CLIENT_COUNT =
                JsonFactory.newIntFieldDefinition("clientCount", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Connection} failover enabled.
         */
        public static final JsonFieldDefinition<Boolean> FAILOVER_ENABLED =
                JsonFactory.newBooleanFieldDefinition("failoverEnabled", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Connection} trust all certificates.
         */
        public static final JsonFieldDefinition<Boolean> VALIDATE_CERTIFICATES =
                JsonFactory.newBooleanFieldDefinition("validateCertificates", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Connection} processor pool size.
         */
        public static final JsonFieldDefinition<Integer> PROCESSOR_POOL_SIZE =
                JsonFactory.newIntFieldDefinition("processorPoolSize", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Connection} {@link ConnectionType} specific config.
         */
        public static final JsonFieldDefinition<JsonObject> SPECIFIC_CONFIG =
                JsonFactory.newJsonObjectFieldDefinition("specificConfig", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Connection} {@link MappingContext} to apply.
         */
        public static final JsonFieldDefinition<JsonObject> MAPPING_CONTEXT =
                JsonFactory.newJsonObjectFieldDefinition("mappingContext", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

    /**
     * An enumeration of regular expressions for an URI.
     */
    final class UriRegex {

        /**
         * Regex group for the protocol part of an URI.
         */
        public static final String PROTOCOL_REGEX_GROUP = "protocol";

        /**
         * Regex for the protocol part of an URI.
         */
        private static final String PROTOCOL_REGEX = "(?<" + PROTOCOL_REGEX_GROUP + ">amqps?)://";

        /**
         * Regex group for the username part of an URI.
         */
        public static final String USERNAME_REGEX_GROUP = "username";

        /**
         * Regex for the username part of an URI.
         */
        private static final String USERNAME_REGEX = "(?<" + USERNAME_REGEX_GROUP + ">(\\S+))";


        /**
         * Regex group for the password part of an URI.
         */
        @SuppressWarnings("squid:S2068")
        public static final String PASSWORD_REGEX_GROUP = "password";

        /**
         * Regex for the password part of an URI.
         */
        private static final String PASSWORD_REGEX = "(?<" + PASSWORD_REGEX_GROUP + ">(\\S+))";

        /**
         * Regex for the username part of an URI.
         */

        private static final String USERNAME_PASSWORD_REGEX = "(" + USERNAME_REGEX + ":" + PASSWORD_REGEX + "@)?";
        /**
         * Regex group for the host part of an URI.
         */
        public static final String HOSTNAME_REGEX_GROUP = "hostname";

        /**
         * Regex for the host part of an URI.
         */
        private static final String HOSTNAME_REGEX = "(?<" + HOSTNAME_REGEX_GROUP + ">[\\S&&[^:]]+):";

        /**
         * Regex group for the port part of an URI.
         */
        public static final String PORT_REGEX_GROUP = "port";

        /**
         * Regex for the port part of an URI.
         */
        private static final String PORT_REGEX = "(?<" + PORT_REGEX_GROUP + ">(\\d*))?";

        /**
         * Regex group for the path part of an URI.
         */
        public static final String PATH_REGEX_GROUP = "path";

        /**
         * Regex for the path part of an URI.
         */
        private static final String PATH_REGEX = "(/(?<" + PATH_REGEX_GROUP + ">(\\S+))?)?";

        /**
         * Regex for an URI.
         */
        public static final String REGEX =
                PROTOCOL_REGEX + USERNAME_PASSWORD_REGEX + HOSTNAME_REGEX + PORT_REGEX + PATH_REGEX;

        private UriRegex() {
            throw new AssertionError();
        }

    }

}
