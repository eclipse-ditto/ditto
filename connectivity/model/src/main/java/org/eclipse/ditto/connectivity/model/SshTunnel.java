/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;

/**
 * A {@link SshTunnel} contains the information to open and persists a ssh tunnel to a remote endpoint.
 *
 * @since 2.0.0
 */
public interface SshTunnel extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * @return {@code true} if the ssh tunnel is enabhled
     */
    boolean isEnabled();

    /**
     * @return the credentials for the ssh tunnel
     */
    Credentials getCredentials();

    /**
     * @return {@code true} if host should be validated
     */
    boolean isValidateHost();

    /**
     * @return the known hosts for the ssh tunnel
     */
    List<String> getKnownHosts();

    /**
     * @return the uri for the ssh tunnel
     */
    String getUri();

    /**
     * Returns all non-hidden marked fields of this {@code SshTunnel}.
     *
     * @return a JSON object representation of this SshTunnel including only non-hidden marked fields
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
     * An enumeration of the known {@code JsonField}s of a {@code SshTunnel} configuration.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the {@code JsonSchemaVersion}.
         *
         * @deprecated as of 2.3.0 this field definition is not used anymore.
         */
        @Deprecated
        public static final JsonFieldDefinition<Integer> SCHEMA_VERSION =
                JsonFactory.newIntFieldDefinition(JsonSchemaVersion.getJsonKey(),
                        FieldType.SPECIAL,
                        FieldType.HIDDEN,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code SshTunnel} enabled.
         */
        public static final JsonFieldDefinition<Boolean> ENABLED =
                JsonFactory.newBooleanFieldDefinition("enabled", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Credentials} for {@code SshTunnel}.
         */
        public static final JsonFieldDefinition<JsonObject> CREDENTIALS =
                JsonFactory.newJsonObjectFieldDefinition("credentials", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code SshTunnel} known hosts.
         */
        public static final JsonFieldDefinition<Boolean> VALIDATE_HOST =
                JsonFactory.newBooleanFieldDefinition("validateHost", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code SshTunnel} known hosts.
         */
        public static final JsonFieldDefinition<JsonArray> KNOWN_HOSTS =
                JsonFactory.newJsonArrayFieldDefinition("knownHosts", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code SshTunnel} uri.
         */
        public static final JsonFieldDefinition<String> URI =
                JsonFactory.newStringFieldDefinition("uri", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
