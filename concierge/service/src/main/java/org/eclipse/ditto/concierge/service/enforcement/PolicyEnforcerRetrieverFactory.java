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
package org.eclipse.ditto.concierge.service.enforcement;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.internal.utils.cacheloaders.EnforcementCacheKey;
import org.eclipse.ditto.policies.model.PolicyConstants;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;

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
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static EnforcerRetriever<Enforcer> create(
            final Cache<EnforcementCacheKey, Entry<EnforcementCacheKey>> idCache,
            final Cache<EnforcementCacheKey, Entry<Enforcer>> policyEnforcerCache
    ) {
        ConditionChecker.checkNotNull(idCache, "idCache");
        ConditionChecker.checkNotNull(policyEnforcerCache, "policyEnforcerCache");

        final Map<EntityType, Cache<EnforcementCacheKey, Entry<Enforcer>>> mapping = new HashMap<>();
        mapping.put(PolicyConstants.ENTITY_TYPE, policyEnforcerCache);

        return new EnforcerRetriever<>(idCache, mapping);
    }

}
