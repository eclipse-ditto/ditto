/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.pubsub.api;

import akka.actor.ActorRef;

/**
 * Reply for acknowledgement label declaration.
 */
public final class AcksDeclared {

    private final AckRequest request;
    private final ActorRef sender;

    private AcksDeclared(final AckRequest request, final ActorRef sender) {
        this.request = request;
        this.sender = sender;
    }

    /**
     * Create a reply for acknowledgement label declaration.
     *
     * @param request the declaration request.
     * @param sender the sender of the request.
     * @return the reply.
     */
    public static AcksDeclared of(final AckRequest request, final ActorRef sender) {
        return new AcksDeclared(request, sender);
    }

    /**
     * @return the request this object is acknowledging.
     */
    public AckRequest getRequest() {
        return request;
    }

    /**
     * @return sender of the request.
     */
    public ActorRef getSender() {
        return sender;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "[request=" + request +
                ",sender=" + sender +
                "]";
    }
}
