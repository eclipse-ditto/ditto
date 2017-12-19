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
package org.eclipse.ditto.services.amqpbridge.messaging;

import static java.util.stream.Collectors.toMap;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.TextMessage;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersBuilder;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocoladapter.JsonifiableAdaptable;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import kamon.Kamon;
import kamon.trace.TraceContext;
import scala.Option;

/**
 * This Actor processes incoming {@link Command}s and dispatches them via {@link DistributedPubSubMediator} to a
 * consumer actor.
 */
final class CommandProcessorActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    static final String ACTOR_NAME_PREFIX = "amqpCommandProcessor-";

    private static final String REPLY_TO_HEADER = "replyTo";
    private static final DittoProtocolAdapter PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef pubSubMediator;
    private final String pubSubTargetActorPath;
    private final AuthorizationSubject authorizationSubject;
    private final Map<String, TraceContext> traces;

    private CommandProcessorActor(final ActorRef pubSubMediator, final String pubSubTargetActorPath,
            final AuthorizationSubject authorizationSubject) {
        this.pubSubMediator = pubSubMediator;
        this.pubSubTargetActorPath = pubSubTargetActorPath;
        this.authorizationSubject = authorizationSubject;
        traces = new HashMap<>();
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param pubSubMediator the akka pubsub mediator actor.
     * @param pubSubTargetActorPath the path of the command consuming actor (via pubsub).
     * @param authorizationSubject the authorized subject that are set in command headers.
     * @return the Akka configuration Props object
     */
    static Props props(final ActorRef pubSubMediator, final String pubSubTargetActorPath,
            final AuthorizationSubject authorizationSubject) {
        return Props.create(CommandProcessorActor.class, new Creator<CommandProcessorActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public CommandProcessorActor create() {
                return new CommandProcessorActor(pubSubMediator, pubSubTargetActorPath, authorizationSubject);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(Message.class, this::handleMessage)
                .match(CommandResponse.class, this::handleCommandResponse)
                .match(DittoRuntimeException.class, this::handleDittoRuntimeException)
                .match(Status.Failure.class, f -> log.error(f.cause(), "Got an unexpected failure."))
                .matchAny(m -> {
                    log.debug("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private void handleDittoRuntimeException(final DittoRuntimeException exception) {
        final ThingErrorResponse errorResponse = ThingErrorResponse.of(exception);

        logDittoRuntimeException(exception);
        handleCommandResponse(errorResponse);
    }

    private void logDittoRuntimeException(final DittoRuntimeException exception) {
        LogUtil.enhanceLogWithCorrelationId(log, exception);

        final String msgTemplate = "Got DittoRuntimeException '{}' when command via AMQP was processed: {}";
        log.info(msgTemplate, exception.getErrorCode(), exception.getMessage());
    }

    private void handleCommandResponse(final CommandResponse response) {
        final Optional<String> correlationId = response.getDittoHeaders().getCorrelationId();
        LogUtil.enhanceLogWithCorrelationId(log, correlationId);

        if (response.getStatusCodeValue() < HttpStatusCode.BAD_REQUEST.toInt()) {
            log.debug("Received response: {}", response);
        } else {
            log.debug("Received error response: {}", response);
        }

        final Optional<TraceContext> traceContext = correlationId.map(traces::remove);
        if (traceContext.isPresent()) {
            traceContext.get().finish();
        } else {
            log.warning("Trace missing for response: '{}'", response);
        }
    }

    private void handleMessage(final Message message) throws JMSException {
        log.debug("Received Message: {}", message);

        final Command<?> command = buildCommandFromPublicProtocol(message);
        if (command != null) {
            traceCommand(command);

            log.info("Publishing '{}' from AMQP Message '{}'", command.getType(), message.getJMSMessageID());
            pubSubMediator.tell(new DistributedPubSubMediator.Send(pubSubTargetActorPath, command, true), getSelf());
        }
    }

    private void traceCommand(final Command<?> command) {
        command.getDittoHeaders().getCorrelationId().ifPresent(correlationId -> {
            final Option<String> token = Option.apply(correlationId);
            final TraceContext traceContext = Kamon.tracer().newContext("roundtrip.amqp_" + command.getType(), token);
            traceContext.addMetadata("command", command.getType());
            traces.put(correlationId, traceContext);
        });
    }

    private Command<?> buildCommandFromPublicProtocol(final Message message) throws JMSException {
        final String commandJsonString = extractCommandStringFromMessage(message);
        final Map<String, String> headers = extractHeadersMapFromJmsMessage(message);
        final DittoHeadersBuilder dittoHeadersBuilder = DittoHeaders.newBuilder(headers);

        final String replyTo = message.getJMSReplyTo() != null ? String.valueOf(message.getJMSReplyTo()) : null;
        if (replyTo != null) {
            dittoHeadersBuilder.putHeader(REPLY_TO_HEADER, replyTo);
        }

        final String jmsCorrelationId = message.getJMSCorrelationID() != null ? message.getJMSCorrelationID() :
                message.getJMSMessageID();

        dittoHeadersBuilder.authorizationSubjects(authorizationSubject.getId());

        try {
            final JsonObject publicCommandJsonObject = JsonFactory.newObject(commandJsonString);

            final JsonifiableAdaptable jsonifiableAdaptable =
                    ProtocolFactory.jsonifiableAdaptableFromJson(publicCommandJsonObject);

            // use correlationId from json headers if present
            final String correlationId = jsonifiableAdaptable.getHeaders()
                    .flatMap(DittoHeaders::getCorrelationId)
                    .orElse(jmsCorrelationId);

            dittoHeadersBuilder.correlationId(correlationId);
            LogUtil.enhanceLogWithCorrelationId(log, correlationId);
            log.debug("received public command: {}", jsonifiableAdaptable.toJsonString());

            // convert to internal command with DittoProtocolAdapter
            final Command<?> command = (Command<?>) PROTOCOL_ADAPTER.fromAdaptable(jsonifiableAdaptable);
            final DittoHeaders dittoHeaders = dittoHeadersBuilder.build();
            return command.setDittoHeaders(dittoHeaders);
        } catch (final DittoRuntimeException e) {
            log.info("Got DittoRuntimeException '{}' when command was parsed: {}", e.getErrorCode(), e.getMessage());
            return null;
        } catch (final Exception e) {
            log.info("Unexpected Exception: {}", e.getMessage(), e);
            return null;
        }
    }

    private String extractCommandStringFromMessage(final Message message) throws JMSException {
        if (message instanceof TextMessage) {
            return ((TextMessage) message).getText();
        } else if (message instanceof BytesMessage) {
            final BytesMessage bytesMessage = (BytesMessage) message;
            final long bodyLength = bytesMessage.getBodyLength();
            if (bodyLength >= Integer.MIN_VALUE && bodyLength <= Integer.MAX_VALUE) {
                final int length = (int) bodyLength;
                final ByteBuffer byteBuffer = ByteBuffer.allocate(length);
                bytesMessage.readBytes(byteBuffer.array());
                return new String(byteBuffer.array(), StandardCharsets.UTF_8);
            } else {
                throw new IllegalArgumentException("Message too large...");
            }
        } else {
            throw new IllegalArgumentException("Only messages of type TEXT or BYTE are supported.");
        }
    }

    private Map<String, String> extractHeadersMapFromJmsMessage(final Message message) throws JMSException {
        @SuppressWarnings("unchecked") final List<String> names = Collections.list(message.getPropertyNames());
        return names.stream()
                .map(key -> getPropertyAsEntry(message, key))
                .filter(Objects::nonNull)
                .collect(toMap(Entry::getKey, Entry::getValue));
    }

    private Entry<String, String> getPropertyAsEntry(final Message message, final String key) {
        try {
            return new AbstractMap.SimpleImmutableEntry<>(key, message.getObjectProperty(key).toString());
        } catch (final JMSException e) {
            log.debug("Property '{}' could not be read, dropping...", key);
            return null;
        }
    }

}
