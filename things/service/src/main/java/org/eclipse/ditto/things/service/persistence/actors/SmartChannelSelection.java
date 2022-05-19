/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.things.service.persistence.actors;

import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

import org.eclipse.ditto.base.model.exceptions.AskException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.base.model.headers.LiveChannelTimeoutStrategy;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.ErrorResponse;
import org.eclipse.ditto.base.model.signals.commands.exceptions.CommandTimeoutException;
import org.eclipse.ditto.internal.models.signal.CommandHeaderRestoration;
import org.eclipse.ditto.policies.enforcement.AbstractEnforcementReloaded;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommand;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommandResponse;

import akka.pattern.AskTimeoutException;

/**
 * TODO TJ doc
 * TODO TJ make the smart channel selection "nicer" and also create an abstraction for live messages, etc. in similar way
 */
final class SmartChannelSelection {

    private static final Duration DEFAULT_LIVE_TIMEOUT = Duration.ofSeconds(60L);

    private SmartChannelSelection() {
        throw new AssertionError();
    }

    static ThingQueryCommandResponse<?> handleSmartChannelTwinResponse(
            final ThingQueryCommand<?> enforcedThingQueryCommand, final Object response) {

        if (response instanceof ThingQueryCommandResponse<?> thingQueryCommandResponse) {
            final ThingQueryCommandResponse<?> twinResponseWithTwinChannel = setTwinChannel(thingQueryCommandResponse);
            return CommandHeaderRestoration.restoreCommandConnectivityHeaders(
                    twinResponseWithTwinChannel, enforcedThingQueryCommand.getDittoHeaders()
            );
        } else {
            throw new IllegalArgumentException("Response was not as expected a thing query command response!");
        }
    }

    static Function<Object, ThingQueryCommandResponse<?>> getFallbackResponseCaster(
            final ThingQueryCommand<?> liveCommand, final ThingQueryCommandResponse<?> twinResponse) {

        return response -> {
            if (response instanceof ThingQueryCommandResponse) {
                return setAdditionalHeaders((ThingQueryCommandResponse<?>) response, liveCommand.getDittoHeaders());
            } else if (response instanceof ErrorResponse) {
                throw setAdditionalHeaders(((ErrorResponse<?>) response),
                        liveCommand.getDittoHeaders()).getDittoRuntimeException();
            } else if (response instanceof Throwable t) {
                Throwable throwable = t;
                if (response instanceof CompletionException completionException) {
                    throwable = completionException.getCause();
                }

                if (throwable instanceof AskException || throwable instanceof AskTimeoutException) {
                    return applyTimeoutStrategy(liveCommand, twinResponse);
                }
            }

            throw AbstractEnforcementReloaded.reportErrorOrResponse(
                    "before building JsonView for live response via smart channel selection",
                    response, null, liveCommand.getDittoHeaders());
        };
    }

    static boolean shouldAttemptLiveChannel(final ThingQueryCommand<?> command,
            final ThingQueryCommandResponse<?> twinResponse) {
        return isLiveChannelConditionMatched(command, twinResponse) || isLiveQueryCommandWithTimeoutStrategy(command);
    }

    static ThingQueryCommand<?> toLiveCommand(final ThingQueryCommand<?> command) {

        return command.setDittoHeaders(command.getDittoHeaders().toBuilder()
                .liveChannelCondition(null)
                .channel(Signal.CHANNEL_LIVE)
                .build());
    }

    static ThingQueryCommand<?> ensureTwinChannel(final ThingQueryCommand<?> command) {

        if (Signal.isChannelLive(command)) {
            return command.setDittoHeaders(command.getDittoHeaders()
                    .toBuilder()
                    .channel(Signal.CHANNEL_TWIN)
                    .build());
        } else {
            return command;
        }
    }

    private static ThingQueryCommandResponse<?> setTwinChannel(final ThingQueryCommandResponse<?> response) {

        return response.setDittoHeaders(response.getDittoHeaders()
                .toBuilder()
                .channel(Signal.CHANNEL_TWIN)
                .putHeaders(getAdditionalLiveResponseHeaders(response.getDittoHeaders()))
                .build());
    }

    private static boolean isLiveChannelConditionMatched(final ThingQueryCommand<?> command,
            final ThingQueryCommandResponse<?> twinResponse) {

        return command.getDittoHeaders().getLiveChannelCondition().isPresent() &&
                twinResponse.getDittoHeaders().didLiveChannelConditionMatch();
    }

    private static boolean isLiveQueryCommandWithTimeoutStrategy(final Signal<?> command) {

        return command instanceof ThingQueryCommand &&
                command.getDittoHeaders().getLiveChannelTimeoutStrategy().isPresent() &&
                Signal.isChannelLive(command);
    }

    @SuppressWarnings("unchecked")
    private static <T extends DittoHeadersSettable<?>> T setAdditionalHeaders(final T settable,
            final DittoHeaders commandHeaders) {

        final DittoHeaders dittoHeaders = settable.getDittoHeaders();
        final DittoHeadersSettable<?> theSettable = settable.setDittoHeaders(dittoHeaders
                .toBuilder()
                .putHeaders(getAdditionalLiveResponseHeaders(dittoHeaders))
                .build());
        return (T) CommandHeaderRestoration.restoreCommandConnectivityHeaders(theSettable, commandHeaders);
    }

    private static ThingQueryCommandResponse<?> applyTimeoutStrategy(
            final ThingCommand<?> command,
            final ThingQueryCommandResponse<?> twinResponse) {

        if (isTwinFallbackEnabled(twinResponse)) {
            return twinResponse;
        } else {
            final var timeout = getLiveSignalTimeout(command);
            final CommandTimeoutException timeoutException = CommandTimeoutException.newBuilder(timeout)
                    .dittoHeaders(twinResponse.getDittoHeaders()
                            .toBuilder()
                            .channel(Signal.CHANNEL_LIVE)
                            .putHeaders(getAdditionalLiveResponseHeaders(twinResponse.getDittoHeaders()))
                            .build())
                    .build();
            throw CommandHeaderRestoration.restoreCommandConnectivityHeaders(timeoutException,
                    command.getDittoHeaders());
        }
    }

    private static Duration getLiveSignalTimeout(final Signal<?> signal) {
        return signal.getDittoHeaders().getTimeout().orElse(DEFAULT_LIVE_TIMEOUT);
    }

    private static boolean isTwinFallbackEnabled(final Signal<?> signal) {

        final var liveChannelFallbackStrategy =
                signal.getDittoHeaders().getLiveChannelTimeoutStrategy().orElse(LiveChannelTimeoutStrategy.FAIL);
        return LiveChannelTimeoutStrategy.USE_TWIN == liveChannelFallbackStrategy;
    }

    private static DittoHeaders getAdditionalLiveResponseHeaders(final DittoHeaders responseHeaders) {

        final var liveChannelConditionMatched = responseHeaders.getOrDefault(
                DittoHeaderDefinition.LIVE_CHANNEL_CONDITION_MATCHED.getKey(), Boolean.TRUE.toString());
        final DittoHeadersBuilder<?, ?> dittoHeadersBuilder = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.LIVE_CHANNEL_CONDITION_MATCHED.getKey(), liveChannelConditionMatched)
                .responseRequired(false);
        return dittoHeadersBuilder.build();
    }

}
