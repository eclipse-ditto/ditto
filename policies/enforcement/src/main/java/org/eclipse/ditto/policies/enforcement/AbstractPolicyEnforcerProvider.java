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
package org.eclipse.ditto.policies.enforcement;

import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.policies.model.PolicyId;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorSystem;
import akka.dispatch.MessageDispatcher;

/**
 * Abstract base of {@link PolicyEnforcer} implementations.
 */
abstract class AbstractPolicyEnforcerProvider implements PolicyEnforcerProvider {

    protected AbstractPolicyEnforcerProvider() {
        // no-op
    }

    protected static AsyncCacheLoader<PolicyId, Entry<PolicyEnforcer>> policyEnforcerCacheLoader(
            final ActorSystem actorSystem) {

        final PolicyCacheLoader policyCacheLoader = PolicyCacheLoader.getSingletonInstance(actorSystem);
        return new PolicyEnforcerCacheLoader(policyCacheLoader);
    }

    protected static MessageDispatcher enforcementCacheDispatcher(final ActorSystem actorSystem) {
        return actorSystem.dispatchers().lookup(PolicyEnforcerCacheLoader.ENFORCEMENT_CACHE_DISPATCHER);
    }

}
