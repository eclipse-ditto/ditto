/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.model.connectivity;

import java.util.Set;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

public interface MqttSource extends Source {

    /**
     * @return the qos level of this MQTT source
     */
    int getQos();

    /**
     * @return the filters that are applied after mapping
     */
    Set<String> getFilters();

    /**
     * An enumeration of the known {@code JsonField}s of a {@code Source} configuration.
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
         * JSON field containing the {@code Source} consumer count.
         */
        public static final JsonFieldDefinition<Integer> QOS =
                JsonFactory.newIntFieldDefinition("qos", FieldType.REGULAR, JsonSchemaVersion.V_1,
                        JsonSchemaVersion.V_2);

        /**
         * JSON field containing the {@code Source} authorization context (list of authorization subjects).
         */
        public static final JsonFieldDefinition<JsonArray> FILTERS =
                JsonFactory.newJsonArrayFieldDefinition("filters", FieldType.REGULAR,
                        JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        JsonFields() {
            throw new AssertionError();
        }

    }
}
