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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAcl;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclEntry;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttribute;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributes;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeature;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDefinition;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperties;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperty;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatures;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;

/**
 * Adapter for mapping a {@link ThingQueryCommand} to and from an {@link Adaptable}.
 */
final class ThingQueryCommandAdapter extends AbstractAdapter<ThingQueryCommand> {

    private ThingQueryCommandAdapter(final Map<String, JsonifiableMapper<ThingQueryCommand>> mappingStrategies) {
        super(mappingStrategies);
    }

    public static ThingQueryCommandAdapter newInstance() {
        return new ThingQueryCommandAdapter(mappingStrategies());
    }

    private static Map<String, JsonifiableMapper<ThingQueryCommand>> mappingStrategies() {
        final Map<String, JsonifiableMapper<ThingQueryCommand>> mappingStrategies = new HashMap<>();

        // the snapshot revision is not yet relevant for Protocol Adapter as it is only used by Topologies
        mappingStrategies.put(RetrieveThing.TYPE, adaptable -> RetrieveThing.getBuilder(thingIdFrom(adaptable),
                dittoHeadersFrom(adaptable))
                .withSelectedFields(selectedFieldsFrom(adaptable))
                .build());

        mappingStrategies.put(RetrieveThings.TYPE, adaptable -> RetrieveThings.getBuilder(thingsIdsFrom(adaptable))
                .dittoHeaders(dittoHeadersFrom(adaptable))
                .namespace(namespaceFrom(adaptable))
                .selectedFields(selectedFieldsFrom(adaptable)).build());

        mappingStrategies.put(RetrieveAcl.TYPE, adaptable -> RetrieveAcl.of(thingIdFrom(adaptable),
                dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveAclEntry.TYPE, adaptable -> RetrieveAclEntry.of(thingIdFrom(adaptable),
                authorizationSubjectFrom(adaptable), selectedFieldsFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveAttributes.TYPE, adaptable -> RetrieveAttributes.of(thingIdFrom(adaptable),
                selectedFieldsFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveAttribute.TYPE, adaptable -> RetrieveAttribute.of(thingIdFrom(adaptable),
                attributePointerFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveFeatures.TYPE, adaptable -> RetrieveFeatures.of(thingIdFrom(adaptable),
                selectedFieldsFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveFeature.TYPE, adaptable -> RetrieveFeature.of(thingIdFrom(adaptable),
                featureIdFrom(adaptable), selectedFieldsFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveFeatureDefinition.TYPE, adaptable ->
                RetrieveFeatureDefinition.of(thingIdFrom(adaptable), featureIdFrom(adaptable),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveFeatureProperties.TYPE, adaptable ->
                RetrieveFeatureProperties.of(thingIdFrom(adaptable), featureIdFrom(adaptable),
                        selectedFieldsFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RetrieveFeatureProperty.TYPE, adaptable ->
                RetrieveFeatureProperty.of(thingIdFrom(adaptable), featureIdFrom(adaptable),
                        featurePropertyPointerFrom(adaptable), dittoHeadersFrom(adaptable)));

        return mappingStrategies;
    }

    @Override
    protected String getType(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        if (topicPath.isWildcardTopic()) {
            return RetrieveThings.TYPE;
        } else {
            final JsonPointer path = adaptable.getPayload().getPath();
            final String commandName = getAction(topicPath) + upperCaseFirst(PathMatcher.match(path));
            return topicPath.getGroup() + "." + topicPath.getCriterion() + ":" + commandName;
        }
    }

    @Override
    public Adaptable toAdaptable(final ThingQueryCommand command, final TopicPath.Channel channel) {
        if (command instanceof RetrieveThings) {
            return handleMultipleRetrieve((RetrieveThings) command, channel);
        } else {
            return handleSingleRetrieve(command, channel);
        }
    }

    private static Adaptable handleSingleRetrieve(final ThingQueryCommand<?> command, final TopicPath.Channel channel) {
        final TopicPathBuilder topicPathBuilder = ProtocolFactory.newTopicPathBuilder(command.getThingId());

        final CommandsTopicPathBuilder commandsTopicPathBuilder;
        commandsTopicPathBuilder = fromTopicPathBuilderWithChannel(topicPathBuilder, channel);

        final String commandName = command.getClass().getSimpleName().toLowerCase();
        if (!commandName.startsWith(TopicPath.Action.RETRIEVE.toString())) {
            throw UnknownCommandException.newBuilder(commandName).build();
        }

        final PayloadBuilder payloadBuilder = Payload.newBuilder(command.getResourcePath());
        command.getSelectedFields().ifPresent(payloadBuilder::withFields);

        return Adaptable.newBuilder(commandsTopicPathBuilder.retrieve().build())
                .withPayload(payloadBuilder.build())
                .withHeaders(ProtocolFactory.newHeadersWithDittoContentType(command.getDittoHeaders()))
                .build();
    }

    private static Adaptable handleMultipleRetrieve(final RetrieveThings command,
            final TopicPath.Channel channel) {

        final String commandName = command.getClass().getSimpleName().toLowerCase();
        if (!commandName.startsWith(TopicPath.Action.RETRIEVE.toString())) {
            throw UnknownCommandException.newBuilder(commandName).build();
        }

        final String namespace = command.getNamespace().orElse("_");
        final TopicPathBuilder topicPathBuilder = ProtocolFactory.newTopicPathBuilderFromNamespace(namespace);
        final CommandsTopicPathBuilder commandsTopicPathBuilder =
                fromTopicPathBuilderWithChannel(topicPathBuilder, channel);

        final PayloadBuilder payloadBuilder = Payload.newBuilder(command.getResourcePath());
        command.getSelectedFields().ifPresent(payloadBuilder::withFields);
        payloadBuilder.withValue(createIdsPayload(command.getThingIds()));

        return Adaptable.newBuilder(commandsTopicPathBuilder.retrieve().build())
                .withPayload(payloadBuilder.build())
                .withHeaders(ProtocolFactory.newHeadersWithDittoContentType(command.getDittoHeaders()))
                .build();
    }

    private static List<String> thingsIdsFrom(final Adaptable adaptable) {
        final JsonArray array = adaptable.getPayload()
                .getValue()
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .orElseThrow(() -> new JsonParseException("Adaptable payload was non existing or no JsonObject"))
                .getValue(RetrieveThings.JSON_THING_IDS)
                .filter(JsonValue::isArray)
                .map(JsonValue::asArray)
                .orElseThrow(() -> new JsonParseException("Could not map 'thingIds' value to expected JsonArray"));

        return array.stream()
                .map(JsonValue::asString)
                .collect(Collectors.toList());
    }

    private static JsonValue createIdsPayload(final Collection<String> ids) {
        final JsonArray thingIdsArray = ids.stream().map(JsonFactory::newValue).collect(JsonCollectors.valuesToArray());
        return JsonFactory.newObject().setValue(RetrieveThings.JSON_THING_IDS.getPointer(), thingIdsArray);
    }

}
