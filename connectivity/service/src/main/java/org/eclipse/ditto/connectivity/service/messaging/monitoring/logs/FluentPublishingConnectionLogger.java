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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.LogCategory;
import org.eclipse.ditto.connectivity.model.LogEntry;
import org.eclipse.ditto.connectivity.model.LogLevel;
import org.eclipse.ditto.connectivity.model.LogType;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.komamitsu.fluency.BufferFullException;
import org.komamitsu.fluency.EventTime;
import org.komamitsu.fluency.Fluency;

/**
 * Implementation of {@link ConnectionLogger} that publishes logs via {@link Fluency} library to a Fluentd/Fluentbit
 * endpoint.
 */
final class FluentPublishingConnectionLogger
        extends AbstractConnectionLogger<FluentPublishingConnectionLogger.Builder, FluentPublishingConnectionLogger> {

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(FluentPublishingConnectionLogger.class);

    private static final String TAG_CONNECTION_ID = "connectionId";

    private static final String TAG_LEVEL = toTag(LogEntry.JsonFields.LEVEL);
    private static final String TAG_CATEGORY = toTag(LogEntry.JsonFields.CATEGORY);
    private static final String TAG_TYPE = toTag(LogEntry.JsonFields.TYPE);
    private static final String TAG_CORRELATION_ID = toTag(LogEntry.JsonFields.CORRELATION_ID);
    private static final String TAG_ADDRESS = toTag(LogEntry.JsonFields.ADDRESS);
    private static final String TAG_ENTITY_TYPE = toTag(LogEntry.JsonFields.ENTITY_TYPE);
    private static final String TAG_ENTITY_ID = toTag(LogEntry.JsonFields.ENTITY_ID);
    private static final String TAG_MESSAGE = toTag(LogEntry.JsonFields.MESSAGE);
    private static final String TAG_INSTANCE_ID = "instanceId";

    private static String toTag(final JsonFieldDefinition<String> level) {
        return level.getPointer().get(0).orElseThrow().toString();
    }

    private final ConnectionId connectionId;
    private final String fluentTag;
    private final Set<LogLevel> logLevels;
    private final Fluency fluencyForwarder;
    private final Duration waitUntilAllBufferFlushedDurationOnClose;
    private final Map<String, Object> additionalLogContext;
    @Nullable private final String instanceIdentifier;

    FluentPublishingConnectionLogger(final Builder builder) {
        super(builder);
        connectionId = builder.connectionId;
        fluentTag = builder.fluentTag;
        logLevels = builder.logLevels;
        fluencyForwarder = builder.fluencyForwarder;
        waitUntilAllBufferFlushedDurationOnClose = builder.waitUntilAllBufferFlushedDurationOnClose;
        additionalLogContext = Map.copyOf(builder.additionalLogContext);
        instanceIdentifier = builder.instanceIdentifier;
    }

    /**
     * Create a new builder.
     *
     * @param connectionId the {@code ConnectionId} of the logged connection.
     * @param category category of logs stored by the logger.
     * @param type type of logs stored by the logger.
     * @param fluencyForwarder the {@code Fluency} forwarder used to forward logs to fluentd/fluentbit.
     * @param waitUntilAllBufferFlushedDurationOnClose the duration of how long to wait after closing the Fluency buffer.
     * @return a new Builder for {@code FluentPublishingConnectionLogger}.
     * @throws java.lang.NullPointerException if any non-nullable argument is {@code null}.
     */
    static Builder newBuilder(final ConnectionId connectionId, final LogCategory category,
            final LogType type,
            final Fluency fluencyForwarder,
            final Duration waitUntilAllBufferFlushedDurationOnClose) {

        return new Builder(connectionId, category, type, fluencyForwarder, waitUntilAllBufferFlushedDurationOnClose);
    }

    @Override
    public Collection<LogEntry> getLogs() {
        return Collections.emptyList();
    }

    @Override
    public void clear() {
        // no-op
    }

    @Override
    public void close() throws IOException {
        LOGGER.info("Flushing and closing Fluency forwarder, waiting <{}> for buffers being flushed...",
                waitUntilAllBufferFlushedDurationOnClose);

        // fluencyForwarder.close also flushes:
        fluencyForwarder.close();

        if (!waitUntilAllBufferFlushedDurationOnClose.isZero() &&
                !waitUntilAllBufferFlushedDurationOnClose.isNegative()) {
            try {
                fluencyForwarder.waitUntilAllBufferFlushed((int) waitUntilAllBufferFlushedDurationOnClose.getSeconds());
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void success(final ConnectionMonitor.InfoProvider infoProvider, final String message,
            final Object... messageArguments) {
        emitLogEntry(infoProvider, message, messageArguments, LogLevel.SUCCESS);
    }


    @Override
    public void failure(final ConnectionMonitor.InfoProvider infoProvider, final String message,
            final Object... messageArguments) {
        emitLogEntry(infoProvider, message, messageArguments, LogLevel.FAILURE);
    }

    @Override
    public void exception(final ConnectionMonitor.InfoProvider infoProvider, final String message,
            final Object... messageArguments) {
        emitLogEntry(infoProvider, message, messageArguments, LogLevel.FAILURE);
    }

    @Override
    public void logEntry(final LogEntry logEntry) {
        emitLogEntry(logEntry);
    }

    private void emitLogEntry(final ConnectionMonitor.InfoProvider infoProvider, final String message,
            final Object[] messageArguments, final LogLevel logLevel) {
        final String formattedMessage = formatMessage(infoProvider, message, messageArguments);
        emitLogEntry(getLogEntry(infoProvider, formattedMessage, logLevel));
    }

    private void emitLogEntry(final LogEntry logEntry) {
        final String correlationId = InfoProviderFactory.FALLBACK_CORRELATION_ID
                .equals(logEntry.getCorrelationId()) ? null : logEntry.getCorrelationId();
        if (logLevels.contains(logEntry.getLogLevel())) {
            try {
                final Instant timestamp = logEntry.getTimestamp();
                final EventTime eventTime = EventTime.fromEpoch(timestamp.getEpochSecond(), timestamp.getNano());

                final Map<String, Object> logMap = new LinkedHashMap<>();
                logMap.put(TAG_CONNECTION_ID, connectionId.toString());
                logMap.put(TAG_LEVEL, logEntry.getLogLevel().toString());
                logMap.put(TAG_CATEGORY, logEntry.getLogCategory().toString());
                logMap.put(TAG_TYPE, logEntry.getLogType().toString());
                if (null != correlationId) {
                    logMap.put(TAG_CORRELATION_ID, correlationId);
                }
                logEntry.getAddress().ifPresent(address -> logMap.put(TAG_ADDRESS, address));
                logEntry.getEntityId().ifPresent(entityId -> {
                    logMap.put(TAG_ENTITY_TYPE, entityId.getEntityType().toString());
                    logMap.put(TAG_ENTITY_ID, entityId.toString());
                });
                logMap.put(TAG_MESSAGE, logEntry.getMessage());
                if (null != instanceIdentifier) {
                    logMap.put(TAG_INSTANCE_ID, instanceIdentifier);
                }
                logMap.putAll(additionalLogContext);

                fluencyForwarder.emit(fluentTag, eventTime, logMap);
            } catch (final BufferFullException e) {
                LOGGER.withCorrelationId(correlationId)
                        .error("Got BufferFullException when trying to emit further connection log entries to fluentd: {}",
                                e.getMessage());
            } catch (final IOException e) {
                LOGGER.withCorrelationId(correlationId)
                        .error("Got IOException when trying to emit further connection log entries to fluentd: <{}>: {}",
                                e.getClass().getSimpleName(), e.getMessage());
            }
        } else {
            LOGGER.withCorrelationId(correlationId)
                    .debug("Not emitting log entry with logLevel <{}> as the configured logLevels contained: <{}>",
                            logEntry.getLogLevel(), logLevels);
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) return false;
        final FluentPublishingConnectionLogger that = (FluentPublishingConnectionLogger) o;
        return Objects.equals(connectionId, that.connectionId) &&
                Objects.equals(fluentTag, that.fluentTag) &&
                Objects.equals(logLevels, that.logLevels) &&
                Objects.equals(fluencyForwarder, that.fluencyForwarder) &&
                Objects.equals(additionalLogContext, that.additionalLogContext) &&
                Objects.equals(instanceIdentifier, that.instanceIdentifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), connectionId, fluentTag, logLevels, fluencyForwarder,
                additionalLogContext, instanceIdentifier);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", connectionId=" + connectionId +
                ", fluentTag=" + fluentTag +
                ", logLevels=" + logLevels +
                ", fluencyForwarder=" + fluencyForwarder +
                ", additionalLogContext=" + additionalLogContext +
                ", instanceIdentifier=" + instanceIdentifier +
                "]";
    }

    /**
     * Builder for {@code FluentPublishingConnectionLogger}.
     */
    static final class Builder extends AbstractConnectionLoggerBuilder
            <FluentPublishingConnectionLogger.Builder, FluentPublishingConnectionLogger> {

        private static final String CONNECTION_TAG_PREFIX = "connection:";

        final ConnectionId connectionId;
        String fluentTag;
        Set<LogLevel> logLevels;
        final Fluency fluencyForwarder;
        final Duration waitUntilAllBufferFlushedDurationOnClose;
        Map<String, Object> additionalLogContext;
        @Nullable String instanceIdentifier;

        private Builder(final ConnectionId connectionId,
                final LogCategory category,
                final LogType type,
                final Fluency fluencyForwarder,
                final Duration waitUntilAllBufferFlushedDurationOnClose) {
            super(category, type);
            this.connectionId = connectionId;
            this.fluentTag = CONNECTION_TAG_PREFIX + connectionId; // default to the connectionId as tag
            this.fluencyForwarder = fluencyForwarder;
            this.waitUntilAllBufferFlushedDurationOnClose = waitUntilAllBufferFlushedDurationOnClose;
            this.additionalLogContext = Collections.emptyMap();
        }

        /**
         * Use the provided {@code fluentTag} for the built {@code FluentPublishingConnectionLogger}.
         *
         * @param fluentTag the tag to use when forwarding logs to fluentd/fluentbit.
         * @return the builder for method chaining.
         * @throws NullPointerException if the passed {@code fluentTag} was {@code null}.
         */
        Builder withFluentTag(final CharSequence fluentTag) {
            this.fluentTag = checkNotNull(fluentTag, "fluentTag").toString();
            return this;
        }

        /**
         * Use the provided {@code logLevels}, only log entries in those levels will be published.
         *
         * @param logLevels the log levels to include for the log publishing.
         * @return the builder for method chaining.
         * @throws NullPointerException if the passed {@code logLevel} was {@code null}.
         */
        Builder withLogLevels(final Collection<LogLevel> logLevels) {
            this.logLevels = Set.copyOf(checkNotNull(logLevels, "logLevels"));
            return this;
        }

        /**
         * Use the provided {@code additionalLogContext} for the built {@code FluentPublishingConnectionLogger}.
         *
         * @param additionalLogContext the map of additional entries to emit for each forwarded log entry.
         * @return the builder for method chaining.
         * @throws NullPointerException if the passed {@code additionalLogContext} was {@code null}.
         */
        Builder withAdditionalLogContext(final Map<String, Object> additionalLogContext) {
            this.additionalLogContext = checkNotNull(additionalLogContext, "additionalLogContext");
            return this;
        }

        /**
         * Use the provided {@code instanceIdentifier} for the built {@code FluentPublishingConnectionLogger}.
         *
         * @param instanceIdentifier the identifier of the connectivity instance.
         * @return the builder for method chaining.
         * @throws NullPointerException if the passed {@code instanceIdentifier} was {@code null}.
         */
        Builder withInstanceIdentifier(final CharSequence instanceIdentifier) {
            this.instanceIdentifier = checkNotNull(instanceIdentifier, "instanceIdentifier").toString();
            return this;
        }

        @Override
        public FluentPublishingConnectionLogger build() {
            return new FluentPublishingConnectionLogger(this);
        }

    }
}
