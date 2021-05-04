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
package org.eclipse.ditto.internal.models.acks;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.internal.models.acks.AcknowledgementForwarderActorStarter.isLiveSignal;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.ditto.base.model.acks.AbstractCommandAckRequestSetter;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.models.acks.config.AcknowledgementConfig;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.protocol.HeaderTranslator;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommand;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.PFBuilder;
import scala.PartialFunction;

/**
 * Starting an acknowledgement aggregator actor is more complex than simply call {@code actorOf}.
 * Thus starting logic is worth to be handled within its own class.
 *
 * @since 1.1.0
 */
public final class AcknowledgementAggregatorActorStarter {

    protected final ActorContext actorContext;
    protected final AcknowledgementConfig acknowledgementConfig;
    protected final HeaderTranslator headerTranslator;
    protected final PartialFunction<Signal<?>, Signal<?>> ackRequestSetter;

    private int childCounter = 0;

    private AcknowledgementAggregatorActorStarter(final ActorContext context,
            final AcknowledgementConfig acknowledgementConfig,
            final HeaderTranslator headerTranslator,
            final PartialFunction<Signal<?>, Signal<?>> ackRequestSetter) {

        actorContext = checkNotNull(context, "context");
        this.ackRequestSetter = ackRequestSetter;
        this.acknowledgementConfig = checkNotNull(acknowledgementConfig, "acknowledgementConfig");
        this.headerTranslator = checkNotNull(headerTranslator, "headerTranslator");
    }

    /**
     * Returns an instance of {@code AcknowledgementAggregatorActorStarter}.
     *
     * @param context the context to start the aggregator actor in.
     * @param acknowledgementConfig provides configuration setting regarding acknowledgement handling.
     * @param headerTranslator translates headers from external sources or to external sources.
     * response over a channel to the user.
     * @return a means to start an acknowledgement forwarder actor.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static AcknowledgementAggregatorActorStarter of(final ActorContext context,
            final AcknowledgementConfig acknowledgementConfig,
            final HeaderTranslator headerTranslator,
            final AbstractCommandAckRequestSetter<?>... ackRequestSetters) {

        return new AcknowledgementAggregatorActorStarter(context, acknowledgementConfig,
                headerTranslator, buildAckRequestSetter(ackRequestSetters));
    }

    /**
     * Start an acknowledgement aggregator actor if needed.
     *
     * @param signal the signal to start the aggregator actor for.
     * @param responseSignalConsumer consumer of the aggregated response or error.
     * @param ackregatorStartedFunction what to do if the aggregator actor started. The first argument is
     * the signal after setting requested-acks and response-required.
     * @param ackregatorNotStartedFunction what to do if the aggregator actor did not start.
     * @param <T> type of the result.
     * @return the result.
     */
    public <T> T start(final Signal<?> signal,
            final Function<Object, T> responseSignalConsumer,
            final BiFunction<Signal<?>, ActorRef, T> ackregatorStartedFunction,
            final Function<Signal<?>, T> ackregatorNotStartedFunction) {

        return preprocess(signal,
                (s, shouldStart) -> {
                    final Optional<ThingId> thingIdOptional = WithEntityId.getEntityIdOfType(ThingId.class, s);
                    if (shouldStart && thingIdOptional.isPresent()) {
                        return doStart(thingIdOptional.get(), s.getDittoHeaders(), responseSignalConsumer::apply,
                                ackregator -> ackregatorStartedFunction.apply(s, ackregator));
                    } else {
                        return ackregatorNotStartedFunction.apply(s);
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
        final Signal<?> signalToForward = ackRequestSetter.apply(signal);
        final Optional<DittoHeaderInvalidException> headerInvalid = getDittoHeaderInvalidException(signalToForward);
        return headerInvalid.map(onInvalidHeader)
                .orElseGet(() -> preprocessor.apply(signalToForward, shouldStartForIncoming(signalToForward)));
    }

    /**
     * Start an acknowledgement aggregator actor for a signal with acknowledgement requests.
     *
     * @param thingId the ThingId of the originating signal signal.
     * @param dittoHeaders The headers of the originating signal. Must have nonempty acknowledgement requests.
     * @param responseSignalConsumer consumer of the aggregated response or error.
     * @param forwarderStartedFunction what to do after the aggregator actor started.
     * @param <T> type of results.
     * @return the result.
     */
    public <T> T doStart(final ThingId thingId,
            final DittoHeaders dittoHeaders,
            final Consumer<Object> responseSignalConsumer,
            final Function<ActorRef, T> forwarderStartedFunction) {
        return forwarderStartedFunction.apply(startAckAggregatorActor(thingId, dittoHeaders, responseSignalConsumer));
    }

    private ActorRef startAckAggregatorActor(final ThingId thingId, final DittoHeaders dittoHeaders,
            final Consumer<Object> responseSignalConsumer) {
        final Props props = AcknowledgementAggregatorActor.props(thingId, dittoHeaders, acknowledgementConfig, headerTranslator,
                responseSignalConsumer);
        final String actorName = getNextActorName(dittoHeaders);
        return actorContext.actorOf(props, actorName);
    }

    private String getNextActorName(final DittoHeaders dittoHeaders) {
        final String correlationId = dittoHeaders
                .getCorrelationId()
                .map(cid -> URLEncoder.encode(cid, StandardCharsets.UTF_8))
                .orElse("_");
        return String.format("ackr%x-%s", childCounter++, correlationId);
    }

    @SuppressWarnings({"unchecked", "rawtypes", "java:S3740"})
    private static PartialFunction<Signal<?>, Signal<?>> buildAckRequestSetter(
            final AbstractCommandAckRequestSetter<?>... ackRequestSetters) {
        PFBuilder<Signal<?>, Signal<?>> pfBuilder = new PFBuilder<>();
        // unavoidable raw type due to the lack of existential type
        for (final AbstractCommandAckRequestSetter ackRequestSetter : ackRequestSetters) {
            pfBuilder = pfBuilder.match(ackRequestSetter.getMatchedClass(), ackRequestSetter::isApplicable,
                    s -> (Signal<?>) ackRequestSetter.apply(s));
        }
        return pfBuilder.matchAny(x -> x).build();
    }

    private static Optional<DittoHeaderInvalidException> getDittoHeaderInvalidException(final Signal<?> signal) {
        final DittoHeaders dittoHeaders = signal.getDittoHeaders();
        final boolean isTimeoutZero = dittoHeaders.getTimeout().map(Duration::isZero).orElse(false);
        final boolean isTimeoutHeaderInvalid = isTimeoutZero &&
                (dittoHeaders.isResponseRequired() || !dittoHeaders.getAcknowledgementRequests().isEmpty());
        if (isTimeoutHeaderInvalid) {
            final var invalidHeaderKey = DittoHeaderDefinition.TIMEOUT.getKey();
            final String message = String.format("The value of the header '%s' must not be zero if " +
                    "response or acknowledgements are requested.", invalidHeaderKey);
            return Optional.of(DittoHeaderInvalidException.newBuilder()
                    .withInvalidHeaderKey(invalidHeaderKey)
                    .message(message)
                    .description("Please provide a positive timeout.")
                    .dittoHeaders(dittoHeaders)
                    .build());
        } else {
            return Optional.empty();
        }
    }

    static boolean shouldStartForIncoming(final Signal<?> signal) {
        final boolean isLiveSignal = isLiveSignal(signal);
        final Collection<AcknowledgementRequest> ackRequests = signal.getDittoHeaders().getAcknowledgementRequests();
        if (signal instanceof ThingModifyCommand && !isLiveSignal) {
            return ackRequests.stream().anyMatch(AcknowledgementForwarderActorStarter::isNotLiveResponse);
        } else if (signal instanceof MessageCommand || (isLiveSignal && signal instanceof ThingCommand)) {
            return ackRequests.stream().anyMatch(AcknowledgementForwarderActorStarter::isNotTwinPersisted);
        } else {
            return false;
        }
    }
}
