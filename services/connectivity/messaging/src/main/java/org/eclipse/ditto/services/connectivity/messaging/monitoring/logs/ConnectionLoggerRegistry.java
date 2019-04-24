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
package org.eclipse.ditto.services.connectivity.messaging.monitoring.logs;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.LogCategory;
import org.eclipse.ditto.model.connectivity.LogEntry;
import org.eclipse.ditto.model.connectivity.LogType;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitorRegistry;
import org.eclipse.ditto.services.connectivity.util.ConnectionLogUtil;
import org.eclipse.ditto.services.connectivity.util.MonitoringConfigReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This registry holds loggers for the connectivity service. The loggers are identified by the connection ID, a {@link
 * org.eclipse.ditto.model.connectivity.LogType}, a {@link org.eclipse.ditto.model.connectivity.LogCategory} and an
 * address.
 */
public final class ConnectionLoggerRegistry implements ConnectionMonitorRegistry<ConnectionLogger> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionLoggerRegistry.class);

    private static final ConcurrentMap<MapKey, MuteableConnectionLogger> loggers = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, LogMetadata> metadata = new ConcurrentHashMap<>();

    // artificial internal address for responses
    private static final String RESPONSES_ADDRESS = "_responses";

    private final int successCapacity;
    private final int failureCapacity;
    private final TemporalAmount loggingDuration;

    private ConnectionLoggerRegistry(final int successCapacity, final int failureCapacity,
            final Duration loggingDuration) {
        this.successCapacity = successCapacity;
        this.failureCapacity = failureCapacity;
        this.loggingDuration = checkNotNull(loggingDuration);
    }

    /**
     * Build a new {@code ConnectionLoggerRegistry} from configuration.
     *
     * @param config the configuration to use.
     * @return a new instance of {@code ConnectionLoggerRegistry}.
     */
    public static ConnectionLoggerRegistry fromConfig(
            final MonitoringConfigReader.MonitoringLoggerConfigReader config) {
        checkNotNull(config);
        return new ConnectionLoggerRegistry(config.successCapacity(), config.failureCapacity(), config.logDuration());
    }


    /**
     * Aggregate the {@link org.eclipse.ditto.model.connectivity.LogEntry}s for the given connection from the loggers in
     * this registry.
     *
     * @param connectionId connection id
     * @return the {@link org.eclipse.ditto.model.connectivity.LogEntry}s.
     */
    public ConnectionLogs aggregateLogs(final String connectionId) {
        ConnectionLogUtil.enhanceLogWithConnectionId(connectionId);
        LOGGER.info("Aggregating logs for connection <{}>.", connectionId);

        final LogMetadata timing;
        final Collection<LogEntry> logs;

        if (isLoggingActive(connectionId)) {
            LOGGER.trace("Logging is enabled, will aggregate logs for connection <{}>", connectionId);

            timing = refreshMetadata(connectionId);
            // TODO: should we sort the log entries by date?
            logs = streamLoggers(connectionId)
                    .map(ConnectionLogger::getLogs)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());
        } else {
            LOGGER.debug("Logging is disabled, will return empty logs for connection <{}>", connectionId);

            timing = getMetadata(connectionId);
            logs = Collections.emptyList();
        }

        LOGGER.debug("Aggregated logs for connection <{}>: {}", connectionId, logs);
        return new ConnectionLogs(timing.getEnabledSince(), timing.getEnabledUntil(), logs);
    }

    private boolean isLoggingActive(final String connectionId) {
        final boolean muted = streamLoggers(connectionId)
                .findFirst()
                .map(MuteableConnectionLogger::isMuted)
                .orElse(true);
        return !muted;
    }

    // TODO: doc, test and use
    public void muteForConnection(final String connectionId) {
        ConnectionLogUtil.enhanceLogWithConnectionId(connectionId);
        LOGGER.info("Muting loggers for connection <{}>.", connectionId);

        streamLoggers(connectionId)
                .forEach(MuteableConnectionLogger::mute);
        // TODO: stopMetadata -> should either remove the metadata or set to LotMetadata#empty.
    }

    /**
     * Unmute / activate all loggers for the connection {@code connectionId}.
     *
     * @param connectionId the connection for which the loggers should be enabled.
     */
    public void unmuteForConnection(final String connectionId) {
        ConnectionLogUtil.enhanceLogWithConnectionId(connectionId);
        LOGGER.info("Unmuting loggers for connection <{}>.", connectionId);

        streamLoggers(connectionId)
                .forEach(MuteableConnectionLogger::unmute);
        startMetadata(connectionId);
    }

    private Stream<MuteableConnectionLogger> streamLoggers(final String connectionId) {
        return loggers.entrySet()
                .stream()
                .filter(e -> e.getKey().connectionId.equals(connectionId))
                .map(Map.Entry::getValue);
    }

    /**
     * Initializes the global {@code loggers} Map with the address information of the passed {@code connection}.
     *
     * @param connection the connection to initialize the global loggers map with.
     */
    @Override
    public void initForConnection(final Connection connection) {
        ConnectionLogUtil.enhanceLogWithConnectionId(connection.getId());
        LOGGER.info("Initializing loggers for connection <{}>.", connection.getId());

        final String connectionId = connection.getId();
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
        initLogger(connectionId, LogCategory.CONNECTION);
    }

    private void initLogger(final String connectionId, final LogCategory logCategory) {
        initLogger(connectionId, logCategory, null);
    }

    private void initLogger(final String connectionId, final LogCategory logCategory,
            @Nullable final String address) {
        Arrays.stream(LogType.values())
                .filter(logType -> logType.supportsCategory(logCategory))
                .forEach(logType -> {
                    final MapKey key = new MapKey(connectionId, logCategory, logType, address);
                    loggers.computeIfAbsent(key, m -> newMuteableLogger(connectionId, logCategory, logType, address));
                });
    }

    @Override
    public void resetForConnection(final Connection connection) {
        final String connectionId = connection.getId();
        ConnectionLogUtil.enhanceLogWithConnectionId(connectionId);
        LOGGER.info("Resetting loggers for connection <{}>.", connectionId);

        loggers.keySet().stream()
                .filter(key -> key.connectionId.equals(connectionId))
                .forEach(loggers::remove);

        refreshMetadata(connectionId);
    }

    private LogMetadata refreshMetadata(final String connectionId) {
        return metadata.compute(connectionId, (c, oldTimings) -> {
            final Instant now = Instant.now();
            if (null != oldTimings) {
                return oldTimings.withEnabledUntil(now.plus(loggingDuration));
            }
            return new LogMetadata(now, now.plus(loggingDuration));
        });
    }

    private void startMetadata(final String connectionId) {
        final Instant now = Instant.now();
        final LogMetadata timing = new LogMetadata(now, now.plus(loggingDuration));
        metadata.put(connectionId, timing);
    }

    private LogMetadata getMetadata(final String connectionId) {
        return metadata.getOrDefault(connectionId, LogMetadata.empty());
    }

    @Override
    public ConnectionLogger forOutboundDispatched(final String connectionId, final String target) {
        return getLogger(connectionId, LogCategory.TARGET, LogType.DISPATCHED, target);
    }

    @Override
    public ConnectionLogger forOutboundFiltered(final String connectionId, final String target) {
        return getLogger(connectionId, LogCategory.TARGET, LogType.FILTERED, target);
    }

    @Override
    public ConnectionLogger forOutboundPublished(final String connectionId, final String target) {
        return getLogger(connectionId, LogCategory.TARGET, LogType.PUBLISHED, target);
    }

    @Override
    public ConnectionLogger forInboundConsumed(final String connectionId, final String source) {
        return getLogger(connectionId, LogCategory.SOURCE, LogType.CONSUMED, source);
    }

    @Override
    public ConnectionLogger forInboundMapped(final String connectionId, final String source) {
        return getLogger(connectionId, LogCategory.SOURCE, LogType.MAPPED, source);
    }

    @Override
    public ConnectionLogger forInboundEnforced(final String connectionId, final String source) {
        return getLogger(connectionId, LogCategory.SOURCE, LogType.ENFORCED, source);
    }

    @Override
    public ConnectionLogger forInboundDropped(final String connectionId, final String source) {
        return getLogger(connectionId, LogCategory.SOURCE, LogType.DROPPED, source);
    }

    @Override
    public ConnectionLogger forResponseDispatched(final String connectionId) {
        return getLogger(connectionId, LogCategory.RESPONSE, LogType.DISPATCHED, RESPONSES_ADDRESS);
    }

    @Override
    public ConnectionLogger forResponseDropped(final String connectionId) {
        return getLogger(connectionId, LogCategory.RESPONSE, LogType.DROPPED, RESPONSES_ADDRESS);
    }

    @Override
    public ConnectionLogger forResponseMapped(final String connectionId) {
        return getLogger(connectionId, LogCategory.RESPONSE, LogType.MAPPED, RESPONSES_ADDRESS);
    }

    @Override
    public ConnectionLogger forResponsePublished(final String connectionId) {
        return getLogger(connectionId, LogCategory.RESPONSE, LogType.PUBLISHED, RESPONSES_ADDRESS);
    }

    /**
     * Get the logger for connection specific logs that can't be associated to a specific category/type.
     *
     * @param connectionId the connection.
     * @return a new logger instance.
     */
    public ConnectionLogger forConnection(final String connectionId) {
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
            final String connectionId,
            final LogCategory logCategory,
            final LogType logType,
            @Nullable final String address) {

        final MapKey key = new MapKey(connectionId, logCategory, logType, address);
        return loggers.computeIfAbsent(key, m -> newMuteableLogger(connectionId, logCategory, logType, address));
    }

    private MuteableConnectionLogger newMuteableLogger(final String connectionId, final LogCategory logCategory,
            final LogType logType,
            @Nullable final String address) {
        final ConnectionLogger logger =
                ConnectionLoggerFactory.newEvictingLogger(successCapacity, failureCapacity, logCategory, logType,
                        address);
        return ConnectionLoggerFactory.newMuteableLogger(connectionId, logger);
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
                Objects.equals(loggingDuration, that.loggingDuration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(successCapacity, failureCapacity, loggingDuration);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                ", successCapacity=" + successCapacity +
                ", failureCapacity=" + failureCapacity +
                ", loggingDuration=" + loggingDuration +
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
            this.logs = Collections.unmodifiableCollection(new ArrayList<>(logs));
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
                    ", enabledSince=" + enabledSince +
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

        private final String connectionId;
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
        MapKey(final String connectionId,
                final LogCategory logCategory,
                final LogType logType,
                @Nullable final String address) {
            this.connectionId = checkNotNull(connectionId, "connectionId");
            this.category = checkNotNull(logCategory, "log category").getName();
            this.type = checkNotNull(logType, "log type").getType();
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
                    ", connectionId=" + connectionId +
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
                    ", enabledSince=" + enabledSince +
                    ", enabledUntil=" + enabledUntil +
                    "]";
        }

    }

}
