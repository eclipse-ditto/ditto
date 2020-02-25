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
import java.util.Map;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.CancelSubscription;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.CreateSubscription;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.RequestSubscription;

/**
 * Adapter for mapping a {@link org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand} to and from an {@link Adaptable}.
 *
 * @since 1.2.0
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


    @Override
    protected String getType(final Adaptable adaptable) {

        return ThingSearchCommand.TYPE_PREFIX + adaptable.getTopicPath().getSearchAction().orElse(null);
    }


    @Override
    public Adaptable constructAdaptable(final ThingSearchCommand command, final TopicPath.Channel channel) {

        final String namespace = String.join(",", command.getNamespaces()
                .orElse(Collections.singleton("_"))
                .toString()
                .replace(" ", "")
                .replace("[", "")
                .replace("]", ""));

        final SearchTopicPathBuilder topicPathBuilder =
                ProtocolFactory.newTopicPathBuilderFromNamespace(namespace).things().twin().search();


        final PayloadBuilder payloadBuilder = Payload.newBuilder(command.getResourcePath());

        final String commandName = command.getClass().getSimpleName().toLowerCase();
        if (commandName.startsWith("create")) {
            topicPathBuilder.subscribe();
            CreateSubscription createCommand = (CreateSubscription) command;
            if (createCommand.getSelectedFields().isPresent()) {
                payloadBuilder.withFields(command.getSelectedFields().orElseGet(null).toString());
            }
            if (createCommand.getFilter().isPresent() && createCommand.getOptions().isPresent()) {
                payloadBuilder.withValue(JsonObject.of(
                        String.format("{\"filter\": \"%s\", \"options\": \"%s\"}", createCommand.getFilter().get(),
                                String.join(",", createCommand.getOptions()
                                        .orElse(Collections.emptyList())
                                        .toString()
                                        .replace(" ", "")
                                        .replace("[", "")
                                        .replace("]", "")))));
            } else if (createCommand.getFilter().isPresent()) {
                payloadBuilder.withValue(JsonObject.of(
                        String.format("{\"filter\": \"%s\"}", createCommand.getFilter().get())));
            } else if (createCommand.getOptions().isPresent()) {
                payloadBuilder.withValue(
                        JsonObject.of(String.format("{\"options\": \"%s\"}", String.join(",", createCommand.getOptions()
                                .orElse(Collections.emptyList())
                                .toString()
                                .replace(" ", "")
                                .replace("[", "")
                                .replace("]", "")))));
            } else {
                payloadBuilder.withValue(null);
            }
        } else if (commandName.startsWith(TopicPath.SearchAction.CANCEL.toString())) {
            topicPathBuilder.cancel();
            CancelSubscription cancelCommand = (CancelSubscription) command;
            payloadBuilder.withValue(JsonObject.of(
                    String.format("{\"subscriptionId\": \"%s\"}", cancelCommand.getSubscriptionId())));

        } else if (commandName.startsWith(TopicPath.SearchAction.REQUEST.toString())) {
            topicPathBuilder.request();
            RequestSubscription requestCommand = (RequestSubscription) command;
            payloadBuilder.withValue(JsonObject.of(
                    String.format("{\"subscriptionId\": \"%s\", \"demand\": \"%s\"}",
                            requestCommand.getSubscriptionId(),
                            requestCommand.getDemand())));
        } else {
            throw UnknownCommandException.newBuilder(commandName).build();
        }


        return Adaptable.newBuilder((topicPathBuilder).build())
                .withPayload(payloadBuilder.build())
                .withHeaders(ProtocolFactory.newHeadersWithDittoContentType(command.getDittoHeaders()))
                .build();
    }
}
