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

import static nl.jqno.equalsverifier.EqualsVerifier.forClass;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.LogEntry;
import org.eclipse.ditto.model.connectivity.LogLevel;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.eclipse.ditto.services.connectivity.messaging.config.DefaultMonitoringLoggerConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.MonitoringLoggerConfig;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.ConnectionLoggerRegistry.ConnectionLogs;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link ConnectionLoggerRegistry}.
 */
@NotThreadSafe // since a static class is used ...
public final class ConnectionLoggerRegistryTest {

    private static final String ID = ConnectionLoggerRegistryTest.class.getSimpleName();

    private final ConnectionLoggerRegistry underTest =
            ConnectionLoggerRegistry.fromConfig(TestConstants.MONITORING_CONFIG.logger());

    private final Duration moreThanLoggingDuration =
            TestConstants.MONITORING_CONFIG.logger().logDuration()
                    .plusMinutes(1);

    @Test
    public void addsLoggerToRegistryOnRetrieve() {
        final ConnectionId connectionId = connectionId();
        final ConnectionLogger logger1 = underTest.forConnection(connectionId);
        final ConnectionLogger logger2 = underTest.forConnection(connectionId);
        assertThat(logger1).isNotNull()
                .isEqualTo(logger2)
                .isSameAs(logger2);
    }

    @Test
    public void clearsLoggersOnReset() {
        final ConnectionId connectionId = connectionId();
        final ConnectionLogger before = underTest.forConnection(connectionId);
        underTest.unmuteForConnection(connectionId);

        before.success(randomInfoProvider());

        assertThat(before.getLogs()).isNotEmpty();

        underTest.resetForConnection(connection(connectionId));

        assertThat(before.getLogs()).isEmpty();
        final ConnectionLogger after = underTest.forConnection(connectionId);
        assertThat(after).isNotNull()
                .isSameAs(before);
    }

    @Test
    public void leavesMutedLoggersMutedOnReset() {
        final ConnectionId connectionId = connectionId();
        underTest.initForConnection(connection(connectionId));

        underTest.resetForConnection(connection(connectionId));

        assertThat(underTest.isActiveForConnection(connectionId)).isFalse();
    }

    @Test
    public void leavesActivatedLoggersActivatedOnReset() {
        final ConnectionId connectionId = connectionId();
        underTest.initForConnection(connection(connectionId));
        underTest.unmuteForConnection(connectionId);

        underTest.resetForConnection(connection(connectionId));

        assertThat(underTest.isActiveForConnection(connectionId)).isTrue();
    }

    @Test
    public void clearsLoggersOnMute() {
        final ConnectionId connectionId = connectionId();
        final ConnectionLogger before = underTest.forConnection(connectionId);
        underTest.unmuteForConnection(connectionId);

        before.success(randomInfoProvider());

        assertThat(before.getLogs()).isNotEmpty();

        underTest.muteForConnection(connectionId);
        underTest.unmuteForConnection(connectionId);

        assertThat(before.getLogs()).isEmpty();
    }

    @Test
    public void isActiveForConnection() {
        final ConnectionId connectionId = connectionId();
        underTest.initForConnection(connection(connectionId));

        assertThat(underTest.isActiveForConnection(connectionId)).isFalse();

        underTest.unmuteForConnection(connectionId);

        assertThat(underTest.isActiveForConnection(connectionId)).isTrue();

        underTest.muteForConnection(connectionId);

        assertThat(underTest.isActiveForConnection(connectionId)).isFalse();
    }

    @Test
    public void isLoggingExpiredIsTrueByDefault() {
        final ConnectionId connectionId = connectionId();
        underTest.initForConnection(connection(connectionId));

        assertThat(underTest.isLoggingExpired(connectionId, Instant.now())).isTrue();
    }

    @Test
    public void isLoggingExpiredIsFalseForCurrentTimestamp() {
        final ConnectionId connectionId = connectionId();
        underTest.initForConnection(connection(connectionId));

        underTest.unmuteForConnection(connectionId);

        assertThat(underTest.isLoggingExpired(connectionId, Instant.now())).isFalse();
    }

    @Test
    public void isLoggingExpiredIsTrueForFutureTimestamp() {
        final ConnectionId connectionId = connectionId();
        underTest.initForConnection(connection(connectionId));

        underTest.unmuteForConnection(connectionId);

        assertThat(underTest.isLoggingExpired(connectionId, Instant.now().plus(moreThanLoggingDuration))).isTrue();
    }

    @Test
    public void isLoggingExpiredIsTrueAfterMutingConnections() {
        final ConnectionId connectionId = connectionId();
        underTest.initForConnection(connection(connectionId));

        underTest.unmuteForConnection(connectionId);

        assertThat(underTest.isLoggingExpired(connectionId, Instant.now())).isFalse();

        underTest.muteForConnection(connectionId);

        assertThat(underTest.isLoggingExpired(connectionId, Instant.now())).isTrue();
    }

    @Test
    public void aggregatesLogs() {
        final ConnectionId connectionId = connectionId();
        final String source = "a:b";
        underTest.initForConnection(connection(connectionId));
        underTest.unmuteForConnection(connectionId);

        final ConnectionLogger connectionLogger = underTest.forConnection(connectionId);
        final ConnectionLogger inboundConsumed = underTest.forInboundConsumed(connectionId, source);

        connectionLogger.success(randomInfoProvider());
        connectionLogger.failure(randomInfoProvider());
        inboundConsumed.success(randomInfoProvider());

        final Collection<LogEntry> connectionLogs = connectionLogger.getLogs();
        final Collection<LogEntry> inboundConsumedLogs = inboundConsumed.getLogs();

        final ConnectionLogs aggregatedLogs = underTest.aggregateLogs(connectionId);

        assertThat(aggregatedLogs.getEnabledSince()).isNotNull();
        assertThat(aggregatedLogs.getEnabledUntil()).isNotNull();
        assertThat(aggregatedLogs.getLogs())
                .containsAll(connectionLogs)
                .containsAll(inboundConsumedLogs)
                .hasSize(connectionLogs.size() + inboundConsumedLogs.size());
    }

    @Test
    public void aggregatesNoLogsForMutedLoggers() {
        final ConnectionId connectionId = connectionId();
        final String source = "a:b";
        // inits the loggers muted
        underTest.initForConnection(connection(connectionId));

        final ConnectionLogger connectionLogger = underTest.forConnection(connectionId);
        final ConnectionLogger inboundConsumed = underTest.forInboundConsumed(connectionId, source);

        connectionLogger.success(randomInfoProvider());
        connectionLogger.failure(randomInfoProvider());
        inboundConsumed.success(randomInfoProvider());

        final ConnectionLogs aggregatedLogs = underTest.aggregateLogs(connectionId);

        assertThat(aggregatedLogs.getEnabledSince()).isNull();
        assertThat(aggregatedLogs.getEnabledUntil()).isNull();
        assertThat(aggregatedLogs.getLogs()).isEmpty();
    }

    private ConnectionMonitor.InfoProvider randomInfoProvider() {
        return InfoProviderFactory.forHeaders(
                DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString()).build());
    }

    @Test
    public void usesCorrectCapacitiesFromConfig() {
        final int successCapacity = 2;
        final int failureCapacity = 1;
        final MonitoringLoggerConfig specialConfig =
                configWithCapacities(successCapacity, failureCapacity);
        final ConnectionLoggerRegistry specialLoggerRegistry = ConnectionLoggerRegistry.fromConfig(specialConfig);

        final ConnectionId connectionId = connectionId();
        final Connection connection = connection(connectionId);

        specialLoggerRegistry.initForConnection(connection);
        specialLoggerRegistry.unmuteForConnection(connectionId);

        final ConnectionLogger logger = specialLoggerRegistry.forConnection(connectionId);

        // create too much logs
        logNtimes(successCapacity + 1, logger::success);
        logNtimes(failureCapacity + 1, logger::failure);


        final Collection<LogEntry> logs = logger.getLogs();
        final long successCount = countByLevel(logs, LogLevel.SUCCESS);
        final long failureCount = countByLevel(logs, LogLevel.FAILURE);

        assertThat(successCount).isEqualTo(successCapacity);
        assertThat(failureCount).isEqualTo(failureCapacity);
    }

    private long countByLevel(final Collection<LogEntry> logEntries, final LogLevel level) {
        return logEntries.stream()
                .map(LogEntry::getLogLevel)
                .filter(level::equals)
                .count();
    }

    private void logNtimes(final int amount, final Consumer<ConnectionMonitor.InfoProvider> logger) {
        Stream.iterate(0, i -> i + 1)
                .limit(amount)
                .forEach(i -> logger.accept(randomInfoProvider()));
    }

    private MonitoringLoggerConfig configWithCapacities(final int successCapacity,
            final int failureCapacity) {
        final Map<String, Object> loggerEntries = new HashMap<>();
        loggerEntries.put("successCapacity", successCapacity);
        loggerEntries.put("failureCapacity", failureCapacity);
        loggerEntries.put("logDuration", "1d");

        final Map<String, Object> configEntries = new HashMap<>();
        configEntries.put("logger", loggerEntries);

        final Config config = ConfigFactory.parseMap(configEntries);
        return DefaultMonitoringLoggerConfig.of(config);
    }

    @Test
    public void enableConnectionLogsUnmutesTheLoggersAndStartsTimer() {
        final ConnectionId connectionId = connectionId();
        underTest.initForConnection(connection(connectionId));

        final MuteableConnectionLogger anyLoggerOfConnection =
                (MuteableConnectionLogger) underTest.forConnection(connectionId);

        assertThat(anyLoggerOfConnection.isMuted()).isTrue();
        final Instant before = Instant.now();

        underTest.unmuteForConnection(connectionId);

        final Instant after = Instant.now();

        assertThat(anyLoggerOfConnection.isMuted()).isFalse();
        assertThat(underTest.aggregateLogs(connectionId).getEnabledSince())
                .isBetween(before, after);

    }

    @Test
    public void disableConnectionLogsMutesTheLoggersAndStopsTimer() {
        final ConnectionId connectionId = connectionId();
        underTest.initForConnection(connection(connectionId));

        final MuteableConnectionLogger anyLoggerOfConnection =
                (MuteableConnectionLogger) underTest.forConnection(connectionId);

        underTest.unmuteForConnection(connectionId);
        assertThat(anyLoggerOfConnection.isMuted()).isFalse();

        underTest.muteForConnection(connectionId);
        assertThat(anyLoggerOfConnection.isMuted()).isTrue();
        final ConnectionLogs connectionLogs = underTest.aggregateLogs(connectionId);
        assertThat(connectionLogs.getEnabledSince())
                .isNull();
        assertThat(connectionLogs.getEnabledUntil())
                .isNull();

    }

    @Test
    public void testEqualsAndHashcode() {
        forClass(ConnectionLoggerRegistry.class)
                .verify();
    }

    private ConnectionId connectionId() {
        return ConnectionId.of(ID + ":" + UUID.randomUUID().toString());
    }

    private Connection connection(final ConnectionId connectionId) {
        final Source source = ConnectivityModelFactory.newSource(
                AuthorizationContext.newInstance(AuthorizationSubject.newInstance("integration:solution:dummy")),
                "a:b");
        return ConnectivityModelFactory.newConnectionBuilder(connectionId, ConnectionType.AMQP_10,
                ConnectivityStatus.CLOSED, "amqp://uri:5672")
                .sources(Collections.singletonList(source))
                .build();
    }

    @Test
    public void testImmutabilityOfCollectionLogs() {
        assertInstancesOf(ConnectionLogs.class, areImmutable(), provided(LogEntry.class).isAlsoImmutable());
    }

    @Test
    public void testEqualsAndHashcodeOfCollectionLogs() {
        forClass(ConnectionLogs.class).verify();
    }

}
