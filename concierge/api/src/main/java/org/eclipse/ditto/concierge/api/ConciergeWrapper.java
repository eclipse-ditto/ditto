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
package org.eclipse.ditto.concierge.api;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.signals.Signal;

import akka.routing.ConsistentHashingRouter;

/**
 * Wrap messages which are send to the concierge service.
 */
public final class ConciergeWrapper {

    private ConciergeWrapper() {
        throw new AssertionError();
    }

    /**
     * Wrap a signal in a sharded hashable envelope addressed to the correct {@code EnforcerActor}.
     *
     * @param signal the signal to wrap.
     * @return the message envelope.
     */
    public static ConsistentHashingRouter.ConsistentHashableEnvelope wrapForEnforcerRouter(final Signal<?> signal) {
        return new ConsistentHashingRouter.ConsistentHashableEnvelope(signal, hashFor(signal));
    }

    private static String hashFor(final Signal<?> signal) {
        return WithEntityId.getEntityIdOfType(EntityId.class, signal)
                .map(Object::toString)
                .orElse(signal.getResourceType());
    }

}
