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
package org.eclipse.ditto.internal.models.streaming;

/**
 * Supertype of commands to request the start of a stream.
 */
public interface StartStreamRequest extends StreamingMessage {

    /**
     * Returns the streaming burst.
     *
     * @return number of elements to send per message.
     */
    int getBurst();

    /**
     * Returns the timeout in milliseconds.
     *
     * @return the timeout.
     */
    long getTimeoutMillis();

}
