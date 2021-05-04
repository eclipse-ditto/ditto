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
package org.eclipse.ditto.internal.utils.health;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.concurrent.Immutable;

import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import scala.concurrent.duration.FiniteDuration;

/**
 * Actor which provides the health of multiple underlying systems, such as messaging and persistence by aggregating the
 * health by means of child actors. The child actors must answer {@link RetrieveHealth} requests with {@link StatusInfo}
 * answers. The composite {@link StatusInfo} contains the {@link StatusInfo} of its children in the order of the child
 * actors. The health statuses from the sub systems are retrieved in a configurable interval and cached in the meantime.
 */
public final class CompositeCachingHealthCheckingActor extends AbstractHealthCheckingActor {

    private final boolean enabled;
    private final Duration updateInterval;
    private final LinkedHashMap<String, ActorRef> labelsToChildActors;
    private final Map<ActorRef, String> childActorsToLabels;
    private final Map<String, StatusInfo> currentChildStatuses;
    private Cancellable checkHealthCancellable;

    /**
     * Constructs a {@link CompositeCachingHealthCheckingActor}.
     */
    @SuppressWarnings("unused")
    private CompositeCachingHealthCheckingActor(final Map<String, Props> childActorProps, final Duration updateInterval,
            final boolean enabled) {
        this.labelsToChildActors = new LinkedHashMap<>();

        for (final Map.Entry<String, Props> entry : childActorProps.entrySet()) {
            final String childLabel = entry.getKey();
            final Props childProps = entry.getValue();
            final ActorRef childActor = getContext().actorOf(childProps, childLabel);
            labelsToChildActors.put(childLabel, childActor);
        }

        this.childActorsToLabels = inverseMap(labelsToChildActors);
        this.currentChildStatuses = new LinkedHashMap<>();
        this.updateInterval = updateInterval;
        this.enabled = enabled;
    }

    /**
     * Creates Akka configuration object Props for this {@link CompositeCachingHealthCheckingActor}.
     *
     * @param childActorProps the Props for creating child actors, each providing health for a different subsystem.
     * @param updateInterval the duration between the updates of the health (sub systems will be queried for their
     * health in this interval).
     * @param enabled whether the health-check should be enabled.
     * @return the Akka configuration Props object
     */
    public static Props props(final Map<String, Props> childActorProps, final Duration updateInterval,
            final boolean enabled) {

        return Props.create(CompositeCachingHealthCheckingActor.class, childActorProps, updateInterval, enabled);
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        if (enabled) {
            final FiniteDuration interval = FiniteDuration.apply(updateInterval.toMillis(), TimeUnit.MILLISECONDS);
            checkHealthCancellable = getContext().system().scheduler()
                    .schedule(interval, interval, getSelf(), CheckHealth.newInstance(),
                            getContext().dispatcher(),
                            getSelf());
        } else {
            log.warning("HealthCheck is DISABLED - not polling for health at all");
        }
    }

    @Override
    public void postStop() throws Exception {
        if (checkHealthCancellable != null) {
            checkHealthCancellable.cancel();
        }
        super.postStop();
    }


    @Override
    protected Receive matchCustomMessages() {
        return ReceiveBuilder.create()
                .match(CheckHealth.class, checkHealth -> pollHealth())
                .match(StatusInfo.class, this::applyChildStatus)
                .build();
    }

    @Override
    protected void triggerHealthRetrieval() {
        // just use the currently cached status
        updateHealth(getAggregatedStatus());
    }

    private static <K, V> Map<V, K> inverseMap(final Map<K, V> toInverse) {
        Map<V, K> inverseMap = new HashMap<>();
        for (Map.Entry<K, V> entry : toInverse.entrySet()) {
            inverseMap.put(entry.getValue(), entry.getKey());
        }
        return inverseMap;
    }

    private StatusInfo getAggregatedStatus() {
        final List<StatusInfo> resultingChildStatuses = new ArrayList<>(labelsToChildActors.size());

        labelsToChildActors.keySet().forEach(label -> {
            final StatusInfo childStatus = currentChildStatuses.getOrDefault(label, StatusInfo.unknown())
                    .label(label);
            resultingChildStatuses.add(childStatus);
        });

        return StatusInfo.composite(resultingChildStatuses);
    }

    private void applyChildStatus(final StatusInfo statusInfo) {
        final ActorRef sender = getSender();
        final String labelToUpdate = childActorsToLabels.get(sender);
        if (labelToUpdate == null) {
            log.warning("Ignoring status from unknown sender <{}> : <{}>", sender, statusInfo);
            return;
        }

        currentChildStatuses.put(labelToUpdate, statusInfo.label(labelToUpdate));
    }

    private void pollHealth() {
        labelsToChildActors.values()
                .forEach(childActor ->
                        childActor.tell(RetrieveHealth.newInstance(), getSelf()));
    }

    /**
     * Internal command to check the health of underlying systems.
     */
    @Immutable
    private static final class CheckHealth {

        private CheckHealth() {
        }

        /**
         * Returns a new {@code CheckHealth} instance.
         *
         * @return the new CheckHealth instance.
         */
        public static CheckHealth newInstance() {
            return new CheckHealth();
        }
    }
}
