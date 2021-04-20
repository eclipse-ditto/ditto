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
package org.eclipse.ditto.services.concierge.enforcement;

import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.model.base.entity.type.EntityType;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.policies.PolicyConstants;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.services.utils.cache.CacheKey;
import org.eclipse.ditto.services.utils.cache.entry.Entry;

/**
 * Creates an {@link EnforcerRetriever} which retrieves an enforcer by using an policy-enforcer-cache.
 */
final class PolicyEnforcerRetrieverFactory {

    private PolicyEnforcerRetrieverFactory() {
        throw new AssertionError();
    }

    /**
     * Creates a new instance.
     *
     * @param idCache the id-cache.
     * @param policyEnforcerCache the policy-enforcer-cache.
     * @return the instance.
     */
    public static EnforcerRetriever<Enforcer> create(
            final Cache<CacheKey, Entry<CacheKey>> idCache,
            final Cache<CacheKey, Entry<Enforcer>> policyEnforcerCache) {
        requireNonNull(idCache);
        requireNonNull(policyEnforcerCache);

        final Map<EntityType, Cache<CacheKey, Entry<Enforcer>>> mapping = new HashMap<>();
        mapping.put(PolicyConstants.ENTITY_TYPE, policyEnforcerCache);

        return new EnforcerRetriever<>(idCache, mapping);
    }

}
