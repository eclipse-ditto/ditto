/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.acks;

import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;

/**
 * Represents a wrapper for requests for domain-specific acknowledgements
 * with an optional filter applied to the requested acknowledgements.
 *
 * @since 1.2.0
 */
@Immutable
public interface FilteredAcknowledgementRequest extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * Returns an instance of FilteredAcknowledgementRequest.
     *
     * @param includes the to be included acknowledgement requests of the returned FilteredAcknowledgementRequest.
     * @param filter the filter to be applied to the FilteredAcknowledgementRequest
     * @return the instance.
     * @throws NullPointerException if {@code includes} is {@code null}.
     */
    static FilteredAcknowledgementRequest of(final Set<AcknowledgementRequest> includes,
            @Nullable final String filter) {
        return AcknowledgementRequests.newFilteredAcknowledgementRequest(includes, filter);
    }

    /**
     * Deserializes the FilteredAcknowledgementRequest from Json
     *
     * @param jsonObject the JsonObject from which to deserialize the FilteredAcknowledgementRequest
     * @return the serialized FilteredAcknowledgementRequest.
     */
    static FilteredAcknowledgementRequest fromJson(final JsonObject jsonObject) {
        return AcknowledgementRequests.filteredAcknowledgementRequestFromJson(jsonObject);
    }

    /**
     * Returns the requested acknowledgements.
     *
     * @return the requested acknowledgements.
     */
    Set<AcknowledgementRequest> getIncludes();

    /**
     * Returns the optional filter to be applied to the requested acknowledgements.
     *
     * @return the filter.
     */
    Optional<String> getFilter();

    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    default JsonObject toJson(final JsonSchemaVersion schemaVersion, final JsonFieldSelector fieldSelector) {
        return toJson(schemaVersion, FieldType.notHidden()).get(fieldSelector);
    }

    /**
     * An enumeration of the JSON fields of a FilteredAcknowledgementRequest.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the included acknowledgement-requests.
         */
        public static final JsonFieldDefinition<JsonArray> INCLUDES =
                JsonFactory.newJsonArrayFieldDefinition("includes", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the applied filter.
         */
        public static final JsonFieldDefinition<String> FILTER =
                JsonFactory.newStringFieldDefinition("filter", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        JsonFields() {
            throw new AssertionError();
        }
    }

}
