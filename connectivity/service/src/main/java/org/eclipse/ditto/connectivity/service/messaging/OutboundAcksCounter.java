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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.Target;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.Terminated;
import akka.japi.Pair;

/**
 * Tracks how many acknowledgement forwarder actors are waiting for acknowledgements.
 */
@NotThreadSafe
final class OutboundAcksCounter {

    private final Set<AcknowledgementLabel> issuedAcks;
    private final Map<ActorRef, Integer> state = new HashMap<>();
    private boolean isShuttingDown = false;

    private OutboundAcksCounter(final Set<AcknowledgementLabel> issuedAcks) {
        this.issuedAcks = issuedAcks;
    }

    static OutboundAcksCounter of(final Connection connection) {
        final Set<AcknowledgementLabel> issuedAcks = connection.getTargets()
                .stream()
                .map(Target::getIssuedAcknowledgementLabel)
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());
        return new OutboundAcksCounter(issuedAcks);
    }

    void register(final Pair<Signal<?>, Optional<ActorRef>> signalPair, final ActorContext context) {
        if (signalPair.second().isEmpty()) {
            // nothing to do
            return;
        }
        final ActorRef ackForwarder = signalPair.second().get();
        final int relevantAckRequests = countRelevantAckRequests(signalPair.first());
        if (relevantAckRequests > 0) {
            state.merge(ackForwarder, relevantAckRequests, Integer::sum);
            context.watch(ackForwarder);
        }
    }

    void terminated(final Terminated terminated) {
        state.remove(terminated.actor());
    }

    void shutdown() {
        isShuttingDown = true;
    }

    boolean shouldTerminateOutboundDispatchingActor() {
        return isShuttingDown && state.isEmpty();
    }

    String describeState() {
        return String.format("%d acknowledgement forwarders, %d pending issued acks", state.size(),
                state.values().stream().mapToInt(Integer::intValue).sum());
    }

    void countDown(final ActorRef forwarder) {
        state.compute(forwarder, (actorRef, count) -> {
            if (count == null) {
                return null;
            } else {
                final var nextCount = count - 1;
                return nextCount <= 0 ? null : nextCount;
            }
        });
    }

    private int countRelevantAckRequests(final Signal<?> s) {
        final var ackRequests = s.getDittoHeaders().getAcknowledgementRequests();
        return (int) ackRequests.stream()
                .map(AcknowledgementRequest::getLabel)
                .filter(issuedAcks::contains)
                .count();
    }
}
