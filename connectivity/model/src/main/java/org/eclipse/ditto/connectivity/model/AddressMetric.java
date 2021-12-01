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

import java.util.Set;

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
 * Contains a set of {@link AddressMetric}s.
 */
public interface AddressMetric extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * @return set of measurements for this address
     */
    Set<Measurement> getMeasurements();

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
     * An enumeration of the known {@code JsonField}s of an {@code AddressMetric}.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the {@code JsonSchemaVersion}.
         *
         * @deprecated as of 2.3.0 this field definition is not used anymore.
         */
        @Deprecated
        public static final JsonFieldDefinition<Integer> SCHEMA_VERSION = JsonFactory.newIntFieldDefinition(
                JsonSchemaVersion.getJsonKey(),
                FieldType.SPECIAL,
                FieldType.HIDDEN,
                JsonSchemaVersion.V_2
        );

        /**
         * JSON field containing the timestamp when the last message was consumed/published.
         */
        public static final JsonFieldDefinition<String> LAST_MESSAGE_AT =
                JsonFactory.newStringFieldDefinition("lastMessageAt", FieldType.REGULAR,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }
}
