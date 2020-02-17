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
package org.eclipse.ditto.protocoladapter;

import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.thingsearch.query.ThingSearchQueryCommand;

/**
 * Adapter for mapping a {@link org.eclipse.ditto.signals.commands.thingsearch.query.ThingSearchQueryCommand} to and from an {@link Adaptable}.
 */
public class ThingSearchQueryCommandAdapter extends AbstractAdapter<ThingSearchQueryCommand> {

    private ThingSearchQueryCommandAdapter(
            final Map<String, JsonifiableMapper<ThingSearchQueryCommand>> mappingStrategies,
            final HeaderTranslator headerTranslator) {
        super(mappingStrategies, headerTranslator);
    }

    /**
     * Returns a new ThingSearchQueryCommandAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return the adapter.
     */
    public static ThingSearchQueryCommandAdapter of(final HeaderTranslator headerTranslator) {
        return new ThingSearchQueryCommandAdapter(mappingStrategies(), requireNonNull(headerTranslator));
    }

    private static Map<String, JsonifiableMapper<ThingSearchQueryCommand>> mappingStrategies() {
        final Map<String, JsonifiableMapper<ThingSearchQueryCommand>> mappingStrategies = new HashMap<>();

        //TODO: Implement Mapping strategies for different search signals

        return mappingStrategies;
    }

    private static Adaptable handleSearch(final ThingSearchQueryCommand<?> command, final TopicPath.Channel channel) {
        final TopicPathBuilder topicPathBuilder = ProtocolFactory.newTopicPathBuilder(command.getThingEntityId());

        final CommandsTopicPathBuilder commandsTopicPathBuilder;
        commandsTopicPathBuilder = fromTopicPathBuilderWithChannel(topicPathBuilder, channel);

        final String commandName = command.getClass().getSimpleName().toLowerCase();
        if (!commandName.startsWith(TopicPath.Action.SEARCH.toString())) {
            throw UnknownCommandException.newBuilder(commandName).build();
        }

        final PayloadBuilder payloadBuilder = Payload.newBuilder(command.getResourcePath());
        command.getSelectedFields().ifPresent(payloadBuilder::withFields);

        return Adaptable.newBuilder(commandsTopicPathBuilder.retrieve().build())
                .withPayload(payloadBuilder.build())
                .withHeaders(ProtocolFactory.newHeadersWithDittoContentType(command.getDittoHeaders()))
                .build();
    }


    private static JsonValue createIdsPayload(final Collection<Thing> things) {
        final JsonArray thingsArray = things.stream()
                .map(String::valueOf)
                .map(JsonFactory::newValue)
                .collect(JsonCollectors.valuesToArray());
        return JsonFactory.newObject().setValue(Command.JSON_THING_IDS.getPointer(), thingsArray);
    }

    @Override
    protected String getType(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        if (topicPath.isWildcardTopic()) {
            return Command.TYPE;
        } else {
            final JsonPointer path = adaptable.getPayload().getPath();
            final String commandName = getAction(topicPath) + upperCaseFirst(PathMatcher.match(path));
            return topicPath.getGroup() + "." + topicPath.getCriterion() + ":" + commandName;
        }
    }

    @Override
    public Adaptable constructAdaptable(final ThingSearchQueryCommand command, final TopicPath.Channel channel) {
        return handleSearch(command, channel);
    }
}
