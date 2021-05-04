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

import java.util.Map;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;

/**
 * Definition of payload mappings as a map. The key of the map defines the ID of the mapping, the value defines the
 * mapping context.
 */
public interface PayloadMappingDefinition extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * @return the payload mapping definitions
     */
    Map<String, MappingContext> getDefinitions();

    /**
     * Constructs a new copy of this PayloadMappingDefinition instance with the passed new mapping ID and mapping
     * context.
     *
     * @param id the ID of the new mapping
     * @param mappingContext the new mapping context to be added
     * @return new instance of {@link PayloadMappingDefinition} with the given mapping definition
     */
    PayloadMappingDefinition withDefinition(String id, MappingContext mappingContext);

    /**
     * @return {@code true} if no mappings are defined
     */
    boolean isEmpty();

    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    default JsonObject toJson(final JsonSchemaVersion schemaVersion, final JsonFieldSelector fieldSelector) {
        return toJson(schemaVersion, FieldType.notHidden()).get(fieldSelector);
    }
}
