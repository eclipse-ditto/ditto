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

import org.eclipse.ditto.protocol.JsonifiableMapper;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThingResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteAttributeResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteAttributesResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureDefinitionResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureDesiredPropertiesResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureDesiredPropertyResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeaturePropertiesResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeaturePropertyResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeaturesResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteThingDefinitionResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteThingResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttributeResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttributesResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureDefinitionResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureDesiredPropertiesResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureDesiredPropertyResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeaturePropertiesResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeaturePropertyResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeaturesResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyPolicyIdResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThingDefinitionResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThingResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommandResponse;

/**
 * Defines mapping strategies (map from signal type to JsonifiableMapper) for thing modify command responses.
 */
final class ThingModifyCommandResponseMappingStrategies implements MappingStrategies<ThingModifyCommandResponse<?>> {

    private static final ThingModifyCommandResponseMappingStrategies INSTANCE =
            new ThingModifyCommandResponseMappingStrategies();

    private final Map<String, JsonifiableMapper<? extends ThingModifyCommandResponse<?>>> mappingStrategies;

    private ThingModifyCommandResponseMappingStrategies() {
        mappingStrategies = Collections.unmodifiableMap(initMappingStrategies());
    }

    static ThingModifyCommandResponseMappingStrategies getInstance() {
        return INSTANCE;
    }

    private static Map<String, JsonifiableMapper<? extends ThingModifyCommandResponse<?>>> initMappingStrategies() {
        final Map<String, JsonifiableMapper<? extends ThingModifyCommandResponse<?>>> result = new HashMap<>();
        result.putAll(getTopLevelResponseMappers());
        result.putAll(getAttributeResponseMappers());
        result.putAll(getDefinitionResponseMappers());
        result.putAll(getFeatureResponseMappers());
        return result;
    }

    private static Map<String, JsonifiableMapper<? extends ThingModifyCommandResponse<?>>> getTopLevelResponseMappers() {
        final Map<String, JsonifiableMapper<? extends ThingModifyCommandResponse<?>>> result = new HashMap<>();
        result.put(CreateThingResponse.TYPE,
                AdaptableToSignalMapper.of(CreateThingResponse.class,
                        context -> CreateThingResponse.newInstance(context.getThingOrThrow(),
                                context.getHttpStatusOrThrow(),
                                context.getDittoHeaders())));
        result.put(ModifyThingResponse.TYPE,
                AdaptableToSignalMapper.of(ModifyThingResponse.class,
                        context -> ModifyThingResponse.newInstance(context.getThingId(),
                                context.getThing().orElse(null),
                                context.getHttpStatusOrThrow(),
                                context.getDittoHeaders())));
        result.put(DeleteThingResponse.TYPE,
                AdaptableToSignalMapper.of(DeleteThingResponse.class,
                        context -> DeleteThingResponse.newInstance(context.getThingId(),
                                context.getHttpStatusOrThrow(),
                                context.getDittoHeaders())));
        result.put(ModifyPolicyIdResponse.TYPE,
                AdaptableToSignalMapper.of(ModifyPolicyIdResponse.class,
                        context -> ModifyPolicyIdResponse.newInstance(context.getThingId(),
                                context.getPolicyId().orElse(null),
                                context.getHttpStatusOrThrow(),
                                context.getDittoHeaders())));
        return result;
    }

    private static Map<String, JsonifiableMapper<? extends ThingModifyCommandResponse<?>>> getAttributeResponseMappers() {
        final Map<String, JsonifiableMapper<? extends ThingModifyCommandResponse<?>>> result = new HashMap<>();
        result.put(ModifyAttributesResponse.TYPE,
                AdaptableToSignalMapper.of(ModifyAttributesResponse.class,
                        context -> ModifyAttributesResponse.newInstance(context.getThingId(),
                                context.getAttributes().orElseGet(ThingsModelFactory::nullAttributes),
                                context.getHttpStatusOrThrow(),
                                context.getDittoHeaders())));
        result.put(DeleteAttributesResponse.TYPE,
                AdaptableToSignalMapper.of(DeleteAttributesResponse.class,
                        context -> DeleteAttributesResponse.newInstance(context.getThingId(),
                                context.getHttpStatusOrThrow(),
                                context.getDittoHeaders())));
        result.put(ModifyAttributeResponse.TYPE,
                AdaptableToSignalMapper.of(ModifyAttributeResponse.class,
                        context -> ModifyAttributeResponse.newInstance(context.getThingId(),
                                context.getAttributePointerOrThrow(),
                                context.getAttributeValue().orElse(null),
                                context.getHttpStatusOrThrow(),
                                context.getDittoHeaders())));
        result.put(DeleteAttributeResponse.TYPE,
                AdaptableToSignalMapper.of(DeleteAttributeResponse.class,
                        context -> DeleteAttributeResponse.newInstance(context.getThingId(),
                                context.getAttributePointerOrThrow(),
                                context.getHttpStatusOrThrow(),
                                context.getDittoHeaders())));
        return result;
    }

    private static Map<String, JsonifiableMapper<? extends ThingModifyCommandResponse<?>>> getDefinitionResponseMappers() {
        final Map<String, JsonifiableMapper<? extends ThingModifyCommandResponse<?>>> result = new HashMap<>();
        result.put(ModifyThingDefinitionResponse.TYPE,
                AdaptableToSignalMapper.of(ModifyThingDefinitionResponse.class,
                        context -> ModifyThingDefinitionResponse.newInstance(context.getThingId(),
                                context.getThingDefinition().orElse(null),
                                context.getHttpStatusOrThrow(),
                                context.getDittoHeaders())));
        result.put(DeleteThingDefinitionResponse.TYPE,
                AdaptableToSignalMapper.of(DeleteThingDefinitionResponse.class,
                        context -> DeleteThingDefinitionResponse.newInstance(context.getThingId(),
                                context.getHttpStatusOrThrow(),
                                context.getDittoHeaders())));
        return result;
    }

    private static Map<String, JsonifiableMapper<? extends ThingModifyCommandResponse<?>>> getFeatureResponseMappers() {
        final Map<String, JsonifiableMapper<? extends ThingModifyCommandResponse<?>>> result = new HashMap<>();
        result.put(ModifyFeaturesResponse.TYPE,
                AdaptableToSignalMapper.of(ModifyFeaturesResponse.class,
                        context -> ModifyFeaturesResponse.newInstance(context.getThingId(),
                                context.getFeatures().orElseGet(ThingsModelFactory::nullFeatures),
                                context.getHttpStatusOrThrow(),
                                context.getDittoHeaders())));
        result.put(DeleteFeaturesResponse.TYPE,
                AdaptableToSignalMapper.of(DeleteFeaturesResponse.class,
                        context -> DeleteFeaturesResponse.newInstance(context.getThingId(),
                                context.getHttpStatusOrThrow(),
                                context.getDittoHeaders())));
        result.put(ModifyFeatureResponse.TYPE,
                AdaptableToSignalMapper.of(ModifyFeatureResponse.class,
                        context -> ModifyFeatureResponse.newInstance(context.getThingId(),
                                context.getFeature().orElse(null),
                                context.getFeatureIdOrThrow(),
                                context.getHttpStatusOrThrow(),
                                context.getDittoHeaders())));
        result.put(DeleteFeatureResponse.TYPE,
                AdaptableToSignalMapper.of(DeleteFeatureResponse.class,
                        context -> DeleteFeatureResponse.newInstance(context.getThingId(),
                                context.getFeatureIdOrThrow(),
                                context.getHttpStatusOrThrow(),
                                context.getDittoHeaders())));
        result.put(ModifyFeatureDefinitionResponse.TYPE,
                AdaptableToSignalMapper.of(ModifyFeatureDefinitionResponse.class,
                        context -> ModifyFeatureDefinitionResponse.newInstance(context.getThingId(),
                                context.getFeatureIdOrThrow(),
                                context.getFeatureDefinition().orElse(null),
                                context.getHttpStatusOrThrow(),
                                context.getDittoHeaders())));
        result.put(DeleteFeatureDefinitionResponse.TYPE,
                AdaptableToSignalMapper.of(DeleteFeatureDefinitionResponse.class,
                        context -> DeleteFeatureDefinitionResponse.newInstance(context.getThingId(),
                                context.getFeatureIdOrThrow(),
                                context.getHttpStatusOrThrow(),
                                context.getDittoHeaders())));
        result.put(ModifyFeaturePropertiesResponse.TYPE,
                AdaptableToSignalMapper.of(ModifyFeaturePropertiesResponse.class,
                        context -> ModifyFeaturePropertiesResponse.newInstance(context.getThingId(),
                                context.getFeatureIdOrThrow(),
                                context.getFeatureProperties().orElse(null),
                                context.getHttpStatusOrThrow(),
                                context.getDittoHeaders())));
        result.put(DeleteFeaturePropertiesResponse.TYPE,
                AdaptableToSignalMapper.of(DeleteFeaturePropertiesResponse.class,
                        context -> DeleteFeaturePropertiesResponse.of(context.getThingId(),
                                context.getFeatureIdOrThrow(),
                                context.getDittoHeaders())));
        result.put(ModifyFeatureDesiredPropertiesResponse.TYPE,
                AdaptableToSignalMapper.of(ModifyFeatureDesiredPropertiesResponse.class,
                        context -> ModifyFeatureDesiredPropertiesResponse.newInstance(context.getThingId(),
                                context.getFeatureIdOrThrow(),
                                context.getFeatureProperties().orElse(null),
                                context.getHttpStatusOrThrow(),
                                context.getDittoHeaders())));
        result.put(DeleteFeatureDesiredPropertiesResponse.TYPE,
                AdaptableToSignalMapper.of(DeleteFeatureDesiredPropertiesResponse.class,
                        context -> DeleteFeatureDesiredPropertiesResponse.newInstance(context.getThingId(),
                                context.getFeatureIdOrThrow(),
                                context.getHttpStatusOrThrow(),
                                context.getDittoHeaders())));
        result.put(ModifyFeaturePropertyResponse.TYPE,
                AdaptableToSignalMapper.of(ModifyFeaturePropertyResponse.class,
                        context -> ModifyFeaturePropertyResponse.newInstance(context.getThingId(),
                                context.getFeatureIdOrThrow(),
                                context.getFeaturePropertyPointerOrThrow(),
                                context.getFeaturePropertyValue().orElse(null),
                                context.getHttpStatusOrThrow(),
                                context.getDittoHeaders())));
        result.put(DeleteFeaturePropertyResponse.TYPE,
                AdaptableToSignalMapper.of(DeleteFeaturePropertyResponse.class,
                        context -> DeleteFeaturePropertyResponse.newInstance(context.getThingId(),
                                context.getFeatureIdOrThrow(),
                                context.getFeaturePropertyPointerOrThrow(),
                                context.getHttpStatusOrThrow(),
                                context.getDittoHeaders())));
        result.put(ModifyFeatureDesiredPropertyResponse.TYPE,
                AdaptableToSignalMapper.of(ModifyFeatureDesiredPropertyResponse.class,
                        context -> ModifyFeatureDesiredPropertyResponse.newInstance(context.getThingId(),
                                context.getFeatureIdOrThrow(),
                                context.getFeatureDesiredPropertyPointerOrThrow(),
                                context.getFeaturePropertyValue().orElse(null),
                                context.getHttpStatusOrThrow(),
                                context.getDittoHeaders())));
        result.put(DeleteFeatureDesiredPropertyResponse.TYPE,
                AdaptableToSignalMapper.of(DeleteFeatureDesiredPropertyResponse.class,
                        context -> DeleteFeatureDesiredPropertyResponse.newInstance(context.getThingId(),
                                context.getFeatureIdOrThrow(),
                                context.getFeatureDesiredPropertyPointerOrThrow(),
                                context.getHttpStatusOrThrow(),
                                context.getDittoHeaders())));
        return result;
    }

    @Nullable
    @Override
    public JsonifiableMapper<ThingModifyCommandResponse<?>> find(final String type) {
        return (JsonifiableMapper<ThingModifyCommandResponse<?>>) mappingStrategies.get(type);
    }

}
