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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * Contains metrics about a single {@link Target}.
 */
public interface TargetMetrics extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * @return the AddressMetrics for each target
     */
    Map<String, AddressMetric> getAddressMetrics();

    /**
     * @return the total count of published messages on this target
     */
    long getPublishedMessages();

    /**
     * Returns all non hidden marked fields of this {@code TargetMetrics}.
     *
     * @return a JSON object representation of this TargetMetrics including only non hidden marked fields.
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
     * An enumeration of the known {@code JsonField}s of a {@code TargetMetrics}.
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
         * JSON field containing the {@code AddressMetrics} value.
         */
        public static final JsonFieldDefinition<JsonObject> ADDRESS_METRICS =
                JsonFactory.newJsonObjectFieldDefinition("addressMetrics", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the amount of published messages.
         */
        public static final JsonFieldDefinition<Long> PUBLISHED_MESSAGES =
                JsonFactory.newLongFieldDefinition("publishedMessages", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }
}
