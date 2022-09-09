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
package org.eclipse.ditto.internal.utils.pubsub;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.internal.utils.ddata.DistributedDataConfig;
import org.eclipse.ditto.internal.utils.pubsub.actors.AckSupervisor;
import org.eclipse.ditto.internal.utils.pubsub.api.AckRequest;
import org.eclipse.ditto.internal.utils.pubsub.api.AcksDeclared;
import org.eclipse.ditto.internal.utils.pubsub.api.DeclareAcks;
import org.eclipse.ditto.internal.utils.pubsub.api.ReceiveLocalAcks;
import org.eclipse.ditto.internal.utils.pubsub.api.ReceiveRemoteAcks;
import org.eclipse.ditto.internal.utils.pubsub.api.RemoveSubscriberAcks;
import org.eclipse.ditto.internal.utils.pubsub.ddata.literal.LiteralDData;

import akka.actor.AbstractExtensionId;
import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
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
        return create(actorContext, actorContext.system());
    }

    static DistributedAcks create(final ActorRefFactory actorRefFactory, final ActorSystem actorSystem) {
        final LiteralDDataProvider provider = LiteralDDataProvider.of(CLUSTER_ROLE, "acks");
        return create(actorRefFactory, actorSystem, CLUSTER_ROLE, provider);
    }

    static DistributedAcks create(final ActorRefFactory actorRefFactory,
            final ActorSystem system,
            final String clusterRole,
            final LiteralDDataProvider provider) {
        final String supervisorName = clusterRole + "-ack-supervisor";
        final Props props = AckSupervisor.props(LiteralDData.of(system, provider));
        final ActorRef supervisor = actorRefFactory.actorOf(props, supervisorName);
        final DistributedDataConfig config = provider.getConfig(system);
        return new DistributedAcksImpl(config, supervisor);
    }

    private CompletionStage<AcksDeclared> askSupervisor(final AckRequest request) {
        return Patterns.ask(ackSupervisor, request, config.getWriteTimeout())
                .thenCompose(DistributedAcksImpl::processAskResponse);
    }

    @Override
    public void receiveLocalDeclaredAcks(final ActorRef receiver) {
        ackSupervisor.tell(ReceiveLocalAcks.of(receiver), receiver);
    }

    @Override
    public void receiveDistributedDeclaredAcks(final ActorRef receiver) {
        ackSupervisor.tell(ReceiveRemoteAcks.of(receiver), receiver);
    }

    @Override
    public void removeSubscriber(final ActorRef subscriber) {
        ackSupervisor.tell(RemoveSubscriberAcks.of(subscriber), subscriber);
    }

    @Override
    public CompletionStage<AcksDeclared> declareAcknowledgementLabels(
            final Collection<AcknowledgementLabel> acknowledgementLabels,
            final ActorRef subscriber) {
        final Set<String> ackLabelStrings = acknowledgementLabels.stream()
                .map(AcknowledgementLabel::toString)
                .collect(Collectors.toSet());
        final AckRequest request =
                DeclareAcks.of(subscriber, null, ackLabelStrings);
        return askSupervisor(request);
    }

    @Override
    public CompletionStage<AcksDeclared> declareAcknowledgementLabels(
            final Collection<AcknowledgementLabel> acknowledgementLabels, final ActorRef subscriber,
            @Nullable final String group) {
        if (group != null) {
            ConditionChecker.checkNotEmpty(group, "group");
        }
        final Set<String> ackLabelStrings = acknowledgementLabels.stream()
                .map(AcknowledgementLabel::toString)
                .collect(Collectors.toSet());
        return askSupervisor(DeclareAcks.of(subscriber, group, ackLabelStrings));
    }

    @Override
    public void removeAcknowledgementLabelDeclaration(final ActorRef subscriber) {
        ackSupervisor.tell(RemoveSubscriberAcks.of(subscriber), ActorRef.noSender());
    }

    @Override
    public DistributedDataConfig getConfig() {
        return config;
    }

    private static CompletionStage<AcksDeclared> processAskResponse(final Object askResponse) {
        if (askResponse instanceof AcksDeclared acksDeclared) {
            return CompletableFuture.completedStage( acksDeclared);
        } else if (askResponse instanceof Throwable throwable) {
            return CompletableFuture.failedStage(throwable);
        } else {
            return CompletableFuture.failedStage(new ClassCastException("Expect SubAck, got: " + askResponse));
        }
    }

    static final class ExtensionId extends AbstractExtensionId<DistributedAcks> {

        static final ExtensionId INSTANCE = new ExtensionId();

        private ExtensionId() {}

        @Override
        public DistributedAcks createExtension(final ExtendedActorSystem system) {
            return DistributedAcks.create(system);
        }
    }
}
