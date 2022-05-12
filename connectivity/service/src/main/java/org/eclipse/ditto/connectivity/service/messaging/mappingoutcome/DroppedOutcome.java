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
package org.eclipse.ditto.connectivity.service.messaging.mappingoutcome;

import javax.annotation.Nullable;

import org.eclipse.ditto.connectivity.api.ExternalMessage;

// private to MappingOutcome. Do NOT use directly.
final class DroppedOutcome<T> implements MappingOutcome<T> {

    private final CharSequence mapperId;
    private final ExternalMessage droppedMessage;

    DroppedOutcome(final CharSequence mapperId, @Nullable final ExternalMessage droppedMessage) {
        this.mapperId = mapperId;
        this.droppedMessage = droppedMessage;
    }

    @Override
    public <R> R accept(final Visitor<T, R> visitor) {
        try {
            return visitor.onDropped(mapperId.toString(), droppedMessage);
        } catch (final Exception e) {
            return visitor.onError(mapperId.toString(), e, null, droppedMessage);
        }
    }

    @Override
    public boolean wasSuccessfullyMapped() {
        return false;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "[mapperId=" + mapperId +
                ",externalMessage=" + droppedMessage +
                "]";
    }

}
