/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.models.signal;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.headers.LiveChannelTimeoutStrategy;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.WithType;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommandResponse;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommand;

/**
 * Provides dedicated information about specified {@code Signal} arguments.
 *
 * @since 2.3.0
 */
@Immutable
public final class SignalInformationPoint {

    private static final String CHANNEL_LIVE_VALUE = "live";

    private SignalInformationPoint() {
        throw new AssertionError();
    }

    /**
     * Indicates whether the specified signal argument is a live command.
     *
     * @param signal the signal to be checked.
     * @return {@code true} if {@code signal} is an instance of {@link Command} with channel
     * {@value CHANNEL_LIVE_VALUE} in its headers, {@code false} else.
     */
    public static boolean isLiveCommand(@Nullable final Signal<?> signal) {
        return isMessageCommand(signal) || isCommand(signal) && isChannelLive(signal);
    }

    /**
     * Indicates whether the specified signal argument is an instance of {@code Command}.
     *
     * @param signal the signal to be checked.
     * @return {@code true} if {@code signal} is an instance of {@link Command}, {@code false} else.
     */
    public static boolean isCommand(@Nullable final Signal<?> signal) {
        return signal instanceof Command;
    }

    /**
     * Indicates whether the specified signal argument is a {@link MessageCommand}.
     *
     * @param signal the signal to be checked.
     * @return {@code true} if {@code signal} is a {@code MessageCommand}, {@code false} else.
     */
    public static boolean isMessageCommand(@Nullable final Signal<?> signal) {
        return hasTypePrefix(signal, MessageCommand.TYPE_PREFIX);
    }

    /**
     * Indicates whether the specified signal argument is a {@link ThingCommand}.
     *
     * @param signal the signal to be checked.
     * @return {@code true} if {@code signal} is a {@code ThingCommand}, {@code false} else.
     */
    public static boolean isThingCommand(@Nullable final Signal<?> signal) {
        return hasTypePrefix(signal, ThingCommand.TYPE_PREFIX);
    }

    /**
     * Indicates whether the specified signal is a live command response.
     *
     * @param signal the signal to be checked.
     * @return {@code true} if {@code signal} is a live command response, i.e. an instance of {@link CommandResponse}
     * with channel {@value CHANNEL_LIVE_VALUE} in its headers.
     * {@code false} if {@code signal} is not a live command response.
     */
    public static boolean isLiveCommandResponse(@Nullable final Signal<?> signal) {
        return isMessageCommandResponse(signal) || isCommandResponse(signal) && isChannelLive(signal);
    }

    /**
     * Indicates whether the specified signal argument is an instance of {@code CommandResponse}.
     *
     * @param signal the signal to be checked.
     * @return {@code true} if {@code signal} is an instance of {@link CommandResponse}, {@code false} else.
     */
    public static boolean isCommandResponse(@Nullable final Signal<?> signal) {
        return signal instanceof CommandResponse;
    }

    /**
     * Indicates whether the specified signal argument is a {@link MessageCommandResponse}.
     *
     * @param signal the signal to be checked.
     * @return {@code true} if {@code signal} is a {@code MessageCommandResponse}, {@code false} else.
     */
    public static boolean isMessageCommandResponse(@Nullable final Signal<?> signal) {
        return hasTypePrefix(signal, MessageCommandResponse.TYPE_PREFIX);
    }

    /**
     * Indicates whether the specified signal argument is a {@link ThingCommandResponse}.
     *
     * @param signal the signal to be checked.
     * @return {@code true} if {@code signal} is a {@code ThingCommandResponse}, {@code false} else.
     */
    public static boolean isThingCommandResponse(@Nullable final Signal<?> signal) {
        return hasTypePrefix(signal, ThingCommandResponse.TYPE_PREFIX);
    }

    /**
     * Indicates whether the headers of the specified signal argument contain channel {@value CHANNEL_LIVE_VALUE}.
     *
     * @param signal the signal to be checked.
     * @return {@code true} if the headers of {@code signal} contain the channel {@value CHANNEL_LIVE_VALUE}.
     */
    public static boolean isChannelLive(@Nullable final WithDittoHeaders signal) {
        final boolean result;
        if (null != signal) {
            final var dittoHeaders = signal.getDittoHeaders();
            result = dittoHeaders.getChannel().filter(CHANNEL_LIVE_VALUE::equals).isPresent();
        } else {
            result = false;
        }

        return result;
    }

    /**
     * Indicates whether the specified {@code Signal} argument is a {@code ThingQueryCommand} using smart channel
     * selection.
     *
     * @param signal the signal to be checked.
     * @return {@code true} if {@code signal} is a {@code ThingQueryCommand} handled by smart channel selection.
     * @since 2.3.0
     */
    public static boolean isChannelSmart(@Nullable final WithDittoHeaders signal) {
        final boolean result;
        if (signal instanceof ThingQueryCommand) {
            final var headers = signal.getDittoHeaders();
            if (isChannelLive(signal)) {
                result = LiveChannelTimeoutStrategy.USE_TWIN ==
                        headers.getLiveChannelTimeoutStrategy().orElse(LiveChannelTimeoutStrategy.FAIL);
            } else {
                result = headers.getLiveChannelCondition().isPresent();
            }
        } else {
            result = false;
        }
        return result;
    }

    /**
     * Indicates whether the specified signal argument provides an entity ID.
     *
     * @param signal the signal to be checked.
     * @return {@code true} if {@code signal} provides an entity ID because it implements {@link WithEntityId}.
     * {@code false} else.
     */
    public static boolean isWithEntityId(@Nullable final Signal<?> signal) {
        final boolean result;
        if (null != signal) {
            result = WithEntityId.class.isAssignableFrom(signal.getClass());
        } else {
            result = false;
        }

        return result;
    }

    /**
     * Returns the {@link EntityId} for the specified signal argument.
     *
     * @param signal the signal to get the entity ID from.
     * @return an {@code Optional} containing the signal's entity ID if it provides one, an empty {@code Optional} else.
     * @see #isWithEntityId(Signal)
     */
    public static Optional<EntityId> getEntityId(@Nullable final Signal<?> signal) {
        final Optional<EntityId> result;
        if (isWithEntityId(signal)) {
            result = Optional.of(((WithEntityId) signal).getEntityId());
        } else {
            result = Optional.empty();
        }

        return result;
    }

    /**
     * Returns the optional correlation ID of the specified argument's headers.
     *
     * @param signal the signal to get the optional correlation ID from.
     * @return the optional correlation ID. The optional is empty if {@code signal} is {@code null}.
     */
    public static Optional<String> getCorrelationId(@Nullable final WithDittoHeaders signal) {
        final Optional<String> result;
        if (null != signal) {
            final var signalDittoHeaders = signal.getDittoHeaders();
            result = signalDittoHeaders.getCorrelationId();
        } else {
            result = Optional.empty();
        }

        return result;
    }

    private static boolean hasTypePrefix(@Nullable final WithType signal, final String typePrefix) {
        final boolean result;
        if (null != signal) {
            final var signalType = signal.getType();
            result = signalType.startsWith(typePrefix);
        } else {
            result = false;
        }

        return result;
    }

}
