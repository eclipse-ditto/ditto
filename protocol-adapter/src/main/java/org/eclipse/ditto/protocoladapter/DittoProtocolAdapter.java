/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.protocoladapter;

import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.model.messages.MessageHeaderDefinition;
import org.eclipse.ditto.signals.base.AbstractErrorRegistry;
import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.base.JsonTypeNotParsableException;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.MessageCommandResponse;
import org.eclipse.ditto.signals.commands.messages.MessageErrorRegistry;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingErrorRegistry;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommandResponse;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommandResponse;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base implementation of a protocol adapter.
 */
public class DittoProtocolAdapter implements ProtocolAdapter {

    protected final Logger logger;

    private final MessageCommandAdapter messageCommandAdapter;
    private final MessageCommandResponseAdapter messageCommandResponseAdapter;
    private final ThingModifyCommandAdapter thingModifyCommandAdapter;
    private final ThingModifyCommandResponseAdapter thingModifyCommandResponseAdapter;
    private final ThingQueryCommandAdapter thingQueryCommandAdapter;
    private final ThingQueryCommandResponseAdapter thingQueryCommandResponseAdapter;
    private final ThingEventAdapter thingEventAdapter;

    private final AbstractErrorRegistry<DittoRuntimeException> errorRegistry;
    private final HeaderTranslator headerTranslator;

    protected DittoProtocolAdapter(final AbstractErrorRegistry<DittoRuntimeException> errorRegistry,
            final HeaderTranslator headerTranslator) {
        this.logger = LoggerFactory.getLogger(getClass());
        this.errorRegistry = errorRegistry;
        this.messageCommandAdapter = MessageCommandAdapter.of(headerTranslator);
        this.messageCommandResponseAdapter = MessageCommandResponseAdapter.of(headerTranslator);
        this.thingModifyCommandAdapter = ThingModifyCommandAdapter.of(headerTranslator);
        this.thingModifyCommandResponseAdapter = ThingModifyCommandResponseAdapter.of(headerTranslator);
        this.thingQueryCommandAdapter = ThingQueryCommandAdapter.of(headerTranslator);
        this.thingQueryCommandResponseAdapter = ThingQueryCommandResponseAdapter.of(headerTranslator);
        this.thingEventAdapter = ThingEventAdapter.of(headerTranslator);
        this.headerTranslator = headerTranslator;
        logger.info("Created ProtocolAdapter with headerTranslator <{}>.", headerTranslator);
    }

    /**
     * Creates a new {@code DittoProtocolAdapter} instance with the given header translator.
     *
     * @param headerTranslator translator between external and Ditto headers.
     */
    public static DittoProtocolAdapter of(final HeaderTranslator headerTranslator) {
        return new DittoProtocolAdapter(ProtocolAdapterErrorRegistry.newInstance(), requireNonNull(headerTranslator));
    }

    /**
     * Creates a new {@code DittoProtocolAdapter} instance.
     *
     * @return the instance.
     */
    public static DittoProtocolAdapter newInstance() {
        return new DittoProtocolAdapter(ProtocolAdapterErrorRegistry.newInstance(), headerTranslator());
    }

    /**
     * Create a default header translator for this protocol adapter.
     *
     * @return the default header translator.
     */
    public static HeaderTranslator headerTranslator() {
        return HeaderTranslator.of(DittoHeaderDefinition.values(), MessageHeaderDefinition.values());
    }

    @Override
    public Signal<?> fromAdaptable(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();

        if (TopicPath.Group.THINGS.equals(topicPath.getGroup())) { // /things
            final TopicPath.Channel channel = topicPath.getChannel();

            if (channel.equals(TopicPath.Channel.LIVE)) { // /things/live
                return fromLiveAdaptable(adaptable);
            } else if (channel.equals(TopicPath.Channel.TWIN)) { // /things/twin
                return fromTwinAdaptable(adaptable);
            }
        }

        throw UnknownTopicPathException.newBuilder(topicPath).build();
    }

    @Override
    public Adaptable toAdaptable(final Signal<?> signal) {
        final boolean isLive = isLiveSignal(signal);
        final TopicPath.Channel channel = isLive ? TopicPath.Channel.LIVE : TopicPath.Channel.TWIN;
        if (signal instanceof MessageCommand) {
            return toAdaptable((MessageCommand<?, ?>) signal);
        } else if (signal instanceof MessageCommandResponse) {
            return toAdaptable((MessageCommandResponse<?, ?>) signal);
        } else if (signal instanceof Command) {
            return toAdaptable((Command<?>) signal, channel);
        } else if (signal instanceof CommandResponse) {
            return toAdaptable((CommandResponse<?>) signal, channel);
        } else if (signal instanceof Event) {
            return toAdaptable((Event<?>) signal, channel);
        }
        throw UnknownSignalException.newBuilder(signal.getName()).dittoHeaders(signal.getDittoHeaders()).build();
    }

    private static boolean isLiveSignal(final Signal<?> signal) {
        return signal.getDittoHeaders().getChannel().filter(TopicPath.Channel.LIVE.getName()::equals).isPresent();
    }

    @Override
    public Adaptable toAdaptable(final CommandResponse<?> commandResponse) {
        return toAdaptable(commandResponse, TopicPath.Channel.TWIN);
    }

    @Override
    public Adaptable toAdaptable(final CommandResponse<?> commandResponse, final TopicPath.Channel channel) {
        if (commandResponse instanceof MessageCommandResponse && channel == TopicPath.Channel.LIVE) {
            return toAdaptable((MessageCommandResponse) commandResponse);
        } else if (commandResponse instanceof ThingCommandResponse) {
            return toAdaptable((ThingCommandResponse) commandResponse, channel);
        } else {
            throw UnknownCommandResponseException.newBuilder(commandResponse.getName()).build();
        }
    }

    @Override
    public Adaptable toAdaptable(final ThingCommandResponse<?> thingCommandResponse) {
        return toAdaptable(thingCommandResponse, TopicPath.Channel.TWIN);
    }

    @Override
    public Adaptable toAdaptable(final ThingCommandResponse<?> thingCommandResponse, final TopicPath.Channel channel) {
        if (thingCommandResponse instanceof ThingQueryCommandResponse) {
            return toAdaptable((ThingQueryCommandResponse) thingCommandResponse, channel);
        } else if (thingCommandResponse instanceof ThingModifyCommandResponse) {
            return toAdaptable((ThingModifyCommandResponse) thingCommandResponse, channel);
        } else if (thingCommandResponse instanceof ThingErrorResponse) {
            return toAdaptable((ThingErrorResponse) thingCommandResponse, channel);
        } else {
            throw UnknownCommandResponseException.newBuilder(thingCommandResponse.getName()).build();
        }
    }

    @Override
    public Adaptable toAdaptable(final MessageCommand<?, ?> messageCommand) {
        return messageCommandAdapter.toAdaptable(messageCommand, TopicPath.Channel.LIVE);
    }

    @Override
    public Adaptable toAdaptable(final MessageCommandResponse<?, ?> messageCommandResponse) {
        return messageCommandResponseAdapter.toAdaptable(messageCommandResponse, TopicPath.Channel.LIVE);
    }

    @Override
    public Adaptable toAdaptable(final Command<?> command) {
        return toAdaptable(command, TopicPath.Channel.TWIN);
    }

    @Override
    public Adaptable toAdaptable(final Command<?> command, final TopicPath.Channel channel) {
        if (command instanceof MessageCommand && channel == TopicPath.Channel.LIVE) {
            return toAdaptable((MessageCommand) command);
        } else if (command instanceof ThingModifyCommand) {
            return toAdaptable((ThingModifyCommand) command, channel);
        } else if (command instanceof ThingQueryCommand) {
            return toAdaptable((ThingQueryCommand) command, channel);
        } else {
            throw UnknownCommandException.newBuilder(command.getName()).build();
        }
    }

    @Override
    public Adaptable toAdaptable(final ThingModifyCommand<?> thingModifyCommand) {
        return toAdaptable(thingModifyCommand, TopicPath.Channel.TWIN);
    }

    @Override
    public Adaptable toAdaptable(final ThingModifyCommand<?> thingModifyCommand, final TopicPath.Channel channel) {
        return thingModifyCommandAdapter.toAdaptable(thingModifyCommand, channel);
    }

    @Override
    public Adaptable toAdaptable(final ThingModifyCommandResponse<?> thingModifyCommandResponse) {
        return toAdaptable(thingModifyCommandResponse, TopicPath.Channel.TWIN);
    }

    @Override
    public Adaptable toAdaptable(final ThingModifyCommandResponse<?> thingModifyCommandResponse,
            final TopicPath.Channel channel) {
        return thingModifyCommandResponseAdapter.toAdaptable(thingModifyCommandResponse, channel);
    }

    @Override
    public Adaptable toAdaptable(final ThingQueryCommand<?> thingQueryCommand) {
        return toAdaptable(thingQueryCommand, TopicPath.Channel.TWIN);
    }

    @Override
    public Adaptable toAdaptable(final ThingQueryCommand<?> thingQueryCommand, final TopicPath.Channel channel) {
        return thingQueryCommandAdapter.toAdaptable(thingQueryCommand, channel);
    }

    @Override
    public Adaptable toAdaptable(final ThingQueryCommandResponse<?> thingQueryCommandResponse) {
        return toAdaptable(thingQueryCommandResponse, TopicPath.Channel.TWIN);
    }

    @Override
    public Adaptable toAdaptable(final ThingQueryCommandResponse<?> thingQueryCommandResponse,
            final TopicPath.Channel channel) {
        return thingQueryCommandResponseAdapter.toAdaptable(thingQueryCommandResponse, channel);
    }

    @Override
    public Adaptable toAdaptable(final ThingErrorResponse thingErrorResponse, final TopicPath.Channel channel) {
        final Payload payload = Payload.newBuilder(thingErrorResponse.getResourcePath()) //
                .withStatus(thingErrorResponse.getStatusCode()) //
                .withValue(thingErrorResponse.toJson(thingErrorResponse.getImplementedSchemaVersion()) //
                        .getValue(CommandResponse.JsonFields.PAYLOAD)
                        .orElse(JsonFactory.nullObject())) // only use the error payload
                .build();

        final TopicPathBuilder topicPathBuilder = ProtocolFactory.newTopicPathBuilder(thingErrorResponse.getId());
        final TopicPathBuildable topicPathBuildable;
        if (channel == TopicPath.Channel.TWIN) {
            topicPathBuildable = topicPathBuilder.twin().errors();
        } else if (channel == TopicPath.Channel.LIVE) {
            topicPathBuildable = topicPathBuilder.live().errors();
        } else {
            throw new IllegalArgumentException("Unknown Channel '" + channel + "'");
        }

        final DittoHeaders responseHeaders =
                ProtocolFactory.newHeadersWithDittoContentType(thingErrorResponse.getDittoHeaders());

        return Adaptable.newBuilder(topicPathBuildable.build())
                .withPayload(payload)
                .withHeaders(DittoHeaders.of(headerTranslator.toExternalHeaders(responseHeaders)))
                .build();
    }

    @Override
    public Adaptable toAdaptable(final Event<?> event) {
        return toAdaptable(event, TopicPath.Channel.TWIN);
    }

    @Override
    public Adaptable toAdaptable(final Event<?> event, final TopicPath.Channel channel) {
        if (event instanceof ThingEvent) {
            return toAdaptable((ThingEvent) event, channel);
        } else {
            throw UnknownEventException.newBuilder(event.getName()).build();
        }
    }

    @Override
    public Adaptable toAdaptable(final ThingEvent<?> thingEvent) {
        return toAdaptable(thingEvent, TopicPath.Channel.TWIN);
    }

    @Override
    public Adaptable toAdaptable(final ThingEvent<?> thingEvent, final TopicPath.Channel channel) {
        return thingEventAdapter.toAdaptable(thingEvent, channel);
    }

    private Signal<?> fromLiveAdaptable(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();

        final Signal<?> liveSignal;
        if (TopicPath.Criterion.MESSAGES.equals(topicPath.getCriterion())) { // /things/live/messages
            final boolean isResponse = adaptable.getPayload().getStatus().isPresent();
            if (isResponse) {
                liveSignal = messageCommandResponseAdapter.fromAdaptable(adaptable);
            } else {
                liveSignal = messageCommandAdapter.fromAdaptable(adaptable);
            }
        } else {
            liveSignal = signalFromAdaptable(adaptable, topicPath); // /things/live/(commands|events)
        }

        if (liveSignal != null) {
            final DittoHeadersBuilder enhancedHeadersBuilder = liveSignal.getDittoHeaders()
                    .toBuilder()
                    .channel(TopicPath.Channel.LIVE.getName());

            return liveSignal.setDittoHeaders(enhancedHeadersBuilder.build());
        } else {
            throw UnknownTopicPathException.newBuilder(topicPath).build();
        }
    }

    private Signal<?> fromTwinAdaptable(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();

        final Signal<?> signal =
                signalFromAdaptable(adaptable, topicPath); // /things/twin/(commands|events)
        if (signal != null) {
            return signal;
        } else {
            throw UnknownTopicPathException.newBuilder(topicPath).build();
        }
    }

    @Nullable
    private Signal<?> signalFromAdaptable(final Adaptable adaptable, final TopicPath topicPath) {
        if (TopicPath.Criterion.COMMANDS.equals(topicPath.getCriterion())) {

            if (adaptable.getPayload().getStatus().isPresent()) {
                // this was a command response:
                return processCommandResponseSignalFromAdaptable(adaptable, topicPath);
            } else if (TopicPath.Action.RETRIEVE.equals(topicPath.getAction().orElse(null))) {
                return thingQueryCommandAdapter.fromAdaptable(adaptable);
            } else {
                return thingModifyCommandAdapter.fromAdaptable(adaptable);
            }

        } else if (TopicPath.Criterion.EVENTS.equals(topicPath.getCriterion())) {
            return thingEventAdapter.fromAdaptable(adaptable);
        } else if (TopicPath.Criterion.ERRORS.equals(topicPath.getCriterion())) {
            return thingErrorResponseFromAdaptable(adaptable);
        }
        return null;
    }

    private Signal<?> processCommandResponseSignalFromAdaptable(final Adaptable adaptable, final TopicPath topicPath) {
        final Optional<HttpStatusCode> status = adaptable.getPayload().getStatus();
        final boolean isErrorResponse =
                status.isPresent() && status.get().toInt() >= HttpStatusCode.BAD_REQUEST.toInt();

        if (TopicPath.Action.RETRIEVE.equals(topicPath.getAction().orElse(null))) {
            return isErrorResponse ? thingErrorResponseFromAdaptable(adaptable) :
                    thingQueryCommandResponseAdapter.fromAdaptable(adaptable);
        } else {
            return isErrorResponse ? thingErrorResponseFromAdaptable(adaptable) :
                    thingModifyCommandResponseAdapter.fromAdaptable(adaptable);
        }
    }

    /**
     * Creates a {@code ThingErrorResponse} from the given {@code adaptable}.
     *
     * @param adaptable the adaptable to convert.
     * @return the ThingErrorResponse.
     */
    private ThingErrorResponse thingErrorResponseFromAdaptable(final Adaptable adaptable) {
        final DittoHeaders dittoHeaders =
                headerTranslator.fromExternalHeaders(adaptable.getHeaders().orElse(DittoHeaders.empty()));
        final TopicPath topicPath = adaptable.getTopicPath();

        final DittoRuntimeException dittoRuntimeException = adaptable.getPayload()
                .getValue()
                .map(JsonValue::asObject)
                .map(jsonObject -> {
                    try {
                        return errorRegistry.parse(jsonObject, dittoHeaders);
                    } catch (final JsonTypeNotParsableException e) {
                        return DittoRuntimeException.fromUnknownErrorJson(jsonObject, dittoHeaders)
                                .orElseThrow(() -> e);
                    }
                })
                .orElseThrow(() -> new JsonMissingFieldException(ThingCommandResponse.JsonFields.PAYLOAD));

        final String thingId = topicPath.getNamespace() + ":" + topicPath.getId();
        return ThingErrorResponse.of(thingId, dittoRuntimeException, dittoRuntimeException.getDittoHeaders());
    }

    /**
     * ErrorRegistry for DittoProtocolAdapter.
     */
    public static final class ProtocolAdapterErrorRegistry extends AbstractErrorRegistry<DittoRuntimeException> {

        private ProtocolAdapterErrorRegistry(final Map<String, JsonParsable<DittoRuntimeException>> parseStrategies) {
            super(parseStrategies);
        }

        /**
         * Returns a new {@code ProtocolAdapterErrorRegistry}.
         *
         * @return the error registry.
         */
        public static ProtocolAdapterErrorRegistry newInstance() {
            final Map<String, JsonParsable<DittoRuntimeException>> parseStrategies = new HashMap<>();

            final ThingErrorRegistry thingErrorRegistry = ThingErrorRegistry.newInstance();
            thingErrorRegistry.getTypes()
                    .forEach(type -> parseStrategies.put(type, thingErrorRegistry));
            final MessageErrorRegistry messageErrorRegistry = MessageErrorRegistry.newInstance();
            messageErrorRegistry.getTypes()
                    .forEach(type -> parseStrategies.put(type, messageErrorRegistry));

            // Protocol Adapter exceptions:
            parseStrategies.put(UnknownSignalException.ERROR_CODE, UnknownSignalException::fromJson);
            parseStrategies.put(UnknownCommandException.ERROR_CODE, UnknownCommandException::fromJson);
            parseStrategies.put(UnknownCommandResponseException.ERROR_CODE, UnknownCommandResponseException::fromJson);
            parseStrategies.put(UnknownEventException.ERROR_CODE, UnknownEventException::fromJson);
            parseStrategies.put(UnknownPathException.ERROR_CODE, UnknownPathException::fromJson);
            parseStrategies.put(UnknownTopicPathException.ERROR_CODE, UnknownTopicPathException::fromJson);

            return new ProtocolAdapterErrorRegistry(parseStrategies);
        }
    }
}
