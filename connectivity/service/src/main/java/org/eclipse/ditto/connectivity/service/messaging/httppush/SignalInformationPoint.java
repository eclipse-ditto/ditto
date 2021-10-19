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
package org.eclipse.ditto.connectivity.service.messaging.httppush;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommandResponse;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;

/**
 * Provides dedicated information about specified {@code Signal} arguments.
 */
@Immutable
final class SignalInformationPoint {

    private SignalInformationPoint() {
        throw new AssertionError("nope");
    }

    static boolean isLiveCommand(@Nullable final Signal<?> signal) {
        return isMessageCommand(signal) || isThingCommand(signal) && isChannelLive(signal);
    }

    private static boolean isMessageCommand(@Nullable final Signal<?> signal) {
        return hasTypePrefix(signal, MessageCommand.TYPE_PREFIX);
    }

    private static boolean hasTypePrefix(@Nullable final Signal<?> signal, final String typePrefix) {
        final boolean result;
        if (null != signal) {
            final var signalType = signal.getType();
            result = signalType.startsWith(typePrefix);
        } else {
            result = false;
        }
        return result;
    }

    private static boolean isThingCommand(@Nullable final Signal<?> signal) {
        return hasTypePrefix(signal, ThingCommand.TYPE_PREFIX);
    }

    static boolean isLiveCommandResponse(@Nullable final Signal<?> signal) {
        return isMessageCommandResponse(signal) || isThingCommandResponse(signal) && isChannelLive(signal);
    }

    private static boolean isMessageCommandResponse(@Nullable final Signal<?> signal) {
        return hasTypePrefix(signal, MessageCommandResponse.TYPE_PREFIX);
    }

    private static boolean isThingCommandResponse(@Nullable final Signal<?> signal) {
        return hasTypePrefix(signal, ThingCommandResponse.TYPE_PREFIX);
    }

    static boolean isChannelLive(@Nullable final WithDittoHeaders withDittoHeaders) {
        return null != withDittoHeaders &&
                withDittoHeaders.getDittoHeaders().getChannel().map("live"::equals).isPresent();
    }

    static Optional<SignalWithEntityId<?>> tryToGetAsLiveCommandWithEntityId(@Nullable final Signal<?> signal) {
        final SignalWithEntityId<?> result;
        if (isLiveCommand(signal)) {
            result = (SignalWithEntityId<?>) signal;
        } else {
            result = null;
        }
        return Optional.ofNullable(result);
    }

}
