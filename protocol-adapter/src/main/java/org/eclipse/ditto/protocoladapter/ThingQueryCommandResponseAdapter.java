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

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.signals.commands.base.WithNamespace;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclEntryResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributeResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributesResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDefinitionResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturePropertiesResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturePropertyResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturesResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingDefinitionResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingsResponse;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommandResponse;

/**
 * Adapter for mapping a {@link ThingQueryCommandResponse} to and from an {@link Adaptable}.
 */
final class ThingQueryCommandResponseAdapter extends AbstractAdapter<ThingQueryCommandResponse> {

    private ThingQueryCommandResponseAdapter(
            final Map<String, JsonifiableMapper<ThingQueryCommandResponse>> mappingStrategies,
            final HeaderTranslator headerTranslator) {
        super(mappingStrategies, headerTranslator);
    }

    /**
     * Returns a new ThingQueryCommandResponseAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return the adapter.
     */
    public static ThingQueryCommandResponseAdapter of(final HeaderTranslator headerTranslator) {
        return new ThingQueryCommandResponseAdapter(mappingStrategies(), requireNonNull(headerTranslator));
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067"})
    private static Map<String, JsonifiableMapper<ThingQueryCommandResponse>> mappingStrategies() {
        final Map<String, JsonifiableMapper<ThingQueryCommandResponse>> mappingStrategies = new HashMap<>();

        mappingStrategies.put(RetrieveThingResponse.TYPE,
                adaptable -> RetrieveThingResponse.of(getThingId(adaptable), getThingOrThrow(adaptable),
                        adaptable.getDittoHeaders()));

        mappingStrategies.put(RetrieveThingsResponse.TYPE,
                adaptable -> RetrieveThingsResponse.of(getThingsArray(adaptable),
                        getNamespaceOrNull(adaptable), adaptable.getDittoHeaders()));

        mappingStrategies.put(RetrieveAclResponse.TYPE,
                adaptable -> RetrieveAclResponse.of(getThingId(adaptable), getAclOrThrow(adaptable),
                        adaptable.getDittoHeaders()));

        mappingStrategies.put(RetrieveAclEntryResponse.TYPE,
                adaptable -> RetrieveAclEntryResponse.of(getThingId(adaptable), getAclEntryOrThrow(adaptable),
                        adaptable.getDittoHeaders()));

        mappingStrategies.put(RetrieveAttributesResponse.TYPE,
                adaptable -> RetrieveAttributesResponse.of(getThingId(adaptable), getAttributesOrThrow(adaptable),
                        adaptable.getDittoHeaders()));

        mappingStrategies.put(RetrieveAttributeResponse.TYPE, adaptable -> RetrieveAttributeResponse
                .of(getThingId(adaptable), getAttributePointerOrThrow(adaptable), getAttributeValueOrThrow(adaptable),
                        adaptable.getDittoHeaders()));

        mappingStrategies.put(RetrieveThingDefinitionResponse.TYPE,
                adaptable -> RetrieveThingDefinitionResponse.of(getThingId(adaptable), getThingDefinitionOrThrow(adaptable),
                        adaptable.getDittoHeaders()));

        mappingStrategies.put(RetrieveFeaturesResponse.TYPE,
                adaptable -> RetrieveFeaturesResponse.of(getThingId(adaptable), getFeaturesOrThrow(adaptable),
                        adaptable.getDittoHeaders()));

        mappingStrategies.put(RetrieveFeatureResponse.TYPE, adaptable -> RetrieveFeatureResponse
                .of(getThingId(adaptable), getFeatureIdOrThrow(adaptable), getFeaturePropertiesOrThrow(adaptable),
                        adaptable.getDittoHeaders()));

        mappingStrategies.put(RetrieveFeatureDefinitionResponse.TYPE, adaptable -> RetrieveFeatureDefinitionResponse
                .of(getThingId(adaptable), getFeatureIdOrThrow(adaptable), getFeatureDefinitionOrThrow(adaptable),
                        adaptable.getDittoHeaders()));

        mappingStrategies.put(RetrieveFeaturePropertiesResponse.TYPE, adaptable -> RetrieveFeaturePropertiesResponse
                .of(getThingId(adaptable), getFeatureIdOrThrow(adaptable), getFeaturePropertiesOrThrow(adaptable),
                        adaptable.getDittoHeaders()));

        mappingStrategies
                .put(RetrieveFeaturePropertyResponse.TYPE,
                        adaptable -> RetrieveFeaturePropertyResponse.of(getThingId(adaptable),
                                getFeatureIdOrThrow(adaptable),
                                getFeaturePropertyPointerOrThrow(adaptable), getFeaturePropertyValueOrThrow(adaptable),
                                adaptable.getDittoHeaders()));

        return mappingStrategies;
    }

    private static JsonArray getThingsArray(final Adaptable adaptable) {
        return adaptable.getPayload()
                .getValue()
                .filter(JsonValue::isArray)
                .map(JsonValue::asArray)
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

    @Override
    protected String getType(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        if (topicPath.isWildcardTopic()) {
            return RetrieveThingsResponse.TYPE;
        } else {
            final JsonPointer path = adaptable.getPayload().getPath();
            final String commandName = getActionOrThrow(topicPath) + upperCaseFirst(PathMatcher.match(path));
            return topicPath.getGroup() + ".responses:" + commandName;
        }
    }

    @Override
    public Adaptable constructAdaptable(final ThingQueryCommandResponse commandResponse,
            final TopicPath.Channel channel) {
        final String responseName = commandResponse.getClass().getSimpleName().toLowerCase();
        if (!responseName.endsWith("response")) {
            throw UnknownCommandResponseException.newBuilder(responseName).build();
        }

        final TopicPathBuilder topicPathBuilder;
        if (commandResponse instanceof RetrieveThingsResponse) {
            final String namespace = ((WithNamespace) commandResponse).getNamespace().orElse("_");
            topicPathBuilder = ProtocolFactory.newTopicPathBuilderFromNamespace(namespace);
        } else {
            topicPathBuilder = ProtocolFactory.newTopicPathBuilder(commandResponse.getThingEntityId());
        }

        final CommandsTopicPathBuilder commandsTopicPathBuilder =
                fromTopicPathBuilderWithChannel(topicPathBuilder, channel);

        final String commandName = commandResponse.getClass().getSimpleName().toLowerCase();
        if (commandName.startsWith(TopicPath.Action.RETRIEVE.toString())) {
            commandsTopicPathBuilder.retrieve();
        } else {
            throw UnknownCommandException.newBuilder(commandName).build();
        }

        final Payload payload = Payload.newBuilder(commandResponse.getResourcePath())
                .withStatus(commandResponse.getStatusCode())
                .withValue(commandResponse.getEntity(commandResponse.getImplementedSchemaVersion()))
                .build();

        return Adaptable.newBuilder(commandsTopicPathBuilder.build())
                .withPayload(payload)
                .withHeaders(ProtocolFactory.newHeadersWithDittoContentType(commandResponse.getDittoHeaders()))
                .build();
    }

}
