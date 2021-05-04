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
package org.eclipse.ditto.internal.models.streaming;

/**
 * Represents an identifiable Streaming message (identifier is required to correlate acknowledgements).
 */
public interface IdentifiableStreamingMessage extends StreamingMessage {

    /**
     * Returns an identifier for this message.
     *
     * @return the identifier
     */
    String asIdentifierString();

}
