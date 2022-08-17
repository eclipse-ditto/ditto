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
package org.eclipse.ditto.internal.utils.pubsub;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotEmpty;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.ddata.DistributedDataConfig;
import org.eclipse.ditto.internal.utils.pubsub.api.RemoveSubscriber;
import org.eclipse.ditto.internal.utils.pubsub.api.Request;
import org.eclipse.ditto.internal.utils.pubsub.api.SubAck;
import org.eclipse.ditto.internal.utils.pubsub.api.Subscribe;
import org.eclipse.ditto.internal.utils.pubsub.api.Unsubscribe;

import akka.actor.ActorRef;
import akka.pattern.Patterns;

/**
 * Package-private implementation of {@link DistributedSub}.
 */
final class DistributedSubImpl implements DistributedSub {

    // package-private for unit tests
    final ActorRef subSupervisor;

    private final DistributedDataConfig config;
    private final long ddataDelayInMillis;

    DistributedSubImpl(final DistributedDataConfig config, final ActorRef subSupervisor) {
        this.config = config;
        this.subSupervisor = subSupervisor;
        ddataDelayInMillis = config.getSubscriptionDelay().toMillis();
    }

    @Override
    public CompletionStage<SubAck> subscribeWithFilterAndGroup(final Collection<String> topics,
            final ActorRef subscriber,
            @Nullable final Predicate<Collection<String>> filter,
            @Nullable final String group,
            final boolean resubscribe) {
        if (group != null) {
            checkNotEmpty(group, "group");
        }
        final Subscribe subscribe = Subscribe.of(topics, subscriber, true, filter, group, resubscribe);
        final CompletionStage<SubAck> subAckFuture = askSubSupervisor(subscribe);

        if (ddataDelayInMillis <= 0) {
            return subAckFuture;
        } else {
            return subAckFuture.thenCompose(result -> {
                // delay completion to account for dissemination delay between ddata replicator and change recipient
                final CompletableFuture<SubAck> resultFuture = new CompletableFuture<>();
                resultFuture.completeOnTimeout(result, ddataDelayInMillis, TimeUnit.MILLISECONDS);
                return resultFuture;
            });
        }
    }

    @Override
    public CompletionStage<SubAck> unsubscribeWithAck(final Collection<String> topics,
            final ActorRef subscriber) {
        return askSubSupervisor(Unsubscribe.of(topics, subscriber, true));
    }

    private CompletionStage<SubAck> askSubSupervisor(final Request request) {
        return Patterns.ask(subSupervisor, request, config.getWriteTimeout())
                .thenCompose(DistributedSubImpl::processAskResponse);
    }

    @Override
    public void subscribeWithoutAck(final Collection<String> topics, final ActorRef subscriber) {
        final Request request = Subscribe.of(topics, subscriber, false, null);
        subSupervisor.tell(request, subscriber);
    }

    @Override
    public void unsubscribeWithoutAck(final Collection<String> topics, final ActorRef subscriber) {
        final Request request = Unsubscribe.of(topics, subscriber, false);
        subSupervisor.tell(request, subscriber);
    }

    @Override
    public void removeSubscriber(final ActorRef subscriber) {
        final Request request = RemoveSubscriber.of(subscriber, false);
        subSupervisor.tell(request, subscriber);
    }

    private static CompletionStage<SubAck> processAskResponse(final Object askResponse) {
        if (askResponse instanceof SubAck subAck) {
            return CompletableFuture.completedStage(subAck);
        } else if (askResponse instanceof Throwable throwable) {
            return CompletableFuture.failedStage(throwable);
        } else {
            return CompletableFuture.failedStage(new ClassCastException("Expect SubAck, got: " + askResponse));
        }
    }
}
