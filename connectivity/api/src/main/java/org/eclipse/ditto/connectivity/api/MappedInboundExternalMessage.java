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
package org.eclipse.ditto.connectivity.api;

import java.util.Objects;

import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.base.model.signals.Signal;

/**
 * Represent an inbound {@link Signal} that was mapped from an {@link ExternalMessage}.
 * It wraps the original {@link ExternalMessage}, the mapped {@link Signal} and the {@link TopicPath} of the mapped
 * signal.
 */
public final class MappedInboundExternalMessage implements InboundExternalMessage {

    private final ExternalMessage externalMessage;
    private final TopicPath topicPath;
    private final Signal<?> signal;

    private MappedInboundExternalMessage(final ExternalMessage externalMessage, final TopicPath topicPath,
            final Signal<?> signal) {
        this.externalMessage = externalMessage;
        this.topicPath = topicPath;
        this.signal = signal;
    }

    public static MappedInboundExternalMessage of(final ExternalMessage externalMessage, final TopicPath topicPath,
            final Signal<?> signal) {
        return new MappedInboundExternalMessage(externalMessage, topicPath, signal);
    }

    @Override
    public ExternalMessage getSource() {
        return externalMessage;
    }

    @Override
    public TopicPath getTopicPath() {
        return topicPath;
    }

    @Override
    public Signal<?> getSignal() {
        return signal;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final MappedInboundExternalMessage that = (MappedInboundExternalMessage) o;
        return Objects.equals(externalMessage, that.externalMessage) &&
                Objects.equals(topicPath, that.topicPath) &&
                Objects.equals(signal, that.signal);
    }

    @Override
    public int hashCode() {
        return Objects.hash(externalMessage, topicPath, signal);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "externalMessage=" + externalMessage +
                ", topicPath=" + topicPath +
                ", signal=" + signal +
                "]";
    }
}
