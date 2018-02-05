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

import java.util.Optional;
import java.util.concurrent.TimeUnit;

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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;

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
public final class CommandProcessorActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    static final String ACTOR_NAME_PREFIX = "amqpCommandProcessor-";

    private static final DittoProtocolAdapter PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final ActorRef pubSubMediator;
    private final String pubSubTargetActorPath;
    private final AuthorizationSubject authorizationSubject;
    private final Cache<String, TraceContext> traces;

    private CommandProcessorActor(final ActorRef pubSubMediator, final String pubSubTargetActorPath,
            final AuthorizationSubject authorizationSubject) {
        this.pubSubMediator = pubSubMediator;
        this.pubSubTargetActorPath = pubSubTargetActorPath;
        this.authorizationSubject = authorizationSubject;
        traces = CacheBuilder.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .removalListener((RemovalListener<String, TraceContext>) notification
                        -> log.info("Trace for {} expired.", notification.getKey()))
                .build();
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
                .match(InternalMessage.class, this::handle)
                .match(CommandResponse.class, this::handleCommandResponse)
                .match(DittoRuntimeException.class, this::handleDittoRuntimeException)
                .match(Status.Failure.class, f -> log.error(f.cause(), "Got an unexpected failure."))
                .matchAny(m -> {
                    log.debug("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private void handle(final InternalMessage m) {
        try {
            final Command<?> command = buildCommandFromPublicProtocol(m);
            if (command != null) {
                traceCommand(command);
                log.info("Publishing '{}' to '{}'", command.getType(), pubSubTargetActorPath);
                pubSubMediator.tell(new DistributedPubSubMediator.Send(pubSubTargetActorPath, command, true),
                        getSelf());
            }
        } finally {
            getSender().tell(m.getAckMessage(), self());
        }
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

        correlationId.ifPresent(cid -> {
            final TraceContext traceContext = traces.getIfPresent(cid);
            traces.invalidate(cid);
            if (traceContext != null) {
                traceContext.finish();
            } else {
                log.info("Trace missing for response: '{}'", response);
            }
        });
    }

    private void traceCommand(final Command<?> command) {
        command.getDittoHeaders().getCorrelationId().ifPresent(correlationId -> {
            final Option<String> token = Option.apply(correlationId);
            final TraceContext traceContext = Kamon.tracer().newContext("roundtrip.amqp_" + command.getType(), token);
            traceContext.addMetadata("command", command.getType());
            traces.put(correlationId, traceContext);
        });
    }

    private Command<?> buildCommandFromPublicProtocol(final InternalMessage m) {
        try {

            final DittoHeadersBuilder dittoHeadersBuilder = DittoHeaders.newBuilder(m.getDittoHeaders());
            // inject configured authorization subjects into command headers
            dittoHeadersBuilder.authorizationSubjects(authorizationSubject.getId());

            final JsonObject publicCommandJsonObject = JsonFactory.newObject(m.getCommandJsonString());
            final JsonifiableAdaptable jsonifiableAdaptable =
                    ProtocolFactory.jsonifiableAdaptableFromJson(publicCommandJsonObject);

            // use correlationId from json payload if present
            jsonifiableAdaptable.getHeaders()
                    .flatMap(DittoHeaders::getCorrelationId)
                    .ifPresent(dittoHeadersBuilder::correlationId);

            final DittoHeaders dittoHeaders = dittoHeadersBuilder.build();
            LogUtil.enhanceLogWithCorrelationId(log, dittoHeaders.getCorrelationId());
            log.debug("received public command: {}", jsonifiableAdaptable.toJsonString());
            // convert to internal command with DittoProtocolAdapter
            final Command<?> command = (Command<?>) PROTOCOL_ADAPTER.fromAdaptable(jsonifiableAdaptable);
            return command.setDittoHeaders(dittoHeaders);
        } catch (final DittoRuntimeException e) {
            log.info("Got DittoRuntimeException '{}' when command was parsed: {}", e.getErrorCode(), e.getMessage());
            return null;
        } catch (final Exception e) {
            log.info("Unexpected Exception: {}", e.getMessage(), e);
            return null;
        }
    }
}
