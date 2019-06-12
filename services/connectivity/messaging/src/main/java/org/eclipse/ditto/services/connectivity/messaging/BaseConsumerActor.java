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

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.HeaderMapping;
import org.eclipse.ditto.model.connectivity.ResourceStatus;
import org.eclipse.ditto.services.connectivity.messaging.metrics.ConnectionMetricsCollector;
import org.eclipse.ditto.services.connectivity.messaging.metrics.ConnectivityCounterRegistry;
import org.eclipse.ditto.services.utils.config.InstanceIdentifierSupplier;

import akka.actor.AbstractActor;
import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;

/**
 * Base class for consumer actors that holds common fields and handles the address status.
 */
public abstract class BaseConsumerActor extends AbstractActorWithTimers {

    protected final String sourceAddress;
    protected final ActorRef messageMappingProcessor;
    protected final AuthorizationContext authorizationContext;
    @Nullable protected final HeaderMapping headerMapping;
    protected final ConnectionMetricsCollector inboundCounter;

    @Nullable protected ResourceStatus resourceStatus;

    protected BaseConsumerActor(final String connectionId, final String sourceAddress,
            final ActorRef messageMappingProcessor, final AuthorizationContext authorizationContext,
            @Nullable final HeaderMapping headerMapping) {
        checkNotNull(connectionId, "connectionId");
        this.sourceAddress = checkNotNull(sourceAddress, "sourceAddress");
        this.messageMappingProcessor = checkNotNull(messageMappingProcessor, "messageMappingProcessor");
        this.authorizationContext = checkNotNull(authorizationContext, "authorizationContext");
        this.headerMapping = headerMapping;
        resourceStatus = ConnectivityModelFactory.newSourceStatus(getInstanceIdentifier(),
                ConnectivityStatus.OPEN, sourceAddress,"Started at " + Instant.now());

        inboundCounter = ConnectivityCounterRegistry.getInboundConsumedCounter(connectionId, sourceAddress);
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

    private static String getInstanceIdentifier() {
        return InstanceIdentifierSupplier.getInstance().get();
    }

}
