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

import akka.actor.Status;

/**
 * Constants used for streaming utils.
 */
public final class StreamConstants {

    /**
     * Message which signals that a stream has been successfully finished.
     */
    public static final Object STREAM_COMPLETED = new Status.Success("done");

    /**
     * Message which signals that a stream is started.
     */
    public static final Object STREAM_STARTED = new Status.Success("start");

    /**
     * Ack message from {@code AbstractStreamForwarder} to {@code AbstractStreamingActor} for back-pressure.
     */
    public static final Object STREAM_ACK_MSG = new Status.Success("ack");

    /**
     * Message to signal upstream failure.
     */
    public static final Object STREAM_FAILED = new Status.Success("failed");

    /**
     * Message received by stream forwarder to signal that all messages for a stream element have been sent.
     */
    public static final Object DOES_NOT_HAVE_NEXT_MSG = new Status.Success("no-next");

    /**
     * Message which signals the forwarder stayed idle for too long.
     */
    public static final Object FORWARDER_EXCEEDED_MAX_IDLE_TIME_MSG = new Status.Success("max-idle-time-exceeded");

    private StreamConstants() {
        throw new AssertionError();
    }
}
