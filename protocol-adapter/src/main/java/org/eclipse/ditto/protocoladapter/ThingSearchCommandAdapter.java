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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.CancelSubscription;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.CreateSubscription;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.RequestSubscription;

/**
 * Adapter for mapping a {@link org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand} to and from an {@link Adaptable}.
 */
public class ThingSearchCommandAdapter extends AbstractAdapter<ThingSearchCommand> {

    private ThingSearchCommandAdapter(
            final Map<String, JsonifiableMapper<ThingSearchCommand>> mappingStrategies,
            final HeaderTranslator headerTranslator) {
        super(mappingStrategies, headerTranslator);
    }

    /**
     * Returns a new ThingSearchCommandAdapter.
     *
     * @param headerTranslator translator between external and Ditto headers.
     * @return the adapter.
     */
    public static ThingSearchCommandAdapter of(final HeaderTranslator headerTranslator) {
        return new ThingSearchCommandAdapter(mappingStrategies(), requireNonNull(headerTranslator));
    }

    private static Map<String, JsonifiableMapper<ThingSearchCommand>> mappingStrategies() {
        final Map<String, JsonifiableMapper<ThingSearchCommand>> mappingStrategies = new HashMap<>();

        mappingStrategies.put(CreateSubscription.TYPE,
                adaptable -> CreateSubscription.of(filterFrom(adaptable), optionsFrom(adaptable),
                        selectedFieldsFrom(adaptable), namespacesFrom(adaptable), dittoHeadersFrom(adaptable)));

        mappingStrategies.put(CancelSubscription.TYPE,
                adaptable -> CancelSubscription.of(requireNonNull(subscriptionIdFrom(adaptable)),
                        dittoHeadersFrom(adaptable)));

        mappingStrategies.put(RequestSubscription.TYPE, adaptable -> RequestSubscription.of(
                requireNonNull(subscriptionIdFrom(adaptable)), demandFrom(adaptable), dittoHeadersFrom(adaptable)));

        return mappingStrategies;

    }

    private static Adaptable handleSearch(final ThingSearchCommand<?> command, final TopicPath.Channel channel) {

        final String namespace = String.join(",", command.getNamespaces().orElse(new HashSet<>(
                Collections.singletonList("_"))));
        final TopicPathBuilder topicPathBuilder = ProtocolFactory.newTopicPathBuilderFromNamespace(namespace);

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


    @Override
    protected String getType(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();
        final JsonPointer path = adaptable.getPayload().getPath();
        final String commandName = getAction(topicPath) + upperCaseFirst(PathMatcher.match(path));
        return topicPath.getGroup() + "." + topicPath.getCriterion() + ":" + commandName;
    }


    @Override
    public Adaptable constructAdaptable(final ThingSearchCommand command, final TopicPath.Channel channel) {
        return handleSearch(command, channel);
    }
}
