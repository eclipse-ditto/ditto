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
package org.eclipse.ditto.protocoladapter.adaptables;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableMapper;
import org.eclipse.ditto.signals.commands.things.modify.MergeThingResponse;

/**
 * Defines mapping strategies (map from signal type to JsonifiableMapper) for thing modify command responses.
 */
final class ThingMergeCommandResponseMappingStrategies
        extends AbstractThingMappingStrategies<MergeThingResponse> {

    private static final ThingMergeCommandResponseMappingStrategies INSTANCE =
            new ThingMergeCommandResponseMappingStrategies();

    private ThingMergeCommandResponseMappingStrategies() {
        super(initMappingStrategies());
    }

    static ThingMergeCommandResponseMappingStrategies getInstance() {
        return INSTANCE;
    }

    private static Map<String, JsonifiableMapper<MergeThingResponse>> initMappingStrategies() {
        final Map<String, JsonifiableMapper<MergeThingResponse>> mappingStrategies = new HashMap<>();

        mappingStrategies.put("thing", ThingMergeCommandResponseMappingStrategies::mergeThing);
        mappingStrategies.put("policyId", ThingMergeCommandResponseMappingStrategies::mergeThing);
        mappingStrategies.put("definition", ThingMergeCommandResponseMappingStrategies::mergeThing);
        mappingStrategies.put("attributes", ThingMergeCommandResponseMappingStrategies::mergeThing);
        mappingStrategies.put("attribute", ThingMergeCommandResponseMappingStrategies::mergeThing);
        mappingStrategies.put("features", ThingMergeCommandResponseMappingStrategies::mergeThing);
        mappingStrategies.put("feature", ThingMergeCommandResponseMappingStrategies::mergeThing);
        mappingStrategies.put("featureDefinition", ThingMergeCommandResponseMappingStrategies::mergeThing);
        mappingStrategies.put("featureProperties", ThingMergeCommandResponseMappingStrategies::mergeThing);
        mappingStrategies.put("featureProperty", ThingMergeCommandResponseMappingStrategies::mergeThing);
        mappingStrategies.put("featureDesiredProperties", ThingMergeCommandResponseMappingStrategies::mergeThing);
        mappingStrategies.put("featureDesiredProperty", ThingMergeCommandResponseMappingStrategies::mergeThing);
        return mappingStrategies;
    }

    private static MergeThingResponse mergeThing(final Adaptable adaptable) {
        return MergeThingResponse.of(thingIdFrom(adaptable),
                JsonPointer.of(adaptable.getPayload().getPath().toString()),
                adaptable.getPayload().getValue().orElse(JsonValue.nullLiteral()), dittoHeadersFrom(adaptable));
    }

}
