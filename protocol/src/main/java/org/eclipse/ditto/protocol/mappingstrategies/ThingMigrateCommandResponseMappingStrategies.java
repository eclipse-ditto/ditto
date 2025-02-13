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

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.JsonifiableMapper;
import org.eclipse.ditto.things.model.signals.commands.modify.MigrateThingDefinitionResponse;

import javax.annotation.Nullable;

import java.util.Collections;
import java.util.Map;

/**
 * Defines mapping strategies (map from signal type to JsonifiableMapper) for thing modify command responses.
 */
final class ThingMigrateCommandResponseMappingStrategies implements MappingStrategies<MigrateThingDefinitionResponse> {

    private static final ThingMigrateCommandResponseMappingStrategies INSTANCE =
            new ThingMigrateCommandResponseMappingStrategies();

    private final Map<String, JsonifiableMapper<MigrateThingDefinitionResponse>> mappingStrategies;

    private ThingMigrateCommandResponseMappingStrategies() {
        mappingStrategies = initMappingStrategies();
    }

    private static Map<String, JsonifiableMapper<MigrateThingDefinitionResponse>> initMappingStrategies() {
        final AdaptableToSignalMapper<MigrateThingDefinitionResponse> mapper = AdaptableToSignalMapper.of(MigrateThingDefinitionResponse.TYPE,
                context -> {
                    final Adaptable adaptable = context.getAdaptable();
                    final JsonObject payload = adaptable.getPayload().getValue().orElse(JsonObject.empty()).asObject();
                    return MigrateThingDefinitionResponse.fromJson(payload, context.getDittoHeaders());
                });
        return Collections.singletonMap(mapper.getSignalType(), mapper);
    }

    static ThingMigrateCommandResponseMappingStrategies getInstance() {
        return INSTANCE;
    }

    @Nullable
    @Override
    public JsonifiableMapper<MigrateThingDefinitionResponse> find(final String type) {

        return mappingStrategies.get(MigrateThingDefinitionResponse.TYPE);
    }

}
