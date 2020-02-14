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
package org.eclipse.ditto.protocoladapter;

import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.signals.commands.things.modify.CreateThingResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAclEntryResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributeResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributesResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDefinitionResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeaturePropertiesResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeaturePropertyResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeaturesResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThingDefinitionResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThingResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAclEntryResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAclResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributeResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributesResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureDefinitionResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeaturePropertiesResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeaturePropertyResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeaturesResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThingDefinitionResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThingResponse;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommandResponse;

/**
 * Adapter for mapping a {@link ThingModifyCommandResponse} to and from an {@link Adaptable}.
 */
final class ThingModifyCommandResponseAdapter extends AbstractAdapter<ThingModifyCommandResponse> {

    private ThingModifyCommandResponseAdapter(final Map<String, JsonifiableMapper<ThingModifyCommandResponse>> mappingStrategies,
            final HeaderTranslator headerTranslator) {

        super(mappingStrategies, headerTranslator);
    }

    /**
     * Returns a new ThingModifyCommandResponseAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return the adapter.
     */
    public static ThingModifyCommandResponseAdapter of(final HeaderTranslator headerTranslator) {
        return new ThingModifyCommandResponseAdapter(mappingStrategies(), requireNonNull(headerTranslator));
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067"})
    private static Map<String, JsonifiableMapper<ThingModifyCommandResponse>> mappingStrategies() {
        final Map<String, JsonifiableMapper<ThingModifyCommandResponse>> mappingStrategies = new HashMap<>();

        addTopLevelResponses(mappingStrategies);

        addAclResponses(mappingStrategies);

        addAttributeResponses(mappingStrategies);

        addDefinitionResponses(mappingStrategies);

        addFeatureResponses(mappingStrategies);

        return mappingStrategies;
    }

    private static void addTopLevelResponses(
            final Map<String, JsonifiableMapper<ThingModifyCommandResponse>> mappingStrategies) {
        mappingStrategies.put(CreateThingResponse.TYPE,
                adaptable -> CreateThingResponse.of(getThingOrThrow(adaptable), adaptable.getDittoHeaders()));
        mappingStrategies.put(ModifyThingResponse.TYPE,
                adaptable -> isCreatedOrThrow(adaptable)
                        ? ModifyThingResponse.created(getThingOrThrow(adaptable), adaptable.getDittoHeaders())
                        : ModifyThingResponse.modified(getThingId(adaptable), adaptable.getDittoHeaders()));
        mappingStrategies.put(DeleteThingResponse.TYPE,
                adaptable -> DeleteThingResponse.of(getThingId(adaptable), adaptable.getDittoHeaders()));
    }

    private static void addAclResponses(
            final Map<String, JsonifiableMapper<ThingModifyCommandResponse>> mappingStrategies) {
        mappingStrategies.put(ModifyAclResponse.TYPE,
                adaptable -> ModifyAclResponse.modified(getThingId(adaptable), getAclOrThrow(adaptable),
                        adaptable.getDittoHeaders()));

        mappingStrategies.put(ModifyAclEntryResponse.TYPE,
                adaptable -> isCreatedOrThrow(adaptable)
                        ? ModifyAclEntryResponse.created(getThingId(adaptable), getAclEntryOrThrow(adaptable),
                        adaptable.getDittoHeaders())
                        : ModifyAclEntryResponse.modified(getThingId(adaptable), getAclEntryOrThrow(adaptable),
                        adaptable.getDittoHeaders()));
        mappingStrategies.put(DeleteAclEntryResponse.TYPE,
                adaptable -> DeleteAclEntryResponse.of(getThingId(adaptable), getAuthorizationSubject(adaptable),
                        adaptable.getDittoHeaders()));
    }

    private static void addAttributeResponses(
            final Map<String, JsonifiableMapper<ThingModifyCommandResponse>> mappingStrategies) {
        mappingStrategies.put(ModifyAttributesResponse.TYPE,
                adaptable -> isCreatedOrThrow(adaptable)
                        ? ModifyAttributesResponse.created(getThingId(adaptable), getAttributesOrThrow(adaptable),
                        adaptable.getDittoHeaders())
                        : ModifyAttributesResponse.modified(getThingId(adaptable), adaptable.getDittoHeaders()));
        mappingStrategies.put(DeleteAttributesResponse.TYPE,
                adaptable -> DeleteAttributesResponse.of(getThingId(adaptable), adaptable.getDittoHeaders()));

        mappingStrategies.put(ModifyAttributeResponse.TYPE,
                adaptable -> isCreatedOrThrow(adaptable)
                        ?
                        ModifyAttributeResponse.created(getThingId(adaptable), getAttributePointerOrThrow(adaptable),
                                getAttributeValueOrThrow(adaptable),
                                adaptable.getDittoHeaders())
                        :
                        ModifyAttributeResponse.modified(getThingId(adaptable), getAttributePointerOrThrow(adaptable),
                                adaptable.getDittoHeaders()));
        mappingStrategies.put(DeleteAttributeResponse.TYPE,
                adaptable -> DeleteAttributeResponse.of(getThingId(adaptable), getAttributePointerOrThrow(adaptable),
                        adaptable.getDittoHeaders()));
    }

    private static void addDefinitionResponses(
            final Map<String, JsonifiableMapper<ThingModifyCommandResponse>> mappingStrategies) {
        mappingStrategies.put(ModifyThingDefinitionResponse.TYPE,
                adaptable -> isCreatedOrThrow(adaptable)
                        ? ModifyThingDefinitionResponse.created(getThingId(adaptable),
                        getThingDefinitionOrThrow(adaptable), adaptable.getDittoHeaders())
                        : ModifyThingDefinitionResponse.modified(getThingId(adaptable), adaptable.getDittoHeaders()));
        mappingStrategies.put(DeleteThingDefinitionResponse.TYPE,
                adaptable -> DeleteThingDefinitionResponse.of(getThingId(adaptable), adaptable.getDittoHeaders()));
    }

    private static void addFeatureResponses(
            final Map<String, JsonifiableMapper<ThingModifyCommandResponse>> mappingStrategies) {
        mappingStrategies.put(ModifyFeaturesResponse.TYPE,
                adaptable -> isCreatedOrThrow(adaptable)
                        ? ModifyFeaturesResponse.created(getThingId(adaptable), getFeaturesOrThrow(adaptable),
                        adaptable.getDittoHeaders())
                        : ModifyFeaturesResponse.modified(getThingId(adaptable), adaptable.getDittoHeaders()));
        mappingStrategies.put(DeleteFeaturesResponse.TYPE,
                adaptable -> DeleteFeaturesResponse.of(getThingId(adaptable), adaptable.getDittoHeaders()));

        mappingStrategies.put(ModifyFeatureResponse.TYPE,
                adaptable -> isCreatedOrThrow(adaptable)
                        ? ModifyFeatureResponse.created(getThingId(adaptable), getFeatureOrThrow(adaptable),
                        adaptable.getDittoHeaders())
                        : ModifyFeatureResponse.modified(getThingId(adaptable), getFeatureIdOrThrow(adaptable),
                        adaptable.getDittoHeaders()));
        mappingStrategies.put(DeleteFeatureResponse.TYPE,
                adaptable -> DeleteFeatureResponse.of(getThingId(adaptable), getFeatureIdOrThrow(adaptable),
                        adaptable.getDittoHeaders()));

        mappingStrategies.put(ModifyFeatureDefinitionResponse.TYPE,
                adaptable -> isCreatedOrThrow(adaptable)
                        ? ModifyFeatureDefinitionResponse.created(getThingId(adaptable), getFeatureIdOrThrow(adaptable),
                        getFeatureDefinitionOrThrow(adaptable),
                        adaptable.getDittoHeaders())
                        : ModifyFeatureDefinitionResponse.modified(getThingId(adaptable), getFeatureIdOrThrow(adaptable),
                        adaptable.getDittoHeaders()));
        mappingStrategies.put(DeleteFeatureDefinitionResponse.TYPE,
                adaptable -> DeleteFeatureDefinitionResponse.of(getThingId(adaptable), getFeatureIdOrThrow(adaptable),
                        adaptable.getDittoHeaders()));

        mappingStrategies.put(ModifyFeaturePropertiesResponse.TYPE,
                adaptable -> isCreatedOrThrow(adaptable)
                        ? ModifyFeaturePropertiesResponse.created(getThingId(adaptable), getFeatureIdOrThrow(adaptable),
                        getFeaturePropertiesOrThrow(adaptable),
                        adaptable.getDittoHeaders())
                        : ModifyFeaturePropertiesResponse.modified(getThingId(adaptable), getFeatureIdOrThrow(adaptable),
                        adaptable.getDittoHeaders()));
        mappingStrategies.put(DeleteFeaturePropertiesResponse.TYPE,
                adaptable -> DeleteFeaturePropertiesResponse.of(getThingId(adaptable), getFeatureIdOrThrow(adaptable),
                        adaptable.getDittoHeaders()));

        mappingStrategies.put(ModifyFeaturePropertyResponse.TYPE,
                adaptable -> isCreatedOrThrow(adaptable)
                        ? ModifyFeaturePropertyResponse.created(getThingId(adaptable), getFeatureIdOrThrow(adaptable),
                        getFeaturePropertyPointerOrThrow(adaptable),
                        getFeaturePropertyValueOrThrow(adaptable), adaptable.getDittoHeaders())
                        : ModifyFeaturePropertyResponse.modified(getThingId(adaptable), getFeatureIdOrThrow(adaptable),
                        getFeaturePropertyPointerOrThrow(adaptable),
                        adaptable.getDittoHeaders()));
        mappingStrategies.put(DeleteFeaturePropertyResponse.TYPE, adaptable -> DeleteFeaturePropertyResponse
                .of(getThingId(adaptable), getFeatureIdOrThrow(adaptable), getFeaturePropertyPointerOrThrow(adaptable),
                        adaptable.getDittoHeaders()));
    }

    @Override
    protected String getType(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        final JsonPointer path = adaptable.getPayload().getPath();
        final String commandName = getActionOrThrow(topicPath) + upperCaseFirst(PathMatcher.match(path));
        return topicPath.getGroup() + ".responses:" + commandName;
    }

    @Override
    public Adaptable constructAdaptable(final ThingModifyCommandResponse commandResponse,
            final TopicPath.Channel channel) {

        final String responseName = commandResponse.getClass().getSimpleName().toLowerCase();
        if (!responseName.endsWith("response")) {
            throw UnknownCommandResponseException.newBuilder(responseName).build();
        }

        final TopicPathBuilder topicPathBuilder =
                ProtocolFactory.newTopicPathBuilder(commandResponse.getThingEntityId());

        final CommandsTopicPathBuilder commandsTopicPathBuilder =
                fromTopicPathBuilderWithChannel(topicPathBuilder, channel);

        final String commandName = commandResponse.getClass().getSimpleName().toLowerCase();
        if (commandName.startsWith(TopicPath.Action.CREATE.toString())) {
            commandsTopicPathBuilder.create();
        } else if (commandName.startsWith(TopicPath.Action.MODIFY.toString())) {
            commandsTopicPathBuilder.modify();
        } else if (commandName.startsWith(TopicPath.Action.DELETE.toString())) {
            commandsTopicPathBuilder.delete();
        } else {
            throw UnknownCommandException.newBuilder(commandName).build();
        }

        final PayloadBuilder payloadBuilder = Payload.newBuilder(commandResponse.getResourcePath())
                .withStatus(commandResponse.getStatusCode());

        final Optional<JsonValue> value = commandResponse.getEntity(commandResponse.getImplementedSchemaVersion());
        value.ifPresent(payloadBuilder::withValue);

        return Adaptable.newBuilder(commandsTopicPathBuilder.build())
                .withPayload(payloadBuilder.build())
                .withHeaders(ProtocolFactory.newHeadersWithDittoContentType(commandResponse.getDittoHeaders()))
                .build();
    }

}
