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

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableMapper;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributeResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributesResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDefinitionResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDesiredPropertiesResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDesiredPropertyResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturePropertiesResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturePropertyResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturesResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingDefinitionResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingsResponse;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommandResponse;

/**
 * Defines mapping strategies (map from signal type to JsonifiableMapper) for thing query command responses.
 */
final class ThingQueryCommandResponseMappingStrategies
        extends AbstractThingMappingStrategies<ThingQueryCommandResponse<?>> {

    private static final ThingQueryCommandResponseMappingStrategies INSTANCE =
            new ThingQueryCommandResponseMappingStrategies();

    private ThingQueryCommandResponseMappingStrategies() {
        super(initMappingStrategies());
    }

    static ThingQueryCommandResponseMappingStrategies getInstance() {
        return INSTANCE;
    }

    private static Map<String, JsonifiableMapper<ThingQueryCommandResponse<?>>> initMappingStrategies() {

        final Map<String, JsonifiableMapper<ThingQueryCommandResponse<?>>> mappingStrategies = new HashMap<>();
        mappingStrategies.put(RetrieveThingResponse.TYPE,
                adaptable -> RetrieveThingResponse.of(thingIdFrom(adaptable), payloadValueAsJsonObjectFrom(adaptable),
                        dittoHeadersFrom(adaptable)));


        mappingStrategies.put(RetrieveAttributesResponse.TYPE,
                adaptable -> RetrieveAttributesResponse.of(thingIdFrom(adaptable), attributesFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveAttributeResponse.TYPE, adaptable -> RetrieveAttributeResponse
                .of(thingIdFrom(adaptable), attributePointerFrom(adaptable), attributeValueFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveThingDefinitionResponse.TYPE,
                adaptable -> RetrieveThingDefinitionResponse.of(thingIdFrom(adaptable), thingDefinitionFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveFeaturesResponse.TYPE,
                adaptable -> RetrieveFeaturesResponse.of(thingIdFrom(adaptable), featuresFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveFeatureResponse.TYPE, adaptable -> RetrieveFeatureResponse
                .of(thingIdFrom(adaptable), featureIdFrom(adaptable), featurePropertiesFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveFeatureDefinitionResponse.TYPE, adaptable -> RetrieveFeatureDefinitionResponse
                .of(thingIdFrom(adaptable), featureIdFrom(adaptable), featureDefinitionFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveFeaturePropertiesResponse.TYPE, adaptable -> RetrieveFeaturePropertiesResponse
                .of(thingIdFrom(adaptable), featureIdFrom(adaptable), featurePropertiesFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveFeatureDesiredPropertiesResponse.TYPE,
                adaptable -> RetrieveFeatureDesiredPropertiesResponse
                        .of(thingIdFrom(adaptable), featureIdFrom(adaptable), featurePropertiesFrom(adaptable),
                                dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveFeaturePropertyResponse.TYPE,
                adaptable -> RetrieveFeaturePropertyResponse.of(thingIdFrom(adaptable),
                        featureIdFrom(adaptable),
                        featurePropertyPointerFrom(adaptable), featurePropertyValueFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveFeatureDesiredPropertyResponse.TYPE,
                adaptable -> RetrieveFeatureDesiredPropertyResponse.of(thingIdFrom(adaptable),
                        featureIdFrom(adaptable),
                        featurePropertyPointerFrom(adaptable), featurePropertyValueFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        return mappingStrategies;
    }

}
