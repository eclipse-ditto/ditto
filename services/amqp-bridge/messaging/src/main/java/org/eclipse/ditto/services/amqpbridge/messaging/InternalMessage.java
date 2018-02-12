/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.amqpbridge.messaging;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.PayloadMapperMessage;


/**
 * Simple wrapper around {@link DittoHeaders} and the command as a JSON String received from external AMQP source.
 * An instance of this message can be forwarded to the {@link CommandProcessorActor} for further processing.
 */
public class InternalMessage {

    private final Ack<?> ackMessage;
    private final Map<String, String> headers;
    private final ByteBuffer payload;

    public InternalMessage(final Ack<?> ackMessage, final Map<String, String> headers, final ByteBuffer payload) {
        this.ackMessage = ackMessage;
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        this.payload = payload;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public ByteBuffer getPayload() {
        return payload;
    }

    public Ack<?> getAckMessage() {
        return ackMessage;
    }

    private PayloadMapperMessage toPayloadMapperMessage() {
        throw new UnsupportedOperationException("not implemented");
    }

    public static final class Ack<T> {

        private T message;

        public Ack(T message) {
            this.message = message;
        }

        public T getMessage() {
            return message;
        }
    }
}
