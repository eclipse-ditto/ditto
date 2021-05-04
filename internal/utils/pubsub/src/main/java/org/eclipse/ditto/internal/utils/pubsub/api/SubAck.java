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

import java.util.Objects;

import akka.actor.ActorRef;

/**
 * Acknowledgement for subscription requests.
 */
public final class SubAck {

    private final Request request;
    private final ActorRef sender;
    private final int seqNr;

    private SubAck(final Request request, final ActorRef sender, final int seqNr) {
        this.request = request;
        this.sender = sender;
        this.seqNr = seqNr;
    }

    /**
     * Creates a new acknowledgement for a subscription request.
     *
     * @param request the subscription request.
     * @param sender the sender of the subscription request.
     * @param seqNr the sequence number increased for each subscription request.
     * @return the created instance of SubAck.
     */
    public static SubAck of(final Request request, final ActorRef sender, final int seqNr) {
        return new SubAck(request, sender, seqNr);
    }

    /**
     * @return the request this object is acknowledging.
     */
    public Request getRequest() {
        return request;
    }

    /**
     * @return sender of the request.
     */
    public ActorRef getSender() {
        return sender;
    }

    /**
     * @return the sequence number.
     */
    public int getSeqNr() {
        return seqNr;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SubAck subAck = (SubAck) o;
        return seqNr == subAck.seqNr &&
                Objects.equals(request, subAck.request) &&
                Objects.equals(sender, subAck.sender);
    }

    @Override
    public int hashCode() {
        return Objects.hash(request, sender, seqNr);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "request=" + request +
                ", sender=" + sender +
                ", seqNr=" + seqNr +
                "]";
    }
}
