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
package org.eclipse.ditto.protocol;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.json.Jsonifiable;

/**
 * Represents the {@code Payload} of an {@link Adaptable}.
 * The Ditto Protocol defines that a {@code Payload} must always have a {@code path} property.
 */
public interface Payload extends Jsonifiable<JsonObject> {

    /**
     * Returns a mutable builder to create immutable {@code Payload} instances for a given {@code path}.
     *
     * @param path the path.
     * @return the builder.
     * @throws NullPointerException if {@code path} is {@code null}.
     */
    static PayloadBuilder newBuilder(final JsonPointer path) {
        return ProtocolFactory.newPayloadBuilder(path);
    }

    /**
     * Returns a mutable builder to create immutable {@code Payload} without a {@code path}.
     *
     * @return the builder.
     */
    static PayloadBuilder newBuilder() {
        return ProtocolFactory.newPayloadBuilder();
    }

    /**
     * Returns a mutable builder with a fluent API for creating a Payload.
     * The returned builder is initialised with the values of the given payload.
     *
     * @param payload provides the initial properties of the returned builder.
     * @return the builder.
     * @throws NullPointerException if {@code payload} is {@code null}.
     */
    static PayloadBuilder newBuilder(final Payload payload) {
        checkNotNull(payload, "payload");
        final PayloadBuilder result = newBuilder(payload.getPath())
                .withValue(payload.getValue().orElse(null))
                .withExtra(payload.getExtra().orElse(null))
                .withStatus(payload.getHttpStatus().orElse(null))
                .withTimestamp(payload.getTimestamp().orElse(null))
                .withFields(payload.getFields().orElse(null));
        payload.getRevision().ifPresent(result::withRevision);
        return result;
    }

    /**
     * Returns the {@code path} of this {@code Payload}.
     *
     * @return the path.
     */
    MessagePath getPath();

    /**
     * Returns the {@code value} of this {@code Payload} if present.
     *
     * @return the optional value.
     */
    Optional<JsonValue> getValue();

    /**
     * Returns the extra information which enriches the actual value of this payload.
     *
     * @return the extra payload or an empty Optional.
     */
    Optional<JsonObject> getExtra();

    /**
     * Returns the {@code status} of this {@code Payload} if present.
     *
     * @return the optional status.
     * @since 2.0.0
     */
    Optional<HttpStatus> getHttpStatus();

    /**
     * Returns the {@code revision} of this {@code Payload} if present.
     *
     * @return the optional revision.
     */
    Optional<Long> getRevision();

    /**
     * Returns the {@code timestamp} of this {@code Payload} if present.
     *
     * @return the optional timestamp.
     */
    Optional<Instant> getTimestamp();

    /**
     * Returns the {@code metadata} of this {@code Payload} if present.
     *
     * @return the optional metadata.
     * @since 1.3.0
     */
    Optional<Metadata> getMetadata();

    /**
     * Returns the {@code fields} of this {@code Payload} if present.
     *
     * @return the optional fields.
     */
    Optional<JsonFieldSelector> getFields();

    /**
     * Json Fields of the Jsonifiable Payload.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the path.
         */
        public static final JsonFieldDefinition<String> PATH = JsonFactory.newStringFieldDefinition("path");

        /**
         * JSON field containing the value.
         */
        public static final JsonFieldDefinition<JsonValue> VALUE = JsonFactory.newJsonValueFieldDefinition("value");

        /**
         * JSON field containing the extra data aka payload enrichment.
         */
        public static final JsonFieldDefinition<JsonObject> EXTRA = JsonFactory.newJsonObjectFieldDefinition("extra");

        /**
         * JSON field containing the status.
         */
        public static final JsonFieldDefinition<Integer> STATUS = JsonFactory.newIntFieldDefinition("status");

        /**
         * JSON field containing the revision.
         */
        public static final JsonFieldDefinition<Long> REVISION = JsonFactory.newLongFieldDefinition("revision");

        /**
         * JSON field containing the timestamp.
         */
        public static final JsonFieldDefinition<String> TIMESTAMP = JsonFactory.newStringFieldDefinition("timestamp");

        /**
         * JSON field containing the metadata.
         *
         * @since 1.3.0
         */
        public static final JsonFieldDefinition<JsonObject> METADATA =
                JsonFactory.newJsonObjectFieldDefinition("metadata");

        /**
         * JSON field containing the fields.
         */
        public static final JsonFieldDefinition<String> FIELDS = JsonFactory.newStringFieldDefinition("fields");

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
