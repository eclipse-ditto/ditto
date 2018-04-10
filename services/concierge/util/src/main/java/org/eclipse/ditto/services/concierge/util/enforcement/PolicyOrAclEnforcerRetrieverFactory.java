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
package org.eclipse.ditto.services.concierge.util.enforcement;

import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.services.concierge.util.cache.entry.Entry;
import org.eclipse.ditto.services.models.concierge.EntityId;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.things.ThingCommand;

public final class PolicyOrAclEnforcerRetrieverFactory {

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
