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
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.LogCategory;
import org.eclipse.ditto.model.connectivity.LogEntry;
import org.eclipse.ditto.model.connectivity.LogType;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitorRegistry;
import org.eclipse.ditto.services.connectivity.util.MonitoringConfigReader;

/**
 * This registry holds loggers for the connectivity service. The loggers are identified by the connection ID, a {@link
 * org.eclipse.ditto.model.connectivity.LogType}, a {@link org.eclipse.ditto.model.connectivity.LogCategory} and an
 * address.
 */
// TODO: docs & test
public final class ConnectionLoggerRegistry implements ConnectionMonitorRegistry<ConnectionLogger> {

    private static final ConcurrentMap<MapKey, MuteableConnectionLogger> loggers = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, LogMetadata> metadata = new ConcurrentHashMap<>();

    // artificial internal address for responses TODO: unify with connection counter
    private static final String RESPONSES_ADDRESS = "_responses";

    private final int successCapacity;
    private final int failureCapacity;
    private final TemporalAmount loggingDuration;

    private ConnectionLoggerRegistry(final int successCapacity, final int failureCapacity, final Duration loggingDuration) {
        this.successCapacity = successCapacity;
        this.failureCapacity = failureCapacity;
        this.loggingDuration = checkNotNull(loggingDuration);
    }

    public static ConnectionLoggerRegistry fromConfig(final MonitoringConfigReader.MonitoringLoggerConfigReader config) {
        checkNotNull(config);
        return new ConnectionLoggerRegistry(config.successCapacity(), config.failureCapacity(), config.logDuration());
    }


    /**
     * Aggregate the {@link org.eclipse.ditto.model.connectivity.LogEntry}s for the given connection from the
     * loggers in this registry.
     *
     * @param connectionId connection id
     * @return the {@link org.eclipse.ditto.model.connectivity.LogEntry}s.
     */
    public CollectionLogs aggregateLogs(final String connectionId) {
        // TODO: should we sort the log entries by date?
        // TODO: it does not make sense to refresh metadata for a connection id if the logger for the connection is muted! implement & test
        final LogMetadata timing = refreshMetadata(connectionId);
        final Collection<LogEntry> logs = loggers.entrySet()
                .stream()
                .filter(e -> e.getKey().connectionId.equals(connectionId))
                .map(Map.Entry::getValue)
                .map(ConnectionLogger::getLogs)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        return new CollectionLogs(timing.getEnabledSince(), timing.getEnabledUntil(), logs);
    }

    /**
     * Initializes the global {@code loggers} Map with the address information of the passed {@code connection}.
     *
     * @param connection the connection to initialize the global loggers map with.
     */
    @Override
    public void initForConnection(final Connection connection) {

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

        refreshMetadata(connectionId);
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
                    loggers.computeIfAbsent(key, m -> newMuteableLogger(logCategory, logType, address));
                });
    }

    @Override
    public void resetForConnection(final Connection connection) {
        final String connectionId = connection.getId();
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
        // TODO: should the new logger be muted or unmuted when starting? probably muted -> test it
        return loggers.computeIfAbsent(key, m -> newMuteableLogger(logCategory, logType, address));
    }

    private MuteableConnectionLogger newMuteableLogger(final LogCategory logCategory, final LogType logType,
            @Nullable final String address) {
        final ConnectionLogger logger = ConnectionLoggerFactory.newEvictingLogger(successCapacity, failureCapacity, logCategory, logType, address);
        return ConnectionLoggerFactory.newMuteableLogger(logger);
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
     * Helper class that stores logs together with information on when they were enabled and how long they will stay enabled.
     */
    @Immutable
    public static class CollectionLogs {

        private Instant enabledSince;
        private Instant enabledUntil;
        private Collection<LogEntry> logs;

        private CollectionLogs(final Instant enabledSince, final Instant enabledUntil,
                final Collection<LogEntry> logs) {
            this.enabledSince = enabledSince;
            this.enabledUntil = enabledUntil;
            this.logs = logs;
        }

        public Instant getEnabledSince() {
            return enabledSince;
        }

        public Instant getEnabledUntil() {
            return enabledUntil;
        }

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
            final CollectionLogs that = (CollectionLogs) o;
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
    private static class MapKey {

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
            this.category = logCategory.getName();
            this.type = logType.getType();
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

        private Instant enabledSince;
        private Instant enabledUntil;

        /**
         * A new LogMetadata.
         *
         * @param enabledSince since when logging is enabled.
         * @param enabledUntil until when logging will stay enabled.
         */
        private LogMetadata(final Instant enabledSince, final Instant enabledUntil) {
            this.enabledSince = enabledSince;
            this.enabledUntil = enabledUntil;
        }

        private Instant getEnabledSince() {
            return enabledSince;
        }

        private Instant getEnabledUntil() {
            return enabledUntil;
        }

        private LogMetadata withEnabledUntil(final Instant newEnabledUntil) {
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
