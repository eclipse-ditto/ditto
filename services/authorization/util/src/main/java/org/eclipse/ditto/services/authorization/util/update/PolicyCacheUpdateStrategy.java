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
package org.eclipse.ditto.services.authorization.util.update;

import org.eclipse.ditto.services.authorization.util.cache.AuthorizationCaches;
import org.eclipse.ditto.services.models.authorization.EntityId;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;

/**
 * A strategy implementation for cache updates due to Policy Events.
 */
public class PolicyCacheUpdateStrategy extends AbstractCacheUpdateStrategy<PolicyEvent> {

    /**
     * Constructor.
     * @param caches the caches.
     */
    public PolicyCacheUpdateStrategy(final AuthorizationCaches caches) {
        super(caches);
    }

    @Override
    public String getEventTopic() {
        return PolicyEvent.TYPE_PREFIX;
    }

    @Override
    public Class<PolicyEvent> getEventClass() {
        return PolicyEvent.class;
    }

    @Override
    public void handleEvent(final PolicyEvent policyEvent) {
        // TODO CR-5397: be less wasteful.
        final EntityId key = EntityId.of(PolicyCommand.RESOURCE_TYPE, policyEvent.getPolicyId());
        getCaches().invalidateAll(key);
    }
}
