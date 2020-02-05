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

import java.time.Instant;

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
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.config.InstanceIdentifierSupplier;

import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;

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

    protected void forwardToMappingActor(final ExternalMessage message) {
        doForwardToMappingActor(addReplyTarget(message));
    }

    protected void forwardToMappingActor(final DittoRuntimeException message) {
        doForwardToMappingActor(message);
    }

    private void doForwardToMappingActor(final Object message) {
        messageMappingProcessor.forward(message, getContext());
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

    private ExternalMessage addReplyTarget(final ExternalMessage message) {
        if (source.getReplyTarget().isPresent()) {
            return ExternalMessageFactory.newExternalMessageBuilder(message)
                    .withInternalHeaders(message.getInternalHeaders()
                            .toBuilder()
                            .replyTarget(source.getIndex())
                            .build())
                    .build();
        } else {
            return message;
        }
    }

    private static String getInstanceIdentifier() {
        return InstanceIdentifierSupplier.getInstance().get();
    }

}
