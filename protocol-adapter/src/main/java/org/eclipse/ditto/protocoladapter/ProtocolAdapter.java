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
 * A protocol adapter provides methods for mapping {@link Signal} instances to an {@link Adaptable}.
 */
public interface ProtocolAdapter {

    /**
     * Maps the given {@code adaptable} to the corresponding {@code Signal}, which can be a {@code Command},
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
     * Maps the given {@code CommandResponse} to an {@code Adaptable}.
     *
     * @param commandResponse the response.
     * @return the adaptable.
     * @throws UnknownCommandResponseException if the passed CommandResponse was not supported by the ProtocolAdapter
     */
    Adaptable toAdaptable(CommandResponse<?> commandResponse);

    /**
     * Maps the given {@code CommandResponse} to an {@code Adaptable}.
     *
     * @param commandResponse the response.
     * @param channel the Channel (Twin/Live) to use.
     * @return the adaptable.
     * @throws UnknownCommandResponseException if the passed CommandResponse was not supported by the ProtocolAdapter
     */
    Adaptable toAdaptable(CommandResponse<?> commandResponse, TopicPath.Channel channel);

    /**
     * Maps the given {@code ThingCommandResponse} to an {@code Adaptable}.
     *
     * @param thingCommandResponse the response.
     * @return the adaptable.
     * @throws UnknownCommandResponseException if the passed ThingCommandResponse was not supported by the
     * ProtocolAdapter
     */
    Adaptable toAdaptable(ThingCommandResponse<?> thingCommandResponse);

    /**
     * Maps the given {@code ThingCommandResponse} to an {@code Adaptable}.
     *
     * @param thingCommandResponse the response.
     * @param channel the Channel (Twin/Live) to use.
     * @return the adaptable.
     * @throws UnknownCommandResponseException if the passed ThingCommandResponse was not supported by the
     * ProtocolAdapter
     */
    Adaptable toAdaptable(ThingCommandResponse<?> thingCommandResponse, TopicPath.Channel channel);

    /**
     * Maps the given {@code messageCommand} to an {@code Adaptable}.
     *
     * @param messageCommand the messageCommand.
     * @return the adaptable.
     * @throws UnknownCommandException if the passed MessageCommand was not supported by the ProtocolAdapter
     */
    Adaptable toAdaptable(MessageCommand<?, ?> messageCommand);

    /**
     * Maps the given {@code messageCommandResponse} to an {@code Adaptable}.
     *
     * @param messageCommandResponse the messageCommandResponse.
     * @return the adaptable.
     * @throws UnknownCommandException if the passed MessageCommandResponse was not supported by the ProtocolAdapter
     */
    Adaptable toAdaptable(MessageCommandResponse<?, ?> messageCommandResponse);

    /**
     * Maps the given {@code command} to an {@code Adaptable}.
     *
     * @param command the command.
     * @return the adaptable.
     * @throws UnknownCommandException if the passed Command was not supported by the ProtocolAdapter
     */
    Adaptable toAdaptable(Command<?> command);

    /**
     * Maps the given {@code command} to an {@code Adaptable}.
     *
     * @param command the command.
     * @param channel the Channel (Twin/Live) to use.
     * @return the adaptable.
     * @throws UnknownCommandException if the passed Command was not supported by the ProtocolAdapter
     */
    Adaptable toAdaptable(Command<?> command, TopicPath.Channel channel);

    /**
     * Maps the given {@code thingModifyCommand} to an {@code Adaptable}.
     *
     * @param thingModifyCommand the command.
     * @return the adaptable.
     */
    Adaptable toAdaptable(ThingModifyCommand<?> thingModifyCommand);

    /**
     * Maps the given {@code thingModifyCommand} to an {@code Adaptable}.
     *
     * @param thingModifyCommand the command.
     * @param channel the Channel (Twin/Live) to use.
     * @return the adaptable.
     */
    Adaptable toAdaptable(ThingModifyCommand<?> thingModifyCommand, TopicPath.Channel channel);

    /**
     * Maps the given {@code thingModifyCommandResponse} to an {@code Adaptable}.
     *
     * @param thingModifyCommandResponse the response.
     * @return the adaptable.
     */
    Adaptable toAdaptable(ThingModifyCommandResponse<?> thingModifyCommandResponse);

    /**
     * Maps the given {@code thingModifyCommandResponse} to an {@code Adaptable}.
     *
     * @param thingModifyCommandResponse the response.
     * @param channel the Channel (Twin/Live) to use.
     * @return the adaptable.
     */
    Adaptable toAdaptable(ThingModifyCommandResponse<?> thingModifyCommandResponse,
            TopicPath.Channel channel);

    /**
     * Maps the given {@code thingQueryCommand} to an {@code Adaptable}.
     *
     * @param thingQueryCommand the command.
     * @return the adaptable.
     */
    Adaptable toAdaptable(ThingQueryCommand<?> thingQueryCommand);

    /**
     * Maps the given {@code thingQueryCommand} to an {@code Adaptable}.
     *
     * @param thingQueryCommand the command.
     * @param channel the Channel (Twin/Live) to use.
     * @return the adaptable.
     */
    Adaptable toAdaptable(ThingQueryCommand<?> thingQueryCommand, TopicPath.Channel channel);

    /**
     * Maps the given {@code thingQueryCommandResponse} to an {@code Adaptable}.
     *
     * @param thingQueryCommandResponse the response.
     * @return the adaptable.
     */
    Adaptable toAdaptable(ThingQueryCommandResponse<?> thingQueryCommandResponse);

    /**
     * Maps the given {@code thingQueryCommandResponse} to an {@code Adaptable}.
     *
     * @param thingQueryCommandResponse the response.
     * @param channel the Channel (Twin/Live) to use.
     * @return the adaptable.
     */
    Adaptable toAdaptable(ThingQueryCommandResponse<?> thingQueryCommandResponse,
            TopicPath.Channel channel);

    /**
     * Maps the given {@code thingErrorResponse} to an {@code Adaptable}.
     *
     * @param thingErrorResponse the error response.
     * @param channel the Channel (Twin/Live) to use.
     * @return the adaptable.
     */
    Adaptable toAdaptable(ThingErrorResponse thingErrorResponse, TopicPath.Channel channel);

    /**
     * Maps the given {@code event} to an {@code Adaptable}.
     *
     * @param event the event.
     * @return the adaptable.
     * @throws UnknownEventException if the passed Event was not supported by the ProtocolAdapter
     */
    Adaptable toAdaptable(Event<?> event);

    /**
     * Maps the given {@code event} to an {@code Adaptable}.
     *
     * @param event the event.
     * @param channel the Channel (Twin/Live) to use.
     * @return the adaptable.
     * @throws UnknownEventException if the passed Event was not supported by the ProtocolAdapter
     */
    Adaptable toAdaptable(Event<?> event, TopicPath.Channel channel);

    /**
     * Maps the given {@code thingEvent} to an {@code Adaptable}.
     *
     * @param thingEvent the event.
     * @return the adaptable.
     */
    Adaptable toAdaptable(ThingEvent<?> thingEvent);

    /**
     * Maps the given {@code thingEvent} to an {@code Adaptable}.
     *
     * @param thingEvent the event.
     * @param channel the Channel (Twin/Live) to use.
     * @return the adaptable.
     */
    Adaptable toAdaptable(ThingEvent<?> thingEvent, TopicPath.Channel channel);

}
