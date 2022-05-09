/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.LogCategory;
import org.eclipse.ditto.connectivity.model.LogEntry;
import org.eclipse.ditto.connectivity.model.LogType;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.service.config.FluencyLoggerPublisherConfig;
import org.eclipse.ditto.connectivity.service.config.LoggerPublisherConfig;
import org.eclipse.ditto.connectivity.service.config.MonitoringLoggerConfig;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitorRegistry;
import org.eclipse.ditto.connectivity.service.util.ConnectivityMdcEntryKey;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.komamitsu.fluency.Fluency;
import org.slf4j.Logger;

/**
 * This registry holds loggers for the connectivity service. The loggers are identified by the connection ID,
 * a {@link org.eclipse.ditto.connectivity.model.LogType}, a {@link org.eclipse.ditto.connectivity.model.LogCategory}
 * and an address. The public methods of this class should not throw exceptions since this can lead to crashing connections.
 */
public final class ConnectionLoggerRegistry implements ConnectionMonitorRegistry<ConnectionLogger> {

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(ConnectionLoggerRegistry.class);

    private static final String MDC_CONNECTION_ID = ConnectivityMdcEntryKey.CONNECTION_ID.toString();

    private static final ConcurrentMap<MapKey, ConnectionLogger> LOGGERS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<EntityId, LogMetadata> METADATA = new ConcurrentHashMap<>();

    // artificial internal address for responses
    private static final String RESPONSES_ADDRESS = "_responses";

    private final int successCapacity;
    private final int failureCapacity;
    private final TemporalAmount loggingDuration;
    private final long maximumLogSizeInByte;
    @Nullable private final FluentPublishingConnectionLoggerContext fluentPublishingConnectionLoggerContext;

    private ConnectionLoggerRegistry(final int successCapacity,
            final int failureCapacity,
            final long maximumLogSizeInByte,
            final Duration loggingDuration,
            final LoggerPublisherConfig loggerPublisherConfig) {

        this.successCapacity = successCapacity;
        this.failureCapacity = failureCapacity;
        this.maximumLogSizeInByte = maximumLogSizeInByte;
        this.loggingDuration = checkNotNull(loggingDuration);

        if (loggerPublisherConfig.isEnabled()) {
            final FluencyLoggerPublisherConfig fluencyConfig = loggerPublisherConfig.getFluencyLoggerPublisherConfig();
            final Fluency fluency = fluencyConfig.buildFluencyLoggerPublisher();
            fluentPublishingConnectionLoggerContext = ConnectionLoggerFactory.newPublishingLoggerContext(fluency,
                    fluencyConfig.getWaitUntilAllBufferFlushedDurationOnClose(),
                    loggerPublisherConfig.getLogLevels(),
                    loggerPublisherConfig.isLogHeadersAndPayload(),
                    loggerPublisherConfig.getLogTag().orElse(null),
                    loggerPublisherConfig.getAdditionalLogContext()
            );
        } else {
            fluentPublishingConnectionLoggerContext = null;
        }
    }

    /**
     * Build a new {@code ConnectionLoggerRegistry} from configuration.
     *
     * @param config the configuration to use.
     * @return a new instance of {@code ConnectionLoggerRegistry}.
     */
    public static ConnectionLoggerRegistry fromConfig(final MonitoringLoggerConfig config) {
        checkNotNull(config);
        return new ConnectionLoggerRegistry(config.successCapacity(), config.failureCapacity(),
                config.maxLogSizeInBytes(), config.logDuration(), config.getLoggerPublisherConfig());
    }

    /**
     * Aggregate the {@link org.eclipse.ditto.connectivity.model.LogEntry}s for the given connection from the loggers in
     * this registry. If an exception occurs, empty {@link ConnectionLogs} will be returned in effort to prevent
     * crashing connections when log aggregation fails.
     *
     * @param connectionId connection id
     * @return the {@link org.eclipse.ditto.connectivity.model.LogEntry}s.
     */
    public ConnectionLogs aggregateLogs(final ConnectionId connectionId) {
        final ThreadSafeDittoLogger logger = LOGGER.withMdcEntry(MDC_CONNECTION_ID, connectionId);
        logger.info("Aggregating logs for connection <{}>.", connectionId);
        ConnectionLogs result;

        try {
            final LogMetadata timing;
            final List<LogEntry> logs;
            if (isActiveForConnection(connectionId)) {
                timing = refreshMetadata(connectionId);
                logs = aggregateLogsForActiveConnection(logger, connectionId);
            } else {
                logger.debug("Logging is disabled, will return empty logs for connection <{}>", connectionId);
                timing = getMetadata(connectionId);
                logs = Collections.emptyList();
            }
            logger.debug("Aggregated logs for connection <{}>: {}", connectionId, logs);
            result = new ConnectionLogs(timing.getEnabledSince(), timing.getEnabledUntil(), logs);
        } catch (final Exception e) {
            logger.error("Encountered exception: <{}> while trying to aggregate logs for connection: <{}>. " +
                    "Returning empty logs instead.", e, connectionId);
            result = ConnectionLogs.empty();
        }
        return result;
    }

    private List<LogEntry> aggregateLogsForActiveConnection(final Logger logger,
            final ConnectionId connectionId) {

        logger.trace("Logging is enabled, will aggregate logs for connection <{}>", connectionId);
        final List<LogEntry> allLogs = loggersForConnectionId(connectionId)
                .map(ConnectionLogger::getLogs)
                .flatMap(Collection::stream)
                .sorted(Comparator.comparing(LogEntry::getTimestamp).reversed())
                .toList();

        return restrictMaxLogEntriesLength(allLogs, connectionId);
    }

    // needed so that the logs fit into the max cluster message size
    private List<LogEntry> restrictMaxLogEntriesLength(final List<LogEntry> originalLogEntries,
            final ConnectionId connectionId) {

        final List<LogEntry> restrictedLogs = new ArrayList<>();
        long currentSize = 0;
        for (final LogEntry logEntry : originalLogEntries) {
            final long sizeOfLogEntry = logEntry.toJsonString().length();
            final long sizeWithNextEntry = currentSize + sizeOfLogEntry;
            if (sizeWithNextEntry > maximumLogSizeInByte) {
                LOGGER.withMdcEntry(MDC_CONNECTION_ID, connectionId)
                        .info("Dropping <{}> of <{}> log entries for connection with ID <{}>, because of size limit.",
                                originalLogEntries.size() - restrictedLogs.size(), originalLogEntries.size(),
                                connectionId);
                break;
            }
            restrictedLogs.add(logEntry);
            currentSize = sizeWithNextEntry;
        }
        Collections.reverse(restrictedLogs);

        return restrictedLogs;
    }

    /**
     * Checks if logging is enabled for the given connection.
     *
     * @param connectionId the connection to check.
     * @return true if logging is currently enabled for the connection.
     */
    static boolean isActiveForConnection(final ConnectionId connectionId) {
        return muteableLoggersForConnectionId(connectionId).anyMatch(Predicate.not(MuteableConnectionLogger::isMuted));
    }

    /**
     * Checks if logging is expired for the given connection.
     *
     * @param connectionId the connection to check.
     * @param timestamp the actual time. If timestamp is after enabledUntil then deactivate logging.
     * @return true if either the logging is not active anyway or the logging is expired.
     */
    public static boolean isLoggingExpired(final ConnectionId connectionId, final Instant timestamp) {
        final Instant enabledUntil = getMetadata(connectionId).getEnabledUntil();
        if (enabledUntil == null || timestamp.isAfter(enabledUntil)) {
            LOGGER.withMdcEntry(MDC_CONNECTION_ID, connectionId)
                    .debug("Logging for connection <{}> expired.", connectionId);

            return true;
        }

        return false;
    }

    /**
     * Mute / deactivate all loggers for the connection {@code connectionId}.
     * If an exception occurs the loggers don't get deactivated in effort to keep the connection alive.
     *
     * @param connectionId the connection for which the loggers should be enabled.
     */
    public static void muteForConnection(final ConnectionId connectionId) {
        LOGGER.withMdcEntry(MDC_CONNECTION_ID, connectionId)
                .info("Muting loggers for connection <{}>.", connectionId);

        try {
            muteableLoggersForConnectionId(connectionId).forEach(MuteableConnectionLogger::mute);
            stopMetadata(connectionId);
            resetForConnectionId(connectionId);
        } catch (final Exception e) {
            LOGGER.withMdcEntry(MDC_CONNECTION_ID, connectionId)
                    .error("Failed muting loggers for connection <{}>. Reason: <{}>.", connectionId, e);
        }
    }

    /**
     * Unmute / activate all loggers for the connection {@code connectionId}.
     * If an exception occurs the loggers don't get activated in effort to keep the connection alive.
     *
     * @param connectionId the connection for which the loggers should be enabled.
     */
    public void unmuteForConnection(final ConnectionId connectionId) {
        LOGGER.withMdcEntry(MDC_CONNECTION_ID, connectionId)
                .info("Unmuting loggers for connection <{}>.", connectionId);
        tryToUnmuteLoggersForConnection(connectionId);
        startMetadata(connectionId);
    }

    private static void tryToUnmuteLoggersForConnection(final ConnectionId connectionId) {
        try {
            unmuteLoggersForConnection(connectionId);
        } catch (final Exception e) {
            LOGGER.withMdcEntry(MDC_CONNECTION_ID, connectionId)
                    .error("Failed unmuting loggers for connection <{}>. Reason: <{}>.", connectionId, e);
        }
    }

    private static void unmuteLoggersForConnection(final ConnectionId connectionId) {
        final var amountUnmutedLoggers = muteableLoggersForConnectionId(connectionId)
                .mapToInt(logger -> {
                    logger.unmute();
                    return 1;
                })
                .sum();
        LOGGER.withMdcEntry(MDC_CONNECTION_ID, connectionId)
                .info("Unmuted <{}> loggers for connection <{}>.", amountUnmutedLoggers, connectionId);
    }

    private static Stream<MuteableConnectionLogger> muteableLoggersForConnectionId(final ConnectionId connectionId) {
        return loggersForConnectionId(connectionId)
                .filter(MuteableConnectionLogger.class::isInstance)
                .map(MuteableConnectionLogger.class::cast);
    }

    private static Stream<ConnectionLogger> loggersForConnectionId(final ConnectionId connectionId) {
        final var connectionLoggerEntries = LOGGERS.entrySet();
        return connectionLoggerEntries.stream()
                .filter(e -> {
                    final var connectionLoggerMapKey = e.getKey();
                    return connectionId.equals(connectionLoggerMapKey.connectionId);
                })
                .map(Map.Entry::getValue);
    }

    /**
     * Initializes the global {@code loggers} Map with the address information of the passed {@code connection}.
     *
     * @param connection the connection to initialize the global loggers map with.
     */
    @Override
    public void initForConnection(final Connection connection) {
        final ConnectionId connectionId = connection.getId();
        invalidateLoggers(connectionId);
        LOGGER.withMdcEntry(MDC_CONNECTION_ID, connectionId)
                .info("Initializing loggers for connection <{}>.", connectionId);

        try {
            connection.getSources().stream()
                    .map(Source::getAddresses)
                    .flatMap(Collection::stream)
                    .forEach(address ->
                            initLogger(connectionId, LogCategory.SOURCE, address));
            connection.getTargets().stream()
                    .map(Target::getAddress)
                    .forEach(address ->
                            initLogger(connectionId, LogCategory.TARGET, address));
            initLogger(connectionId, LogCategory.RESPONSE, RESPONSES_ADDRESS);
            initLogger(connectionId);
        } catch (final Exception e) {
            LOGGER.withMdcEntry(MDC_CONNECTION_ID, connectionId)
                    .error("Failed initializing loggers for connection <{}>. Reason: <{}>.", connectionId, e);
        }
    }

    private void invalidateLoggers(final ConnectionId connectionId) {
        LOGGER.withMdcEntry(MDC_CONNECTION_ID, connectionId)
                .info("Invalidating loggers for connection <{}>.", connectionId);
        final Set<MapKey> mapsKeysToDelete = LOGGERS.keySet().stream()
                .filter(mapKey -> mapKey.connectionId.equals(connectionId))
                .collect(Collectors.toSet());

        mapsKeysToDelete.forEach(loggerKey -> {
            // flush logs before removing from loggers:
            try {
                final ConnectionLogger connectionLogger = LOGGERS.get(loggerKey);
                if (connectionLogger != null) {
                    connectionLogger.close();
                }
            } catch (final IOException e) {
                LOGGER.warn("Exception during closing logger <{}>: <{}>: {}", loggerKey, e.getClass().getSimpleName(),
                        e.getMessage());
            }
            LOGGERS.remove(loggerKey);
        });
    }

    private void initLogger(final ConnectionId connectionId) {
        initLogger(connectionId, LogCategory.CONNECTION, null);
    }

    private void initLogger(final ConnectionId connectionId, final LogCategory logCategory,
            @Nullable final String address) {
        Arrays.stream(LogType.values())
                .filter(logType -> logType.supportsCategory(logCategory))
                .forEach(logType -> {
                    final MapKey key = new MapKey(connectionId, logCategory, logType, address);
                    LOGGERS.computeIfAbsent(key, m -> newLogger(connectionId, logCategory, logType, address));
                });
    }

    @Override
    public void resetForConnection(final Connection connection) {
        final ConnectionId connectionId = connection.getId();
        LOGGER.withMdcEntry(MDC_CONNECTION_ID, connectionId)
                .info("Resetting loggers for connection <{}>.", connectionId);

        try {
            resetForConnectionId(connectionId);
        } catch (final Exception e) {
            LOGGER.withMdcEntry(MDC_CONNECTION_ID, connectionId)
                    .error("Failed resetting loggers for connection <{}>. Reason: <{}>.", connectionId, e);
        }
    }

    private static void resetForConnectionId(final ConnectionId connectionId) {
        loggersForConnectionId(connectionId)
                .forEach(ConnectionLogger::clear);
    }

    private LogMetadata refreshMetadata(final EntityId connectionId) {
        return METADATA.compute(connectionId, (c, oldTimings) -> {
            final Instant now = Instant.now();
            if (null != oldTimings) {
                return oldTimings.withEnabledUntil(now.plus(loggingDuration));
            }
            return new LogMetadata(now, now.plus(loggingDuration));
        });
    }

    private void startMetadata(final EntityId connectionId) {
        final Instant now = Instant.now();
        final LogMetadata timing = new LogMetadata(now, now.plus(loggingDuration));
        METADATA.put(connectionId, timing);
    }

    private static void stopMetadata(final EntityId connectionId) {
        METADATA.remove(connectionId);
    }

    private static LogMetadata getMetadata(final ConnectionId connectionId) {
        return METADATA.getOrDefault(connectionId, LogMetadata.empty());
    }

    @Override
    public ConnectionLogger forOutboundDispatched(final Connection connection, final String target) {
        return getLogger(connection.getId(), LogCategory.TARGET, LogType.DISPATCHED, target);
    }

    @Override
    public ConnectionLogger forOutboundFiltered(final Connection connection, final String target) {
        return getLogger(connection.getId(), LogCategory.TARGET, LogType.FILTERED, target);
    }

    @Override
    public ConnectionLogger forOutboundPublished(final Connection connection, final String target) {
        return getLogger(connection.getId(), LogCategory.TARGET, LogType.PUBLISHED, target);
    }

    @Override
    public ConnectionLogger forOutboundDropped(final Connection connection, final String target) {
        return getLogger(connection.getId(), LogCategory.TARGET, LogType.DROPPED, target);
    }

    @Override
    public ConnectionLogger forOutboundAcknowledged(final Connection connection, final String target) {
        return getLogger(connection.getId(), LogCategory.TARGET, LogType.ACKNOWLEDGED, target);
    }

    @Override
    public ConnectionLogger forInboundConsumed(final Connection connection, final String source) {
        return getLogger(connection.getId(), LogCategory.SOURCE, LogType.CONSUMED, source);
    }

    @Override
    public ConnectionLogger forInboundMapped(final Connection connection, final String source) {
        return getLogger(connection.getId(), LogCategory.SOURCE, LogType.MAPPED, source);
    }

    @Override
    public ConnectionLogger forInboundEnforced(final Connection connection, final String source) {
        return getLogger(connection.getId(), LogCategory.SOURCE, LogType.ENFORCED, source);
    }

    @Override
    public ConnectionLogger forInboundDropped(final Connection connection, final String source) {
        return getLogger(connection.getId(), LogCategory.SOURCE, LogType.DROPPED, source);
    }

    @Override
    public ConnectionLogger forInboundAcknowledged(final Connection connection, final String source) {
        return getLogger(connection.getId(), LogCategory.SOURCE, LogType.ACKNOWLEDGED, source);
    }

    @Override
    public ConnectionLogger forInboundThrottled(final Connection connection, final String source) {
        return getLogger(connection.getId(), LogCategory.SOURCE, LogType.THROTTLED, source);
    }

    @Override
    public ConnectionLogger forResponseDispatched(final Connection connection) {
        return getLogger(connection.getId(), LogCategory.RESPONSE, LogType.DISPATCHED, RESPONSES_ADDRESS);
    }

    @Override
    public ConnectionLogger forResponseDropped(final Connection connection) {
        return getLogger(connection.getId(), LogCategory.RESPONSE, LogType.DROPPED, RESPONSES_ADDRESS);
    }

    @Override
    public ConnectionLogger forResponseMapped(final Connection connection) {
        return getLogger(connection.getId(), LogCategory.RESPONSE, LogType.MAPPED, RESPONSES_ADDRESS);
    }

    @Override
    public ConnectionLogger forResponsePublished(final Connection connection) {
        return getLogger(connection.getId(), LogCategory.RESPONSE, LogType.PUBLISHED, RESPONSES_ADDRESS);
    }

    @Override
    public ConnectionLogger forResponseAcknowledged(final Connection connection) {
        return getLogger(connection.getId(), LogCategory.RESPONSE, LogType.ACKNOWLEDGED, RESPONSES_ADDRESS);
    }

    /**
     * Get the logger for connection specific logs that can't be associated to a specific category/type.
     *
     * @param connectionId the connection.
     * @return a new logger instance.
     */
    public ConnectionLogger forConnection(final ConnectionId connectionId) {
        return getLogger(connectionId, LogCategory.CONNECTION, LogType.OTHER, null);
    }

    /**
     * Gets the logger for the given parameter from the registry or creates it if it does not yet exist.
     *
     * @param connectionId connection id
     * @param logCategory logCategory
     * @param logType the logType
     * @param address the address
     * @return the logger.
     */
    public ConnectionLogger getLogger(
            final ConnectionId connectionId,
            final LogCategory logCategory,
            final LogType logType,
            @Nullable final String address) {

        try {
            final MapKey key = new MapKey(connectionId, logCategory, logType, address);
            return LOGGERS.computeIfAbsent(key, m -> newLogger(connectionId, logCategory, logType, address));
        } catch (final Exception e) {
            LOGGER.error("Encountered exception: <{}> getting connection logger <{}:{}:{}:{}>", e, connectionId,
                    logCategory, logType, address);
            return ConnectionLoggerFactory.newExceptionalLogger(connectionId, e);
        }
    }

    private ConnectionLogger newLogger(final ConnectionId connectionId,
            final LogCategory logCategory,
            final LogType logType,
            @Nullable final String address) {

        final ConnectionLogger result;
        final var evictingLogger = ConnectionLoggerFactory.newEvictingLogger(successCapacity,
                failureCapacity,
                logCategory,
                logType,
                address);
        final var muteableLogger = ConnectionLoggerFactory.newMuteableLogger(connectionId, evictingLogger);
        if (isActiveForConnection(connectionId)) {
            muteableLogger.unmute();
        } else {
            muteableLogger.mute();
        }

        if (null != fluentPublishingConnectionLoggerContext) {
            final var publishingLogger = ConnectionLoggerFactory.newPublishingLogger(connectionId,
                    logCategory,
                    logType,
                    address,
                    fluentPublishingConnectionLoggerContext);
            result = new CompoundConnectionLogger(List.of(muteableLogger, publishingLogger));
        } else {
            result = muteableLogger;
        }
        return result;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ConnectionLoggerRegistry that = (ConnectionLoggerRegistry) o;
        return successCapacity == that.successCapacity &&
                failureCapacity == that.failureCapacity &&
                maximumLogSizeInByte == that.maximumLogSizeInByte &&
                Objects.equals(loggingDuration, that.loggingDuration) &&
                Objects.equals(fluentPublishingConnectionLoggerContext, that.fluentPublishingConnectionLoggerContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(successCapacity, failureCapacity, loggingDuration, maximumLogSizeInByte,
                fluentPublishingConnectionLoggerContext);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "successCapacity=" + successCapacity +
                ", failureCapacity=" + failureCapacity +
                ", loggingDuration=" + loggingDuration +
                ", maximumLogSizeInByte=" + maximumLogSizeInByte +
                ", fluentPublishingConnectionLoggerContext=" + fluentPublishingConnectionLoggerContext +
                "]";
    }

    /**
     * Helper class that stores logs together with information on when they were enabled and how long they will stay
     * enabled.
     */
    @Immutable
    public static final class ConnectionLogs {

        @Nullable private final Instant enabledSince;
        @Nullable private final Instant enabledUntil;
        private final Collection<LogEntry> logs;

        private ConnectionLogs(@Nullable final Instant enabledSince, @Nullable final Instant enabledUntil,
                final Collection<LogEntry> logs) {
            this.enabledSince = enabledSince;
            this.enabledUntil = enabledUntil;
            this.logs = List.copyOf(logs);
        }

        /**
         * Returns an empty instance indicating inactive logging.
         *
         * @return an empty instance.
         */
        public static ConnectionLogs empty() {
            return new ConnectionLogs(null, null, Collections.emptyList());
        }

        /**
         * @return since when the logs are enabled.
         */
        @Nullable
        public Instant getEnabledSince() {
            return enabledSince;
        }

        /**
         * @return until when the logs will stay enabled.
         */
        @Nullable
        public Instant getEnabledUntil() {
            return enabledUntil;
        }

        /**
         * @return the log entries.
         */
        public Collection<LogEntry> getLogs() {
            return logs;
        }

        @Override
        public boolean equals(@Nullable final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final ConnectionLogs that = (ConnectionLogs) o;
            return Objects.equals(enabledSince, that.enabledSince) &&
                    Objects.equals(enabledUntil, that.enabledUntil) &&
                    Objects.equals(logs, that.logs);
        }

        @Override
        public int hashCode() {
            return Objects.hash(enabledSince, enabledUntil, logs);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    "enabledSince=" + enabledSince +
                    ", enabledUntil=" + enabledUntil +
                    ", logs=" + logs +
                    "]";
        }

    }

    /**
     * Helper class to build the map key of the registry.
     */
    @Immutable
    private static final class MapKey {

        private final ConnectionId connectionId;
        private final String category;
        private final String type;
        @Nullable private final String address;

        /**
         * New map key.
         *
         * @param connectionId connection id
         * @param logCategory the logCategory
         * @param logType the logType
         * @param address the address
         */
        MapKey(final ConnectionId connectionId,
                final LogCategory logCategory,
                final LogType logType,
                @Nullable final String address) {
            this.connectionId = checkNotNull(connectionId, "connectionId");
            category = checkNotNull(logCategory, "log category").getName();
            type = checkNotNull(logType, "log type").getType();
            this.address = address;
        }

        @Override
        public boolean equals(@Nullable final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final MapKey mapKey = (MapKey) o;
            return Objects.equals(connectionId, mapKey.connectionId) &&
                    Objects.equals(category, mapKey.category) &&
                    Objects.equals(type, mapKey.type) &&
                    Objects.equals(address, mapKey.address);
        }

        @Override
        public int hashCode() {
            return Objects.hash(connectionId, category, type, address);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    "connectionId=" + connectionId +
                    ", category=" + category +
                    ", type=" + type +
                    ", address=" + address +
                    "]";
        }

    }

    /**
     * Helper class to store meta-information for logging.
     */
    @Immutable
    private static final class LogMetadata {

        @Nullable
        private final Instant enabledSince;

        @Nullable
        private final Instant enabledUntil;

        /**
         * A new LogMetadata.
         *
         * @param enabledSince since when logging is enabled.
         * @param enabledUntil until when logging will stay enabled.
         */
        private LogMetadata(@Nullable final Instant enabledSince, @Nullable final Instant enabledUntil) {
            this.enabledSince = enabledSince;
            this.enabledUntil = enabledUntil;
        }

        private static LogMetadata empty() {
            return new LogMetadata(null, null);
        }

        @Nullable
        private Instant getEnabledSince() {
            return enabledSince;
        }

        @Nullable
        private Instant getEnabledUntil() {
            return enabledUntil;
        }

        private LogMetadata withEnabledUntil(@Nullable final Instant newEnabledUntil) {
            return new LogMetadata(enabledSince, newEnabledUntil);
        }

        @Override
        public boolean equals(@Nullable final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final LogMetadata that = (LogMetadata) o;
            return Objects.equals(enabledSince, that.enabledSince) &&
                    Objects.equals(enabledUntil, that.enabledUntil);
        }

        @Override
        public int hashCode() {
            return Objects.hash(enabledSince, enabledUntil);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    "enabledSince=" + enabledSince +
                    ", enabledUntil=" + enabledUntil +
                    "]";
        }

    }

}
