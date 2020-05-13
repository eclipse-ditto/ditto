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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.ResourceStatus;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.services.connectivity.messaging.config.DittoConnectivityConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.MonitoringConfig;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.DefaultConnectionMonitorRegistry;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageBuilder;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.config.InstanceIdentifierSupplier;

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

    @Nullable private ResourceStatus resourceStatus;

    protected BaseConsumerActor(final ConnectionId connectionId, final String sourceAddress,
            final ActorRef messageMappingProcessor, final Source source) {
        this.connectionId = checkNotNull(connectionId, "connectionId");
        this.sourceAddress = checkNotNull(sourceAddress, "sourceAddress");
        this.messageMappingProcessor = checkNotNull(messageMappingProcessor, "messageMappingProcessor");
        this.source = checkNotNull(source, "source");
        resetResourceStatus();

        final MonitoringConfig monitoringConfig = DittoConnectivityConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config())
        ).getMonitoringConfig();

        inboundMonitor = DefaultConnectionMonitorRegistry.fromConfig(monitoringConfig)
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
     * @return future that completes with any requested acknowledgements, or with null.
     */
    protected final CompletionStage<ResponseCollectorActor.Output> forwardToMappingActor(
            final ExternalMessage message) {
        return forwardAndAwaitAck(addSourceAndReplyTarget(message));
    }

    /**
     * Send an error to the mapping processor actor to be published in the reply-target.
     * NOT thread-safe!
     *
     * @param message the error.
     */
    protected final void forwardToMappingActor(final DittoRuntimeException message) {
        messageMappingProcessor.forward(message, getContext());
    }

    private CompletionStage<ResponseCollectorActor.Output> forwardAndAwaitAck(final Object message) {
        // 1. start per-inbound-signal actor to collect acks of all thing-modify-commands mapped from incoming signal
        // TODO: configure askTimeout?
        final Duration collectorLifetime = Duration.ofSeconds(100);
        final Duration askTimeout = Duration.ofSeconds(120);
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

    private ExternalMessage addSourceAndReplyTarget(final ExternalMessage message) {
        final ExternalMessageBuilder externalMessageBuilder =
                ExternalMessageFactory.newExternalMessageBuilder(message)
                        .withSource(source);
        if (source.getReplyTarget().isPresent()) {
            externalMessageBuilder.withInternalHeaders(message.getInternalHeaders()
                    .toBuilder()
                    .replyTarget(source.getIndex())
                    .build());
        }
        return externalMessageBuilder.build();
    }

    private static String getInstanceIdentifier() {
        return InstanceIdentifierSupplier.getInstance().get();
    }

}
