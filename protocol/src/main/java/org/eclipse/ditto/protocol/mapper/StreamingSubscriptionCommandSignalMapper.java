/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.signals.WithStreamingSubscriptionId;
import org.eclipse.ditto.base.model.signals.commands.streaming.CancelStreamingSubscription;
import org.eclipse.ditto.base.model.signals.commands.streaming.RequestFromStreamingSubscription;
import org.eclipse.ditto.base.model.signals.commands.streaming.StreamingSubscriptionCommand;
import org.eclipse.ditto.base.model.signals.commands.streaming.SubscribeForPersistedEvents;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.protocol.PayloadBuilder;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.StreamingTopicPathBuilder;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.TopicPathBuilder;
import org.eclipse.ditto.protocol.UnknownCommandException;

/**
 *  Signal mapper implementation for {@link StreamingSubscriptionCommand}s.
 *
 * @param <T> the type of the command
 */
final class StreamingSubscriptionCommandSignalMapper<T extends StreamingSubscriptionCommand<?>>
        extends AbstractSignalMapper<T> {

    @Override
    TopicPath getTopicPath(final T command, final TopicPath.Channel channel) {
        final TopicPathBuilder topicPathBuilder = getTopicPathBuilder(command);
        final StreamingTopicPathBuilder streamingTopicPathBuilder =
                fromTopicPathBuilderWithChannel(topicPathBuilder, channel);
        setTopicPathAction(streamingTopicPathBuilder, command, getSupportedActions());
        return streamingTopicPathBuilder.build();
    }

    /**
     * @return array of {@link org.eclipse.ditto.protocol.TopicPath.Action}s the implementation supports.
     */
    public TopicPath.StreamingAction[] getSupportedActions() {
        return new TopicPath.StreamingAction[]{
                TopicPath.StreamingAction.REQUEST,
                TopicPath.StreamingAction.CANCEL,
                TopicPath.StreamingAction.SUBSCRIBE_FOR_PERSISTED_EVENTS
        };
    }

    @Override
    void enhancePayloadBuilder(final T command, final PayloadBuilder payloadBuilder) {

        final JsonObjectBuilder payloadContentBuilder = JsonFactory.newObjectBuilder();
        if (command instanceof SubscribeForPersistedEvents) {
            final SubscribeForPersistedEvents subscribeCommand = (SubscribeForPersistedEvents) command;
            payloadContentBuilder
                    .set(SubscribeForPersistedEvents.JsonFields.JSON_FROM_HISTORICAL_REVISION,
                            subscribeCommand.getFromHistoricalRevision())
                    .set(SubscribeForPersistedEvents.JsonFields.JSON_TO_HISTORICAL_REVISION,
                            subscribeCommand.getToHistoricalRevision());
            subscribeCommand.getFromHistoricalTimestamp().ifPresent(fromTs ->
                    payloadContentBuilder.set(SubscribeForPersistedEvents.JsonFields.JSON_FROM_HISTORICAL_TIMESTAMP,
                            fromTs.toString()));
            subscribeCommand.getToHistoricalTimestamp().ifPresent(toTs ->
                    payloadContentBuilder.set(SubscribeForPersistedEvents.JsonFields.JSON_TO_HISTORICAL_TIMESTAMP,
                            toTs.toString()));
        } else if (command instanceof CancelStreamingSubscription) {
            final CancelStreamingSubscription cancelCommand = (CancelStreamingSubscription) command;
            payloadContentBuilder
                    .set(WithStreamingSubscriptionId.JsonFields.SUBSCRIPTION_ID, cancelCommand.getSubscriptionId());
        } else if (command instanceof RequestFromStreamingSubscription) {
            final RequestFromStreamingSubscription requestCommand = (RequestFromStreamingSubscription) command;
            payloadContentBuilder
                    .set(WithStreamingSubscriptionId.JsonFields.SUBSCRIPTION_ID, requestCommand.getSubscriptionId())
                    .set(RequestFromStreamingSubscription.JsonFields.DEMAND, requestCommand.getDemand());
        } else {
            throw UnknownCommandException.newBuilder(command.getClass().toString()).build();
        }
        payloadBuilder.withValue(payloadContentBuilder.build());
    }

    private static StreamingTopicPathBuilder fromTopicPathBuilderWithChannel(final TopicPathBuilder topicPathBuilder,
            final TopicPath.Channel channel) {

        if (channel == TopicPath.Channel.TWIN) {
            return topicPathBuilder.twin().streaming();
        } else if (channel == TopicPath.Channel.NONE) {
            return topicPathBuilder.none().streaming();
        } else {
            throw new IllegalArgumentException("Unknown or unsupported Channel '" + channel + "'");
        }
    }

    private TopicPathBuilder getTopicPathBuilder(final StreamingSubscriptionCommand<?> command) {
        return ProtocolFactory.newTopicPathBuilder(command.getEntityId());
    }

    private void setTopicPathAction(final StreamingTopicPathBuilder builder, final T command,
            final TopicPath.StreamingAction... supportedActions) {

        if (supportedActions.length > 0) {
            final String streamingCommandName = command.getName();
            final TopicPath.StreamingAction streamingAction =
                    TopicPath.StreamingAction.forName(streamingCommandName)
                            .orElseThrow(() -> unknownCommandException(streamingCommandName));
            setAction(builder, streamingAction);
        }
    }

    DittoRuntimeException unknownCommandException(final String commandName) {
        return UnknownCommandException.newBuilder(commandName).build();
    }

    private void setAction(final StreamingTopicPathBuilder builder, final TopicPath.StreamingAction streamingAction) {
        switch (streamingAction) {
            case SUBSCRIBE_FOR_PERSISTED_EVENTS:
                builder.subscribe(SubscribeForPersistedEvents.NAME);
                break;
            case REQUEST:
                builder.request();
                break;
            case CANCEL:
                builder.cancel();
                break;
            default:
                throw unknownCommandException(streamingAction.getName());
        }
    }
}
