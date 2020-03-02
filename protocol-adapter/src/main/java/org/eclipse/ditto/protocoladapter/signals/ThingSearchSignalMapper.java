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
package org.eclipse.ditto.protocoladapter.signals;

import java.util.Collections;
import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.Payload;
import org.eclipse.ditto.protocoladapter.PayloadBuilder;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.protocoladapter.SearchTopicPathBuilder;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.protocoladapter.TopicPathBuilder;
import org.eclipse.ditto.protocoladapter.UnknownCommandException;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.CancelSubscription;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.CreateSubscription;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.RequestSubscription;

/**
 * Base class of {@link SignalMapper}s for search (e.g. start, request, cancel stream).
 *
 * @param <T> the type of the command
 */
final class ThingSearchSignalMapper<T extends Signal<T>> extends AbstractSignalMapper<T> {

    @Override
    TopicPath getTopicPath(final T command, final TopicPath.Channel channel) {
        final TopicPathBuilder topicPathBuilder = getTopicPathBuilder((ThingSearchCommand) command);
        final SearchTopicPathBuilder searchTopicPathBuilder =
                fromTopicPathBuilderWithChannel(topicPathBuilder, channel);
        setTopicPathAction(searchTopicPathBuilder, (ThingSearchCommand) command, getSupportedActions());
        return searchTopicPathBuilder.build();
    }

    /**
     * @param command the command that is processed
     * @return a {@link TopicPathBuilder} for the given command.
     */
    public TopicPathBuilder getTopicPathBuilder(final ThingSearchCommand command) {
        return ProtocolFactory.newTopicPathBuilderFromNamespace(getNamespacesConcat(command)).things();
    }

    /**
     * @param command the command that is processed
     * @return a {@link TopicPathBuilder} for the given command.
     */
    private String getNamespacesConcat(final ThingSearchCommand command) {
        return String.join(",", command.getNamespaces()
                .orElse(Collections.singleton("_"))
                .toString()
                .replace(" ", "")
                .replace("[", "")
                .replace("]", ""));
    }

    /**
     * @return array of {@link org.eclipse.ditto.protocoladapter.TopicPath.Action}s the implementation supports.
     */
    public TopicPath.SearchAction[] getSupportedActions() {
        return new TopicPath.SearchAction[]{TopicPath.SearchAction.REQUEST,
                TopicPath.SearchAction.CANCEL, TopicPath.SearchAction.SUBSCRIBE};
    }

    private void setTopicPathAction(final SearchTopicPathBuilder builder, final ThingSearchCommand command,
            final TopicPath.SearchAction... supportedActions) {

        // e.g. message commands have no associated action
        if (supportedActions.length > 0) {
            final String searchCommandName = (command.getType().replace(command.getTypePrefix(),"").toLowerCase());
            setAction(builder, Stream.of(supportedActions)
                    .filter(action -> searchCommandName.startsWith(action.toString()))
                    .findAny()
                    .orElseThrow(() -> unknownCommandException(searchCommandName)));
        }
    }

    DittoRuntimeException unknownCommandException(final String commandName) {
        return UnknownCommandException.newBuilder(commandName).build();
    }

    private void setAction(final SearchTopicPathBuilder builder, final TopicPath.SearchAction searchAction) {
        switch (searchAction) {
            case SUBSCRIBE:
                builder.subscribe();
                break;
            case REQUEST:
                builder.request();
                break;
            case CANCEL:
                builder.cancel();
                break;
            default:
                throw unknownCommandException(searchAction.getName());
        }
    }

    private static SearchTopicPathBuilder fromTopicPathBuilderWithChannel(final TopicPathBuilder topicPathBuilder,
            final TopicPath.Channel channel) {
        final SearchTopicPathBuilder searchTopicPathBuilder;
        if (channel == TopicPath.Channel.TWIN) {

            searchTopicPathBuilder = topicPathBuilder.twin().search();
        } else {
            throw new IllegalArgumentException("Unknown or unsupported Channel '" + channel + "'");
        }
        return searchTopicPathBuilder;
    }

    @Override
    public Adaptable mapSignalToAdaptable(final T command, final TopicPath.Channel channel) {

        final PayloadBuilder payloadBuilder = Payload.newBuilder(command.getResourcePath());

        final String commandName = command.getClass().getSimpleName().toLowerCase();
        if (commandName.startsWith("create")) {
            CreateSubscription createCommand = (CreateSubscription) command;
            if (createCommand.getSelectedFields().isPresent()) {
                payloadBuilder.withFields(createCommand.getSelectedFields().orElse(JsonFieldSelector.newInstance("/")).toString());
            }
            if (createCommand.getFilter().isPresent() && createCommand.getOptions().isPresent()) {
                payloadBuilder.withValue(JsonObject.of(
                        String.format("{\"filter\": \"%s\", \"options\": \"%s\"}", createCommand.getFilter().orElse(null),
                                String.join(",", createCommand.getOptions()
                                        .orElse(Collections.emptyList())
                                        .toString()
                                        .replace(" ", "")
                                        .replace("[", "")
                                        .replace("]", "")))));
            } else if (createCommand.getFilter().isPresent()) {
                payloadBuilder.withValue(JsonObject.of(
                        String.format("{\"filter\": \"%s\"}", createCommand.getFilter().orElse(null))));
            } else if (createCommand.getOptions().isPresent()) {
                payloadBuilder.withValue(
                        JsonObject.of(
                                String.format("{\"options\": \"%s\"}", String.join(",", createCommand.getOptions()
                                        .orElse(Collections.emptyList())
                                        .toString()
                                        .replace(" ", "")
                                        .replace("[", "")
                                        .replace("]", "")))));
            } else {
                payloadBuilder.withValue(null);
            }
        } else if (commandName.startsWith(TopicPath.SearchAction.CANCEL.toString())) {
            CancelSubscription cancelCommand = (CancelSubscription) command;
            payloadBuilder.withValue(JsonObject.of(
                    String.format("{\"subscriptionId\": \"%s\"}", cancelCommand.getSubscriptionId())));

        } else if (commandName.startsWith(TopicPath.SearchAction.REQUEST.toString())) {
            RequestSubscription requestCommand = (RequestSubscription) command;
            payloadBuilder.withValue(JsonObject.of(
                    String.format("{\"subscriptionId\": \"%s\", \"demand\": \"%s\"}",
                            requestCommand.getSubscriptionId(),
                            requestCommand.getDemand())));
        } else {
            throw UnknownCommandException.newBuilder(commandName).build();
        }


        return Adaptable.newBuilder((getTopicPath(command, TopicPath.Channel.TWIN)))
                .withPayload(payloadBuilder.build())
                .withHeaders(ProtocolFactory.newHeadersWithDittoContentType(command.getDittoHeaders()))
                .build();
    }

}
