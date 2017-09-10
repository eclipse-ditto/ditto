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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;

/**
 * Adapter for mapping a {@link RetrieveThings} to and from an {@link Adaptable}.
 */
final class ThingSearchAdapter extends AbstractAdapter<RetrieveThings> {

    private static final JsonFieldDefinition JSON_THING_IDS =
            JsonFieldDefinition.newInstance("thingIds", JsonArray.class);

    private ThingSearchAdapter(final Map<String, JsonifiableMapper<RetrieveThings>> mappingStrategies) {
        super(mappingStrategies);
    }

    public static ThingSearchAdapter newInstance() {
        return new ThingSearchAdapter(mappingStrategies());
    }

    private static String extractNamespace(final RetrieveThings retrieveThings) {
        final List<String> distinctNamespaces = retrieveThings.getThingIds().stream()//
                .map(id -> id.split(":")) //
                .filter(parts -> parts.length > 1) //
                .map(parts -> parts[0]) //
                .distinct() //
                .collect(Collectors.toList());

        if (distinctNamespaces.size() != 1) {
            throw new IllegalArgumentException(
                    "Retrieving multiple things is only supported if all things are in the same, non empty namespace");
        }
        return distinctNamespaces.get(0);
    }

    private static JsonValue createIdsPayload(final List<String> ids) {
        final JsonArray thingIdsArray = ids.stream().map(JsonFactory::newValue).collect(JsonCollectors.valuesToArray());

        return JsonFactory.newObject().setValue(JSON_THING_IDS.getPointer(), thingIdsArray);
    }

    private static Map<String, JsonifiableMapper<RetrieveThings>> mappingStrategies() {
        final Map<String, JsonifiableMapper<RetrieveThings>> mappingStrategies = new HashMap<>();

        mappingStrategies.put(RetrieveThings.TYPE,
                adaptable -> RetrieveThings.getBuilder(idsFrom(adaptable)).dittoHeaders(dittoHeadersFrom(adaptable))
                        .selectedFields(selectedFieldsFrom(adaptable)).build());

        return mappingStrategies;
    }

    private static List<String> idsFrom(final Adaptable adaptable) {
        final JsonArray array = adaptable.getPayload().getValue().filter(JsonValue::isObject).map(JsonValue::asObject)
                .orElseThrow(() -> new JsonParseException("Adaptable payload was non existing or no JsonObject")) //
                .getValue(JSON_THING_IDS).filter(JsonValue::isArray).map(JsonValue::asArray).orElseThrow(() ->
                        new JsonParseException("Could not map 'thingIds' value to expected JsonArray"));

        return array.stream().map(JsonValue::asString).collect(Collectors.toList());
    }

    @Override
    protected String getType(final Adaptable adaptable) {
        return RetrieveThings.TYPE;
    }

    @Override
    public Adaptable toAdaptable(final RetrieveThings command, final TopicPath.Channel channel) {
        return handleRetrieveThings(command, channel);
    }

    private Adaptable handleRetrieveThings(final RetrieveThings retrieveThings, final TopicPath.Channel channel) {
        final String namespace = extractNamespace(retrieveThings);
        final TopicPathBuilder topicPathBuilder = DittoProtocolAdapter.newTopicPathBuilderFromNamespace(namespace);

        final TopicPathBuildable searchTopicPathBuilder;
        if (channel == TopicPath.Channel.TWIN) {
            searchTopicPathBuilder = topicPathBuilder.twin().search();
        } else if (channel == TopicPath.Channel.LIVE) {
            searchTopicPathBuilder = topicPathBuilder.live().search();
        } else {
            throw new IllegalArgumentException("Unknown Channel '" + channel + "'");
        }

        final PayloadBuilder payloadBuilder = Payload.newBuilder(retrieveThings.getResourcePath());
        retrieveThings.getSelectedFields().ifPresent(payloadBuilder::withFields);
        payloadBuilder.withValue(createIdsPayload(retrieveThings.getThingIds()));

        return Adaptable.newBuilder(searchTopicPathBuilder.build()) //
                .withPayload(payloadBuilder.build()) //
                .withHeaders(DittoProtocolAdapter.newHeaders(retrieveThings.getDittoHeaders())) //
                .build();
    }
}
