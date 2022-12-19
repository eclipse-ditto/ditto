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
package org.eclipse.ditto.gateway.service.streaming.signals;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.gateway.service.streaming.actors.SessionedJsonifiable;

import akka.stream.KillSwitch;
import akka.stream.javadsl.SourceQueueWithComplete;

/**
 * Message to be sent in order to establish a new "streaming" connection via {@link org.eclipse.ditto.gateway.service.streaming.actors.StreamingActor}.
 */
public final class Connect {

    private final SourceQueueWithComplete<SessionedJsonifiable> eventAndResponsePublisher;
    private final String connectionCorrelationId;
    private final String type;
    private final JsonSchemaVersion jsonSchemaVersion;
    @Nullable private final Instant sessionExpirationTime;
    private final Set<AcknowledgementLabel> declaredAcknowledgementLabels;
    private final AuthorizationContext connectionAuthContext;
    @Nullable private final KillSwitch killSwitch;

    /**
     * Constructs a new {@link Connect} instance.
     *
     * @param eventAndResponsePublisher a source queue to push events and responses into.
     * @param connectionCorrelationId the correlationId of the connection/session.
     * @param type the type of the "streaming" connection to establish.
     * @param jsonSchemaVersion schema version of the request for the streaming session.
     * @param sessionExpirationTime how long to keep the session alive when idling.
     * @param declaredAcknowledgementLabels labels of acknowledgements this session may send.
     * @param connectionAuthContext the authorizationContext of the streaming session.
     * @param killSwitch the kill switch to terminate the streaming session.
     */
    public Connect(final SourceQueueWithComplete<SessionedJsonifiable> eventAndResponsePublisher,
            final CharSequence connectionCorrelationId,
            final String type,
            final JsonSchemaVersion jsonSchemaVersion,
            @Nullable final Instant sessionExpirationTime,
            final Set<AcknowledgementLabel> declaredAcknowledgementLabels,
            final AuthorizationContext connectionAuthContext,
            @Nullable final KillSwitch killSwitch) {
        this.eventAndResponsePublisher = eventAndResponsePublisher;
        this.connectionCorrelationId = checkNotNull(connectionCorrelationId, "connectionCorrelationId")
                .toString();
        this.type = type;
        this.jsonSchemaVersion = jsonSchemaVersion;
        this.sessionExpirationTime = sessionExpirationTime;
        this.declaredAcknowledgementLabels = declaredAcknowledgementLabels;
        this.connectionAuthContext = connectionAuthContext;
        this.killSwitch = killSwitch;
    }

    public SourceQueueWithComplete<SessionedJsonifiable> getEventAndResponsePublisher() {
        return eventAndResponsePublisher;
    }

    public String getConnectionCorrelationId() {
        return connectionCorrelationId;
    }

    public String getType() {
        return type;
    }

    public Optional<Instant> getSessionExpirationTime() {
        return Optional.ofNullable(sessionExpirationTime);
    }

    public JsonSchemaVersion getJsonSchemaVersion() {
        return jsonSchemaVersion;
    }

    public Set<AcknowledgementLabel> getDeclaredAcknowledgementLabels() {
        return declaredAcknowledgementLabels;
    }

    public AuthorizationContext getConnectionAuthContext() {
        return connectionAuthContext;
    }

    public Optional<KillSwitch> getKillSwitch() {
        return Optional.ofNullable(killSwitch);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Connect connect = (Connect) o;
        return Objects.equals(eventAndResponsePublisher, connect.eventAndResponsePublisher) &&
                Objects.equals(connectionCorrelationId, connect.connectionCorrelationId) &&
                Objects.equals(type, connect.type) &&
                Objects.equals(sessionExpirationTime, connect.sessionExpirationTime) &&
                Objects.equals(declaredAcknowledgementLabels, connect.declaredAcknowledgementLabels) &&
                Objects.equals(connectionAuthContext, connect.connectionAuthContext) &&
                Objects.equals(killSwitch, connect.killSwitch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventAndResponsePublisher, connectionCorrelationId, type, sessionExpirationTime,
                declaredAcknowledgementLabels, connectionAuthContext, killSwitch);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "eventAndResponsePublisher=" + eventAndResponsePublisher +
                ", connectionCorrelationId=" + connectionCorrelationId +
                ", type=" + type +
                ", sessionExpirationTime=" + sessionExpirationTime +
                ", declaredAcknowledgementLabels=" + declaredAcknowledgementLabels +
                ", connectionAuthContext=" + connectionAuthContext +
                ", killSwitch=" + killSwitch +
                "]";
    }

}
