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
package org.eclipse.ditto.edge.service.acknowledgements;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.acks.AbstractCommandAckRequestSetter;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.acks.CommandResponseAcknowledgementProvider;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.models.signal.correlation.MatchingValidationResult;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;

import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.actor.Address;
import akka.cluster.Cluster;
import akka.japi.pf.PFBuilder;
import scala.PartialFunction;

/**
 * Starting an acknowledgement aggregator actor is more complex than simply call {@code actorOf}.
 * Thus starting logic is worth to be handled within its own class.
 *
 * @since 1.1.0
 */
public final class AcknowledgementAggregatorActorStarter {

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(AcknowledgementAggregatorActorStarter.class);

    private final ActorRefFactory actorRefFactory;
    private final Duration maxTimeout;
    private final HeaderTranslator headerTranslator;
    private final PartialFunction<Signal<?>, Signal<?>> ackRequestSetter;
    private final Collection<CommandResponseAcknowledgementProvider<?>> responseAcknowledgementProviders;
    @Nullable private final Consumer<MatchingValidationResult.Failure> matchingValidationFailureConsumer;
    private final Address selfRemoteAddress;
    private int childCounter;

    private AcknowledgementAggregatorActorStarter(final ActorRefFactory actorRefFactory,
            final Duration maxTimeout,
            final HeaderTranslator headerTranslator,
            @Nullable final Consumer<MatchingValidationResult.Failure> matchingValidationFailureConsumer,
            final PartialFunction<Signal<?>, Signal<?>> ackRequestSetter,
            final Collection<CommandResponseAcknowledgementProvider<?>> responseAcknowledgementProviders) {

        this.actorRefFactory = checkNotNull(actorRefFactory, "actorRefFactory");
        this.maxTimeout = checkNotNull(maxTimeout, "maxTimeout");
        this.headerTranslator = checkNotNull(headerTranslator, "headerTranslator");
        this.matchingValidationFailureConsumer = matchingValidationFailureConsumer;
        this.ackRequestSetter = ackRequestSetter;
        this.responseAcknowledgementProviders = responseAcknowledgementProviders;
        selfRemoteAddress = Cluster.get(actorRefFactory.systemImpl()).selfUniqueAddress().address();
        childCounter = 0;
    }

    /**
     * Returns an instance of {@code AcknowledgementAggregatorActorStarter}.
     *
     * @param actorRefFactory the actorRefFactory to start the aggregator actor in.
     * @param acknowledgementConfig provides configuration setting regarding acknowledgement handling.
     * @param headerTranslator translates headers from external sources or to external sources.
     * response over a channel to the user.
     * @param matchingValidationFailureConsumer optional handler for response validation failures.
     * @param ackRequestSetters a collection of {@code ackRequestSetters} which are used to enhance inbound signals with
     * (implicit) acknowledgement requests.
     * @param responseAcknowledgementProviders a collection of Acknowledgement providers which provide Acks based on
     * processed command responses.
     * @return a means to start an acknowledgement forwarder actor.
     * @throws NullPointerException if any argument but {@code responseValidationFailureContextConsumer} is {@code null}.
     */
    public static AcknowledgementAggregatorActorStarter of(final ActorRefFactory actorRefFactory,
            final AcknowledgementConfig acknowledgementConfig,
            final HeaderTranslator headerTranslator,
            @Nullable final Consumer<MatchingValidationResult.Failure> matchingValidationFailureConsumer,
            final Collection<AbstractCommandAckRequestSetter<?>> ackRequestSetters,
            final Collection<CommandResponseAcknowledgementProvider<?>> responseAcknowledgementProviders) {

        return of(actorRefFactory,
                acknowledgementConfig.getForwarderFallbackTimeout(),
                headerTranslator,
                matchingValidationFailureConsumer,
                ackRequestSetters,
                responseAcknowledgementProviders);
    }

    /**
     * Returns an instance of {@code AcknowledgementAggregatorActorStarter}.
     *
     * @param actorRefFactory the actorRefFactory to start the aggregator actor in.
     * @param maxTimeout the maximum timeout.
     * @param headerTranslator translates headers from external sources or to external sources.
     * response over a channel to the user.
     * @param matchingValidationFailureConsumer optional handler for response validation failures.
     * @param ackRequestSetters a collection of {@code ackRequestSetters} which are used to enhance inbound signals with
     * (implicit) acknowledgement requests.
     * @param responseAcknowledgementProviders a collection of Acknowledgement providers which provide Acks based on
     * processed command responses.
     * @return a means to start an acknowledgement forwarder actor.
     * @throws NullPointerException if any argument but {@code responseValidationFailureContextConsumer} is {@code null}.
     */
    public static AcknowledgementAggregatorActorStarter of(final ActorRefFactory actorRefFactory,
            final Duration maxTimeout,
            final HeaderTranslator headerTranslator,
            @Nullable final Consumer<MatchingValidationResult.Failure> matchingValidationFailureConsumer,
            final Collection<AbstractCommandAckRequestSetter<?>> ackRequestSetters,
            final Collection<CommandResponseAcknowledgementProvider<?>> responseAcknowledgementProviders) {

        return new AcknowledgementAggregatorActorStarter(actorRefFactory,
                maxTimeout,
                headerTranslator,
                matchingValidationFailureConsumer,
                buildAckRequestSetter(ackRequestSetters),
                responseAcknowledgementProviders);
    }


    /**
     * Checks if the {@code AcknowledgementAggregatorActor} should be started for the passed {@code signal}.
     * Public because this is also used in unit tests.
     *
     * @param signal the signal to check for whether the ack aggregator actor should be started.
     * @return whether the ack aggregator actor should be started.
     */
    public static boolean shouldStartForIncoming(final Signal<?> signal) {
        final boolean result;

        final var isLiveSignal = Signal.isChannelLive(signal);
        final var isChannelSmart = Signal.isChannelSmart(signal);
        final Collection<AcknowledgementRequest> ackRequests = signal.getDittoHeaders().getAcknowledgementRequests();
        if (signal instanceof Command<?> command &&
                Command.Category.isEntityModifyingCommand(command.getCategory()) &&
                Command.isThingCommand(signal) &&
                !isLiveSignal) {
            result = ackRequests.stream().anyMatch(AcknowledgementForwarderActorStarter::isNotLiveResponse);
        } else if (Command.isMessageCommand(signal) || isLiveSignal && Command.isThingCommand(signal)) {
            result = ackRequests.stream().anyMatch(AcknowledgementForwarderActorStarter::isNotTwinPersisted);
        } else {
            result = isChannelSmart;
        }

        return result;
    }


    /**
     * Start an acknowledgement aggregator actor if needed.
     *
     * @param <T> type of the result.
     * @param signal the signal to start the aggregator actor for.
     * @param timeoutOverride duration to override the timeout of the signal.
     * @param responseSignalConsumer consumer of the aggregated response or error.
     * @param ackregatorStartedFunction what to do if the aggregator actor started. The first argument is
     * the signal after setting requested-acks and response-required.
     * @param ackregatorNotStartedFunction what to do if the aggregator actor did not start.
     * @return the result.
     */
    public <T> T start(final Signal<?> signal,
            @Nullable final Duration timeoutOverride,
            final Function<Object, T> responseSignalConsumer,
            final BiFunction<Signal<?>, ActorRef, T> ackregatorStartedFunction,
            final Function<Signal<?>, T> ackregatorNotStartedFunction) {

        return preprocess(signal,
                (originatingSignal, shouldStart) -> {
                    final var entityIdOptional = WithEntityId.getEntityId(signal);
                    if (shouldStart && entityIdOptional.isPresent() &&
                            originatingSignal instanceof Command<?> originatingCommand) {
                        return doStart(entityIdOptional.get(),
                                originatingCommand,
                                timeoutOverride,
                                responseSignalConsumer::apply,
                                (ackregator, adjustedSignal) ->
                                        ackregatorStartedFunction.apply(adjustedSignal, ackregator)
                        );
                    } else {
                        return ackregatorNotStartedFunction.apply(originatingSignal);
                    }
                },
                responseSignalConsumer
        );
    }

    /**
     * Preprocess a signal for starting acknowledgement aggregator actors.
     *
     * @param signal the signal for which an acknowledgement aggregator should start.
     * @param preprocessor what to do. The first parameter is the signal with requested-acks and response-required
     * set. The second parameter is whether an acknowledgement aggregator should start.
     * @param onInvalidHeader what to do if the headers are invalid.
     * @param <T> the type of results.
     * @return the result.
     */
    public <T> T preprocess(final Signal<?> signal,
            final BiFunction<Signal<?>, Boolean, T> preprocessor,
            final Function<? super DittoHeaderInvalidException, T> onInvalidHeader) {

        final var signalToForward = ackRequestSetter.apply(signal);
        final var headerInvalid = getDittoHeaderInvalidException(signalToForward);

        return headerInvalid.map(onInvalidHeader)
                .orElseGet(() -> preprocessor.apply(signalToForward, shouldStartForIncoming(signalToForward)));
    }

    /**
     * Start an acknowledgement aggregator actor for a signal with acknowledgement requests.
     *
     * @param <T> type of results.
     * @param entityId the entity ID of the originating signal.
     * @param command the originating command. Must have nonempty acknowledgement requests.
     * @param timeoutOverride override timeout of the signal by another duration.
     * @param responseSignalConsumer consumer of the aggregated response or error.
     * @param forwarderStartedFunction what to do after the aggregator actor started.
     * @return the result.
     */
    public <T> T doStart(final EntityId entityId,
            final Command<?> command,
            @Nullable final Duration timeoutOverride,
            final Consumer<Object> responseSignalConsumer,
            final BiFunction<ActorRef, Signal<?>, T> forwarderStartedFunction) {

        final ActorRef ackgregator =
                startAckAggregatorActor(entityId, command, timeoutOverride, responseSignalConsumer);
        final String addressSerializationFormat = ackgregator.path()
                .toSerializationFormatWithAddress(selfRemoteAddress);
        final Signal<?> adjustedSignal = command.setDittoHeaders(command.getDittoHeaders().toBuilder()
                .putHeader(DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey(), addressSerializationFormat)
                .build());
        return forwarderStartedFunction.apply(ackgregator, adjustedSignal);
    }

    private <C extends Command<?>> ActorRef startAckAggregatorActor(final EntityId entityId,
            final C command,
            @Nullable final Duration timeoutOverride,
            final Consumer<Object> responseSignalConsumer) {

        final CommandResponseAcknowledgementProvider<C> acknowledgementProvider =
                findRelevantAcknowledgementProvider(command)
                        .orElseThrow(() -> {
                            LOGGER.withCorrelationId(command)
                                    .error("Tried to start acknowledgement aggregator for command <{}> but don't " +
                                            "know any applicable acknowledgement providers for this.", command);
                            return DittoInternalErrorException.newBuilder()
                                    .dittoHeaders(command.getDittoHeaders())
                                    .build();
                        });
        final var props = AcknowledgementAggregatorActor.props(entityId,
                command,
                timeoutOverride,
                maxTimeout,
                headerTranslator,
                responseSignalConsumer,
                matchingValidationFailureConsumer,
                acknowledgementProvider);

        return actorRefFactory.actorOf(props, getNextActorName(command.getDittoHeaders()));
    }

    @SuppressWarnings("unchecked")
    private <C extends Command<?>> Optional<CommandResponseAcknowledgementProvider<C>> findRelevantAcknowledgementProvider(
            final C signal) {
        return responseAcknowledgementProviders.stream()
                .filter(provider -> provider.getCommandClass().isAssignableFrom(signal.getClass()))
                .map(provider -> (CommandResponseAcknowledgementProvider<C>) provider)
                .findAny();
    }

    private String getNextActorName(final DittoHeaders dittoHeaders) {
        final var correlationId = dittoHeaders.getCorrelationId()
                .map(cid -> URLEncoder.encode(cid, StandardCharsets.UTF_8))
                .orElse("_");

        return String.format("ackr%x-%s", childCounter++, correlationId);
    }

    @SuppressWarnings({"unchecked", "rawtypes", "java:S3740"})
    private static PartialFunction<Signal<?>, Signal<?>> buildAckRequestSetter(
            final Collection<AbstractCommandAckRequestSetter<?>> ackRequestSetters) {

        PFBuilder<Signal<?>, Signal<?>> pfBuilder = new PFBuilder<>();
        // unavoidable raw type due to the lack of existential type
        for (final AbstractCommandAckRequestSetter ackRequestSetter : ackRequestSetters) {
            pfBuilder = pfBuilder.match(ackRequestSetter.getMatchedClass(),
                    ackRequestSetter::isApplicable,
                    s -> (Signal<?>) ackRequestSetter.apply(s));
        }

        return pfBuilder.matchAny(x -> x).build();
    }

    private static Optional<DittoHeaderInvalidException> getDittoHeaderInvalidException(final Signal<?> signal) {
        final Optional<DittoHeaderInvalidException> result;

        final var dittoHeaders = signal.getDittoHeaders();
        if (isTimeoutHeaderInvalid(dittoHeaders)) {
            final var invalidHeaderKey = DittoHeaderDefinition.TIMEOUT.getKey();
            final var message = String.format("The value of the header '%s' must not be zero if " +
                    "response or acknowledgements are requested.", invalidHeaderKey);
            result = Optional.of(DittoHeaderInvalidException.newBuilder()
                    .withInvalidHeaderKey(invalidHeaderKey)
                    .message(message)
                    .description("Please provide a positive timeout.")
                    .dittoHeaders(dittoHeaders)
                    .build());
        } else {
            result = Optional.empty();
        }

        return result;
    }

    private static boolean isTimeoutHeaderInvalid(final DittoHeaders dittoHeaders) {
        final boolean result;

        final var isTimeoutZero = dittoHeaders.getTimeout().filter(Duration::isZero).isPresent();
        if (isTimeoutZero) {
            if (dittoHeaders.isResponseRequired()) {
                result = true;
            } else {
                final var acknowledgementRequests = dittoHeaders.getAcknowledgementRequests();
                result = !acknowledgementRequests.isEmpty();
            }
        } else {
            result = false;
        }

        return result;
    }

}
