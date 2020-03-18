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
package org.eclipse.ditto.services.models.acks;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.base.entity.id.EntityIdWithType;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.models.acks.config.AcknowledgementConfig;
import org.eclipse.ditto.signals.acks.Acknowledgement;
import org.eclipse.ditto.signals.acks.AcknowledgementRequestDuplicateCorrelationIdException;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.InvalidActorNameException;
import akka.actor.Props;

/**
 * Starting an acknowledgement forwarder actor is more complex than simply call {@code actorOf}.
 * Thus starting logic is worth to be handled within its own class.
 *
 * @since 1.1.0
 */
final class AcknowledgementForwarderActorStarter implements Supplier<Optional<ActorRef>> {

    private final ActorContext actorContext;
    private final EntityIdWithType entityId;
    private final DittoHeaders dittoHeaders;
    private final Set<AcknowledgementRequest> acknowledgementRequests;
    private final AcknowledgementConfig acknowledgementConfig;

    private AcknowledgementForwarderActorStarter(final ActorContext context,
            final EntityIdWithType entityId,
            final DittoHeaders dittoHeaders,
            final AcknowledgementConfig acknowledgementConfig) {

        actorContext = checkNotNull(context, "context");
        this.entityId = checkNotNull(entityId, "entityId");
        this.dittoHeaders = checkNotNull(dittoHeaders, "dittoHeaders");
        acknowledgementRequests = dittoHeaders.getAcknowledgementRequests();
        this.acknowledgementConfig = checkNotNull(acknowledgementConfig, "acknowledgementConfig");
    }

    /**
     * Returns an instance of {@code ActorStarter}.
     *
     * @param context the context to start the forwarder actor in. Furthermore provides the sender and self
     * reference for forwarding.
     * @param entityId is used for the NACKs if the forwarder actor cannot be started.
     * @param dittoHeaders the headers which contain the acknowledgement requests.
     * @param acknowledgementConfig provides configuration setting regarding acknowledgement handling.
     * @return a means to start an acknowledgement forwarder actor.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static AcknowledgementForwarderActorStarter getInstance(final ActorContext context,
            final EntityIdWithType entityId,
            final DittoHeaders dittoHeaders,
            final AcknowledgementConfig acknowledgementConfig) {

        return new AcknowledgementForwarderActorStarter(context, entityId, dittoHeaders, acknowledgementConfig);
    }

    @Override
    public Optional<ActorRef> get() {
        ActorRef actorRef = null;
        if (!acknowledgementRequests.isEmpty()) {
            actorRef = tryToStartAckForwarderActor();
        }
        return Optional.ofNullable(actorRef);
    }

    @Nullable
    private ActorRef tryToStartAckForwarderActor() {
        try {
            return startAckForwarderActor();
        } catch (final InvalidActorNameException e) {

            // In case that the actor with that name already existed, the correlation-id was already used recently:
            declineAllNonDittoAckRequests(getDuplicateCorrelationIdException(e));
            return null;
        }
    }

    private ActorRef startAckForwarderActor() {
        final Props props = AcknowledgementForwarderActor.props(actorContext.sender(), dittoHeaders,
                acknowledgementConfig.getForwarderFallbackTimeout());
        return actorContext.actorOf(props, AcknowledgementForwarderActor.determineActorName(dittoHeaders));
    }

    private DittoRuntimeException getDuplicateCorrelationIdException(final Throwable cause) {
        return AcknowledgementRequestDuplicateCorrelationIdException
                .newBuilder(dittoHeaders.getCorrelationId().orElse("?"))
                .dittoHeaders(dittoHeaders)
                .cause(cause)
                .build();
    }

    private void declineAllNonDittoAckRequests(final DittoRuntimeException dittoRuntimeException) {
        final ActorRef sender = actorContext.sender();
        final ActorRef self = actorContext.self();

        // answer NACKs for all AcknowledgementRequests with labels which were not Ditto-defined
        acknowledgementRequests.stream()
                .map(AcknowledgementRequest::getLabel)
                .filter(Predicate.not(DittoAcknowledgementLabel::contains))
                .map(label -> getNack(label, dittoRuntimeException))
                .forEach(nack -> sender.tell(nack, self));
    }

    private Acknowledgement getNack(final AcknowledgementLabel label,
            final DittoRuntimeException dittoRuntimeException) {

        return Acknowledgement.of(label, entityId, dittoRuntimeException.getStatusCode(), dittoHeaders,
                dittoRuntimeException.toJson());
    }

}
