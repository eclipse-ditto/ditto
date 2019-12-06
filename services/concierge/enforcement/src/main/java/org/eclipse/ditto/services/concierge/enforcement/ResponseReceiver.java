/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;

import akka.actor.ActorRef;

/**
 * Keeps all information relevant for response handling of live signals.
 */
final class ResponseReceiver {

    private final ActorRef actorRef;
    private final DittoHeaders internalHeaders;

    private ResponseReceiver(final ActorRef actorRef, final DittoHeaders internalHeaders) {
        this.actorRef = actorRef;
        this.internalHeaders = internalHeaders;
    }

    /**
     * Create a cache entry for live response handling.
     *
     * @param sender who sent the live signal.
     * @param commandHeaders headers of the live signal.
     * @return context to handle the response of the live signal.
     */
    static ResponseReceiver of(final ActorRef sender, final DittoHeaders commandHeaders) {
        return new ResponseReceiver(sender, filterRelevantHeaders(commandHeaders));
    }

    /**
     * @return sender of the original live signal and receiver of the response.
     */
    ActorRef ref() {
        return actorRef;
    }

    /**
     * Enhance response by relevant internal headers from the original live signal.
     *
     * @param signal the original signal.
     * @return the enhanced signal.
     */
    WithDittoHeaders enhance(final WithDittoHeaders signal) {
        return internalHeaders.isEmpty()
                ? signal
                : signal.setDittoHeaders(signal.getDittoHeaders().toBuilder().putHeaders(internalHeaders).build());
    }

    private static DittoHeaders filterRelevantHeaders(final DittoHeaders commandHeaders) {
        return commandHeaders.getReplyTarget()
                .map(replyTarget -> DittoHeaders.newBuilder().replyTarget(replyTarget).build())
                .orElse(DittoHeaders.empty());
    }
}
