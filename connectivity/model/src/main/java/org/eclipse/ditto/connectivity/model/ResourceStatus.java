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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;

/**
 * Represents the status of a resource e.g. a client connection, a source or a target, defined in {@link ResourceStatus.ResourceType}.
 */
public interface ResourceStatus extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * @return type of the resource
     */
    ResourceType getResourceType();

    /**
     * @return the resource client identifier
     */
    String getClient();

    /**
     * @return the resource address
     */
    Optional<String> getAddress();

    /**
     * @return the current status of the resource
     */
    ConnectivityStatus getStatus();

    /**
     * @return the current recovery status of the resource (only applicable for the {@code CLIENT} resource).
     */
    Optional<RecoveryStatus> getRecoveryStatus();

    /**
     * @return the optional status details
     */
    Optional<String> getStatusDetails();

    /**
     * @return the instant since the resource is in this state
     */
    Optional<Instant> getInStateSince();

    /**
     * Returns all non-hidden marked fields of this {@code AddressMetric}.
     *
     * @return a JSON object representation of this Source including only non-hidden marked fields.
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
     * Identifies the type of resource included in a ResourceStatus.
     */
    enum ResourceType implements CharSequence {
        /**
         * A {@link Source}.
         */
        SOURCE("source"),

        /**
         * A {@link Target}.
         */
        TARGET("target"),

        /**
         * A {@code Client}.
         */
        CLIENT("client"),

        /**
         * An ssh tunnel.
         */
        SSH_TUNNEL("ssh"),

        /**
         * Unknown resource type.
         */
        UNKNOWN("unknown");

        private final String name;

        ResourceType(final String name) {
            this.name = checkNotNull(name);
        }

        /**
         * Returns the {@code ResourceType} for the given {@code name} if it exists.
         *
         * @param name the name.
         * @return the ResourceType or an empty optional.
         */
        public static Optional<ResourceType> forName(final CharSequence name) {
            checkNotNull(name, "Name");
            return Arrays.stream(values())
                    .filter(c -> c.name.contentEquals(name))
                    .findFirst();
        }

        /**
         * Returns the name of this {@code ResourceType}.
         *
         * @return the name.
         */
        public String getName() {
            return name;
        }

        @Override
        public int length() {
            return name.length();
        }

        @Override
        public char charAt(final int index) {
            return name.charAt(index);
        }

        @Override
        public CharSequence subSequence(final int start, final int end) {
            return name.subSequence(start, end);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * An enumeration of the known {@code JsonField}s of an {@code ResourceStatus}.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the {@code ResourceType} value.
         */
        public static final JsonFieldDefinition<String> TYPE =
                JsonFactory.newStringFieldDefinition("type", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code ConnectivityStatus} value.
         */
        public static final JsonFieldDefinition<String> STATUS =
                JsonFactory.newStringFieldDefinition("status", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code RecoveryStatus} value.
         */
        public static final JsonFieldDefinition<String> RECOVERY_STATUS =
                JsonFactory.newStringFieldDefinition("recoveryStatus", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code client} value.
         */
        public static final JsonFieldDefinition<String> CLIENT =
                JsonFactory.newStringFieldDefinition("client", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code address} value.
         */
        public static final JsonFieldDefinition<String> ADDRESS =
                JsonFactory.newStringFieldDefinition("address", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code ConnectivityStatus} details.
         */
        public static final JsonFieldDefinition<String> STATUS_DETAILS =
                JsonFactory.newStringFieldDefinition("statusDetails", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the instant since the resource is in the state.
         */
        public static final JsonFieldDefinition<String> IN_STATE_SINCE =
                JsonFactory.newStringFieldDefinition("inStateSince", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
