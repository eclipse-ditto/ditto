/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.messaging;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.ResourceStatus;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.services.connectivity.messaging.config.ConnectivityConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.DittoConnectivityConfig;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.DefaultConnectionMonitorRegistry;
import org.eclipse.ditto.services.models.acks.config.AcknowledgementConfig;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageBuilder;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.config.InstanceIdentifierSupplier;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionUnavailableException;

import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.pattern.Patterns;

/**
 * Base class for consumer actors that holds common fields and handles the address status.
 */
public abstract class BaseConsumerActor extends AbstractActorWithTimers {

    protected final String sourceAddress;
    protected final Source source;
    protected final ConnectionMonitor inboundMonitor;
    protected final ConnectionId connectionId;

    private final ActorRef messageMappingProcessor;
    private final AcknowledgementConfig acknowledgementConfig;

    @Nullable private ResourceStatus resourceStatus;

    protected BaseConsumerActor(final ConnectionId connectionId, final String sourceAddress,
            final ActorRef messageMappingProcessor, final Source source) {
        this.connectionId = checkNotNull(connectionId, "connectionId");
        this.sourceAddress = checkNotNull(sourceAddress, "sourceAddress");
        this.messageMappingProcessor = checkNotNull(messageMappingProcessor, "messageMappingProcessor");
        this.source = checkNotNull(source, "source");
        resetResourceStatus();

        final ConnectivityConfig connectivityConfig = DittoConnectivityConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config()));

        acknowledgementConfig = connectivityConfig.getAcknowledgementConfig();

        inboundMonitor = DefaultConnectionMonitorRegistry.fromConfig(connectivityConfig.getMonitoringConfig())
                .forInboundConsumed(connectionId, sourceAddress);
    }

    /**
     * @return the logging adapter of this actor.
     */
    protected abstract DittoDiagnosticLoggingAdapter log();

    /**
     * Send an external message to the mapping processor actor.
     * NOT thread-safe!
     *
     * @param message the external message
     * @param settle technically settle the incoming message. MUST be thread-safe.
     * @param reject technically reject the incoming message. MUST be thread-safe.
     */
    protected final void forwardToMappingActor(final ExternalMessage message, final Runnable settle,
            final Reject reject) {
        forwardAndAwaitAck(addSourceAndReplyTarget(message))
                .whenComplete((output, error) -> {
                    if (output != null) {
                        final List<CommandResponse<?>> failedResponses = output.getFailedResponses();
                        if (output.allExpectedResponsesArrived() && failedResponses.isEmpty()) {
                            settle.run();
                        } else {
                            log().debug("Rejecting due to failed responses <{}>", failedResponses);
                            reject.reject(true);
                        }
                    } else {
                        final DittoRuntimeException dittoRuntimeException =
                                DittoRuntimeException.asDittoRuntimeException(error, rootCause -> {
                                    // Redeliver and pray this unexpected error goes away
                                    log().debug("Rejecting due to error <{}>", rootCause);
                                    reject.reject(true);
                                    inboundFailure(rootCause);
                                    return null;
                                });
                        if (dittoRuntimeException != null) {
                            final HttpStatusCode status = dittoRuntimeException.getStatusCode();
                            log().debug("Rejecting due to error <{}>", dittoRuntimeException);
                            reject.reject(!status.isClientError() || status == HttpStatusCode.REQUEST_TIMEOUT);
                            inboundFailure(dittoRuntimeException);
                        }
                    }
                })
                .exceptionally(e -> {
                    log().error(e, "Unexpected error during manual acknowledgement.");
                    return null;
                });
    }

    /**
     * Send an error to the mapping processor actor to be published in the reply-target.
     *
     * @param message the error.
     */
    protected final void forwardToMappingActor(final DittoRuntimeException message) {
        final DittoRuntimeException messageWithReplyInformation =
                message.setDittoHeaders(enrichHeadersWithReplyInformation(message.getDittoHeaders()));
        messageMappingProcessor.tell(messageWithReplyInformation, ActorRef.noSender());
    }

    protected void resetResourceStatus() {
        resourceStatus = ConnectivityModelFactory.newSourceStatus(getInstanceIdentifier(),
                ConnectivityStatus.OPEN, sourceAddress, "Started at " + Instant.now());
    }

    protected ResourceStatus getCurrentSourceStatus() {
        return ConnectivityModelFactory.newSourceStatus(getInstanceIdentifier(),
                resourceStatus != null ? resourceStatus.getStatus() : ConnectivityStatus.UNKNOWN,
                sourceAddress,
                resourceStatus != null ? resourceStatus.getStatusDetails().orElse(null) : null);
    }

    protected void handleAddressStatus(final ResourceStatus resourceStatus) {
        if (resourceStatus.getResourceType() == ResourceStatus.ResourceType.UNKNOWN) {
            this.resourceStatus = ConnectivityModelFactory.newSourceStatus(getInstanceIdentifier(),
                    resourceStatus.getStatus(), sourceAddress,
                    resourceStatus.getStatusDetails().orElse(null));
        } else {
            this.resourceStatus = resourceStatus;
        }
    }

    protected void inboundFailure(final Throwable error) {
        final DittoRuntimeException dittoRuntimeException = DittoRuntimeException.asDittoRuntimeException(
                error,
                e -> {
                    log().error(e, "Inbound failure");
                    return ConnectionUnavailableException.newBuilder(connectionId).build();
                }
        );
        inboundMonitor.failure(dittoRuntimeException);
    }

    private CompletionStage<ResponseCollectorActor.Output> forwardAndAwaitAck(final Object message) {
        // 1. start per-inbound-signal actor to collect acks of all thing-modify-commands mapped from incoming signal
        final Duration collectorLifetime = acknowledgementConfig.getCollectorFallbackLifetime();
        final Duration askTimeout = acknowledgementConfig.getCollectorFallbackAskTimeout();
        final ActorRef responseCollector = getContext().actorOf(ResponseCollectorActor.props(collectorLifetime));
        // 2. forward message to mapping processor actor with response collector actor as sender
        // message mapping processor actor will set the number of expected acks (can be 0)
        // and start the same amount of ack aggregator actors
        messageMappingProcessor.tell(message, responseCollector);
        // 3. ask response collector actor to get the collected responses in a future

        return Patterns.ask(responseCollector, ResponseCollectorActor.query(), askTimeout).thenCompose(output -> {
            if (output instanceof ResponseCollectorActor.Output) {
                return CompletableFuture.completedFuture((ResponseCollectorActor.Output) output);
            } else if (output instanceof Throwable) {
                return CompletableFuture.failedFuture((Throwable) output);
            } else {
                log().error("Expect ResponseCollectorActor.Output, got: <{}>", output);
                return CompletableFuture.failedFuture(new ClassCastException("Unexpected acknowledgement type."));
            }
        });
    }

    private ExternalMessage addSourceAndReplyTarget(final ExternalMessage message) {
        final ExternalMessageBuilder externalMessageBuilder =
                ExternalMessageFactory.newExternalMessageBuilder(message)
                        .withSource(source);
        externalMessageBuilder.withInternalHeaders(enrichHeadersWithReplyInformation(message.getInternalHeaders()));
        return externalMessageBuilder.build();
    }

    protected DittoHeaders enrichHeadersWithReplyInformation(final DittoHeaders headers) {
        return source.getReplyTarget()
                .map(replyTarget -> headers.toBuilder()
                        .replyTarget(source.getIndex())
                        .expectedResponseTypes(replyTarget.getExpectedResponseTypes())
                        .build())
                .orElse(headers);
    }

    private static String getInstanceIdentifier() {
        return InstanceIdentifierSupplier.getInstance().get();
    }

    /**
     * Reject an incoming message.
     */
    @FunctionalInterface
    public interface Reject {

        /**
         * Reject a message.
         *
         * @param shouldRedeliver whether the broker should redeliver.
         */
        void reject(boolean shouldRedeliver);
    }
}
