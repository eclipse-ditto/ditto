/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.postgres.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.PostgresConfig;
import org.junit.Rule;
import org.junit.Test;
import org.reactivestreams.Publisher;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigResolveOptions;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.pool.PoolMetrics;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;

import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.pool.PoolAcquirePendingLimitException;

/**
 * Pins the connection budget of ONE service instance as shipped in {@code ditto-postgres-client.conf}: at most
 * {@code pool.max-size} (25) connections, open or opening, at most {@code 2 x max-size} (50) waiting borrowers, and
 * idle connections closed after r2dbc-pool's 30 minutes.
 * <p>
 * Operators size PostgreSQL's {@code max_connections} as the sum of {@code (replicas + maxSurge) x max-size} over all
 * PostgreSQL-backed services (see the "Connection budget" comment in {@code ditto-postgres-client.conf}), so these
 * numbers are part of the deployment contract.
 * </p>
 * <p>
 * Fully offline: the production conf (system environment ignored), the production
 * {@code ConnectionPoolFactory.buildPoolConfiguration} and in-memory connection factories.
 * </p>
 */
public final class ConnectionPoolFactoryPoolSizingTest {

    private static final int SHIPPED_MAX_SIZE = 25;
    private static final int SHIPPED_PENDING_BOUND = 2 * SHIPPED_MAX_SIZE;
    private static final Duration BLOCK = Duration.ofSeconds(5);

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    private static PostgresConfig productionConfigWith(final String overrides) {
        final Config production = ConfigFactory.parseResources("ditto-postgres-client.conf")
                .resolve(ConfigResolveOptions.defaults().setUseSystemEnvironment(false));
        return DefaultPostgresConfig.of(ConfigFactory.parseString(
                        "ditto.postgresql.ssl.mode = \"disable\"\n" + overrides)
                .withFallback(production)
                .getConfig("ditto"));
    }

    private static PoolMetrics metricsOf(final ConnectionPool pool) {
        return pool.getMetrics().orElseThrow();
    }

    @Test
    public void confAndCodeDefaultAgreeOnTwentyFiveConnectionsPerServiceInstance() {
        final PostgresConfig.PoolConfig fromConf = productionConfigWith("").getPoolConfig();
        final PostgresConfig.PoolConfig fromCode = DefaultPostgresConfig.of(ConfigFactory.empty()).getPoolConfig();

        softly.assertThat(fromConf.getMaxSize())
                .as("ditto-postgres-client.conf pool.max-size")
                .isEqualTo(SHIPPED_MAX_SIZE);
        softly.assertThat(fromCode.getMaxSize())
                .as("PoolConfigValue.MAX_SIZE code default (must match the conf)")
                .isEqualTo(SHIPPED_MAX_SIZE);
        softly.assertThat(fromConf.getPendingAcquireMaxSize())
                .as("pool.pending-acquire-max-size: unset in the conf, derived as 2 x max-size")
                .isEqualTo(SHIPPED_PENDING_BOUND);
    }

    @Test
    public void shippedPoolHoldsTwentyFiveConnectionsQueuesFiftyBorrowersAndShedsTheNext() throws Exception {
        // A 30s acquire budget keeps the queued borrowers waiting for the whole test; with the shipped 4s they could
        // time out while a slow machine is still filling the queue. Size and queue bound are the shipped ones.
        final StubConnectionFactory factory = StubConnectionFactory.emitting();
        final ConnectionPool pool = new ConnectionPool(ConnectionPoolFactory.buildPoolConfiguration(factory,
                productionConfigWith("ditto.postgresql.pool.max-acquire-time = 30s\n")));
        final List<Disposable> borrowers = new ArrayList<>();
        try {
            for (int i = 0; i < SHIPPED_MAX_SIZE; i++) {
                assertThat(Mono.from(pool.create()).block(BLOCK)).isNotNull();
            }
            final List<Throwable> queuedErrors = Collections.synchronizedList(new ArrayList<>());
            for (int i = 0; i < SHIPPED_PENDING_BOUND; i++) {
                borrowers.add(Mono.from(pool.create()).subscribe(c -> { }, queuedErrors::add));
            }
            await(() -> metricsOf(pool).pendingAcquireSize() == SHIPPED_PENDING_BOUND);

            final List<Throwable> excessErrors = Collections.synchronizedList(new ArrayList<>());
            borrowers.add(Mono.from(pool.create()).subscribe(c -> { }, excessErrors::add));
            await(() -> !excessErrors.isEmpty());

            softly.assertThat(factory.openedConnections())
                    .as("physical connections opened for 76 concurrent borrowers")
                    .isEqualTo(SHIPPED_MAX_SIZE);
            softly.assertThat(metricsOf(pool).allocatedSize()).as("allocated").isEqualTo(SHIPPED_MAX_SIZE);
            softly.assertThat(metricsOf(pool).pendingAcquireSize())
                    .as("waiting borrowers")
                    .isEqualTo(SHIPPED_PENDING_BOUND);
            softly.assertThat(queuedErrors).as("borrowers within the queue bound wait").isEmpty();
            // No navigation (singleElement()) on a soft assertion: when it fails it yields null and the chain NPEs.
            softly.assertThat(excessErrors).as("the 76th borrower is shed at once").hasSize(1);
            softly.assertThat(excessErrors)
                    .as("the 76th borrower is rejected at the shipped pending bound")
                    .allSatisfy(error -> assertThat(error).isInstanceOfSatisfying(
                            PoolAcquirePendingLimitException.class,
                            e -> assertThat(e.getAcquirePendingLimit()).isEqualTo(SHIPPED_PENDING_BOUND)));
        } finally {
            borrowers.forEach(Disposable::dispose);
            pool.dispose();
        }
    }

    @Test
    public void aColdBurstStartsAtMostTwentyFiveConnectionAttemptsAtOnce() throws Exception {
        // r2dbc-pool has no counterpart of MongoDB's maxConnecting: the only bound on simultaneous handshakes is
        // max-size, counted for connections that are still being opened.
        final GatedConnectionFactory factory = new GatedConnectionFactory();
        final ConnectionPool pool = new ConnectionPool(ConnectionPoolFactory.buildPoolConfiguration(factory,
                productionConfigWith("ditto.postgresql.pool.max-acquire-time = 30s\n")));
        final List<Disposable> borrowers = new ArrayList<>();
        try {
            for (int i = 0; i < 80; i++) {
                borrowers.add(Mono.from(pool.create()).subscribe(c -> { }, e -> { }));
            }
            await(() -> factory.attempts() >= SHIPPED_MAX_SIZE);
            Thread.sleep(200L); // an unbounded pool would start more attempts in this window

            assertThat(factory.attempts())
                    .as("connection attempts in flight for 80 borrowers against a cold pool")
                    .isEqualTo(SHIPPED_MAX_SIZE);
        } finally {
            factory.failAll();
            borrowers.forEach(Disposable::dispose);
            pool.dispose();
        }
    }

    @Test
    public void shippedPoolClosesIdleConnectionsAfterThirtyMinutesAndNeverRetiresThemByAge() throws Exception {
        final ConnectionPoolConfiguration configuration = ConnectionPoolFactory.buildPoolConfiguration(
                StubConnectionFactory.emitting(), productionConfigWith(""));

        softly.assertThat(read(configuration, "getInitialSize"))
                .as("initial-size = 0: connections are opened on demand")
                .isEqualTo(0);
        softly.assertThat(read(configuration, "getMaxIdleTime"))
                .as("max-idle-time = 0s keeps r2dbc-pool's default: idle connections close after 30 minutes, "
                        + "it does NOT mean 'no limit'")
                .isEqualTo(Duration.ofMinutes(30));
        softly.assertThat(read(configuration, "getMaxLifeTime"))
                .as("max-life-time = 0s keeps r2dbc-pool's default: no age limit")
                .isEqualTo(ConnectionPoolConfiguration.NO_TIMEOUT);
    }

    /**
     * {@link ConnectionPoolConfiguration}'s getters are package-private
     * ({@code io.r2dbc.pool.R2dbcPoolLibraryDefaultsTest} reads the library defaults from inside that package). This
     * test reads the configuration the production {@code buildPoolConfiguration} builds, which is only reachable from
     * this package, so it calls them reflectively.
     */
    private static Object read(final ConnectionPoolConfiguration configuration, final String getter)
            throws ReflectiveOperationException {
        final Method method = ConnectionPoolConfiguration.class.getDeclaredMethod(getter);
        method.setAccessible(true);
        return method.invoke(configuration);
    }

    private static void await(final BooleanSupplier condition) throws InterruptedException {
        final long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(10L);
        }
    }

    /**
     * Connection attempts that hang until the test releases them, so the attempts in flight can be counted.
     */
    private static final class GatedConnectionFactory implements ConnectionFactory {

        private final Sinks.One<Connection> gate = Sinks.one();
        private final AtomicInteger attempts = new AtomicInteger();

        @Override
        public Publisher<? extends Connection> create() {
            return gate.asMono().doOnSubscribe(subscription -> attempts.incrementAndGet());
        }

        @Override
        public ConnectionFactoryMetadata getMetadata() {
            return () -> "gated";
        }

        int attempts() {
            return attempts.get();
        }

        void failAll() {
            gate.tryEmitError(new IllegalStateException("test teardown: connection attempts released"));
        }

    }

}
