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

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.MessageCommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommandResponse;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommandResponse;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * Contract for the Ditto Protocol Adapter library. Provides methods for mapping {@link Command} and {@link Event}
 * instances to an {@link Adaptable}.
 */
public final class DittoProtocolAdapter {

    /**
     * Placeholder for Thing topics that do not belong to a specific Id.
     */
    public static final String ID_PLACEHOLDER = "_";

    private final MessageCommandAdapter messageCommandAdapter;
    private final MessageCommandResponseAdapter messageCommandResponseAdapter;
    private final ThingModifyCommandAdapter thingModifyCommandAdapter;
    private final ThingModifyCommandResponseAdapter thingModifyCommandResponseAdapter;
    private final ThingQueryCommandAdapter thingQueryCommandAdapter;
    private final ThingQueryCommandResponseAdapter thingQueryCommandResponseAdapter;
    private final ThingEventAdapter thingEventAdapter;

    private DittoProtocolAdapter() {
        this.messageCommandAdapter = MessageCommandAdapter.newInstance();
        this.messageCommandResponseAdapter = MessageCommandResponseAdapter.newInstance();
        this.thingModifyCommandAdapter = ThingModifyCommandAdapter.newInstance();
        this.thingModifyCommandResponseAdapter = ThingModifyCommandResponseAdapter.newInstance();
        this.thingQueryCommandAdapter = ThingQueryCommandAdapter.newInstance();
        this.thingQueryCommandResponseAdapter = ThingQueryCommandResponseAdapter.newInstance();
        this.thingEventAdapter = ThingEventAdapter.newInstance();
    }

    public static DittoProtocolAdapter newInstance() {
        return new DittoProtocolAdapter();
    }


    /**
     * Returns a new {@code AdaptableBuilder} for the specified {@code topicPath}.
     *
     * @param topicPath the topic path.
     * @return the builder.
     */
    public static AdaptableBuilder newAdaptableBuilder(final TopicPath topicPath) {
        return ImmutableAdaptableBuilder.of(topicPath);
    }

    /**
     * Returns a new {@code AdaptableBuilder} for the existing {@code existingAdaptable}.
     *
     * @param existingAdaptable the existingAdaptable to initialize the AdaptableBuilder with.
     * @return the builder.
     */
    public static AdaptableBuilder newAdaptableBuilder(final Adaptable existingAdaptable) {
        return newAdaptableBuilder(existingAdaptable, existingAdaptable.getTopicPath());
    }

    /**
     * Returns a new {@code AdaptableBuilder} for the existing {@code existingAdaptable} and a specific
     * {@code overwriteTopicPath} to overwrite the one in {@code existingAdaptable}.
     *
     * @param existingAdaptable the existingAdaptable to initialize the AdaptableBuilder with.
     * @param overwriteTopicPath the specific {@code TopicPath} to set as overwrite.
     * @return the builder.
     */
    public static AdaptableBuilder newAdaptableBuilder(final Adaptable existingAdaptable,
            final TopicPath overwriteTopicPath) {
        return ImmutableAdaptableBuilder.of(overwriteTopicPath).withPayload(existingAdaptable.getPayload())
                .withHeaders(existingAdaptable.getHeaders().orElse(null));
    }

    /**
     * Returns an empty {@code TopicPath}.
     *
     * @return the topic path.
     */
    public static TopicPath emptyTopicPath() {
        return ImmutableTopicPathBuilder.empty();
    }

    /**
     * Returns a new {@code TopicPath} for the specified {@code path}.
     *
     * @param path the path.
     * @return the builder.
     * @throws NullPointerException if {@code path} is {@code null}.
     * @throws UnknownTopicPathException if {@code path} is no valid {@code TopicPath}.
     */
    @SuppressWarnings({"squid:S1166"})
    public static TopicPath newTopicPath(final String path) {
        final String[] parts = path.split("/");

        try {
            final String namespace = parts[0];
            final String id = parts[1];
            final TopicPath.Group group =
                    TopicPath.Group.forName(parts[2])
                            .orElseThrow(() -> UnknownTopicPathException.newBuilder(path).build());
            final TopicPath.Channel channel =
                    TopicPath.Channel.forName(parts[3])
                            .orElseThrow(() -> UnknownTopicPathException.newBuilder(path).build());
            final TopicPath.Criterion criterion =
                    TopicPath.Criterion.forName(parts[4])
                            .orElseThrow(() -> UnknownTopicPathException.newBuilder(path).build());

            switch (criterion) {
                case COMMANDS:
                case EVENTS:
                    // commands and events Path always contain an ID:
                    final TopicPath.Action action =
                            TopicPath.Action.forName(parts[5])
                                    .orElseThrow(() -> UnknownTopicPathException.newBuilder(path).build());
                    return ImmutableTopicPath.of(namespace, id, group, channel, criterion, action);
                case ERRORS:
                    // errors Path does neither contain an "action":
                    return ImmutableTopicPath.of(namespace, id, group, channel, criterion);
                case MESSAGES:
                    // messages Path always contain a subject:
                    final String[] subjectParts = Arrays.copyOfRange(parts, 5, parts.length);
                    final String subject = String.join("/", (CharSequence[]) subjectParts);
                    return ImmutableTopicPath.of(namespace, id, group, channel, criterion, subject);
                default:
                    throw UnknownTopicPathException.newBuilder(path).build();
            }
        } catch (final ArrayIndexOutOfBoundsException e) {
            throw UnknownTopicPathException.newBuilder(path).build();
        }
    }

    /**
     * Returns a new {@code TopicPathBuilder} for the specified {@code thingId}. The {@code namespace} and {@code id}
     * part of the {@code TopicPath} will pe parsed from the {@code thingId} and set in the builder.
     *
     * @param thingId the id.
     * @return the builder.
     * @throws NullPointerException if {@code thingId} is {@code null}.
     * @throws org.eclipse.ditto.model.things.ThingIdInvalidException if {@code thingId} is not in the expected format.
     */
    public static TopicPathBuilder newTopicPathBuilder(final String thingId) {
        return ImmutableTopicPathBuilder.of(thingId).things();
    }

    /**
     * Returns a new {@code TopicPathBuilder}. The {@code id} part of the {@code TopicPath} is set to
     * {@link DittoProtocolAdapter#ID_PLACEHOLDER}.
     *
     * @param namespace the namespace.
     * @return the builder.
     * @throws NullPointerException if {@code namespace} is {@code null}.
     */
    public static TopicPathBuilder newTopicPathBuilderFromNamespace(final String namespace) {
        return ImmutableTopicPathBuilder.of(namespace, ID_PLACEHOLDER).things();
    }


    /**
     * Returns a new {@code Payload} from the specified {@code jsonString}.
     *
     * @param jsonString the JSON string.
     * @return the payload.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonString} does not contain a JSON object.
     */
    public static Payload newPayload(final String jsonString) {
        return newPayload(JsonFactory.newObject(jsonString));
    }

    /**
     * Returns a new {@code Payload} from the specified {@code jsonObject}.
     *
     * @param jsonObject the JSON object.
     * @return the payload.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} does not have the expected format.
     */
    public static Payload newPayload(final JsonObject jsonObject) {
        return ImmutablePayload.fromJson(jsonObject);
    }

    /**
     * Returns a new {@code PayloadBuilder} without a path.
     *
     * @return the builder.
     */
    public static PayloadBuilder newPayloadBuilder() {
        return ImmutablePayloadBuilder.of();
    }

    /**
     * Returns a new {@code PayloadBuilder} for the specified {@code path}.
     *
     * @param path the path.
     * @return the builder.
     */
    public static PayloadBuilder newPayloadBuilder(final JsonPointer path) {
        return ImmutablePayloadBuilder.of(path);
    }


    /**
     * Returns new empty {@code Headers}.
     *
     * @return the headers.
     */
    public static DittoHeaders emptyHeaders() {
        return DittoHeaders.empty();
    }

    /**
     * Returns new {@code Headers} for the specified {@code headers} map.
     *
     * @param headers the headers map.
     * @return the headers.
     */
    public static DittoHeaders newHeaders(final Map<String, String> headers) {
        return DittoHeaders.of(headers);
    }

    /**
     * Returns new {@code Headers} for the specified {@code headers} map.
     *
     * @param headers the headers map.
     * @return the headers.
     */
    public static DittoHeaders newHeaders(final Collection<Map.Entry<String, String>> headers) {
        return DittoHeaders.of(headers.stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    /**
     * Returns new {@code Headers} for the specified {@code headersAsJson}.
     *
     * @param headersAsJson the headers as JSON.
     * @return the headers.
     */
    public static DittoHeaders newHeaders(final JsonObject headersAsJson) {
        return DittoHeaders.newBuilder(headersAsJson).build();
    }

    /**
     * Wraps the passed in {@code adaptable} to a {@link JsonifiableAdaptable} which has a JSON representation.
     *
     * @param adaptable the already created {@code Adaptable} to wrap.
     * @return the JsonifiableAdaptable.
     */
    public static JsonifiableAdaptable wrapAsJsonifiableAdaptable(final Adaptable adaptable) {
        return ImmutableJsonifiableAdaptable.of(adaptable);
    }

    /**
     * Converts the passed in {@code adaptableAsJson} to a {@link JsonifiableAdaptable}.
     *
     * @param adaptableAsJson the adaptable as JsonObject.
     * @return the JsonifiableAdaptable.
     */
    public static JsonifiableAdaptable jsonifiableAdaptableFromJson(final JsonObject adaptableAsJson) {
        return ImmutableJsonifiableAdaptable.fromJson(adaptableAsJson);
    }


    /**
     * Maps the given {@code adaptable} to the corresponding {@code Signal}, which can be a {@code Command},
     * {@code CommandResponse} or an {@code Event}.
     *
     * @param adaptable the adaptable.
     * @return the Signal.
     */
    public Signal<?> fromAdaptable(final Adaptable adaptable) {
        final TopicPath topicPath = adaptable.getTopicPath();

        if (TopicPath.Group.THINGS.equals(topicPath.getGroup())) { // /things
            final TopicPath.Channel channel = topicPath.getChannel();

            if (channel.equals(TopicPath.Channel.LIVE)) { // /things/live

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
                }
            } else if (channel.equals(TopicPath.Channel.TWIN)) { // /things/twin

                final Signal<?> signal =
                        signalFromAdaptable(adaptable, topicPath); // /things/twin/(commands|events)
                if (signal != null) {
                    return signal;
                }
            }
        }

        throw UnknownTopicPathException.newBuilder(topicPath).build();
    }

    private Signal<?> signalFromAdaptable(final Adaptable adaptable, final TopicPath topicPath) {
        if (TopicPath.Criterion.COMMANDS.equals(topicPath.getCriterion())) {
            final boolean isResponse = adaptable.getPayload().getStatus().isPresent();

            if (TopicPath.Action.RETRIEVE.equals(topicPath.getAction().orElse(null))) {
                return isResponse ? thingQueryCommandResponseAdapter.fromAdaptable(adaptable) :
                        thingQueryCommandAdapter.fromAdaptable(adaptable);
            } else {
                return isResponse ? thingModifyCommandResponseAdapter.fromAdaptable(adaptable) :
                        thingModifyCommandAdapter.fromAdaptable(adaptable);
            }
        } else if (TopicPath.Criterion.EVENTS.equals(topicPath.getCriterion())) {
            return thingEventAdapter.fromAdaptable(adaptable);
        } else if (TopicPath.Criterion.ERRORS.equals(topicPath.getCriterion())) {
            return thingErrorResponseFromAdaptable(adaptable);
        }
        return null;
    }

    /**
     * Maps the given {@code CommandResponse} to an {@code Adaptable}.
     *
     * @param commandResponse the response.
     * @return the adaptable.
     * @throws UnknownCommandResponseException if the passed CommandResponse was not supported by the ProtocolAdapter
     */
    public Adaptable toAdaptable(final CommandResponse<?> commandResponse) {
        return toAdaptable(commandResponse, TopicPath.Channel.TWIN);
    }

    /**
     * Maps the given {@code CommandResponse} to an {@code Adaptable}.
     *
     * @param commandResponse the response.
     * @param channel the Channel (Twin/Live) to use.
     * @return the adaptable.
     * @throws UnknownCommandResponseException if the passed CommandResponse was not supported by the ProtocolAdapter
     */
    public Adaptable toAdaptable(final CommandResponse<?> commandResponse, final TopicPath.Channel channel) {
        if (commandResponse instanceof MessageCommandResponse && channel == TopicPath.Channel.LIVE) {
            return toAdaptable((MessageCommandResponse) commandResponse);
        } else if (commandResponse instanceof ThingCommandResponse) {
            return toAdaptable((ThingCommandResponse) commandResponse, channel);
        } else {
            throw UnknownCommandResponseException.newBuilder(commandResponse.getName()).build();
        }
    }

    /**
     * Maps the given {@code ThingCommandResponse} to an {@code Adaptable}.
     *
     * @param thingCommandResponse the response.
     * @return the adaptable.
     * @throws UnknownCommandResponseException if the passed ThingCommandResponse was not supported by the
     * ProtocolAdapter
     */
    public Adaptable toAdaptable(final ThingCommandResponse<?> thingCommandResponse) {
        return toAdaptable(thingCommandResponse, TopicPath.Channel.TWIN);
    }

    /**
     * Maps the given {@code ThingCommandResponse} to an {@code Adaptable}.
     *
     * @param thingCommandResponse the response.
     * @param channel the Channel (Twin/Live) to use.
     * @return the adaptable.
     * @throws UnknownCommandResponseException if the passed ThingCommandResponse was not supported by the
     * ProtocolAdapter
     */
    public Adaptable toAdaptable(final ThingCommandResponse<?> thingCommandResponse, final TopicPath.Channel channel) {
        if (thingCommandResponse instanceof ThingQueryCommandResponse) {
            return toAdaptable((ThingQueryCommandResponse) thingCommandResponse, channel);
        } else if (thingCommandResponse instanceof ThingModifyCommandResponse) {
            return toAdaptable((ThingModifyCommandResponse) thingCommandResponse, channel);
        } else if (thingCommandResponse instanceof ThingErrorResponse) {
            return toAdaptable((ThingErrorResponse) thingCommandResponse);
        } else {
            throw UnknownCommandResponseException.newBuilder(thingCommandResponse.getName()).build();
        }
    }

    /**
     * Maps the given {@code messageCommand} to an {@code Adaptable}.
     *
     * @param messageCommand the messageCommand.
     * @return the adaptable.
     * @throws UnknownCommandException if the passed MessageCommand was not supported by the ProtocolAdapter
     */
    public Adaptable toAdaptable(final MessageCommand<?,?> messageCommand) {
        return messageCommandAdapter.toAdaptable(messageCommand, TopicPath.Channel.LIVE);
    }

    /**
     * Maps the given {@code messageCommandResponse} to an {@code Adaptable}.
     *
     * @param messageCommandResponse the messageCommandResponse.
     * @return the adaptable.
     * @throws UnknownCommandException if the passed MessageCommandResponse was not supported by the ProtocolAdapter
     */
    public Adaptable toAdaptable(final MessageCommandResponse<?, ?> messageCommandResponse) {
        return messageCommandResponseAdapter.toAdaptable(messageCommandResponse, TopicPath.Channel.LIVE);
    }

    /**
     * Maps the given {@code command} to an {@code Adaptable}.
     *
     * @param command the command.
     * @return the adaptable.
     * @throws UnknownCommandException if the passed Command was not supported by the ProtocolAdapter
     */
    public Adaptable toAdaptable(final Command<?> command) {
        return toAdaptable(command, TopicPath.Channel.TWIN);
    }

    /**
     * Maps the given {@code command} to an {@code Adaptable}.
     *
     * @param command the command.
     * @param channel the Channel (Twin/Live) to use.
     * @return the adaptable.
     * @throws UnknownCommandException if the passed Command was not supported by the ProtocolAdapter
     */
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

    /**
     * Maps the given {@code thingModifyCommand} to an {@code Adaptable}.
     *
     * @param thingModifyCommand the command.
     * @return the adaptable.
     */
    public Adaptable toAdaptable(final ThingModifyCommand<?> thingModifyCommand) {
        return toAdaptable(thingModifyCommand, TopicPath.Channel.TWIN);
    }

    /**
     * Maps the given {@code thingModifyCommand} to an {@code Adaptable}.
     *
     * @param thingModifyCommand the command.
     * @param channel the Channel (Twin/Live) to use.
     * @return the adaptable.
     */
    public Adaptable toAdaptable(final ThingModifyCommand<?> thingModifyCommand, final TopicPath.Channel channel) {
        return thingModifyCommandAdapter.toAdaptable(thingModifyCommand, channel);
    }

    /**
     * Maps the given {@code thingModifyCommandResponse} to an {@code Adaptable}.
     *
     * @param thingModifyCommandResponse the response.
     * @return the adaptable.
     */
    public Adaptable toAdaptable(final ThingModifyCommandResponse<?> thingModifyCommandResponse) {
        return toAdaptable(thingModifyCommandResponse, TopicPath.Channel.TWIN);
    }

    /**
     * Maps the given {@code thingModifyCommandResponse} to an {@code Adaptable}.
     *
     * @param thingModifyCommandResponse the response.
     * @param channel the Channel (Twin/Live) to use.
     * @return the adaptable.
     */
    public Adaptable toAdaptable(final ThingModifyCommandResponse<?> thingModifyCommandResponse,
            final TopicPath.Channel channel) {
        return thingModifyCommandResponseAdapter.toAdaptable(thingModifyCommandResponse, channel);
    }

    /**
     * Maps the given {@code thingQueryCommand} to an {@code Adaptable}.
     *
     * @param thingQueryCommand the command.
     * @return the adaptable.
     */
    public Adaptable toAdaptable(final ThingQueryCommand<?> thingQueryCommand) {
        return toAdaptable(thingQueryCommand, TopicPath.Channel.TWIN);
    }

    /**
     * Maps the given {@code thingQueryCommand} to an {@code Adaptable}.
     *
     * @param thingQueryCommand the command.
     * @param channel the Channel (Twin/Live) to use.
     * @return the adaptable.
     */
    public Adaptable toAdaptable(final ThingQueryCommand<?> thingQueryCommand, final TopicPath.Channel channel) {
        return thingQueryCommandAdapter.toAdaptable(thingQueryCommand, channel);
    }

    /**
     * Maps the given {@code thingQueryCommandResponse} to an {@code Adaptable}.
     *
     * @param thingQueryCommandResponse the response.
     * @return the adaptable.
     */
    public Adaptable toAdaptable(final ThingQueryCommandResponse<?> thingQueryCommandResponse) {
        return toAdaptable(thingQueryCommandResponse, TopicPath.Channel.TWIN);
    }

    /**
     * Maps the given {@code thingQueryCommandResponse} to an {@code Adaptable}.
     *
     * @param thingQueryCommandResponse the response.
     * @param channel the Channel (Twin/Live) to use.
     * @return the adaptable.
     */
    public Adaptable toAdaptable(final ThingQueryCommandResponse<?> thingQueryCommandResponse,
            final TopicPath.Channel channel) {
        return thingQueryCommandResponseAdapter.toAdaptable(thingQueryCommandResponse, channel);
    }

    /**
     * Maps the given {@code thingErrorResponse} to an {@code Adaptable}.
     *
     * @param thingErrorResponse the error response.
     * @return the adaptable.
     */
    public Adaptable toAdaptable(final ThingErrorResponse thingErrorResponse) {
        final Payload payload = Payload.newBuilder(thingErrorResponse.getResourcePath()) //
                .withStatus(thingErrorResponse.getStatusCode()) //
                .withValue(thingErrorResponse.toJson(thingErrorResponse.getImplementedSchemaVersion()) //
                        .getValue(CommandResponse.JsonFields.PAYLOAD)
                        .orElse(JsonFactory.nullObject())) // only use the error payload
                .build();

        final TopicPathBuilder topicPathBuilder = DittoProtocolAdapter.newTopicPathBuilder(thingErrorResponse.getId());
        final TopicPathBuildable topicPathBuildable;
        if (thingErrorResponse.getDittoHeaders().getChannel().flatMap(TopicPath.Channel::forName)
                .orElse(null) == TopicPath.Channel.TWIN) {
            topicPathBuildable = topicPathBuilder.twin().errors();
        } else if (thingErrorResponse.getDittoHeaders().getChannel().flatMap(TopicPath.Channel::forName)
                .orElse(null) == TopicPath.Channel.LIVE) {
            topicPathBuildable = topicPathBuilder.live().errors();
        } else {
            throw new IllegalArgumentException("Unknown Channel '" + thingErrorResponse.getDittoHeaders().getChannel()
                    + "'");
        }

        return Adaptable.newBuilder(topicPathBuildable.build())
                .withPayload(payload) //
                .withHeaders(DittoProtocolAdapter.newHeaders(thingErrorResponse.getDittoHeaders())) //
                .build();
    }

    /**
     * Maps the given {@code event} to an {@code Adaptable}.
     *
     * @param event the event.
     * @return the adaptable.
     * @throws UnknownEventException if the passed Event was not supported by the ProtocolAdapter
     */
    public Adaptable toAdaptable(final Event<?> event) {
        return toAdaptable(event, TopicPath.Channel.TWIN);
    }

    /**
     * Maps the given {@code event} to an {@code Adaptable}.
     *
     * @param event the event.
     * @param channel the Channel (Twin/Live) to use.
     * @return the adaptable.
     * @throws UnknownEventException if the passed Event was not supported by the ProtocolAdapter
     */
    public Adaptable toAdaptable(final Event<?> event, final TopicPath.Channel channel) {
        if (event instanceof ThingEvent) {
            return toAdaptable((ThingEvent) event, channel);
        } else {
            throw UnknownEventException.newBuilder(event.getName()).build();
        }
    }


    /**
     * Maps the given {@code thingEvent} to an {@code Adaptable}.
     *
     * @param thingEvent the event.
     * @return the adaptable.
     */
    public Adaptable toAdaptable(final ThingEvent<?> thingEvent) {
        return toAdaptable(thingEvent, TopicPath.Channel.TWIN);
    }

    /**
     * Maps the given {@code thingEvent} to an {@code Adaptable}.
     *
     * @param thingEvent the event.
     * @param channel the Channel (Twin/Live) to use.
     * @return the adaptable.
     */
    public Adaptable toAdaptable(final ThingEvent<?> thingEvent, final TopicPath.Channel channel) {
        return thingEventAdapter.toAdaptable(thingEvent, channel);
    }

    private static ThingErrorResponse thingErrorResponseFromAdaptable(final Adaptable adaptable) {
        final JsonObjectBuilder jsonObjectBuilder =
                JsonObject.newBuilder().set(ThingCommandResponse.JsonFields.TYPE, ThingErrorResponse.TYPE);

        adaptable.getPayload().getStatus()
                .ifPresent(status -> jsonObjectBuilder.set(ThingCommandResponse.JsonFields.STATUS, status.toInt()));

        adaptable.getPayload().getValue()
                .ifPresent(value -> jsonObjectBuilder.set(ThingCommandResponse.JsonFields.PAYLOAD, value));

        jsonObjectBuilder.set(ThingCommandResponse.JsonFields.JSON_THING_ID, adaptable.getTopicPath().getNamespace()
                + ":" + adaptable.getTopicPath().getId());

        final DittoHeaders dittoHeaders = adaptable.getHeaders().orElse(DittoHeaders.empty());
        final DittoHeaders adjustedHeaders = dittoHeaders.toBuilder()
                .channel(adaptable.getTopicPath().getChannel().getName())
                .build();

        return ThingErrorResponse.fromJson(jsonObjectBuilder.build(), adjustedHeaders);
    }

}
