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

import java.util.Map;

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
 * Contains metrics about a single {@link Target}.
 */
public interface TargetMetrics extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * @return the AddressMetrics for each target
     */
    Map<String, AddressMetric> getAddressMetrics();

    /**
     * Returns all non-hidden marked fields of this {@code TargetMetrics}.
     *
     * @return a JSON object representation of this TargetMetrics including only non-hidden marked fields.
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
         * JSON field containing the {@code AddressMetrics} value.
         */
        public static final JsonFieldDefinition<JsonObject> ADDRESS_METRICS =
                JsonFactory.newJsonObjectFieldDefinition("addressMetrics", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the amount of published messages.
         */
        public static final JsonFieldDefinition<Long> PUBLISHED_MESSAGES =
                JsonFactory.newLongFieldDefinition("publishedMessages", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
