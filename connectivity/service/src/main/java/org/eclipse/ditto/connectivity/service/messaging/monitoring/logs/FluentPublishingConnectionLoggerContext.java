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
package org.eclipse.ditto.connectivity.service.messaging.monitoring.logs;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.connectivity.model.LogLevel;
import org.komamitsu.fluency.Fluency;

/**
 * Holds connection scoped static context information required for building a {@link FluentPublishingConnectionLogger}.
 */
@Immutable
final class FluentPublishingConnectionLoggerContext {

    private final Fluency fluencyForwarder;
    private final Duration waitUntilAllBufferFlushedDurationOnClose;
    private final Set<LogLevel> logLevels;
    private final boolean logHeadersAndPayload;
    @Nullable private final String logTag;
    private final Map<String, Object> additionalLogContext;

    FluentPublishingConnectionLoggerContext(final Fluency fluencyForwarder,
            final Duration waitUntilAllBufferFlushedDurationOnClose,
            final Collection<LogLevel> logLevels,
            final boolean logHeadersAndPayload,
            @Nullable final CharSequence logTag,
            final Map<String, Object> additionalLogContext) {
        this.fluencyForwarder = fluencyForwarder;
        this.waitUntilAllBufferFlushedDurationOnClose = waitUntilAllBufferFlushedDurationOnClose;
        this.logLevels = Set.copyOf(logLevels);
        this.logHeadersAndPayload = logHeadersAndPayload;
        this.logTag = null != logTag ? logTag.toString() : null;
        this.additionalLogContext = Map.copyOf(additionalLogContext);
    }

    Fluency getFluencyForwarder() {
        return fluencyForwarder;
    }

    Set<LogLevel> getLogLevels() {
        return logLevels;
    }

    boolean isLogHeadersAndPayload() {
        return logHeadersAndPayload;
    }

    Optional<String> getLogTag() {
        return Optional.ofNullable(logTag);
    }

    Map<String, Object> getAdditionalLogContext() {
        return additionalLogContext;
    }

    Duration getWaitUntilAllBufferFlushedDurationOnClose() {
        return waitUntilAllBufferFlushedDurationOnClose;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FluentPublishingConnectionLoggerContext that = (FluentPublishingConnectionLoggerContext) o;
        return logHeadersAndPayload == that.logHeadersAndPayload &&
                Objects.equals(fluencyForwarder, that.fluencyForwarder) &&
                Objects.equals(waitUntilAllBufferFlushedDurationOnClose,
                        that.waitUntilAllBufferFlushedDurationOnClose) &&
                Objects.equals(logLevels, that.logLevels) && Objects.equals(logTag, that.logTag) &&
                Objects.equals(additionalLogContext, that.additionalLogContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fluencyForwarder, waitUntilAllBufferFlushedDurationOnClose, logLevels, logHeadersAndPayload,
                logTag, additionalLogContext);
    }


    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "fluencyForwarder=" + fluencyForwarder +
                ", waitUntilAllBufferFlushedDurationOnClose=" + waitUntilAllBufferFlushedDurationOnClose +
                ", logLevels=" + logLevels +
                ", logHeadersAndPayload=" + logHeadersAndPayload +
                ", logTag=" + logTag +
                ", additionalLogContext=" + additionalLogContext +
                "]";
    }
}
