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
package org.eclipse.ditto.services.authorization.util.actors;

import java.time.Duration;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.services.authorization.util.EntityRegionMap;
import org.eclipse.ditto.services.authorization.util.cache.AuthorizationCaches;
import org.eclipse.ditto.services.models.authorization.EntityId;

import akka.actor.Actor;
import akka.event.DiagnosticLoggingAdapter;

/**
 * Contains self-type requirements for aspects of enforcer actor dealing with specific commands.
 * Do NOT call the methods outside this package.
 */
public interface CommandEnforcementSelfType extends Actor {

    /**
     * Self-type requirement: It has the timeout duration for asking an entity shard region.
     * Do not call outside of this package.
     *
     * @return the timeout duration for asking an entity shard region.
     */
    Duration getAskTimeout();

    /**
     * Self-type requirement: It has an {@code EntityRegionMap}.
     * Do not call outside of this package.
     *
     * @return the entity region map.
     */
    EntityRegionMap entityRegionMap();

    /**
     * Self-type requirement: It has an entity ID.
     * Do not call outside of this package.
     *
     * @return the entity ID.
     */
    EntityId entityId();

    /**
     * Self-type requirement: It has a diagnostic logging adapter.
     * Do not call outside of this package.
     *
     * @return the diagnostic logging adapter.
     */
    DiagnosticLoggingAdapter log();

    /**
     * Self-type requirement: It has authorization caches.
     * Do not call outside of this package.
     *
     * @return the authorization caches.
     */
    AuthorizationCaches caches();
}
