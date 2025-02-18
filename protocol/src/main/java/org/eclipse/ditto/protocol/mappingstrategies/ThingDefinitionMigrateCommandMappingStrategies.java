/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocol.mappingstrategies;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableMapper;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.MigrateThingDefinition;

/**
 * Mapping strategies for the {@code MigrateThingDefinition} command.
 */
public final class ThingDefinitionMigrateCommandMappingStrategies extends AbstractThingMappingStrategies<MigrateThingDefinition> {

    private static final ThingDefinitionMigrateCommandMappingStrategies
            INSTANCE = new ThingDefinitionMigrateCommandMappingStrategies();

    private ThingDefinitionMigrateCommandMappingStrategies() {
        super(initMappingStrategies());
    }

    public static ThingDefinitionMigrateCommandMappingStrategies getInstance() {
        return INSTANCE;
    }

    private static Map<String, JsonifiableMapper<MigrateThingDefinition>> initMappingStrategies() {
        final Map<String, JsonifiableMapper<MigrateThingDefinition>> mappingStrategies = new HashMap<>();
        mappingStrategies.put(MigrateThingDefinition.TYPE, ThingDefinitionMigrateCommandMappingStrategies::migrateThingDefinition);
        return mappingStrategies;
    }

    private static MigrateThingDefinition migrateThingDefinition(final Adaptable adaptable) {
        final JsonObject payloadObject = adaptable.getPayload()
                .getValue()
                .orElseGet(JsonFactory::newObject)
                .asObject();
        final ThingId thingId = thingIdFrom(adaptable);
        final String thingDefinitionUrl = payloadObject.getValueOrThrow(MigrateThingDefinition.JsonFields.JSON_THING_DEFINITION_URL);
        final JsonObject migrationPayload = payloadObject.getValue(MigrateThingDefinition.JsonFields.JSON_MIGRATION_PAYLOAD)
                .map(JsonValue::asObject).orElse(JsonFactory.newObject());
        final JsonObject patchConditionsJson = payloadObject.getValue(MigrateThingDefinition.JsonFields.JSON_PATCH_CONDITIONS)
                .map(JsonValue::asObject).orElse(JsonFactory.newObject());
        final Map<ResourceKey, String> patchConditions = patchConditionsJson.stream()
                .collect(Collectors.toMap(
                        field -> ResourceKey.newInstance(field.getKey()),
                        field -> field.getValue().asString()
                ));

        final boolean initializeMissingPropertiesFromDefaults = payloadObject
                .getValue(MigrateThingDefinition.JsonFields.JSON_INITIALIZE_MISSING_PROPERTIES_FROM_DEFAULTS)
                .orElse(false);

        return MigrateThingDefinition.of(
                thingId,
                thingDefinitionUrl,
                migrationPayload,
                patchConditions,
                initializeMissingPropertiesFromDefaults,
                dittoHeadersFrom(adaptable)
        );
    }
}
