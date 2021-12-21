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
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        final Stream.Builder<AdaptableToSignalMapper<? extends ThingModifyCommandResponse<?>>> streamBuilder =
                Stream.builder();

        addTopLevelResponseMappers(streamBuilder);
        addAttributeResponseMappers(streamBuilder);
        addDefinitionResponseMappers(streamBuilder);
        addFeatureResponseMappers(streamBuilder);

        final Stream<AdaptableToSignalMapper<? extends ThingModifyCommandResponse<?>>> mappers = streamBuilder.build();
        return mappers.collect(Collectors.toMap(AdaptableToSignalMapper::getSignalType, Function.identity()));
    }

    private static void addTopLevelResponseMappers(
            final Consumer<AdaptableToSignalMapper<? extends ThingModifyCommandResponse<?>>> streamBuilder
    ) {
        streamBuilder.accept(AdaptableToSignalMapper.of(CreateThingResponse.TYPE,
                context -> CreateThingResponse.newInstance(context.getThingOrThrow(),
                        context.getHttpStatusOrThrow(),
                        context.getDittoHeaders())));
        streamBuilder.accept(AdaptableToSignalMapper.of(ModifyThingResponse.TYPE,
                context -> ModifyThingResponse.newInstance(context.getThingId(),
                        context.getThing().orElse(null),
                        context.getHttpStatusOrThrow(),
                        context.getDittoHeaders())));
        streamBuilder.accept(AdaptableToSignalMapper.of(DeleteThingResponse.TYPE,
                context -> DeleteThingResponse.newInstance(context.getThingId(),
                        context.getHttpStatusOrThrow(),
                        context.getDittoHeaders())));
        streamBuilder.accept(AdaptableToSignalMapper.of(ModifyPolicyIdResponse.TYPE,
                context -> ModifyPolicyIdResponse.newInstance(context.getThingId(),
                        context.getPolicyId().orElse(null),
                        context.getHttpStatusOrThrow(),
                        context.getDittoHeaders())));
    }

    private static void addAttributeResponseMappers(
            final Consumer<AdaptableToSignalMapper<? extends ThingModifyCommandResponse<?>>> streamBuilder
    ) {
        streamBuilder.accept(AdaptableToSignalMapper.of(ModifyAttributesResponse.TYPE,
                context -> ModifyAttributesResponse.newInstance(context.getThingId(),
                        context.getAttributes().orElseGet(ThingsModelFactory::nullAttributes),
                        context.getHttpStatusOrThrow(),
                        context.getDittoHeaders())));
        streamBuilder.accept(AdaptableToSignalMapper.of(DeleteAttributesResponse.TYPE,
                context -> DeleteAttributesResponse.newInstance(context.getThingId(),
                        context.getHttpStatusOrThrow(),
                        context.getDittoHeaders())));
        streamBuilder.accept(AdaptableToSignalMapper.of(ModifyAttributeResponse.TYPE,
                context -> ModifyAttributeResponse.newInstance(context.getThingId(),
                        context.getAttributePointerOrThrow(),
                        context.getAttributeValue().orElse(null),
                        context.getHttpStatusOrThrow(),
                        context.getDittoHeaders())));
        streamBuilder.accept(AdaptableToSignalMapper.of(DeleteAttributeResponse.TYPE,
                context -> DeleteAttributeResponse.newInstance(context.getThingId(),
                        context.getAttributePointerOrThrow(),
                        context.getHttpStatusOrThrow(),
                        context.getDittoHeaders())));
    }

    private static void addDefinitionResponseMappers(
            final Consumer<AdaptableToSignalMapper<? extends ThingModifyCommandResponse<?>>> streamBuilder
    ) {
        streamBuilder.accept(AdaptableToSignalMapper.of(ModifyThingDefinitionResponse.TYPE,
                context -> ModifyThingDefinitionResponse.newInstance(context.getThingId(),
                        context.getThingDefinition().orElse(null),
                        context.getHttpStatusOrThrow(),
                        context.getDittoHeaders())));
        streamBuilder.accept(AdaptableToSignalMapper.of(DeleteThingDefinitionResponse.TYPE,
                context -> DeleteThingDefinitionResponse.newInstance(context.getThingId(),
                        context.getHttpStatusOrThrow(),
                        context.getDittoHeaders())));
    }

    private static void addFeatureResponseMappers(
            final Consumer<AdaptableToSignalMapper<? extends ThingModifyCommandResponse<?>>> streamBuilder
    ) {
        streamBuilder.accept(AdaptableToSignalMapper.of(ModifyFeaturesResponse.TYPE,
                context -> ModifyFeaturesResponse.newInstance(context.getThingId(),
                        context.getFeatures().orElseGet(ThingsModelFactory::nullFeatures),
                        context.getHttpStatusOrThrow(),
                        context.getDittoHeaders())));
        streamBuilder.accept(AdaptableToSignalMapper.of(DeleteFeaturesResponse.TYPE,
                context -> DeleteFeaturesResponse.newInstance(context.getThingId(),
                        context.getHttpStatusOrThrow(),
                        context.getDittoHeaders())));
        streamBuilder.accept(AdaptableToSignalMapper.of(ModifyFeatureResponse.TYPE,
                context -> ModifyFeatureResponse.newInstance(context.getThingId(),
                        context.getFeature().orElse(null),
                        context.getFeatureIdOrThrow(),
                        context.getHttpStatusOrThrow(),
                        context.getDittoHeaders())));
        streamBuilder.accept(AdaptableToSignalMapper.of(DeleteFeatureResponse.TYPE,
                context -> DeleteFeatureResponse.newInstance(context.getThingId(),
                        context.getFeatureIdOrThrow(),
                        context.getHttpStatusOrThrow(),
                        context.getDittoHeaders())));
        streamBuilder.accept(AdaptableToSignalMapper.of(ModifyFeatureDefinitionResponse.TYPE,
                context -> ModifyFeatureDefinitionResponse.newInstance(context.getThingId(),
                        context.getFeatureIdOrThrow(),
                        context.getFeatureDefinition().orElse(null),
                        context.getHttpStatusOrThrow(),
                        context.getDittoHeaders())));
        streamBuilder.accept(AdaptableToSignalMapper.of(DeleteFeatureDefinitionResponse.TYPE,
                context -> DeleteFeatureDefinitionResponse.newInstance(context.getThingId(),
                        context.getFeatureIdOrThrow(),
                        context.getHttpStatusOrThrow(),
                        context.getDittoHeaders())));
        streamBuilder.accept(AdaptableToSignalMapper.of(ModifyFeaturePropertiesResponse.TYPE,
                context -> ModifyFeaturePropertiesResponse.newInstance(context.getThingId(),
                        context.getFeatureIdOrThrow(),
                        context.getFeatureProperties().orElse(null),
                        context.getHttpStatusOrThrow(),
                        context.getDittoHeaders())));
        streamBuilder.accept(AdaptableToSignalMapper.of(DeleteFeaturePropertiesResponse.TYPE,
                context -> DeleteFeaturePropertiesResponse.of(context.getThingId(),
                        context.getFeatureIdOrThrow(),
                        context.getDittoHeaders())));
        streamBuilder.accept(AdaptableToSignalMapper.of(ModifyFeatureDesiredPropertiesResponse.TYPE,
                context -> ModifyFeatureDesiredPropertiesResponse.newInstance(context.getThingId(),
                        context.getFeatureIdOrThrow(),
                        context.getFeatureProperties().orElse(null),
                        context.getHttpStatusOrThrow(),
                        context.getDittoHeaders())));
        streamBuilder.accept(AdaptableToSignalMapper.of(DeleteFeatureDesiredPropertiesResponse.TYPE,
                context -> DeleteFeatureDesiredPropertiesResponse.newInstance(context.getThingId(),
                        context.getFeatureIdOrThrow(),
                        context.getHttpStatusOrThrow(),
                        context.getDittoHeaders())));
        streamBuilder.accept(AdaptableToSignalMapper.of(ModifyFeaturePropertyResponse.TYPE,
                context -> ModifyFeaturePropertyResponse.newInstance(context.getThingId(),
                        context.getFeatureIdOrThrow(),
                        context.getFeaturePropertyPointerOrThrow(),
                        context.getFeaturePropertyValue().orElse(null),
                        context.getHttpStatusOrThrow(),
                        context.getDittoHeaders())));
        streamBuilder.accept(AdaptableToSignalMapper.of(DeleteFeaturePropertyResponse.TYPE,
                context -> DeleteFeaturePropertyResponse.newInstance(context.getThingId(),
                        context.getFeatureIdOrThrow(),
                        context.getFeaturePropertyPointerOrThrow(),
                        context.getHttpStatusOrThrow(),
                        context.getDittoHeaders())));
        streamBuilder.accept(AdaptableToSignalMapper.of(ModifyFeatureDesiredPropertyResponse.TYPE,
                context -> ModifyFeatureDesiredPropertyResponse.newInstance(context.getThingId(),
                        context.getFeatureIdOrThrow(),
                        context.getFeatureDesiredPropertyPointerOrThrow(),
                        context.getFeaturePropertyValue().orElse(null),
                        context.getHttpStatusOrThrow(),
                        context.getDittoHeaders())));
        streamBuilder.accept(AdaptableToSignalMapper.of(DeleteFeatureDesiredPropertyResponse.TYPE,
                context -> DeleteFeatureDesiredPropertyResponse.newInstance(context.getThingId(),
                        context.getFeatureIdOrThrow(),
                        context.getFeatureDesiredPropertyPointerOrThrow(),
                        context.getHttpStatusOrThrow(),
                        context.getDittoHeaders())));
    }

    @Nullable
    @Override
    public JsonifiableMapper<ThingModifyCommandResponse<?>> find(final String type) {
        return (JsonifiableMapper<ThingModifyCommandResponse<?>>) mappingStrategies.get(type);
    }

}
