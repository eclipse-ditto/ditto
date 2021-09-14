/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging;

import org.eclipse.ditto.connectivity.api.ExternalMessage;

/**
 * Combines an {@link ExternalMessage}, which was consumed by a connection, and their responsible settle or reject
 * actions.
 */
public final class AcknowledgeableMessage {

    private final ExternalMessage message;
    private final Runnable settle;
    private final BaseConsumerActor.Reject reject;

    private AcknowledgeableMessage(final ExternalMessage message, final Runnable settle,
            final BaseConsumerActor.Reject reject) {
        this.message = message;
        this.settle = settle;
        this.reject = reject;
    }

    /**
     * Creates a new instance of an acknowledgeable message.
     *
     * @param externalMessage The message which can be acknowledged.
     * @param settle The action that should be called on a successful acknowledge.
     * @param reject The action that should be called on a failed acknowledge.
     * @return the acknowledgeable message.
     */
    public static AcknowledgeableMessage of(final ExternalMessage externalMessage, final Runnable settle,
            final BaseConsumerActor.Reject reject) {
        return new AcknowledgeableMessage(externalMessage, settle, reject);
    }

    void settle() {
        this.settle.run();
    }

    void reject(final boolean shouldRedeliver) {
        this.reject.reject(shouldRedeliver);
    }

    ExternalMessage getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "message=" + message +
                "]";
    }

}
