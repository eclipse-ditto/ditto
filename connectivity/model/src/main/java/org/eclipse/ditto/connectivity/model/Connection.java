/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.Entity;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

/**
 * Represents a connection within the Connectivity service.
 */
@Immutable
public interface Connection extends Entity<ConnectionRevision> {

    /**
     * Returns the identifier of this {@code Connection}.
     *
     * @return the identifier.
     */
    ConnectionId getId();

    /**
     * Returns the name of this {@code Connection}.
     *
     * @return the name.
     */
    Optional<String> getName();

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
    ConnectivityStatus getConnectionStatus();

    /**
     * Returns a list of the sources of this {@code Connection}.
     *
     * @return the sources
     */
    List<Source> getSources();

    /**
     * Returns a list of targets of this {@code Connection}.
     *
     * @return the targets
     */
    List<Target> getTargets();

    /**
     * Return the ssh tunnel if any exist.
     *
     * @return the ssh tunnel or an empty optional.
     * @since 2.0.0
     */
    Optional<SshTunnel> getSshTunnel();

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
     * Return the persisted credentials if any exist.
     *
     * @return the credentials or an empty optional.
     */
    Optional<Credentials> getCredentials();

    /**
     * Return trusted certificates in PEM format if configured.
     *
     * @return the trusted certificates or an empty optional.
     */
    Optional<String> getTrustedCertificates();

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
     *
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
    Optional<String> getPath();

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
     * Returns the payload mapping definitions for this connection.
     *
     * @return the payload mapping definitions for this connection
     */
    PayloadMappingDefinition getPayloadMappingDefinition();

    /**
     * Returns the tags of this {@code Connection}.
     *
     * @return the tags.
     */
    Set<String> getTags();

    /**
     * Returns the current lifecycle of this Connection.
     *
     * @return the current lifecycle of this Connection.
     */
    Optional<ConnectionLifecycle> getLifecycle();

    /**
     * Indicates whether this Connection has the given lifecycle.
     *
     * @param lifecycle the lifecycle to be checked for.
     * @return {@code true} if this Connection has {@code lifecycle} as its lifecycle, {@code false} else.
     */
    default boolean hasLifecycle(final ConnectionLifecycle lifecycle) {
        return getLifecycle()
                .filter(actualLifecycle -> Objects.equals(actualLifecycle, lifecycle))
                .isPresent();
    }

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
     * Returns all non-hidden marked fields of this {@code Connection}.
     *
     * @return a JSON object representation of this Connection including only non-hidden marked fields.
     */
    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    /**
     * An enumeration of the known {@code JsonField}s of a {@code Connection}.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the Connection's lifecycle.
         */
        public static final JsonFieldDefinition<String> LIFECYCLE = JsonFactory.newStringFieldDefinition("__lifecycle",
                FieldType.SPECIAL,
                FieldType.HIDDEN,
                JsonSchemaVersion.V_2);

        /**
         * JSON field containing the Connection's revision.
         * @since 3.2.0
         */
        public static final JsonFieldDefinition<Long> REVISION = JsonFactory.newLongFieldDefinition("_revision",
                FieldType.SPECIAL,
                FieldType.HIDDEN,
                JsonSchemaVersion.V_2);

        /**
         * JSON field containing the Connection's modified timestamp in ISO-8601 format.
         * @since 3.2.0
         */
        public static final JsonFieldDefinition<String> MODIFIED = JsonFactory.newStringFieldDefinition("_modified",
                FieldType.SPECIAL,
                FieldType.HIDDEN,
                JsonSchemaVersion.V_2);

        /**
         * JSON field containing the Connection's created timestamp in ISO-8601 format.
         * @since 3.2.0
         */
        public static final JsonFieldDefinition<String> CREATED = JsonFactory.newStringFieldDefinition("_created",
                FieldType.SPECIAL,
                FieldType.HIDDEN,
                JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Connection} identifier.
         */
        public static final JsonFieldDefinition<String> ID =
                JsonFactory.newStringFieldDefinition("id", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Connection} name.
         */
        public static final JsonFieldDefinition<String> NAME =
                JsonFactory.newStringFieldDefinition("name", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code ConnectionType}.
         */
        public static final JsonFieldDefinition<String> CONNECTION_TYPE =
                JsonFactory.newStringFieldDefinition("connectionType", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code ConnectionStatus}.
         */
        public static final JsonFieldDefinition<String> CONNECTION_STATUS =
                JsonFactory.newStringFieldDefinition("connectionStatus", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing credentials.
         */
        public static final JsonFieldDefinition<JsonObject> CREDENTIALS =
                JsonFactory.newJsonObjectFieldDefinition("credentials", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Connection} uri.
         */
        public static final JsonFieldDefinition<String> URI =
                JsonFactory.newStringFieldDefinition("uri", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Connection} sources configuration.
         */
        public static final JsonFieldDefinition<JsonArray> SOURCES =
                JsonFactory.newJsonArrayFieldDefinition("sources", FieldType.REGULAR, JsonSchemaVersion.V_2);
        /**
         * JSON field containing the {@code Connection} targets configuration.
         */
        public static final JsonFieldDefinition<JsonArray> TARGETS =
                JsonFactory.newJsonArrayFieldDefinition("targets", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code sshTunnel} configuration.
         */
        public static final JsonFieldDefinition<JsonObject> SSH_TUNNEL =
                JsonFactory.newJsonObjectFieldDefinition("sshTunnel", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Connection} client count.
         */
        public static final JsonFieldDefinition<Integer> CLIENT_COUNT =
                JsonFactory.newIntFieldDefinition("clientCount", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Connection} failover enabled.
         */
        public static final JsonFieldDefinition<Boolean> FAILOVER_ENABLED =
                JsonFactory.newBooleanFieldDefinition("failoverEnabled", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Connection} trust all certificates.
         */
        public static final JsonFieldDefinition<Boolean> VALIDATE_CERTIFICATES =
                JsonFactory.newBooleanFieldDefinition("validateCertificates", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Connection} processor pool size.
         */
        public static final JsonFieldDefinition<Integer> PROCESSOR_POOL_SIZE =
                JsonFactory.newIntFieldDefinition("processorPoolSize", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Connection} {@link ConnectionType} specific config.
         */
        public static final JsonFieldDefinition<JsonObject> SPECIFIC_CONFIG =
                JsonFactory.newJsonObjectFieldDefinition("specificConfig", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Connection} {@link MappingContext} to apply.
         *
         * @deprecated MAPPING_CONTEXT is deprecated, use MAPPING_DEFINITIONS instead
         */
        @Deprecated(/*forRemoval = false*/) // This MUST NOT be deleted from the model as there are still connections
        // with that field which have to be deserialized.
        public static final JsonFieldDefinition<JsonObject> MAPPING_CONTEXT =
                JsonFactory.newJsonObjectFieldDefinition("mappingContext", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the definitions of {@code Connection} mappings.
         */
        public static final JsonFieldDefinition<JsonObject> MAPPING_DEFINITIONS =
                JsonFactory.newJsonObjectFieldDefinition("mappingDefinitions",
                        FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Connection} tags configuration.
         */
        public static final JsonFieldDefinition<JsonArray> TAGS =
                JsonFactory.newJsonArrayFieldDefinition("tags", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field definition of trusted certificates.
         */
        public static final JsonFieldDefinition<String> TRUSTED_CERTIFICATES =
                JsonFieldDefinition.ofString("ca", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
