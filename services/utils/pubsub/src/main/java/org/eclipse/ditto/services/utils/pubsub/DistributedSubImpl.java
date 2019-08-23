/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
import java.util.HashSet;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;

import org.eclipse.ditto.services.utils.ddata.DistributedDataConfig;
import org.eclipse.ditto.services.utils.pubsub.actors.SubUpdater;

import akka.actor.ActorRef;
import akka.cluster.ddata.Replicator;
import akka.pattern.Patterns;

/**
 * Package-private implementation of {@link DistributedSub}.
 */
final class DistributedSubImpl implements DistributedSub {

    private final DistributedDataConfig config;
    private final ActorRef subSupervisor;
    private final Replicator.WriteConsistency writeAll;

    DistributedSubImpl(final DistributedDataConfig config, final ActorRef subSupervisor) {
        this.config = config;
        this.subSupervisor = subSupervisor;
        this.writeAll = new Replicator.WriteAll(config.getWriteTimeout());
    }

    @Override
    public CompletionStage<SubUpdater.Acknowledgement> subscribeWithFilterAndAck(final Collection<String> topics,
            final ActorRef subscriber, final Predicate<Collection<String>> filter) {
        final SubUpdater.Subscribe subscribe =
                SubUpdater.Subscribe.of(new HashSet<>(topics), subscriber, writeAll, true, filter);
        return askSubSupervisor(subscribe);
    }

    @Override
    public CompletionStage<SubUpdater.Acknowledgement> subscribeWithAck(final Collection<String> topics,
            final ActorRef subscriber) {
        return askSubSupervisor(SubUpdater.Subscribe.of(new HashSet<>(topics), subscriber, writeAll, true));
    }

    @Override
    public CompletionStage<SubUpdater.Acknowledgement> unsubscribeWithAck(final Collection<String> topics,
            final ActorRef subscriber) {
        return askSubSupervisor(SubUpdater.Unsubscribe.of(new HashSet<>(topics), subscriber, writeAll, true));
    }

    private CompletionStage<SubUpdater.Acknowledgement> askSubSupervisor(final SubUpdater.Request request) {
        return Patterns.ask(subSupervisor, request, config.getWriteTimeout())
                // let any ClassCastException here produce a failed future
                .thenApply(response -> (SubUpdater.Acknowledgement) response);
    }

    @Override
    public void subscribeWithoutAck(final Collection<String> topics, final ActorRef subscriber) {
        final SubUpdater.Request request =
                SubUpdater.Subscribe.of(new HashSet<>(topics), subscriber, Replicator.writeLocal(), false);
        subSupervisor.tell(request, subscriber);
    }

    @Override
    public void unsubscribeWithoutAck(final Collection<String> topics, final ActorRef subscriber) {
        final SubUpdater.Request request =
                SubUpdater.Unsubscribe.of(new HashSet<>(topics), subscriber, Replicator.writeLocal(), false);
        subSupervisor.tell(request, subscriber);
    }

    @Override
    public void removeSubscriber(final ActorRef subscriber) {
        final SubUpdater.Request request =
                SubUpdater.RemoveSubscriber.of(subscriber, Replicator.writeLocal(), false);
        subSupervisor.tell(request, subscriber);
    }
}
