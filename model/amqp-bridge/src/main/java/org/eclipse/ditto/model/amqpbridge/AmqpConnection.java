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
package org.eclipse.ditto.model.amqpbridge;

import java.util.Set;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * Represents a connection within the AMQP Bridge.
 */
@Immutable
public interface AmqpConnection extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

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
     * Returns the Authorization Subject of this {@code Connection}.
     *
     * @return the Authorization Subject.
     */
    AuthorizationSubject getAuthorizationSubject();

    /**
     * Returns the sources of this {@code Connection}.
     *
     * @return the sources
     */
    Set<String> getSources();

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
    String getUsername();

    /**
     * Returns the password part of the URI of this {@code Connection}.
     *
     * @return the password.
     */
    String getPassword();

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
     * Maximum number of messages per second processed by the bridge. 0 (default) means no limit.
     *
     * @return number of messages that are processed per second at most.
     */
    int getThrottle();

    /**
     * Whether to validate server certificates on connection establishment,
     *
     * @return {@code true} (default) if server certificates must be valid
     */
    boolean isValidateCertificates();

    /**
     * Returns all non hidden marked fields of this {@code AmqpConnection}.
     *
     * @return a JSON object representation of this AmqpConnection including only non hidden marked fields.
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
     * An enumeration of the known {@code JsonField}s of a {@code AmqpConnection}.
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
         * JSON field containing the {@code AmqpConnection} identifier.
         */
        public static final JsonFieldDefinition<String> ID =
                JsonFactory.newStringFieldDefinition("id", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code AmqpConnection} uri.
         */
        public static final JsonFieldDefinition<String> URI =
                JsonFactory.newStringFieldDefinition("uri", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code AmqpConnection} authorization subject.
         */
        public static final JsonFieldDefinition<String> AUTHORIZATION_SUBJECT =
                JsonFactory.newStringFieldDefinition("authorizationSubject", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code AmqpConnection} sources.
         */
        public static final JsonFieldDefinition<JsonArray> SOURCES =
                JsonFactory.newJsonArrayFieldDefinition("sources", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code AmqpConnection} failover enabled.
         */
        public static final JsonFieldDefinition<Boolean> FAILOVER_ENABLED =
                JsonFactory.newBooleanFieldDefinition("failoverEnabled", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code AmqpConnection} trust all certificates.
         */
        public static final JsonFieldDefinition<Boolean> VALIDATE_CERTIFICATES =
                JsonFactory.newBooleanFieldDefinition("validateCertificates", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code AmqpConnection} throttle.
         */
        public static final JsonFieldDefinition<Integer> THROTTLE =
                JsonFactory.newIntFieldDefinition("throttle", FieldType.REGULAR, JsonSchemaVersion.V_1,
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
        private static final String USERNAME_REGEX = "(?<" + USERNAME_REGEX_GROUP + ">(\\S+)):";

        /**
         * Regex group for the password part of an URI.
         */
        @SuppressWarnings("squid:S2068")
        public static final String PASSWORD_REGEX_GROUP = "password";

        /**
         * Regex for the password part of an URI.
         */
        private static final String PASSWORD_REGEX = "(?<" + PASSWORD_REGEX_GROUP + ">(\\S+))@";

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
         * Regex for an URI.
         */
        public static final String REGEX =
                PROTOCOL_REGEX + USERNAME_REGEX + PASSWORD_REGEX + HOSTNAME_REGEX + PORT_REGEX;

        private UriRegex() {
            throw new AssertionError();
        }

    }

}
