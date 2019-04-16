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
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitorRegistry;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ImmutableConnectionMonitorRegistry;
import org.eclipse.ditto.services.connectivity.util.ConfigKeys;
import org.eclipse.ditto.services.connectivity.util.MonitoringConfigReader;
import org.eclipse.ditto.services.utils.config.ConfigUtil;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;

/**
 * Base class for consumer actors that holds common fields and handles the address status.
 */
public abstract class BaseConsumerActor extends AbstractActor {

    protected final String sourceAddress;
    protected final ActorRef messageMappingProcessor;
    protected final AuthorizationContext authorizationContext;
    @Nullable protected final HeaderMapping headerMapping;
    protected final ConnectionMonitor inboundMonitor;
    private final ConnectionMonitorRegistry<ConnectionMonitor> connectionMonitorRegistry;
    protected final String connectionId;

    @Nullable protected ResourceStatus resourceStatus;

    protected BaseConsumerActor(final String connectionId, final String sourceAddress,
            final ActorRef messageMappingProcessor, final AuthorizationContext authorizationContext,
            @Nullable final HeaderMapping headerMapping) {
        this.connectionId = checkNotNull(connectionId, "connectionId");
        this.sourceAddress = checkNotNull(sourceAddress, "sourceAddress");
        this.messageMappingProcessor = checkNotNull(messageMappingProcessor, "messageMappingProcessor");
        this.authorizationContext = checkNotNull(authorizationContext, "authorizationContext");
        this.headerMapping = headerMapping;
        resourceStatus = ConnectivityModelFactory.newSourceStatus(ConfigUtil.instanceIdentifier(),
                ConnectivityStatus.OPEN, sourceAddress, "Started at " + Instant.now());

        final MonitoringConfigReader monitoringConfig =
                ConfigKeys.Monitoring.fromRawConfig(getContext().system().settings().config());
        this.connectionMonitorRegistry = ImmutableConnectionMonitorRegistry.fromConfig(monitoringConfig);
        inboundMonitor = connectionMonitorRegistry.forInboundConsumed(connectionId, sourceAddress);
    }

    protected ResourceStatus getCurrentSourceStatus() {

        return ConnectivityModelFactory.newSourceStatus(ConfigUtil.instanceIdentifier(),
                resourceStatus != null ? resourceStatus.getStatus() : ConnectivityStatus.UNKNOWN,
                sourceAddress,
                resourceStatus != null ? resourceStatus.getStatusDetails().orElse(null) : null);
    }

    protected void handleAddressStatus(final ResourceStatus resourceStatus) {
        if (resourceStatus.getResourceType() == ResourceStatus.ResourceType.UNKNOWN) {
            this.resourceStatus = ConnectivityModelFactory.newSourceStatus(ConfigUtil.instanceIdentifier(),
                    resourceStatus.getStatus(), sourceAddress,
                    resourceStatus.getStatusDetails().orElse(null));
        } else {
            this.resourceStatus = resourceStatus;
        }
    }

}
