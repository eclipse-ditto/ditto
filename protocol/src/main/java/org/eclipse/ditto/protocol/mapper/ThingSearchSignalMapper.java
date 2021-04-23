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
package org.eclipse.ditto.protocol.mapper;

import static org.eclipse.ditto.protocol.TopicPath.ID_PLACEHOLDER;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.protocol.PayloadBuilder;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.SearchTopicPathBuilder;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.TopicPathBuilder;
import org.eclipse.ditto.protocol.UnknownCommandException;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.thingsearch.model.signals.commands.ThingSearchCommand;
import org.eclipse.ditto.thingsearch.model.signals.commands.subscription.CancelSubscription;
import org.eclipse.ditto.thingsearch.model.signals.commands.subscription.CreateSubscription;
import org.eclipse.ditto.thingsearch.model.signals.commands.subscription.RequestFromSubscription;

/**
 * Base class of {@link SignalMapper}s for search (e.g. start, request, cancel stream).
 *
 * @param <T> the type of the command
 */
final class ThingSearchSignalMapper<T extends Signal<?>> extends AbstractSignalMapper<T> {

    @Override
    TopicPath getTopicPath(final T command, final TopicPath.Channel channel) {
        final TopicPathBuilder topicPathBuilder = getTopicPathBuilder();
        final SearchTopicPathBuilder searchTopicPathBuilder =
                fromTopicPathBuilderWithChannel(topicPathBuilder, channel);
        setTopicPathAction(searchTopicPathBuilder, (ThingSearchCommand<?>) command, getSupportedActions());
        return searchTopicPathBuilder.build();
    }

    /**
     * @return a {@link TopicPathBuilder} for the given command.
     */
    private TopicPathBuilder getTopicPathBuilder() {
        return ProtocolFactory.newTopicPathBuilderFromNamespace(ID_PLACEHOLDER).things();
    }

    /**
     * @return array of {@link org.eclipse.ditto.protocol.TopicPath.Action}s the implementation supports.
     */
    public TopicPath.SearchAction[] getSupportedActions() {
        return new TopicPath.SearchAction[]{
                TopicPath.SearchAction.REQUEST,
                TopicPath.SearchAction.CANCEL,
                TopicPath.SearchAction.SUBSCRIBE
        };
    }

    private void setTopicPathAction(final SearchTopicPathBuilder builder, final ThingSearchCommand<?> command,
            final TopicPath.SearchAction... supportedActions) {

        // e.g. message commands have no associated action
        if (supportedActions.length > 0) {
            final String searchCommandName = command.getName();
            final TopicPath.SearchAction searchAction =
                    TopicPath.SearchAction.forName(searchCommandName)
                            .orElseThrow(() -> unknownCommandException(searchCommandName));
            setAction(builder, searchAction);
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
    void enhancePayloadBuilder(final T command, final PayloadBuilder payloadBuilder) {
        final JsonObjectBuilder payloadContentBuilder = JsonFactory.newObjectBuilder();

        if (command instanceof CreateSubscription) {
            final CreateSubscription createCommand = (CreateSubscription) command;
            createCommand.getSelectedFields().ifPresent(payloadBuilder::withFields);
            createCommand.getFilter()
                    .ifPresent(filter -> payloadContentBuilder.set(CreateSubscription.JsonFields.FILTER, filter));
            createCommand.getOptions()
                    .ifPresent(options -> payloadContentBuilder.set(CreateSubscription.JsonFields.OPTIONS, options));
            createCommand.getNamespaces()
                    .ifPresent(namespaces -> {
                        final JsonArray namespacesArray = namespaces.stream()
                                .map(JsonValue::of)
                                .collect(JsonCollectors.valuesToArray());
                        payloadContentBuilder.set(CreateSubscription.JsonFields.NAMESPACES, namespacesArray);
                    });
        } else if (command instanceof CancelSubscription) {
            final CancelSubscription cancelCommand = (CancelSubscription) command;
            payloadContentBuilder.set(CancelSubscription.JsonFields.SUBSCRIPTION_ID, cancelCommand.getSubscriptionId());

        } else if (command instanceof RequestFromSubscription) {
            final RequestFromSubscription requestCommand = (RequestFromSubscription) command;
            payloadContentBuilder
                    .set(RequestFromSubscription.JsonFields.SUBSCRIPTION_ID, requestCommand.getSubscriptionId())
                    .set(RequestFromSubscription.JsonFields.DEMAND, requestCommand.getDemand());
        } else {
            throw UnknownCommandException.newBuilder(command.getClass().toString()).build();
        }
        payloadBuilder.withValue(payloadContentBuilder.build());
    }


}
