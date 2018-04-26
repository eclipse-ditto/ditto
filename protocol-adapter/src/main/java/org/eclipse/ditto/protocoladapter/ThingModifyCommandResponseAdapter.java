/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.protocoladapter;

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
import org.eclipse.ditto.signals.commands.things.modify.ModifyThingResponse;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommandResponse;

/**
 * Adapter for mapping a {@link ThingModifyCommandResponse} to and from an {@link Adaptable}.
 */
final class ThingModifyCommandResponseAdapter extends AbstractAdapter<ThingModifyCommandResponse> {

    private ThingModifyCommandResponseAdapter(
            final Map<String, JsonifiableMapper<ThingModifyCommandResponse>> mappingStrategies) {

        super(mappingStrategies);
    }

    /**
     * Returns a new ThingModifyCommandResponseAdapter.
     *
     * @return the adapter.
     */
    public static ThingModifyCommandResponseAdapter newInstance() {
        return new ThingModifyCommandResponseAdapter(mappingStrategies());
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067"})
    private static Map<String, JsonifiableMapper<ThingModifyCommandResponse>> mappingStrategies() {
        final Map<String, JsonifiableMapper<ThingModifyCommandResponse>> mappingStrategies = new HashMap<>();

        addTopLevelResponses(mappingStrategies);

        addAclResponses(mappingStrategies);

        addAttributeResponses(mappingStrategies);

        addFeatureResponses(mappingStrategies);

        return mappingStrategies;
    }

    @Override
    protected String getType(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        final JsonPointer path = adaptable.getPayload().getPath();
        final String commandName = getAction(topicPath) + upperCaseFirst(PathMatcher.match(path));
        return topicPath.getGroup() + ".responses:" + commandName;
    }

    @Override
    public Adaptable toAdaptable(final ThingModifyCommandResponse commandResponse, final TopicPath.Channel channel) {
        final String responseName = commandResponse.getClass().getSimpleName().toLowerCase();
        if (!responseName.endsWith("response")) {
            throw UnknownCommandResponseException.newBuilder(responseName).build();
        }

        final TopicPathBuilder topicPathBuilder = ProtocolFactory.newTopicPathBuilder(commandResponse.getId());

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

        final PayloadBuilder payloadBuilder = Payload.newBuilder(commandResponse.getResourcePath()) //
                .withStatus(commandResponse.getStatusCode());

        final Optional<JsonValue> value = commandResponse.getEntity(commandResponse.getImplementedSchemaVersion());
        value.ifPresent(payloadBuilder::withValue);

        return Adaptable.newBuilder(commandsTopicPathBuilder.build()) //
                .withPayload(payloadBuilder.build()) //
                .withHeaders(ProtocolFactory.newHeadersWithDittoContentType(commandResponse.getDittoHeaders())) //
                .build();
    }

    private static void addTopLevelResponses(
            final Map<String, JsonifiableMapper<ThingModifyCommandResponse>> mappingStrategies) {
        mappingStrategies.put(CreateThingResponse.TYPE,
                adaptable -> CreateThingResponse.of(thingFrom(adaptable), dittoHeadersFrom(adaptable)));
        mappingStrategies.put(ModifyThingResponse.TYPE,
                adaptable -> isCreated(adaptable)
                        ? ModifyThingResponse.created(thingFrom(adaptable), dittoHeadersFrom(adaptable))
                        : ModifyThingResponse.modified(thingIdFrom(adaptable), dittoHeadersFrom(adaptable)));
        mappingStrategies.put(DeleteThingResponse.TYPE,
                adaptable -> DeleteThingResponse.of(thingIdFrom(adaptable), dittoHeadersFrom(adaptable)));
    }

    private static void addAclResponses(
            final Map<String, JsonifiableMapper<ThingModifyCommandResponse>> mappingStrategies) {
        mappingStrategies.put(ModifyAclResponse.TYPE,
                adaptable -> ModifyAclResponse.modified(thingIdFrom(adaptable), aclFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(ModifyAclEntryResponse.TYPE,
                adaptable -> isCreated(adaptable)
                        ? ModifyAclEntryResponse.created(thingIdFrom(adaptable), aclEntryFrom(adaptable),
                        dittoHeadersFrom(adaptable))
                        : ModifyAclEntryResponse.modified(thingIdFrom(adaptable), aclEntryFrom(adaptable),
                        dittoHeadersFrom(adaptable)));
        mappingStrategies.put(DeleteAclEntryResponse.TYPE,
                adaptable -> DeleteAclEntryResponse.of(thingIdFrom(adaptable), authorizationSubjectFrom(adaptable),
                        dittoHeadersFrom(adaptable)));
    }

    private static void addAttributeResponses(
            final Map<String, JsonifiableMapper<ThingModifyCommandResponse>> mappingStrategies) {
        mappingStrategies.put(ModifyAttributesResponse.TYPE,
                adaptable -> isCreated(adaptable)
                        ? ModifyAttributesResponse.created(thingIdFrom(adaptable), attributesFrom(adaptable),
                        dittoHeadersFrom(adaptable))
                        : ModifyAttributesResponse.modified(thingIdFrom(adaptable), dittoHeadersFrom(adaptable)));
        mappingStrategies.put(DeleteAttributesResponse.TYPE,
                adaptable -> DeleteAttributesResponse.of(thingIdFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(ModifyAttributeResponse.TYPE,
                adaptable -> isCreated(adaptable)
                        ?
                        ModifyAttributeResponse.created(thingIdFrom(adaptable), attributePointerFrom(adaptable),
                                attributeValueFrom(adaptable),
                                dittoHeadersFrom(adaptable))
                        : ModifyAttributeResponse.modified(thingIdFrom(adaptable), attributePointerFrom(adaptable),
                        dittoHeadersFrom(adaptable)));
        mappingStrategies.put(DeleteAttributeResponse.TYPE,
                adaptable -> DeleteAttributeResponse.of(thingIdFrom(adaptable), attributePointerFrom(adaptable),
                        dittoHeadersFrom(adaptable)));
    }

    private static void addFeatureResponses(
            final Map<String, JsonifiableMapper<ThingModifyCommandResponse>> mappingStrategies) {
        mappingStrategies.put(ModifyFeaturesResponse.TYPE,
                adaptable -> isCreated(adaptable)
                        ? ModifyFeaturesResponse.created(thingIdFrom(adaptable), featuresFrom(adaptable),
                        dittoHeadersFrom(adaptable))
                        : ModifyFeaturesResponse.modified(thingIdFrom(adaptable), dittoHeadersFrom(adaptable)));
        mappingStrategies.put(DeleteFeaturesResponse.TYPE,
                adaptable -> DeleteFeaturesResponse.of(thingIdFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(ModifyFeatureResponse.TYPE,
                adaptable -> isCreated(adaptable)
                        ? ModifyFeatureResponse.created(thingIdFrom(adaptable), featureFrom(adaptable),
                        dittoHeadersFrom(adaptable))
                        : ModifyFeatureResponse.modified(thingIdFrom(adaptable), featureIdFrom(adaptable),
                        dittoHeadersFrom(adaptable)));
        mappingStrategies.put(DeleteFeatureResponse.TYPE,
                adaptable -> DeleteFeatureResponse.of(thingIdFrom(adaptable), featureIdFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(ModifyFeatureDefinitionResponse.TYPE,
                adaptable -> isCreated(adaptable)
                        ? ModifyFeatureDefinitionResponse.created(thingIdFrom(adaptable), featureIdFrom(adaptable),
                        featureDefinitionFrom(adaptable),
                        dittoHeadersFrom(adaptable))
                        : ModifyFeatureDefinitionResponse.modified(thingIdFrom(adaptable), featureIdFrom(adaptable),
                        dittoHeadersFrom(adaptable)));
        mappingStrategies.put(DeleteFeatureDefinitionResponse.TYPE,
                adaptable -> DeleteFeatureDefinitionResponse.of(thingIdFrom(adaptable), featureIdFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(ModifyFeaturePropertiesResponse.TYPE,
                adaptable -> isCreated(adaptable)
                        ? ModifyFeaturePropertiesResponse.created(thingIdFrom(adaptable), featureIdFrom(adaptable),
                        featurePropertiesFrom(adaptable),
                        dittoHeadersFrom(adaptable))
                        : ModifyFeaturePropertiesResponse.modified(thingIdFrom(adaptable), featureIdFrom(adaptable),
                        dittoHeadersFrom(adaptable)));
        mappingStrategies.put(DeleteFeaturePropertiesResponse.TYPE,
                adaptable -> DeleteFeaturePropertiesResponse.of(thingIdFrom(adaptable), featureIdFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(ModifyFeaturePropertyResponse.TYPE,
                adaptable -> isCreated(adaptable)
                        ? ModifyFeaturePropertyResponse.created(thingIdFrom(adaptable), featureIdFrom(adaptable),
                        featurePropertyPointerFrom(adaptable),
                        featurePropertyValueFrom(adaptable), dittoHeadersFrom(adaptable))
                        : ModifyFeaturePropertyResponse.modified(thingIdFrom(adaptable), featureIdFrom(adaptable),
                        featurePropertyPointerFrom(adaptable),
                        dittoHeadersFrom(adaptable)));
        mappingStrategies.put(DeleteFeaturePropertyResponse.TYPE, adaptable -> DeleteFeaturePropertyResponse
                .of(thingIdFrom(adaptable), featureIdFrom(adaptable), featurePropertyPointerFrom(adaptable),
                        dittoHeadersFrom(adaptable)));
    }
}
