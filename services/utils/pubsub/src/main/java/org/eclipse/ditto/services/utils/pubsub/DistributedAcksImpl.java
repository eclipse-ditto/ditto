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

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.common.ConditionChecker;
import org.eclipse.ditto.services.utils.ddata.DistributedDataConfig;
import org.eclipse.ditto.services.utils.pubsub.actors.AckSupervisor;
import org.eclipse.ditto.services.utils.pubsub.actors.AckUpdater;
import org.eclipse.ditto.services.utils.pubsub.ddata.literal.LiteralDData;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.pattern.Patterns;

/**
 * Package-private implementation of {@link DistributedAcks}.
 */
final class DistributedAcksImpl implements DistributedAcks {

    private static final String CLUSTER_ROLE = "acks-aware";

    private final DistributedDataConfig config;
    private final ActorRef ackSupervisor;

    private DistributedAcksImpl(final DistributedDataConfig config, final ActorRef ackSupervisor) {
        this.config = config;
        this.ackSupervisor = ackSupervisor;
    }

    static DistributedAcks create(final ActorContext actorContext) {
        final LiteralDDataProvider provider = LiteralDDataProvider.of(CLUSTER_ROLE, "acks");
        return create(actorContext, CLUSTER_ROLE, provider);
    }

    static DistributedAcks create(final ActorContext actorContext,
            final String clusterRole,
            final LiteralDDataProvider provider) {
        final String supervisorName = clusterRole + "-ack-supervisor";
        final Props props = AckSupervisor.props(LiteralDData.of(actorContext.system(), provider));
        final ActorRef supervisor = actorContext.actorOf(props, supervisorName);
        final DistributedDataConfig config = provider.getConfig(actorContext.system());
        return new DistributedAcksImpl(config, supervisor);
    }

    private CompletionStage<AckUpdater.AcksDeclared> askSupervisor(final AckUpdater.AckRequest request) {
        return Patterns.ask(ackSupervisor, request, config.getWriteTimeout())
                .thenCompose(DistributedAcksImpl::processAskResponse);
    }

    @Override
    public void receiveLocalDeclaredAcks(final ActorRef receiver) {
        ackSupervisor.tell(AckUpdater.ReceiveLocalChanges.of(receiver), ActorRef.noSender());
    }

    @Override
    public void receiveDistributedDeclaredAcks(final ActorRef receiver) {
        ackSupervisor.tell(AckUpdater.ReceiveDDataChanges.of(receiver), ActorRef.noSender());
    }

    @Override
    public void removeSubscriber(final ActorRef subscriber) {
        ackSupervisor.tell(AckUpdater.RemoveSubscriberAcks.of(subscriber), subscriber);
    }

    @Override
    public CompletionStage<AckUpdater.AcksDeclared> declareAcknowledgementLabels(
            final Collection<AcknowledgementLabel> acknowledgementLabels,
            final ActorRef subscriber) {
        final Set<String> ackLabelStrings = acknowledgementLabels.stream()
                .map(AcknowledgementLabel::toString)
                .collect(Collectors.toSet());
        final AckUpdater.AckRequest request =
                AckUpdater.DeclareAcks.of(subscriber, null, ackLabelStrings);
        return askSupervisor(request);
    }

    @Override
    public CompletionStage<AckUpdater.AcksDeclared> declareAcknowledgementLabels(
            @Nullable final String group,
            final Collection<AcknowledgementLabel> acknowledgementLabels,
            final ActorRef subscriber) {
        if (group != null) {
            ConditionChecker.checkNotEmpty(group, "group");
        }
        final Set<String> ackLabelStrings = acknowledgementLabels.stream()
                .map(AcknowledgementLabel::toString)
                .collect(Collectors.toSet());
        return askSupervisor(AckUpdater.DeclareAcks.of(subscriber, group, ackLabelStrings));
    }

    @Override
    public void removeAcknowledgementLabelDeclaration(final ActorRef subscriber) {
        ackSupervisor.tell(AckUpdater.RemoveSubscriberAcks.of(subscriber), ActorRef.noSender());
    }

    private static CompletionStage<AckUpdater.AcksDeclared> processAskResponse(final Object askResponse) {
        if (askResponse instanceof AckUpdater.AcksDeclared) {
            return CompletableFuture.completedStage((AckUpdater.AcksDeclared) askResponse);
        } else if (askResponse instanceof Throwable) {
            return CompletableFuture.failedStage((Throwable) askResponse);
        } else {
            return CompletableFuture.failedStage(new ClassCastException("Expect SubAck, got: " + askResponse));
        }
    }
}
