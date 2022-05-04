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
package org.eclipse.ditto.things.service.enforcement;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.internal.models.signal.correlation.CommandAndCommandResponseMatchingValidator;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.japi.pf.ReceiveBuilder;

/**
 * An actor to deal with a live thing query command expecting a response and requesting custom acknowledgements.
 * The sender of the live command is an actor in Concierge in order to apply policy enforcement on the response.
 * As a result, custom acknowledgements are also sent to Concierge, which must forward them to the acknowledgement
 * aggregator actor.
 */
final class LiveResponseAndAcknowledgementForwarder extends AbstractActor {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    private final DittoDiagnosticLoggingAdapter logger;
    private final ActorRef messageReceiver;
    private final ActorRef acknowledgementReceiver;
    private final Set<AcknowledgementLabel> pendingAcknowledgements;
    private final Command<?> command;
    private final CommandAndCommandResponseMatchingValidator responseValidator;
    private boolean responseReceived = false;
    private ActorRef messageSender;

    @SuppressWarnings("unused")
    private LiveResponseAndAcknowledgementForwarder(final Command<?> command,
            final ActorRef messageReceiver,
            final ActorRef acknowledgementReceiver) {

        logger = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);
        pendingAcknowledgements = new HashSet<>();
        this.messageReceiver = messageReceiver;
        this.acknowledgementReceiver = acknowledgementReceiver;
        this.command = command;
        responseValidator = CommandAndCommandResponseMatchingValidator.getInstance();
        getContext().setReceiveTimeout(command.getDittoHeaders().getTimeout().orElse(DEFAULT_TIMEOUT));
        for (final var ackRequest : command.getDittoHeaders().getAcknowledgementRequests()) {
            pendingAcknowledgements.add(ackRequest.getLabel());
        }
    }

    /**
     * Create Props object for this actor.
     *
     * @param command The live command whose acknowledgements and responses this actor listens for.
     * @param messageReceiver Receiver of the message to publish.
     * @param acknowledgementReceiver Receiver of acknowledgements.
     * @return The Props object.
     */
    public static Props props(final Command<?> command,
            final ActorRef messageReceiver,
            final ActorRef acknowledgementReceiver) {
        return Props.create(LiveResponseAndAcknowledgementForwarder.class, command, messageReceiver,
                acknowledgementReceiver);
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
        logger.debug("Got message to send <{}>", message);
        messageSender = getSender();
        messageReceiver.tell(message, getSelf());
    }

    private void onAcknowledgement(final Acknowledgement ack) {
        logger.debug("Got <{}>", ack);
        pendingAcknowledgements.remove(ack.getLabel());
        acknowledgementReceiver.forward(ack, getContext());
    }

    private void onAcknowledgements(final Acknowledgements acks) {
        logger.debug("Got <{}>", acks);
        for (final var ack : acks) {
            pendingAcknowledgements.remove(ack.getLabel());
        }
        acknowledgementReceiver.forward(acks, getContext());
    }

    private void onCommandResponse(final CommandResponse<?> incomingResponse) {
        final CommandResponse<?> response = setOriginatorHeader(incomingResponse);
        final boolean validResponse = isValidResponse(response);
        logger.debug("Got <{}>, valid=<{}>", response, validResponse);
        if (validResponse) {
            responseReceived = true;
            if (messageSender != null) {
                messageSender.forward(response, getContext());
                checkCompletion();
            } else {
                logger.error("Got response without receiving command");
                stopSelf("Message sender not found");
            }
        } else {
            acknowledgementReceiver.forward(response, getContext());
        }
    }

    /**
     * Set the "ditto-originator" header to the primary authorization subject of a signal.
     * TODO TJ copied over from DefaultEnforcerActorFactory - consolidate!
     *
     * @param originalSignal A signal with authorization context.
     * @return A copy of the signal with the header "ditto-originator" set.
     */
    @SuppressWarnings("unchecked")
    static <T extends DittoHeadersSettable<?>> T setOriginatorHeader(final T originalSignal) {
        final DittoHeaders dittoHeaders = originalSignal.getDittoHeaders();
        final AuthorizationContext authorizationContext = dittoHeaders.getAuthorizationContext();
        return authorizationContext.getFirstAuthorizationSubject()
                .map(AuthorizationSubject::getId)
                .map(originatorSubjectId -> DittoHeaders.newBuilder(dittoHeaders)
                        .putHeader(DittoHeaderDefinition.ORIGINATOR.getKey(), originatorSubjectId)
                        .build())
                .map(originatorHeader -> (T) originalSignal.setDittoHeaders(originatorHeader))
                .orElse(originalSignal);
    }

    private void checkCompletion() {
        if (responseReceived && pendingAcknowledgements.isEmpty()) {
            stopSelf("All responses and acknowledgements delivered");
        }
    }

    private void stopSelf(final Object trigger) {
        logger.debug("Stopping due to <{}>", trigger);
        getContext().stop(getSelf());
    }

    private boolean isValidResponse(final CommandResponse<?> response) {
        return responseValidator.apply(command, response).isSuccess();
    }
}
