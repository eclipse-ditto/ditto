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
package org.eclipse.ditto.services.utils.akka.streaming;

import akka.actor.ActorRef;

/**
 * Allows implementation of strategies for {@link AbstractStreamForwarder}.
 *
 * @param <E> Type of received stream elements of the forwarder.
 */
public interface ForwardingStrategy<E> {

    /**
     * Forward the given {@code element} and notify the forwarder via {@code callback} about the id(s) of the forwarded
     * element(s) to wait for their acknowledgement. This way, you can dispatch an element to multiple receivers and
     * wait for their acknowledgment.
     *
     * @param element the element to be forwarded
     * @param forwarderActorRef the {@link ActorRef} of the forwarder
     * @param callback the {@link ForwarderCallback} to tell the forwarder which acknowledgment it has to expect
     */
    void forward(E element, ActorRef forwarderActorRef, ForwarderCallback callback);

    /**
     * React on stream completion.
     *
     * @param forwarderActorRef the {@link ActorRef} of the forwarder
     */
    void onComplete(ActorRef forwarderActorRef);

    /**
     * React on stream timeout (maximum idle time exceeded).
     *
     * @param forwarderActorRef the {@link ActorRef} of the forwarder
     */
    void maxIdleTimeExceeded(ActorRef forwarderActorRef);
}
