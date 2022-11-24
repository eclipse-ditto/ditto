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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import org.eclipse.ditto.base.model.exceptions.AskException;
import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.base.model.headers.LiveChannelTimeoutStrategy;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.ErrorResponse;
import org.eclipse.ditto.base.model.signals.commands.exceptions.CommandTimeoutException;
import org.eclipse.ditto.internal.models.signal.CommandHeaderRestoration;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.persistentactors.TargetActorWithMessage;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommand;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommandResponse;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.pattern.AskTimeoutException;
import akka.pattern.Patterns;

/**
 * Functionality used in {@link ThingSupervisorActor} for the "smart channel selection"
 * (a.k.a. "live-channel-condition" parameter handling).
 */
final class SupervisorSmartChannelDispatching {

    private static final Duration DEFAULT_LIVE_TIMEOUT = Duration.ofSeconds(60L);
    private static final Duration DEFAULT_LOCAL_ASK_TIMEOUT = Duration.ofSeconds(5);

    private final ThreadSafeDittoLoggingAdapter log;
    private final ActorSelection thingsPersistenceActor;
    private final SupervisorLiveChannelDispatching liveChannelDispatching;

    SupervisorSmartChannelDispatching(final ThreadSafeDittoLoggingAdapter log,
            final ActorSelection thingsPersistenceActor,
            final SupervisorLiveChannelDispatching liveChannelDispatching) {
        this.log = log;
        this.thingsPersistenceActor = thingsPersistenceActor;
        this.liveChannelDispatching = liveChannelDispatching;
    }

    /**
     * Initiate the "smart channel selection" for the passed in {@code thingQueryCommand}, meaning the following steps
     * are performed until the returned CompletionStage is completed:
     * <ul>
     * <li>The thing persistence actor is asked for the {@code thingQueryCommand} in order to determine the persisted
     * state and perform potentially other conditions</li>
     * <li>Determines whether based on the {@code live-channel-condition} contained in the headers and the returned
     * response from the twin, the query command should be converted to a "live" command as well</li>
     * <li>Dispatches the live command via the {@link SupervisorLiveChannelDispatching}</li>
     * <li>Applies a fallback (if configured for the command) if no "live" response arrives in time</li>
     * </ul>
     *
     * @param thingQueryCommand the command to handle as "smart channel selection" query command
     * @param sender the original sender of the command required for responding the live response to
     * @return a CompletionStage which will be completed with a target actor and a message to send to this target actor
     */
    CompletionStage<TargetActorWithMessage> dispatchSmartChannelThingQueryCommand(
            final ThingQueryCommand<?> thingQueryCommand,
            final ActorRef sender) {

        return initSmartChannelSelection(thingQueryCommand)
                .thenCompose(twinQueryCommandResponse -> {
                    if (shouldAttemptLiveChannel(thingQueryCommand, twinQueryCommandResponse)) {
                        // perform conversion + publishing of live command
                        final ThingQueryCommand<?> liveCommand = toLiveCommand(thingQueryCommand);
                        return liveChannelDispatching.dispatchLiveChannelThingQueryCommand(liveCommand,
                                (command, receiver) ->
                                        liveChannelDispatching.prepareForPubSubPublishing(command, receiver,
                                                response ->
                                                        getFallbackResponseCaster(liveCommand, twinQueryCommandResponse)
                                                                .apply(response)
                                        )
                        );
                    } else {
                        // directly respond with twin response to sender
                        return CompletableFuture.completedStage(new TargetActorWithMessage(
                                sender,
                                twinQueryCommandResponse,
                                Duration.ZERO,
                                Function.identity()
                        ));
                    }
                });
    }

    private CompletionStage<ThingQueryCommandResponse<?>> initSmartChannelSelection(
            final ThingQueryCommand<?> thingQueryCommand) {
        final ThingQueryCommand<?> twinQueryCommand = ensureTwinChannel(thingQueryCommand);

        return Patterns.ask(thingsPersistenceActor, twinQueryCommand, DEFAULT_LOCAL_ASK_TIMEOUT)
                .thenApply(response -> handleSmartChannelTwinResponse(thingQueryCommand, response));
    }

    private static ThingQueryCommandResponse<?> handleSmartChannelTwinResponse(
            final ThingQueryCommand<?> enforcedThingQueryCommand,
            final Object response) {

        if (response instanceof ThingQueryCommandResponse<?> thingQueryCommandResponse) {
            final ThingQueryCommandResponse<?> twinResponseWithTwinChannel = setTwinChannel(thingQueryCommandResponse);

            return CommandHeaderRestoration.restoreCommandConnectivityHeaders(
                    twinResponseWithTwinChannel, enforcedThingQueryCommand.getDittoHeaders()
            );
        } else {
            throw new IllegalArgumentException("Response was not as expected a thing query command response!");
        }
    }

    private Function<Object, ThingQueryCommandResponse<?>> getFallbackResponseCaster(
            final ThingQueryCommand<?> liveCommand,
            final ThingQueryCommandResponse<?> twinResponse) {

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
                } else {
                    log.withCorrelationId(liveCommand)
                            .error("Unknown throwable during smart channel response casting: <{}: {}>",
                                    throwable.getClass().getSimpleName(), throwable.getMessage());
                    throw DittoRuntimeException.asDittoRuntimeException(throwable, cause ->
                            DittoInternalErrorException.newBuilder()
                                    .cause(cause)
                                    .dittoHeaders(liveCommand.getDittoHeaders())
                                    .build()
                    );
                }
            }

            log.withCorrelationId(liveCommand)
                    .error("Unknown response during smart channel response casting: {}", response);
            throw DittoInternalErrorException.newBuilder()
                    .dittoHeaders(liveCommand.getDittoHeaders())
                    .build();
        };
    }

    private static boolean shouldAttemptLiveChannel(final ThingQueryCommand<?> command,
            final ThingQueryCommandResponse<?> twinResponse) {

        return isLiveChannelConditionMatched(command, twinResponse) || isLiveQueryCommandWithTimeoutStrategy(command);
    }

    private static ThingQueryCommand<?> toLiveCommand(final ThingQueryCommand<?> command) {

        return command.setDittoHeaders(command.getDittoHeaders().toBuilder()
                .liveChannelCondition(null)
                .channel(Signal.CHANNEL_LIVE)
                .build());
    }

    private static ThingQueryCommand<?> ensureTwinChannel(final ThingQueryCommand<?> command) {

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
