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
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.internal.models.signal.correlation.CommandAndCommandResponseMatchingValidator;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommand;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.japi.pf.ReceiveBuilder;

/**
 * An actor to deal with a live thing query command expecting a response and requesting custom acknowledgements.
 * The sender of the live command is an actor in order to apply policy enforcement on the response.
 */
final class LiveResponseAndAcknowledgementForwarder extends AbstractActor {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final ActorRef messageReceiver;
    private final Set<AcknowledgementLabel> pendingAcknowledgements;
    private final ThingQueryCommand<?> thingQueryCommand;
    private final CommandAndCommandResponseMatchingValidator responseValidator;
    private boolean responseReceived = false;

    @Nullable
    private ActorRef messageSender = null;

    @SuppressWarnings("unused")
    private LiveResponseAndAcknowledgementForwarder(final ThingQueryCommand<?> thingQueryCommand,
            final ActorRef messageReceiver) {

        pendingAcknowledgements = new HashSet<>();
        this.messageReceiver = messageReceiver;
        this.thingQueryCommand = thingQueryCommand;
        responseValidator = CommandAndCommandResponseMatchingValidator.getInstance();
        getContext().setReceiveTimeout(this.thingQueryCommand.getDittoHeaders().getTimeout().orElse(DEFAULT_TIMEOUT));
        for (final var ackRequest : this.thingQueryCommand.getDittoHeaders().getAcknowledgementRequests()) {
            pendingAcknowledgements.add(ackRequest.getLabel());
        }
    }

    /**
     * Create Props object for this actor.
     *
     * @param thingQueryCommand The live command whose acknowledgements and responses this actor listens for.
     * @param messageReceiver Receiver of the message to publish.
     * @return The Props object.
     */
    public static Props props(final ThingQueryCommand<?> thingQueryCommand, final ActorRef messageReceiver) {
        return Props.create(LiveResponseAndAcknowledgementForwarder.class, thingQueryCommand, messageReceiver);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(Acknowledgement.class, this::onAcknowledgement)
                .match(Acknowledgements.class, this::onAcknowledgements)
                .match(CommandResponse.class, this::onCommandResponse)
                .match(ReceiveTimeout.class, this::stopSelf)
                .matchAny(this::sendMessage)
                .build();
    }

    private void sendMessage(final Object message) {
        log.debug("Got message to send <{}>", message);
        messageSender = getSender();
        messageReceiver.tell(message, getSelf());
    }

    private void onAcknowledgement(final Acknowledgement ack) {
        log.withCorrelationId(ack).debug("Got <{}>", ack);
        pendingAcknowledgements.remove(ack.getLabel());
        forwardToAcknowledgementRequester(ack);
    }

    private void onAcknowledgements(final Acknowledgements acks) {
        log.withCorrelationId(acks).debug("Got <{}>", acks);
        for (final var ack : acks) {
            pendingAcknowledgements.remove(ack.getLabel());
        }
        forwardToAcknowledgementRequester(acks);
    }

    private void onCommandResponse(final CommandResponse<?> incomingResponse) {
        final boolean validResponse = isValidResponse(incomingResponse);
        log.withCorrelationId(incomingResponse)
                .info("Got <{}>, valid=<{}>", incomingResponse, validResponse);
        if (validResponse) {
            responseReceived = true;
            pendingAcknowledgements.remove(DittoAcknowledgementLabel.LIVE_RESPONSE);
            if (messageSender != null) {
                messageSender.tell(incomingResponse, ActorRef.noSender());
                // as the message sender was a temporary one based on "ask" pattern, set it to "null" after first use:
                messageSender = null;
                checkCompletion();
            } else {
                log.withCorrelationId(incomingResponse)
                        .error("Got response without receiving command");
                stopSelf("Message sender not found");
            }
        } else {
            forwardToAcknowledgementRequester(incomingResponse);
        }
    }

    private void forwardToAcknowledgementRequester(final CommandResponse<?> ackResponse) {
        final String ackregatorAddress = ackResponse.getDittoHeaders()
                .getOrDefault(DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey(),
                        thingQueryCommand.getDittoHeaders()
                                .get(DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey()));
        if (null != ackregatorAddress) {
            final ActorSelection acknowledgementRequester = getContext().actorSelection(ackregatorAddress);
            log.withCorrelationId(ackResponse)
                    .debug("Received Acknowledgement / live CommandResponse, forwarding to acknowledgement " +
                            "aggregator <{}>: <{}>", acknowledgementRequester, ackResponse);
            acknowledgementRequester.forward(ackResponse, getContext());
        } else {
            log.withCorrelationId(ackResponse)
                    .error("Received Acknowledgement / live CommandResponse <{}> did not contain header of " +
                                    "acknowledgement aggregator address: {}", ackResponse.getClass().getSimpleName(),
                            ackResponse.getDittoHeaders());
        }
    }

    private void checkCompletion() {
        if (responseReceived && pendingAcknowledgements.isEmpty()) {
            stopSelf("All responses and acknowledgements delivered");
        }
    }

    private void stopSelf(final Object trigger) {
        log.debug("Stopping due to <{}>", trigger);
        getContext().cancelReceiveTimeout();
        getContext().stop(getSelf());
    }

    private boolean isValidResponse(final CommandResponse<?> response) {
        return responseValidator.apply(thingQueryCommand, response).isSuccess();
    }
}
