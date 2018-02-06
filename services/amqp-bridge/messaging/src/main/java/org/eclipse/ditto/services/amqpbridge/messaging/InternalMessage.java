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

import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * Simple wrapper around {@link DittoHeaders} and the command as a JSON String received from external AMQP source.
 * An instance of this message can be forwarded to the {@link CommandProcessorActor} for further processing.
 */
public class InternalMessage {

    private Ack<?> ackMessage;
    private DittoHeaders dittoHeaders;
    private String commandJsonString;

    public InternalMessage(final Ack<?> ackMessage, final DittoHeaders dittoHeaders, final String commandJsonString) {
        this.ackMessage = ackMessage;
        this.dittoHeaders = dittoHeaders;
        this.commandJsonString = commandJsonString;
    }

    public DittoHeaders getDittoHeaders() {
        return dittoHeaders;
    }

    public String getCommandJsonString() {
        return commandJsonString;
    }

    public Ack<?> getAckMessage() {
        return ackMessage;
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
