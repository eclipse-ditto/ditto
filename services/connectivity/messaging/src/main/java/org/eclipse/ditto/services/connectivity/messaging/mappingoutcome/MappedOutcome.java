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
package org.eclipse.ditto.services.connectivity.messaging.mappingoutcome;

import javax.annotation.Nullable;

import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;

// private to MappingOutcome. Do NOT use directly.
final class MappedOutcome<T> implements MappingOutcome<T> {

    private final T mapped;
    private final TopicPath topicPath;
    @Nullable private final ExternalMessage externalMessage;

    MappedOutcome(final T mapped, final TopicPath topicPath, @Nullable final ExternalMessage externalMessage) {
        this.mapped = mapped;
        this.topicPath = topicPath;
        this.externalMessage = externalMessage;
    }

    @Override
    public <R> R accept(final Visitor<T, R> visitor) {
        try {
            return visitor.onMapped(mapped);
        } catch (final Exception e) {
            return visitor.onError(e, topicPath, externalMessage);
        }
    }
}
