/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.distributedcache.actors;

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.distributedcache.model.CacheEntry;

import akka.actor.ActorRef;

/**
 * Command for registering for {@link CacheEntry} updates wrapped in
 * {@link akka.cluster.ddata.Replicator.Changed} messages issued to the {@code subscriber} of this message.
 */
@Immutable
public final class RegisterForCacheUpdates implements CacheCommand {

    private final String id;
    private final ActorRef subscriber;

    /**
     * Creates a new {@code RegisterForCacheUpdates}.
     *
     * @param id the ID of the CacheEntry to subscribe for.
     * @param subscriber the {@link ActorRef} receiving the {@link akka.cluster.ddata.Replicator.Changed} events for the
     * {@code id}.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code id} is empty.
     */
    public RegisterForCacheUpdates(final String id, final ActorRef subscriber) {
        this.id = argumentNotEmpty(id, "ID");
        this.subscriber = checkNotNull(subscriber, "subscriber");
    }

    /**
     * The {@link ActorRef} receiving the {@link akka.cluster.ddata.Replicator.Changed} events for the {@code id}.
     *
     * @return ActorRef receiving the Replicator.Changed events for the {@code id}.
     */
    public ActorRef getSubscriber() {
        return subscriber;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RegisterForCacheUpdates that = (RegisterForCacheUpdates) o;
        return Objects.equals(id, that.id) && Objects.equals(subscriber, that.subscriber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, subscriber);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "id=" + id +
                ", subscriber=" + subscriber +
                "]";
    }

}
