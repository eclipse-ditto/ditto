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
package org.eclipse.ditto.connectivity.service.messaging;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageBuilder;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.ResourceStatus;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.DittoConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.DefaultConnectionMonitorRegistry;
import org.eclipse.ditto.internal.models.acks.config.AcknowledgementConfig;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.config.InstanceIdentifierSupplier;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.internal.utils.tracing.TracingTags;

import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.pattern.Patterns;

/**
 * Base class for consumer actors that holds common fields and handles the address status.
 */
public abstract class BaseConsumerActor extends AbstractActorWithTimers {

    private static final String TIMER_ACK_HANDLING = "connectivity_ack_handling";

    protected final String sourceAddress;
    protected final Source source;
    private final ConnectionType connectionType;
    protected final ConnectionMonitor inboundMonitor;
    protected final ConnectionMonitor inboundAcknowledgedMonitor;
    protected final ConnectionId connectionId;

    private final ActorRef inboundMappingProcessor;
    private final AcknowledgementConfig acknowledgementConfig;

    @Nullable private ResourceStatus resourceStatus;

    protected BaseConsumerActor(final Connection connection, final String sourceAddress,
            final ActorRef inboundMappingProcessor, final Source source) {
        this.connectionId = checkNotNull(connection, "connection").getId();
        this.sourceAddress = checkNotNull(sourceAddress, "sourceAddress");
        this.inboundMappingProcessor = checkNotNull(inboundMappingProcessor, "inboundMappingProcessor");
        this.source = checkNotNull(source, "source");
        this.connectionType = connection.getConnectionType();
        resetResourceStatus();

        final ConnectivityConfig connectivityConfig = DittoConnectivityConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config()));

        acknowledgementConfig = connectivityConfig.getAcknowledgementConfig();

        inboundMonitor = DefaultConnectionMonitorRegistry.fromConfig(connectivityConfig.getMonitoringConfig())
                .forInboundConsumed(connection, sourceAddress);

        inboundAcknowledgedMonitor =
                DefaultConnectionMonitorRegistry.fromConfig(connectivityConfig.getMonitoringConfig())
                        .forInboundAcknowledged(connection, sourceAddress);
    }

    /**
     * @return the logging adapter of this actor.
     */
    protected abstract ThreadSafeDittoLoggingAdapter log();

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

        final StartedTimer timer = DittoMetrics.timer(TIMER_ACK_HANDLING)
                .tag(TracingTags.CONNECTION_ID, connectionId.toString())
                .tag(TracingTags.CONNECTION_TYPE, connectionType.getName())
                .start();
        forwardAndAwaitAck(addSourceAndReplyTarget(message))
                .handle((output, error) -> {
                    if (output != null) {
                        final List<CommandResponse<?>> failedResponses = output.getFailedResponses();
                        if (output.allExpectedResponsesArrived() && failedResponses.isEmpty()) {
                            timer.tag(TracingTags.ACK_SUCCESS, true).stop();
                            settle.run();
                        } else {
                            // empty failed responses indicate that SetCount was missing
                            final boolean shouldRedeliver = failedResponses.isEmpty() ||
                                    someFailedResponseRequiresRedelivery(failedResponses);
                            log().withCorrelationId(failedResponses.get(0))
                                    .debug("Rejecting [redeliver={}] due to failed responses <{}>",
                                    shouldRedeliver, failedResponses);
                            timer.tag(TracingTags.ACK_SUCCESS, false)
                                    .tag(TracingTags.ACK_REDELIVER, shouldRedeliver)
                                    .stop();
                            reject.reject(shouldRedeliver);
                        }
                    } else {
                        // don't count this as "failure" in the "source consumed" metric as the consumption
                        // itself was successful
                        final DittoRuntimeException dittoRuntimeException =
                                DittoRuntimeException.asDittoRuntimeException(error, rootCause -> {
                                    // Redeliver and pray this unexpected error goes away
                                    log().debug("Rejecting [redeliver=true] due to error <{}>", rootCause);
                                    timer.tag(TracingTags.ACK_SUCCESS, false)
                                            .tag(TracingTags.ACK_REDELIVER, true)
                                            .stop();
                                    reject.reject(true);
                                    return null;
                                });
                        if (dittoRuntimeException != null) {
                            if (isConsideredSuccess(dittoRuntimeException)) {
                                timer.tag(TracingTags.ACK_SUCCESS, true).stop();
                                settle.run();
                            } else {
                                final var shouldRedeliver = requiresRedelivery(dittoRuntimeException.getHttpStatus());
                                log().debug("Rejecting [redeliver={}] due to error <{}>",
                                        shouldRedeliver, dittoRuntimeException);
                                timer.tag(TracingTags.ACK_SUCCESS, false)
                                        .tag(TracingTags.ACK_REDELIVER, shouldRedeliver)
                                        .stop();
                                reject.reject(shouldRedeliver);
                            }
                        }
                    }
                    return null;
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
        inboundMappingProcessor.tell(messageWithReplyInformation, ActorRef.noSender());
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

    private CompletionStage<ResponseCollectorActor.Output> forwardAndAwaitAck(final Object message) {
        // 1. start per-inbound-signal actor to collect acks of all thing-modify-commands mapped from incoming signal
        final Duration collectorLifetime = acknowledgementConfig.getCollectorFallbackLifetime();
        final Duration askTimeout = acknowledgementConfig.getCollectorFallbackAskTimeout();
        final ActorRef responseCollector = getContext().actorOf(ResponseCollectorActor.props(collectorLifetime));
        // 2. forward message to mapping processor actor with response collector actor as sender
        // message mapping processor actor will set the number of expected acks (can be 0)
        // and start the same amount of ack aggregator actors
        inboundMappingProcessor.tell(message, responseCollector);
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

    private static boolean someFailedResponseRequiresRedelivery(final Collection<CommandResponse<?>> failedResponses) {
        return failedResponses.stream()
                .flatMap(BaseConsumerActor::extractAggregatedResponses)
                .map(CommandResponse::getHttpStatus)
                .anyMatch(BaseConsumerActor::requiresRedelivery);
    }

    private static Stream<? extends CommandResponse<?>> extractAggregatedResponses(final CommandResponse<?> response) {
        if (response instanceof Acknowledgements) {
            return ((Acknowledgements) response).stream();
        } else {
            return Stream.of(response);
        }
    }

    /**
     * Decide whether an Acknowledgement or DittoRuntimeException requires redelivery based on the status.
     * Client errors excluding 408 request-timeout and 424 failed-dependency are considered unrecoverable and no
     * redelivery will be attempted.
     *
     * @param status HTTP status of the Acknowledgement or DittoRuntimeException.
     * @return whether it requires redelivery.
     */
    private static boolean requiresRedelivery(final HttpStatus status) {
        if (HttpStatus.REQUEST_TIMEOUT.equals(status) || HttpStatus.FAILED_DEPENDENCY.equals(status)) {
            return true;
        }
        return status.isServerError();
    }

    /**
     * Decide whether a DittoRuntimeException is considered successful processing.
     * This happens with ThingPreconditionFailedException and PolicyPreconditionFailedException.
     * All DittoRuntimeException with status 412 Precondition Failed are considered success.
     *
     * @param dittoRuntimeException the DittoRuntimeException.
     * @return whether it is considered successful processing.
     */
    private static boolean isConsideredSuccess(final DittoRuntimeException dittoRuntimeException) {
        return HttpStatus.PRECONDITION_FAILED.equals(dittoRuntimeException.getHttpStatus());
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
