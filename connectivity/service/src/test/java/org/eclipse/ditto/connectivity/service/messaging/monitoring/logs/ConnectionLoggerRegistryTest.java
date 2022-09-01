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

import static nl.jqno.equalsverifier.EqualsVerifier.forClass;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.annotation.concurrent.NotThreadSafe;

import org.assertj.core.api.Fail;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.LogEntry;
import org.eclipse.ditto.connectivity.model.LogLevel;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.config.DefaultMonitoringLoggerConfig;
import org.eclipse.ditto.connectivity.service.config.MonitoringLoggerConfig;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.junit.Test;
import org.komamitsu.fluency.Fluency;
import org.komamitsu.fluency.fluentd.FluencyBuilderForFluentd;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link ConnectionLoggerRegistry}.
 */
@NotThreadSafe // since a static class is used ...
public final class ConnectionLoggerRegistryTest {

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
    public void aggregatesLogs() throws InterruptedException {
        final ConnectionId connectionId = connectionId();
        final Connection connection = connection(connectionId);
        final String source = "a:b";
        underTest.initForConnection(connection(connectionId));
        underTest.unmuteForConnection(connectionId);

        final ConnectionLogger connectionLogger = underTest.forConnection(connectionId);
        final ConnectionLogger inboundConsumed = underTest.forInboundConsumed(connection, source);

        connectionLogger.success(randomInfoProvider());
        TimeUnit.MILLISECONDS.sleep(1); // ensure different timestamps to make ordering assertion stable
        connectionLogger.failure(randomInfoProvider());
        TimeUnit.MILLISECONDS.sleep(1); // ensure different timestamps to make ordering assertion stable
        inboundConsumed.success(randomInfoProvider());

        final Collection<LogEntry> connectionLogs = connectionLogger.getLogs();
        final Collection<LogEntry> inboundConsumedLogs = inboundConsumed.getLogs();

        final ConnectionLoggerRegistry.ConnectionLogs aggregatedLogs = underTest.aggregateLogs(connectionId);

        final ArrayList<LogEntry> logEntries = new ArrayList<>(aggregatedLogs.getLogs());
        assertThat(aggregatedLogs.getEnabledSince()).isNotNull();
        assertThat(aggregatedLogs.getEnabledUntil()).isNotNull();
        assertThat(logEntries)
                .containsAll(connectionLogs)
                .containsAll(inboundConsumedLogs)
                .hasSize(connectionLogs.size() + inboundConsumedLogs.size());
        assertThat(logEntries.get(0).getTimestamp()).isBefore(logEntries.get(1).getTimestamp());
    }

    @Test
    public void aggregatesLogsRespectsMaximumLogSizeLimit() throws InterruptedException {
        final ConnectionId connectionId = connectionId();
        final Connection connection = connection(connectionId);
        final String source = "a:b";
        underTest.initForConnection(connection(connectionId));
        underTest.unmuteForConnection(connectionId);

        final ConnectionLogger connectionLogger = underTest.forConnection(connectionId);
        final ConnectionLogger inboundConsumed = underTest.forInboundConsumed(connection, source);

        connectionLogger.success(randomInfoProvider());
        final Collection<LogEntry> listWithOnlyFirstSuccessLog = connectionLogger.getLogs();
        addLogEntriesUntilMaxSizeIsExceeded(connectionLogger);

        final Collection<LogEntry> connectionLogs = connectionLogger.getLogs();
        final Collection<LogEntry> inboundConsumedLogs = inboundConsumed.getLogs();

        final ConnectionLoggerRegistry.ConnectionLogs aggregatedLogs = underTest.aggregateLogs(connectionId);

        final ArrayList<LogEntry> logEntries = new ArrayList<>(aggregatedLogs.getLogs());
        assertThat(aggregatedLogs.getEnabledSince()).isNotNull();
        assertThat(aggregatedLogs.getEnabledUntil()).isNotNull();
        assertThat(logEntries)
                .doesNotContainSequence(listWithOnlyFirstSuccessLog)
                .hasSize(connectionLogs.size() + inboundConsumedLogs.size() - 1);
    }

    private void addLogEntriesUntilMaxSizeIsExceeded(final ConnectionLogger logger) throws InterruptedException {
        final long maxSize = TestConstants.MONITORING_CONFIG.logger().maxLogSizeInBytes();
        final int maxFailureLogs = TestConstants.MONITORING_CONFIG.logger().failureCapacity();
        int currentFailureLogs = 0;

        while (getCurrentLogsSize(logger) < maxSize) {
            logger.failure(randomInfoProvider());
            TimeUnit.MILLISECONDS.sleep(1); // ensure different timestamps to make ordering assertion stable

            if (++currentFailureLogs > maxFailureLogs) {
                Fail.fail("Breaking the while loop as I can't create enough failure logs to trigger the "
                + "max logs size. Review the logger config and find fitting config values for the maxLogSizeInBytes "
                + "and the failure capacity.");
            }
        }
    }

    private long getCurrentLogsSize(final ConnectionLogger logger) {
        return logger.getLogs()
                .stream()
                .map(LogEntry::toJsonString)
                .map(String::length)
                .reduce(0, Integer::sum);
    }

    @Test
    public void aggregatesNoLogsForMutedLoggers() {
        final ConnectionId connectionId = connectionId();
        final Connection connection = connection(connectionId);
        final String source = "a:b";
        // inits the loggers muted
        underTest.initForConnection(connection(connectionId));

        final ConnectionLogger connectionLogger = underTest.forConnection(connectionId);
        final ConnectionLogger inboundConsumed = underTest.forInboundConsumed(connection, source);

        connectionLogger.success(randomInfoProvider());
        connectionLogger.failure(randomInfoProvider());
        inboundConsumed.success(randomInfoProvider());

        final ConnectionLoggerRegistry.ConnectionLogs aggregatedLogs = underTest.aggregateLogs(connectionId);

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
        final ConnectionLoggerRegistry.ConnectionLogs connectionLogs = underTest.aggregateLogs(connectionId);
        assertThat(connectionLogs.getEnabledSince())
                .isNull();
        assertThat(connectionLogs.getEnabledUntil())
                .isNull();

    }

    @Test
    public void testEqualsAndHashcode() {
        final Fluency red = new FluencyBuilderForFluentd().build();
        final Fluency black = new FluencyBuilderForFluentd().build("localhost", 9999);

        forClass(ConnectionLoggerRegistry.class).withPrefabValues(Fluency.class, red, black).verify();
    }

    private ConnectionId connectionId() {
        return ConnectionId.of("loggerRegistryTest-" + UUID.randomUUID().toString());
    }

    private Connection connection(final ConnectionId connectionId) {
        final Source source = ConnectivityModelFactory.newSource(
                AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                        AuthorizationSubject.newInstance("integration:solution:dummy")), "a:b");
        return ConnectivityModelFactory.newConnectionBuilder(connectionId, ConnectionType.AMQP_10,
                ConnectivityStatus.CLOSED, "amqp://uri:5672")
                .sources(Collections.singletonList(source))
                .build();
    }

    @Test
    public void testImmutabilityOfCollectionLogs() {
        assertInstancesOf(ConnectionLoggerRegistry.ConnectionLogs.class, areImmutable(),
                assumingFields("logs").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements(),
                provided(LogEntry.class).isAlsoImmutable());
    }

    @Test
    public void testEqualsAndHashcodeOfCollectionLogs() {
        forClass(ConnectionLoggerRegistry.ConnectionLogs.class).verify();
    }

}
