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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import scala.NotImplementedError;

/**
 * Loads an enforcer by asking entity shard regions.
 */
@Immutable
@AllValuesAreNonnullByDefault
final class EnforcerCacheLoader implements AsyncCacheLoader<ResourceKey, Versioned<Enforcer>> {

    @Override
    public CompletableFuture<Versioned<Enforcer>> asyncLoad(final ResourceKey key, final Executor executor) {
        throw new NotImplementedError();
    }
}
