/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.PayloadMappingDefinition;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.mapping.DefaultMessageMapperFactory;
import org.eclipse.ditto.connectivity.service.mapping.DittoMessageMapper;
import org.eclipse.ditto.connectivity.service.mapping.MessageMapperFactory;
import org.eclipse.ditto.connectivity.service.mapping.MessageMapperRegistry;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitorRegistry;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.DefaultConnectionMonitorRegistry;
import org.eclipse.ditto.connectivity.service.messaging.persistence.SignalFilter;
import org.eclipse.ditto.connectivity.service.messaging.validation.ConnectionValidator;
import org.eclipse.ditto.connectivity.service.util.ConnectivityMdcEntryKey;
import org.eclipse.ditto.edge.service.acknowledgements.AcknowledgementConfig;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapter;

import akka.actor.ActorSelection;
import akka.actor.ActorSystem;

/**
 * All the information required to create an OutboundMappingProcessor.
 */
@NotThreadSafe
final class OutboundMappingSettings {

    private final ConnectionId connectionId;
    private final ConnectionType connectionType;
    private final MessageMapperRegistry registry;
    private final ThreadSafeDittoLoggingAdapter logger;
    private final ProtocolAdapter protocolAdapter;
    private final Set<AcknowledgementLabel> sourceDeclaredAcks;
    private final Set<AcknowledgementLabel> targetIssuedAcks;
    private final SignalFilter signalFilter;
    private final AcknowledgementConfig acknowledgementConfig;
    private final ActorSelection proxyActor;

    private OutboundMappingSettings(final ConnectionId connectionId,
            final ConnectionType connectionType,
            final MessageMapperRegistry registry,
            final ThreadSafeDittoLoggingAdapter logger,
            final ProtocolAdapter protocolAdapter,
            final Set<AcknowledgementLabel> sourceDeclaredAcks,
            final Set<AcknowledgementLabel> targetIssuedAcks,
            final SignalFilter signalFilter,
            final AcknowledgementConfig acknowledgementConfig,
            final ActorSelection proxyActor) {

        this.connectionId = connectionId;
        this.connectionType = connectionType;
        this.registry = registry;
        this.logger = logger;
        this.protocolAdapter = protocolAdapter;
        this.sourceDeclaredAcks = sourceDeclaredAcks;
        this.targetIssuedAcks = targetIssuedAcks;
        this.signalFilter = signalFilter;
        this.acknowledgementConfig = acknowledgementConfig;
        this.proxyActor = proxyActor;
    }

    static OutboundMappingSettings of(final Connection connection,
            final ConnectivityConfig connectivityConfig,
            final ActorSystem actorSystem,
            final ActorSelection proxyActor,
            final ProtocolAdapter protocolAdapter,
            final ThreadSafeDittoLoggingAdapter logger) {

        final ConnectionId connectionId = connection.getId();
        final ConnectionType connectionType = connection.getConnectionType();
        final PayloadMappingDefinition mappingDefinition = connection.getPayloadMappingDefinition();
        final Set<AcknowledgementLabel> sourceDeclaredAcks =
                ConnectionValidator.getSourceDeclaredAcknowledgementLabels(connectionId, connection.getSources())
                        .collect(Collectors.toSet());
        final Set<AcknowledgementLabel> targetIssuedAcks =
                ConnectionValidator.getTargetIssuedAcknowledgementLabels(connectionId, connection.getTargets())
                        // live response does not require a weak ack
                        .filter(ackLabel -> !DittoAcknowledgementLabel.LIVE_RESPONSE.equals(ackLabel))
                        .collect(Collectors.toSet());

        final ThreadSafeDittoLoggingAdapter loggerWithConnectionId =
                logger.withMdcEntry(ConnectivityMdcEntryKey.CONNECTION_ID, connectionId);

        final MessageMapperFactory messageMapperFactory =
                DefaultMessageMapperFactory.of(connection, connectivityConfig, actorSystem, loggerWithConnectionId);
        final MessageMapperRegistry registry =
                messageMapperFactory.registryOf(DittoMessageMapper.CONTEXT, mappingDefinition);

        final ConnectionMonitorRegistry<ConnectionMonitor> connectionMonitorRegistry =
                DefaultConnectionMonitorRegistry.fromConfig(connectivityConfig);
        final SignalFilter signalFilter = SignalFilter.of(connection, connectionMonitorRegistry);

        final AcknowledgementConfig acknowledgementConfig = connectivityConfig.getAcknowledgementConfig();

        return new OutboundMappingSettings(connectionId, connectionType, registry, loggerWithConnectionId,
                protocolAdapter, sourceDeclaredAcks, targetIssuedAcks, signalFilter, acknowledgementConfig, proxyActor);
    }

    ConnectionId getConnectionId() {
        return connectionId;
    }

    ConnectionType getConnectionType() {
        return connectionType;
    }

    MessageMapperRegistry getRegistry() {
        return registry;
    }

    ThreadSafeDittoLoggingAdapter getLogger() {
        return logger;
    }

    ProtocolAdapter getProtocolAdapter() {
        return protocolAdapter;
    }

    Set<AcknowledgementLabel> getSourceDeclaredAcks() {
        return sourceDeclaredAcks;
    }

    Set<AcknowledgementLabel> getTargetIssuedAcks() {
        return targetIssuedAcks;
    }

    SignalFilter getSignalFilter() {
        return signalFilter;
    }

    AcknowledgementConfig getAcknowledgementConfig() {
        return acknowledgementConfig;
    }

    ActorSelection getProxyActor() {
        return proxyActor;
    }
}
