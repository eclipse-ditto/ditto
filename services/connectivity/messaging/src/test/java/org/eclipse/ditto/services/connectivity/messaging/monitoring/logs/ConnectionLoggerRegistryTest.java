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
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.LogEntry;
import org.eclipse.ditto.model.connectivity.LogLevel;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.ConnectionLoggerRegistry.ConnectionLogs;
import org.eclipse.ditto.services.connectivity.util.ConfigKeys;
import org.eclipse.ditto.services.connectivity.util.MonitoringConfigReader;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;

/**
 * Unit test for {@link ConnectionLoggerRegistry}.
 */
@NotThreadSafe // since a static class is used ...
public final class ConnectionLoggerRegistryTest {

    private static final String ID = ConnectionLoggerRegistryTest.class.getSimpleName();

    private final ConnectionLoggerRegistry underTest =
            ConnectionLoggerRegistry.fromConfig(TestConstants.Monitoring.MONITORING_CONFIG_READER.logger());

    @Test
    public void addsLoggerToRegistryOnRetrieve() {
        final String connectionId = connectionId();
        final ConnectionLogger logger1 = underTest.forConnection(connectionId);
        final ConnectionLogger logger2 = underTest.forConnection(connectionId);
        assertThat(logger1).isNotNull()
                .isEqualTo(logger2)
                .isSameAs(logger2);
    }

    @Test
    public void clearsLoggersOnReset() {
        final String connectionId = connectionId();
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
        final String connectionId = connectionId();
        underTest.initForConnection(connection(connectionId));

        underTest.resetForConnection(connection(connectionId));

        assertThat(underTest.isActiveForConnection(connectionId)).isFalse();
    }

    @Test
    public void leavesActivatedLoggersActivatedOnReset() {
        final String connectionId = connectionId();
        underTest.initForConnection(connection(connectionId));
        underTest.unmuteForConnection(connectionId);

        underTest.resetForConnection(connection(connectionId));

        assertThat(underTest.isActiveForConnection(connectionId)).isTrue();
    }

    @Test
    public void isActiveForConnection() {
        final String connectionId = connectionId();
        underTest.initForConnection(connection(connectionId));

        assertThat(underTest.isActiveForConnection(connectionId)).isFalse();

        underTest.unmuteForConnection(connectionId);

        assertThat(underTest.isActiveForConnection(connectionId)).isTrue();

        underTest.muteForConnection(connectionId);

        assertThat(underTest.isActiveForConnection(connectionId)).isFalse();
    }

    @Test
    public void isStillActiveForConnection() {
        final String connectionId = connectionId();
        underTest.initForConnection(connection(connectionId));
        assertThat(underTest.isActiveForConnection(connectionId)).isFalse();

        underTest.unmuteForConnection(connectionId);
        assertThat(underTest.isActiveForConnection(connectionId)).isTrue();

        assertThat(underTest.disabledDueToEnabledUntilExpired(connectionId, Instant.now())).isFalse();
        assertThat(underTest.isActiveForConnection(connectionId)).isTrue();
    }

    @Test
    public void isTerminatedForConnectionDueToExpiredEnabledUntil() {
        final String connectionId = connectionId();
        underTest.initForConnection(connection(connectionId));
        assertThat(underTest.isActiveForConnection(connectionId)).isFalse();

        underTest.unmuteForConnection(connectionId);
        assertThat(underTest.isActiveForConnection(connectionId)).isTrue();

        final Instant twentyFourHoursFromNow = Instant.now().plus(Duration.ofDays(1));

        assertThat(underTest.disabledDueToEnabledUntilExpired(connectionId, twentyFourHoursFromNow)).isTrue();
        assertThat(underTest.isActiveForConnection(connectionId)).isFalse();
    }

    @Test
    public void aggregatesLogs() {
        final String connectionId = connectionId();
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
        final String connectionId = connectionId();
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
        return InfoProviderFactory.forHeaders(DittoHeaders.newBuilder().correlationId(UUID.randomUUID().toString()).build());
    }

    @Test
    public void usesCorrectCapacitiesFromConfig() {
        final int successCapacity = 2;
        final int failureCapacity = 1;
        final MonitoringConfigReader.MonitoringLoggerConfigReader specialConfig = configWithCapacities(successCapacity, failureCapacity);
        final ConnectionLoggerRegistry specialLoggerRegistry = ConnectionLoggerRegistry.fromConfig(specialConfig);

        final String connectionId = connectionId();
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

    private MonitoringConfigReader.MonitoringLoggerConfigReader configWithCapacities(final int successCapacity, final int failureCapacity) {
        final Map<String, Object> loggerEntries = new HashMap<>();
        loggerEntries.put("successCapacity", successCapacity);
        loggerEntries.put("failureCapacity", failureCapacity);
        loggerEntries.put("logDuration", "1d");

        final Map<String, Object> configEntries = new HashMap<>();
        configEntries.put("ditto.connectivity.monitoring.logger", loggerEntries);

        final Config config = ConfigFactory.parseMap(configEntries);
        final MonitoringConfigReader monitoringConfigReader = ConfigKeys.Monitoring.fromRawConfig(config);
        return monitoringConfigReader.logger();
    }

    @Test
    public void enableConnectionLogsUnmutesTheLoggersAndStartsTimer() {
        final String connectionId = connectionId();
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
        final String connectionId = connectionId();
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

    private String connectionId() {
        return ID + ":" + UUID.randomUUID().toString();
    }

    private Connection connection(final String connectionId) {
        final Source source = ConnectivityModelFactory.newSource(
                AuthorizationContext.newInstance(AuthorizationSubject.newInstance("integration:solution:dummy")), "a:b");
        return ConnectivityModelFactory.newConnectionBuilder(connectionId, ConnectionType.AMQP_10, ConnectivityStatus.CLOSED, "amqp://uri:5672")
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
