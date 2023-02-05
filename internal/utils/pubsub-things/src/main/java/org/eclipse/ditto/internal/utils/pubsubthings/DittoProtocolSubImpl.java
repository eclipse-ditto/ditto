/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.pubsubthings;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabelInvalidException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.pubsub.DistributedAcks;
import org.eclipse.ditto.internal.utils.pubsub.DistributedSub;
import org.eclipse.ditto.internal.utils.pubsub.StreamingType;
import org.eclipse.ditto.internal.utils.pubsub.api.SubAck;
import org.eclipse.ditto.internal.utils.pubsubpolicies.PolicyAnnouncementPubSubFactory;

import akka.actor.AbstractExtensionId;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;

/**
 * Default implementation of {@link DittoProtocolSub}.
 */
final class DittoProtocolSubImpl implements DittoProtocolSub {

    private final DistributedSub liveSignalSub;
    private final DistributedSub twinEventSub;
    private final DistributedSub policyAnnouncementSub;
    private final DistributedAcks distributedAcks;

    private DittoProtocolSubImpl(final DistributedSub liveSignalSub,
            final DistributedSub twinEventSub,
            final DistributedSub policyAnnouncementSub,
            final DistributedAcks distributedAcks) {
        this.liveSignalSub = liveSignalSub;
        this.twinEventSub = twinEventSub;
        this.policyAnnouncementSub = policyAnnouncementSub;
        this.distributedAcks = distributedAcks;
    }

    static DittoProtocolSubImpl of(final ActorSystem system, final DistributedAcks distributedAcks) {
        final DistributedSub liveSignalSub =
                LiveSignalPubSubFactory.of(system, distributedAcks).startDistributedSub();
        final DistributedSub twinEventSub =
                ThingEventPubSubFactory.readSubjectsOnly(system, distributedAcks).startDistributedSub();
        final DistributedSub policyAnnouncementSub =
                PolicyAnnouncementPubSubFactory.of(system, system).startDistributedSub();
        return new DittoProtocolSubImpl(liveSignalSub, twinEventSub, policyAnnouncementSub, distributedAcks);
    }

    @Override
    public CompletionStage<Boolean> subscribe(final Collection<StreamingType> types,
            final Collection<String> topics,
            final ActorRef subscriber,
            @Nullable final String group,
            final boolean resubscribe) {
        final CompletionStage<SubAck> nop = CompletableFuture.completedFuture(null);
        return partitionByStreamingTypes(types,
                liveTypes -> !liveTypes.isEmpty()
                        ? liveSignalSub.subscribeWithFilterAndGroup(topics, subscriber, toFilter(liveTypes), group,
                        resubscribe)
                        : nop,
                hasTwinEvents -> hasTwinEvents
                        ? twinEventSub.subscribeWithFilterAndGroup(topics, subscriber, null, group, resubscribe)
                        : nop,
                hasPolicyAnnouncements -> hasPolicyAnnouncements
                        ? policyAnnouncementSub.subscribeWithFilterAndGroup(topics, subscriber, null, group,
                        resubscribe)
                        : nop
        );
    }

    @Override
    public void removeSubscriber(final ActorRef subscriber) {
        liveSignalSub.removeSubscriber(subscriber);
        twinEventSub.removeSubscriber(subscriber);
        policyAnnouncementSub.removeSubscriber(subscriber);
        distributedAcks.removeSubscriber(subscriber);
    }

    @Override
    public CompletionStage<Void> updateLiveSubscriptions(final Collection<StreamingType> types,
            final Collection<String> topics,
            final ActorRef subscriber) {

        return partitionByStreamingTypes(types,
                liveTypes -> !liveTypes.isEmpty()
                        ? liveSignalSub.subscribeWithFilterAndGroup(topics, subscriber, toFilter(liveTypes), null,
                        false)
                        : liveSignalSub.unsubscribeWithAck(topics, subscriber),
                hasTwinEvents -> CompletableFuture.completedStage(null),
                hasPolicyAnnouncements -> CompletableFuture.completedStage(null)
        ).thenApply(consistent -> null);
    }

    @Override
    public CompletionStage<Void> removeTwinSubscriber(final ActorRef subscriber, final Collection<String> topics) {
        return twinEventSub.unsubscribeWithAck(topics, subscriber).thenApply(ack -> null);
    }

    @Override
    public CompletionStage<Void> removePolicyAnnouncementSubscriber(final ActorRef subscriber,
            final Collection<String> topics) {
        return policyAnnouncementSub.unsubscribeWithAck(topics, subscriber).thenApply(ack -> null);
    }

    @Override
    public CompletionStage<Void> declareAcknowledgementLabels(
            final Collection<AcknowledgementLabel> acknowledgementLabels,
            final ActorRef subscriber,
            @Nullable final String group) {
        if (acknowledgementLabels.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            ensureAcknowledgementLabelsAreFullyResolved(acknowledgementLabels);
        } catch (final DittoRuntimeException dre) {
            return CompletableFuture.failedStage(dre);
        }

        return distributedAcks.declareAcknowledgementLabels(acknowledgementLabels, subscriber, group)
                .thenApply(ack -> null);
    }

    private static void ensureAcknowledgementLabelsAreFullyResolved(final Collection<AcknowledgementLabel> ackLabels) {
        ackLabels.stream()
                .filter(Predicate.not(AcknowledgementLabel::isFullyResolved))
                .findFirst()
                .ifPresent(ackLabel -> {
                    throw AcknowledgementLabelInvalidException.of(ackLabel,
                            "AcknowledgementLabel was not fully resolved while trying to declare it",
                            null,
                            DittoHeaders.empty());
                });
    }

    @Override
    public void removeAcknowledgementLabelDeclaration(final ActorRef subscriber) {
        distributedAcks.removeAcknowledgementLabelDeclaration(subscriber);
    }

    private CompletionStage<Boolean> partitionByStreamingTypes(final Collection<StreamingType> types,
            final Function<Set<StreamingType>, CompletionStage<SubAck>> onLiveSignals,
            final Function<Boolean, CompletionStage<SubAck>> onTwinEvents,
            final Function<Boolean, CompletionStage<SubAck>> onPolicyAnnouncement) {
        final Set<StreamingType> liveTypes;
        final boolean hasTwinEvents;
        final boolean hasPolicyAnnouncements;
        if (types.isEmpty()) {
            liveTypes = Collections.emptySet();
            hasTwinEvents = false;
            hasPolicyAnnouncements = false;
        } else {
            liveTypes = EnumSet.copyOf(types);
            hasTwinEvents = liveTypes.remove(StreamingType.EVENTS);
            hasPolicyAnnouncements = liveTypes.remove(StreamingType.POLICY_ANNOUNCEMENTS);
        }
        final var liveStage = onLiveSignals.apply(liveTypes).thenApply(this::isConsistent);
        final var twinStage = onTwinEvents.apply(hasTwinEvents).thenApply(this::isConsistent);
        final var policyAnnouncementStage =
                onPolicyAnnouncement.apply(hasPolicyAnnouncements).thenApply(this::isConsistent);
        return liveStage.thenCompose(liveConsistent ->
                twinStage.thenCompose(twinConsistent ->
                        policyAnnouncementStage.thenApply(policyConsistent ->
                                liveConsistent && twinConsistent && policyConsistent)));
    }

    private boolean isConsistent(@Nullable final SubAck ack) {
        return ack == null || ack.isConsistent();
    }

    private static Predicate<Collection<String>> toFilter(final Collection<StreamingType> streamingTypes) {
        final Set<String> streamingTypeTopics =
                streamingTypes.stream().map(StreamingType::getDistributedPubSubTopic).collect(Collectors.toSet());
        return topics -> topics.stream().anyMatch(streamingTypeTopics::contains);
    }

    static final class ExtensionId extends AbstractExtensionId<DittoProtocolSub> {

        static final ExtensionId INSTANCE = new ExtensionId();

        private ExtensionId() {}

        @Override
        public DittoProtocolSub createExtension(final ExtendedActorSystem system) {
            final DistributedAcks distributedAcks = DistributedAcks.create(system);
            return of(system, distributedAcks);
        }
    }
}
