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
package org.eclipse.ditto.services.utils.pubsub;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.services.utils.ddata.DistributedDataConfig;
import org.eclipse.ditto.services.utils.pubsub.actors.AcksSupervisor;
import org.eclipse.ditto.services.utils.pubsub.actors.AcksUpdater;
import org.eclipse.ditto.services.utils.pubsub.api.RemoveSubscriber;
import org.eclipse.ditto.services.utils.pubsub.api.Request;
import org.eclipse.ditto.services.utils.pubsub.api.SubAck;
import org.eclipse.ditto.services.utils.pubsub.api.Subscribe;
import org.eclipse.ditto.services.utils.pubsub.ddata.literal.LiteralDData;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.ddata.Replicator;
import akka.pattern.Patterns;

/**
 * Package-private implementation of {@link DistributedAcks}.
 */
final class DistributedAcksImpl implements DistributedAcks {

    private static final String CLUSTER_ROLE = "acks-aware";

    private final DistributedDataConfig config;
    private final ActorRef acksSupervisor;

    private DistributedAcksImpl(final DistributedDataConfig config, final ActorRef acksSupervisor) {
        this.config = config;
        this.acksSupervisor = acksSupervisor;
    }

    static DistributedAcks create(final ActorContext actorContext) {
        final LiteralDDataProvider provider = LiteralDDataProvider.of(CLUSTER_ROLE, "acks");
        return create(actorContext, CLUSTER_ROLE, provider);
    }

    static DistributedAcks create(final ActorContext actorContext,
            final String clusterRole,
            final LiteralDDataProvider provider) {
        final String supervisorName = clusterRole + "-acks-supervisor";
        final Props props = AcksSupervisor.props(LiteralDData.of(actorContext.system(), provider));
        final ActorRef supervisor = actorContext.actorOf(props, supervisorName);
        final DistributedDataConfig config = provider.getConfig(actorContext.system());
        return new DistributedAcksImpl(config, supervisor);
    }

    private CompletionStage<SubAck> askSubSupervisor(final Request request) {
        return Patterns.ask(acksSupervisor, request, config.getWriteTimeout())
                .thenCompose(DistributedAcksImpl::processAskResponse);
    }

    @Override
    public void receiveLocalDeclaredAcks(final ActorRef receiver) {
        acksSupervisor.tell(AcksUpdater.receiveLocalChanges(receiver), ActorRef.noSender());
    }

    @Override
    public void receiveDistributedDeclaredAcks(final ActorRef receiver) {
        acksSupervisor.tell(AcksUpdater.receiveDDataChanges(receiver), ActorRef.noSender());
    }

    @Override
    public void removeSubscriber(final ActorRef subscriber) {
        final Request request =
                RemoveSubscriber.of(subscriber, (Replicator.WriteConsistency) Replicator.writeLocal(),
                        false);
        acksSupervisor.tell(request, subscriber);
    }

    @Override
    public CompletionStage<SubAck> declareAcknowledgementLabels(
            final Collection<AcknowledgementLabel> acknowledgementLabels,
            final ActorRef subscriber) {
        final Set<String> ackLabelStrings = acknowledgementLabels.stream()
                .map(AcknowledgementLabel::toString)
                .collect(Collectors.toSet());
        final Subscribe subscribe =
                Subscribe.of(ackLabelStrings, subscriber, writeLocal(), true);
        return askSubSupervisor(subscribe);
    }

    @Override
    public void removeAcknowledgementLabelDeclaration(final ActorRef subscriber) {
        final RemoveSubscriber request =
                RemoveSubscriber.of(subscriber, writeLocal(), false);
        acksSupervisor.tell(request, ActorRef.noSender());
    }

    private static Replicator.WriteConsistency writeLocal() {
        return (Replicator.WriteConsistency) Replicator.writeLocal();
    }

    private static CompletionStage<SubAck> processAskResponse(final Object askResponse) {
        if (askResponse instanceof SubAck) {
            return CompletableFuture.completedStage((SubAck) askResponse);
        } else if (askResponse instanceof Throwable) {
            return CompletableFuture.failedStage((Throwable) askResponse);
        } else {
            return CompletableFuture.failedStage(new ClassCastException("Expect SubAck, got: " + askResponse));
        }
    }
}
