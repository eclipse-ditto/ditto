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
package org.eclipse.ditto.protocoladapter;

import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * Represents the {@code Payload} of an {@link Adaptable}. The Ditto Protocol defines that a {@code Payload} must
 * always have a {@code path} property.
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
     * Returns the {@code path} of this {@code Payload}.
     *
     * @return the path.
     */
    JsonPointer getPath();

    /**
     * Returns the {@code value} of this {@code Payload} if present.
     *
     * @return the optional value.
     */
    Optional<JsonValue> getValue();

    /**
     * Returns the {@code status} of this {@code Payload} if present.
     *
     * @return the optional status.
     */
    Optional<HttpStatusCode> getStatus();

    /**
     * Returns the {@code revision} of this {@code Payload} if present.
     *
     * @return the optional revision.
     */
    Optional<Long> getRevision();

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
        static final JsonFieldDefinition<String> PATH = JsonFactory.newStringFieldDefinition("path");

        /**
         * JSON field containing the value.
         */
        static final JsonFieldDefinition<JsonValue> VALUE = JsonFactory.newJsonValueFieldDefinition("value");

        /**
         * JSON field containing the status.
         */
        static final JsonFieldDefinition<Integer> STATUS = JsonFactory.newIntFieldDefinition("status");

        /**
         * JSON field containing the revision.
         */
        static final JsonFieldDefinition<Long> REVISION = JsonFactory.newLongFieldDefinition("revision");

        /**
         * JSON field containing the fields.
         */
        static final JsonFieldDefinition<String> FIELDS = JsonFactory.newStringFieldDefinition("fields");

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
