/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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

import static org.eclipse.ditto.protocoladapter.TopicPath.Channel.LIVE;
import static org.eclipse.ditto.protocoladapter.TopicPath.Channel.NONE;
import static org.eclipse.ditto.protocoladapter.TopicPath.Channel.TWIN;

import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.MessageCommandResponse;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.policies.PolicyCommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommandResponse;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommandResponse;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.things.ThingEvent;

/**
 * A protocol adapter provides methods for mapping {@link Signal} instances to an {@link Adaptable}.
 */
public interface ProtocolAdapter {

    /**
     * Maps the given {@code Adaptable} to the corresponding {@code Signal}, which can be a {@code Command},
     * {@code CommandResponse} or an {@code Event}.
     *
     * @param adaptable the adaptable.
     * @return the Signal.
     */
    Signal<?> fromAdaptable(Adaptable adaptable);

    /**
     * Maps the given {@code Signal} to an {@code Adaptable}.
     *
     * @param signal the signal.
     * @return the adaptable.
     * @throws UnknownSignalException if the passed Signal was not supported by the ProtocolAdapter
     */
    Adaptable toAdaptable(Signal<?> signal);

    /**
     * Maps the given {@code Signal} to an {@code Adaptable}.
     *
     * @param signal the signal.
     * @param channel the channel to use when converting toAdaptable. This will overwrite any channel header in {@code signal}.
     * @return the adaptable.
     * @throws UnknownSignalException if the passed Signal was not supported by the ProtocolAdapter
     * @since 1.1.0
     */
    Adaptable toAdaptable(Signal<?> signal, TopicPath.Channel channel);

    /**
     * Maps the given {@code CommandResponse} to an {@code Adaptable} assuming {@link TopicPath.Channel#TWIN}.
     *
     * @param commandResponse the response.
     * @return the adaptable.
     * @throws UnknownCommandResponseException if the passed CommandResponse was not supported by the ProtocolAdapter
     * @deprecated since 1.1.0, use {@link ProtocolAdapter#toAdaptable(Signal)} instead.
     */
    @Deprecated
    default Adaptable toAdaptable(CommandResponse<?> commandResponse) {
        return toAdaptable(commandResponse, determineChannel(commandResponse));
    }

    /**
     * Maps the given {@code CommandResponse} to an {@code Adaptable}.
     *
     * @param commandResponse the response.
     * @param channel the Channel (Twin/Live) to use.
     * @return the adaptable.
     * @throws UnknownCommandResponseException if the passed CommandResponse was not supported by the ProtocolAdapter
     * @deprecated since 1.1.0, use {@link ProtocolAdapter#toAdaptable(Signal, TopicPath.Channel)} instead.
     */
    @Deprecated
    Adaptable toAdaptable(CommandResponse<?> commandResponse, TopicPath.Channel channel);

    /**
     * Maps the given {@code ThingCommandResponse} to an {@code Adaptable}.
     *
     * @param thingCommandResponse the response.
     * @return the adaptable.
     * @throws UnknownCommandResponseException if the passed ThingCommandResponse was not supported by the
     * ProtocolAdapter
     * @deprecated since 1.1.0, use {@link ProtocolAdapter#toAdaptable(Signal)} instead.
     */
    @Deprecated
    default Adaptable toAdaptable(ThingCommandResponse<?> thingCommandResponse) {
        return toAdaptable(thingCommandResponse, determineChannel(thingCommandResponse));
    }

    /**
     * Maps the given {@code ThingCommandResponse} to an {@code Adaptable}.
     *
     * @param thingCommandResponse the response.
     * @param channel the Channel (Twin/Live) to use.
     * @return the adaptable.
     * @throws UnknownCommandResponseException if the passed ThingCommandResponse was not supported by the
     * ProtocolAdapter
     * @deprecated since 1.1.0, use {@link ProtocolAdapter#toAdaptable(Signal, TopicPath.Channel)} instead.
     */
    @Deprecated
    Adaptable toAdaptable(ThingCommandResponse<?> thingCommandResponse, TopicPath.Channel channel);

    /**
     * Maps the given {@code messageCommand} to an {@code Adaptable}.
     *
     * @param messageCommand the messageCommand.
     * @return the adaptable.
     * @throws UnknownCommandException if the passed MessageCommand was not supported by the ProtocolAdapter
     * @deprecated since 1.1.0, use {@link ProtocolAdapter#toAdaptable(Signal)} instead.
     */
    @Deprecated
    Adaptable toAdaptable(MessageCommand<?, ?> messageCommand);

    /**
     * Maps the given {@code messageCommandResponse} to an {@code Adaptable}.
     *
     * @param messageCommandResponse the messageCommandResponse.
     * @return the adaptable.
     * @throws UnknownCommandException if the passed MessageCommandResponse was not supported by the ProtocolAdapter
     * @deprecated since 1.1.0, use {@link ProtocolAdapter#toAdaptable(Signal)} instead.
     */
    @Deprecated
    Adaptable toAdaptable(MessageCommandResponse<?, ?> messageCommandResponse);

    /**
     * Maps the given {@code command} to an {@code Adaptable}.
     *
     * @param command the command.
     * @return the adaptable.
     * @throws UnknownCommandException if the passed Command was not supported by the ProtocolAdapter
     * @deprecated since 1.1.0, use {@link ProtocolAdapter#toAdaptable(Signal)} instead.
     */
    @Deprecated
    Adaptable toAdaptable(Command<?> command);

    /**
     * Maps the given {@code command} to an {@code Adaptable}.
     *
     * @param command the command.
     * @param channel the Channel (Twin/Live) to use.
     * @return the adaptable.
     * @throws UnknownCommandException if the passed Command was not supported by the ProtocolAdapter
     * @deprecated since 1.1.0, use {@link ProtocolAdapter#toAdaptable(Signal, TopicPath.Channel)} instead.
     */
    @Deprecated
    Adaptable toAdaptable(Command<?> command, TopicPath.Channel channel);

    /**
     * Maps the given {@code thingModifyCommand} to an {@code Adaptable}.
     *
     * @param thingModifyCommand the command.
     * @return the adaptable.
     * @deprecated since 1.1.0, use {@link ProtocolAdapter#toAdaptable(Signal)} instead.
     */
    @Deprecated
    default Adaptable toAdaptable(ThingModifyCommand<?> thingModifyCommand) {
        return toAdaptable(thingModifyCommand, determineChannel(thingModifyCommand));
    }

    /**
     * Maps the given {@code thingModifyCommand} to an {@code Adaptable}.
     *
     * @param thingModifyCommand the command.
     * @param channel the Channel (Twin/Live) to use.
     * @return the adaptable.
     * @deprecated since 1.1.0, use {@link ProtocolAdapter#toAdaptable(Signal, TopicPath.Channel)} instead.
     */
    @Deprecated
    Adaptable toAdaptable(ThingModifyCommand<?> thingModifyCommand, TopicPath.Channel channel);

    /**
     * Maps the given {@code thingModifyCommandResponse} to an {@code Adaptable}.
     *
     * @param thingModifyCommandResponse the response.
     * @return the adaptable.
     * @deprecated since 1.1.0, use {@link ProtocolAdapter#toAdaptable(Signal)} instead.
     */
    @Deprecated
    default Adaptable toAdaptable(ThingModifyCommandResponse<?> thingModifyCommandResponse) {
        return toAdaptable(thingModifyCommandResponse, determineChannel(thingModifyCommandResponse));
    }

    /**
     * Maps the given {@code thingModifyCommandResponse} to an {@code Adaptable}.
     *
     * @param thingModifyCommandResponse the response.
     * @param channel the Channel (Twin/Live) to use.
     * @return the adaptable.
     * @deprecated since 1.1.0, use {@link ProtocolAdapter#toAdaptable(Signal, TopicPath.Channel)} instead.
     */
    @Deprecated
    Adaptable toAdaptable(ThingModifyCommandResponse<?> thingModifyCommandResponse, TopicPath.Channel channel);

    /**
     * Maps the given {@code thingQueryCommand} to an {@code Adaptable}.
     *
     * @param thingQueryCommand the command.
     * @return the adaptable.
     * @deprecated since 1.1.0, use {@link ProtocolAdapter#toAdaptable(Signal)} instead.
     */
    @Deprecated
    default Adaptable toAdaptable(ThingQueryCommand<?> thingQueryCommand) {

        return toAdaptable(thingQueryCommand, determineChannel(thingQueryCommand));
    }

    /**
     * Maps the given {@code thingQueryCommand} to an {@code Adaptable}.
     *
     * @param thingQueryCommand the command.
     * @param channel the Channel (Twin/Live) to use.
     * @return the adaptable.
     * @deprecated since 1.1.0, use {@link ProtocolAdapter#toAdaptable(Signal, TopicPath.Channel)} instead.
     */
    @Deprecated
    Adaptable toAdaptable(ThingQueryCommand<?> thingQueryCommand, TopicPath.Channel channel);

    /**
     * Maps the given {@code thingQueryCommandResponse} to an {@code Adaptable}.
     *
     * @param thingQueryCommandResponse the response.
     * @return the adaptable.
     * @deprecated since 1.1.0, use {@link ProtocolAdapter#toAdaptable(Signal)} instead.
     */
    @Deprecated
    default Adaptable toAdaptable(ThingQueryCommandResponse<?> thingQueryCommandResponse) {
        return toAdaptable(thingQueryCommandResponse, determineChannel(thingQueryCommandResponse));
    }

    /**
     * Maps the given {@code thingQueryCommandResponse} to an {@code Adaptable}.
     *
     * @param thingQueryCommandResponse the response.
     * @param channel the Channel (Twin/Live) to use.
     * @return the adaptable.
     * @deprecated since 1.1.0, use {@link ProtocolAdapter#toAdaptable(Signal, TopicPath.Channel)} instead.
     */
    @Deprecated
    Adaptable toAdaptable(ThingQueryCommandResponse<?> thingQueryCommandResponse, TopicPath.Channel channel);

    /**
     * Maps the given {@code thingErrorResponse} to an {@code Adaptable}.
     *
     * @param thingErrorResponse the error response.
     * @param channel the Channel (Twin/Live) to use.
     * @return the adaptable.
     * @deprecated since 1.1.0, use {@link ProtocolAdapter#toAdaptable(Signal, TopicPath.Channel)} instead.
     */
    @Deprecated
    Adaptable toAdaptable(ThingErrorResponse thingErrorResponse, TopicPath.Channel channel);

    /**
     * Maps the given {@code event} to an {@code Adaptable}.
     *
     * @param event the event.
     * @return the adaptable.
     * @throws UnknownEventException if the passed Event was not supported by the ProtocolAdapter
     * @deprecated since 1.1.0, use {@link ProtocolAdapter#toAdaptable(Signal)} instead.
     */
    @Deprecated
    default Adaptable toAdaptable(Event<?> event) {
        return toAdaptable(event, determineChannel(event));
    }

    /**
     * Maps the given {@code event} to an {@code Adaptable}.
     *
     * @param event the event.
     * @param channel the Channel (Twin/Live) to use.
     * @return the adaptable.
     * @throws UnknownEventException if the passed Event was not supported by the ProtocolAdapter
     * @deprecated since 1.1.0, use {@link ProtocolAdapter#toAdaptable(Signal, TopicPath.Channel)} instead.
     */
    @Deprecated
    Adaptable toAdaptable(Event<?> event, TopicPath.Channel channel);

    /**
     * Maps the given {@code thingEvent} to an {@code Adaptable}.
     *
     * @param thingEvent the event.
     * @return the adaptable.
     * @deprecated since 1.1.0, use {@link ProtocolAdapter#toAdaptable(Signal)} instead.
     */
    @Deprecated
    default Adaptable toAdaptable(ThingEvent<?> thingEvent) {
        return toAdaptable(thingEvent, determineChannel(thingEvent));
    }

    /**
     * Maps the given {@code thingEvent} to an {@code Adaptable}.
     *
     * @param thingEvent the event.
     * @param channel the Channel (Twin/Live) to use.
     * @return the adaptable.
     * @deprecated since 1.1.0, use {@link ProtocolAdapter#toAdaptable(Signal, TopicPath.Channel)} instead.
     */
    @Deprecated
    Adaptable toAdaptable(ThingEvent<?> thingEvent, TopicPath.Channel channel);

    /**
     * Retrieve the header translator responsible for this protocol adapter.
     *
     * @return the header translator.
     */
    HeaderTranslator headerTranslator();

    /**
     * Test whether a signal belongs to the live channel.
     *
     * @param signal the signal.
     * @return whether it is a live signal.
     */
    static boolean isLiveSignal(final Signal<?> signal) {
        return signal.getDittoHeaders()
                .getChannel()
                .filter(TopicPath.Channel.LIVE.getName()::equals)
                .isPresent();
    }

    /**
     * Determine the channel of the processed {@link Signal}. First the DittoHeaders are checked for the
     * {@link org.eclipse.ditto.model.base.headers.DittoHeaderDefinition#CHANNEL} header. If not given the default
     * channel is determined by the type of the {@link Signal}.
     *
     * @param signal the processed signal
     * @return the channel determined from the signal
     */
    static TopicPath.Channel determineChannel(final Signal<?> signal) {
        // internally a twin command/event and live command/event are distinguished only  by the channel header i.e.
        // a twin and live command "look the same" except for the channel header
        final boolean isLiveSignal = isLiveSignal(signal);
        return isLiveSignal ? LIVE  // live signals (live commands/events) use the live channel
                : determineDefaultChannel(signal); // use default for other commands
    }

    /**
     * Determines the default channel of the processed {@link Signal} by signal type.
     *
     * @param signal the processed signal
     * @return the default channel determined from the signal
     */
    static TopicPath.Channel determineDefaultChannel(final Signal<?> signal) {
        if (signal instanceof PolicyCommand || signal instanceof PolicyCommandResponse) {
            return NONE;
        } else if (signal instanceof MessageCommand || signal instanceof MessageCommandResponse) {
            return LIVE;
        } else {
            return TWIN;
        }
    }
}
