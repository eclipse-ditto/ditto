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
package org.eclipse.ditto.services.models.concierge;

import java.util.HashMap;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.utils.akka.controlflow.AbstractGraphActor;
import org.eclipse.ditto.services.utils.cache.EntityId;
import org.eclipse.ditto.signals.base.Signal;

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

    /**
     * Wrap a signal in a sharded hashable envelope addressed to the correct {@code EnforcerActor}. Defines that for
     * the wrapped {@code signal} the "special enforcement lane" shall be used - meaning that those messages are
     * processed not based on the hash of their ID but in a common "special lane".
     * <p>
     * Be aware that when using this wrapper all those signals will be effectively sequentially processed but they could
     * be processed in parallel to other signals whose IDs have the same hash partition in {@link AbstractGraphActor}.
     * </p>
     *
     * @param signal the signal to wrap.
     * @return the message envelope.
     */
    public static ConsistentHashingRouter.ConsistentHashableEnvelope wrapForEnforcerRouterSpecialLane(
            final Signal<?> signal) {
        final DittoHeaders dittoHeaders = signal.getDittoHeaders();
        final HashMap<String, String> enhancedMap = new HashMap<>(dittoHeaders);
        enhancedMap.put(AbstractGraphActor.DITTO_INTERNAL_SPECIAL_ENFORCEMENT_LANE, "true");
        final Signal adjustedSignal = signal.setDittoHeaders(DittoHeaders.of(enhancedMap));
        return new ConsistentHashingRouter.ConsistentHashableEnvelope(adjustedSignal, hashFor(adjustedSignal));
    }

    private static String hashFor(final Signal<?> signal) {
        return EntityId.of(signal.getResourceType(), signal.getId()).toString();
    }

}
