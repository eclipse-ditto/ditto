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
package org.eclipse.ditto.services.concierge.enforcement;

import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.services.models.concierge.EntityId;
import org.eclipse.ditto.services.models.concierge.cache.Entry;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.things.ThingCommand;

/**
 * Creates an {@link EnforcerRetriever} which retrieves an enforcer by using an acl- or policy-enforcer-cache depending
 * on the {@code resourceType} of the requested {@code entityId}.
 */
final class PolicyOrAclEnforcerRetrieverFactory {

    private PolicyOrAclEnforcerRetrieverFactory() {
        throw new AssertionError();
    }

    /**
     * Creates a new instance.
     *
     * @param idCache the id-cache.
     * @param policyEnforcerCache the policy-enforcer-cache.
     * @param aclEnforcerCache the acl-enforcer-cache.
     * @return the instance.
     */
    public static EnforcerRetriever create(
            final Cache<EntityId, Entry<EntityId>> idCache,
            final Cache<EntityId, Entry<Enforcer>> policyEnforcerCache,
            final Cache<EntityId, Entry<Enforcer>> aclEnforcerCache) {
        requireNonNull(idCache);
        requireNonNull(policyEnforcerCache);
        requireNonNull(aclEnforcerCache);

        final Map<String, Cache<EntityId, Entry<Enforcer>>> mapping = new HashMap<>();
        mapping.put(PolicyCommand.RESOURCE_TYPE, policyEnforcerCache);
        mapping.put(ThingCommand.RESOURCE_TYPE, aclEnforcerCache);

        return new EnforcerRetriever(idCache, mapping);
    }

}
