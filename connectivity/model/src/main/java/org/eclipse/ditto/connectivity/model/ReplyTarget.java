/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import java.util.Collection;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.common.ResponseType;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;

/**
 * A target to handle outbound Ditto command responses to commands sent by a source or reply-source.
 */
public interface ReplyTarget extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField>, GenericTarget {

    /**
     * @return the address for the outbound responses.
     */
    @Override
    String getAddress();

    /**
     * Defines an optional header mapping e.g. to rename, combine etc. headers for outbound message. Mapping is
     * applied after payload mapping is applied. The mapping may contain {@code thing:*} and {@code header:*}
     * placeholders.
     *
     * @return the optional header mapping
     */
    @Override
    HeaderMapping getHeaderMapping();

    /**
     * @return the list of response types that should be published to the reply target.
     * @since 1.2.0
     */
    Set<ResponseType> getExpectedResponseTypes();

    /**
     * Create a builder with the content of this object.
     *
     * @return the builder.
     */
    Builder toBuilder();

    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    default JsonObject toJson(final JsonSchemaVersion schemaVersion, final JsonFieldSelector fieldSelector) {
        return toJson(schemaVersion, FieldType.notHidden()).get(fieldSelector);
    }

    /**
     * Creates a new reply-target object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the reply-target to be created.
     * @return a new reply-target which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    static ReplyTarget fromJson(final JsonObject jsonObject) {
        return ImmutableReplyTarget.fromJson(jsonObject);
    }

    /**
     * Create a new builder for a reply-target.
     *
     * @return the builder.
     */
    static Builder newBuilder() {
        return new ImmutableReplyTarget.Builder();
    }

    /**
     * Builder for a reply-target.
     */
    interface Builder {

        /**
         * Create a reply-target from this builder.
         */
        ReplyTarget build();

        /**
         * Set the address of the builder.
         *
         * @param address the new address.
         * @return this builder.
         */
        Builder address(String address);

        /**
         * Set the header mapping.
         *
         * @param headerMapping the new header mapping, or null to remove the current mapping.
         * @return this builder.
         */
        Builder headerMapping(@Nullable HeaderMapping headerMapping);

        /**
         * Sets the expected response types that should be delivered to the reply target.
         *
         * @param expectedResponseTypes the expected response types.
         * @return this builder.
         * @since 1.2.0
         */
        Builder expectedResponseTypes(Collection<ResponseType> expectedResponseTypes);

        /**
         * Sets the expected response types that should be delivered to the reply target.
         *
         * @param expectedResponseTypes the expected response types.
         * @return this builder.
         * @since 1.2.0
         */
        Builder expectedResponseTypes(ResponseType... expectedResponseTypes);

    }

    /**
     * An enumeration of the JSON fields of a reply-target.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the target address.
         */
        public static final JsonFieldDefinition<String> ADDRESS =
                JsonFactory.newStringFieldDefinition("address", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Target} header mapping.
         */
        public static final JsonFieldDefinition<JsonObject> HEADER_MAPPING =
                JsonFactory.newJsonObjectFieldDefinition("headerMapping", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the expected response types of this reply target.
         *
         * @since 1.2.0
         */
        public static final JsonFieldDefinition<JsonArray> EXPECTED_RESPONSE_TYPES =
                JsonFactory.newJsonArrayFieldDefinition("expectedResponseTypes", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        JsonFields() {
            throw new AssertionError();
        }
    }
}
