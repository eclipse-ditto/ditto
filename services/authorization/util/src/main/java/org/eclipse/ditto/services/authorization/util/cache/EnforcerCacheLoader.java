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
package org.eclipse.ditto.services.authorization.util.cache;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.services.authorization.util.cache.entry.Entry;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

import akka.actor.ActorRef;

/**
 * Loads an enforcer by asking entity shard regions.
 */
@Immutable
@AllValuesAreNonnullByDefault
public class EnforcerCacheLoader extends AbstractAskCacheLoader<Enforcer> {

    @Override
    protected Duration getAskTimeout() {
        return null;
    }

    @Override
    protected ActorRef getEntityRegion(final String resourceType) {
        return null;
    }

    @Override
    protected Object getCommand(final String resourceType, final String id) {
        return null;
    }

    @Override
    protected Entry<Enforcer> transformResponse(final String resourceType, final Object response) {
        return null;
    }
}
