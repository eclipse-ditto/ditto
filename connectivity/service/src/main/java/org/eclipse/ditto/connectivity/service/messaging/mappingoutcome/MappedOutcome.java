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
import org.eclipse.ditto.protocol.TopicPath;

// private to MappingOutcome. Do NOT use directly.
final class MappedOutcome<T> implements MappingOutcome<T> {

    private final CharSequence mapperId;
    private final T mapped;
    private final TopicPath topicPath;
    @Nullable private final ExternalMessage externalMessage;

    MappedOutcome(final CharSequence mapperId, final T mapped, final TopicPath topicPath,
            @Nullable final ExternalMessage externalMessage) {
        this.mapperId = mapperId;
        this.mapped = mapped;
        this.topicPath = topicPath;
        this.externalMessage = externalMessage;
    }

    @Override
    public <R> R accept(final Visitor<T, R> visitor) {
        try {
            return visitor.onMapped(mapperId.toString(), mapped);
        } catch (final Exception e) {
            return visitor.onError(mapperId.toString(), e, topicPath, externalMessage);
        }
    }

    @Override
    public boolean wasSuccessfullyMapped() {
        return true;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "[mapperId=" + mapperId +
                ",mapped=" + mapped +
                ",topicPath=" + topicPath +
                ",externalMessage=" + externalMessage +
                "]";
    }

}
