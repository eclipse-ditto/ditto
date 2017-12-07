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

    // must not collide with any (thing-)id -> fulfilled if it does not contain ':'
    private static final String STREAM_FINISHED_TXT = "done";
    /**
     * Message which signals that a stream has been successfully finished.
     */
    public static final Object STREAM_FINISHED_MSG = new Status.Success(STREAM_FINISHED_TXT);

    private StreamConstants() {
        throw new AssertionError();
    }
}
