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
package org.eclipse.ditto.protocol.mappingstrategies;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.protocol.JsonifiableMapper;
import org.eclipse.ditto.things.model.FeatureDefinition;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttributeResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttributesResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureDefinitionResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureDesiredPropertiesResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureDesiredPropertyResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeaturePropertiesResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeaturePropertyResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeaturesResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrievePolicyIdResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingDefinitionResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommandResponse;

/**
 * Defines mapping strategies (map from signal type to JsonifiableMapper) for thing query command responses.
 */
final class ThingQueryCommandResponseMappingStrategies implements MappingStrategies<ThingQueryCommandResponse<?>> {

    private static final ThingQueryCommandResponseMappingStrategies INSTANCE =
            new ThingQueryCommandResponseMappingStrategies();

    private final Map<String, JsonifiableMapper<? extends ThingQueryCommandResponse<?>>> mappingStrategies;

    private ThingQueryCommandResponseMappingStrategies() {
        mappingStrategies = Collections.unmodifiableMap(initMappingStrategies());
    }

    static ThingQueryCommandResponseMappingStrategies getInstance() {
        return INSTANCE;
    }

    private static Map<String, JsonifiableMapper<? extends ThingQueryCommandResponse<?>>> initMappingStrategies() {
        final Map<String, JsonifiableMapper<? extends ThingQueryCommandResponse<?>>> result = new HashMap<>();
        result.put(RetrieveThingResponse.TYPE,
                AdaptableToSignalMapper.of(RetrieveThingResponse.class,
                        context -> {
                            final JsonObject thingJsonObject = context.getPayloadValueAsJsonObjectOrThrow();
                            return RetrieveThingResponse.newInstance(context.getThingId(),
                                    thingJsonObject,
                                    thingJsonObject.toString(),
                                    context.getHttpStatusOrThrow(),
                                    context.getDittoHeaders());
                        }));
        result.put(RetrieveAttributesResponse.TYPE,
                AdaptableToSignalMapper.of(RetrieveAttributesResponse.class,
                        context -> RetrieveAttributesResponse.newInstance(context.getThingId(),
                                context.getAttributesOrThrow(),
                                context.getHttpStatusOrThrow(),
                                context.getDittoHeaders())));
        result.put(RetrieveAttributeResponse.TYPE,
                AdaptableToSignalMapper.of(RetrieveAttributeResponse.class,
                        context -> RetrieveAttributeResponse.newInstance(context.getThingId(),
                                context.getAttributePointerOrThrow(),
                                context.getAttributeValueOrThrow(),
                                context.getHttpStatusOrThrow(),
                                context.getDittoHeaders())));
        result.put(RetrieveThingDefinitionResponse.TYPE,
                AdaptableToSignalMapper.of(RetrieveThingDefinitionResponse.class,
                        context -> RetrieveThingDefinitionResponse.newInstance(context.getThingId(),
                                context.getThingDefinitionOrThrow(),
                                context.getHttpStatusOrThrow(),
                                context.getDittoHeaders())));
        result.put(RetrieveFeaturesResponse.TYPE,
                AdaptableToSignalMapper.of(RetrieveFeaturesResponse.class,
                        context -> RetrieveFeaturesResponse.newInstance(context.getThingId(),
                                context.getFeaturesOrThrow(),
                                context.getHttpStatusOrThrow(),
                                context.getDittoHeaders())));
        result.put(RetrieveFeatureResponse.TYPE,
                AdaptableToSignalMapper.of(RetrieveFeatureResponse.class,
                        context -> RetrieveFeatureResponse.newInstance(context.getThingId(),
                                context.getFeatureOrThrow(),
                                context.getHttpStatusOrThrow(),
                                context.getDittoHeaders())));
        result.put(RetrieveFeatureDefinitionResponse.TYPE,
                AdaptableToSignalMapper.of(RetrieveFeatureDefinitionResponse.class,
                        context -> {
                            final FeatureDefinition featureDefinition = context.getFeatureDefinitionOrThrow();
                            return RetrieveFeatureDefinitionResponse.newInstance(context.getThingId(),
                                    context.getFeatureIdOrThrow(),
                                    featureDefinition.toJson(),
                                    context.getHttpStatusOrThrow(),
                                    context.getDittoHeaders());
                        }));
        result.put(RetrieveFeaturePropertiesResponse.TYPE,
                AdaptableToSignalMapper.of(RetrieveFeaturePropertiesResponse.class,
                        context -> RetrieveFeaturePropertiesResponse.newInstance(context.getThingId(),
                                context.getFeatureIdOrThrow(),
                                context.getFeaturePropertiesOrThrow(),
                                context.getHttpStatusOrThrow(),
                                context.getDittoHeaders())));
        result.put(RetrieveFeatureDesiredPropertiesResponse.TYPE,
                AdaptableToSignalMapper.of(RetrieveFeatureDesiredPropertiesResponse.class,
                        context -> RetrieveFeatureDesiredPropertiesResponse.newInstance(context.getThingId(),
                                context.getFeatureIdOrThrow(),
                                context.getFeaturePropertiesOrThrow(),
                                context.getHttpStatusOrThrow(),
                                context.getDittoHeaders())));
        result.put(RetrieveFeaturePropertyResponse.TYPE,
                AdaptableToSignalMapper.of(RetrieveFeaturePropertyResponse.class,
                        context -> RetrieveFeaturePropertyResponse.newInstance(context.getThingId(),
                                context.getFeatureIdOrThrow(),
                                context.getFeaturePropertyPointerOrThrow(),
                                context.getFeaturePropertyValueOrThrow(),
                                context.getHttpStatusOrThrow(),
                                context.getDittoHeaders())));
        result.put(RetrieveFeatureDesiredPropertyResponse.TYPE,
                AdaptableToSignalMapper.of(RetrieveFeatureDesiredPropertyResponse.class,
                        context -> RetrieveFeatureDesiredPropertyResponse.newInstance(context.getThingId(),
                                context.getFeatureIdOrThrow(),
                                context.getFeatureDesiredPropertyPointerOrThrow(),
                                context.getFeaturePropertyValueOrThrow(),
                                context.getHttpStatusOrThrow(),
                                context.getDittoHeaders())));
        result.put(RetrievePolicyIdResponse.TYPE,
                AdaptableToSignalMapper.of(RetrievePolicyIdResponse.class,
                        context -> RetrievePolicyIdResponse.newInstance(context.getThingId(),
                                context.getPolicyIdOrThrow(),
                                context.getHttpStatusOrThrow(),
                                context.getDittoHeaders())));

        return result;
    }

    @Nullable
    @Override
    public JsonifiableMapper<ThingQueryCommandResponse<?>> find(final String type) {
        return (JsonifiableMapper<ThingQueryCommandResponse<?>>) mappingStrategies.get(type);
    }

}
