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
package io.r2dbc.pool;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.Test;
import org.reactivestreams.Publisher;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;

import reactor.core.publisher.Mono;

/**
 * Pins the r2dbc-pool / reactor-pool library defaults that Ditto's {@code ConnectionPoolFactory} either overrides on
 * purpose or relies on implicitly. If a library upgrade changes one of them, this test says which assumption moved.
 * Declared in {@code io.r2dbc.pool} so the package-private {@link ConnectionPoolConfiguration} accessors are readable
 * without reflection.
 * <ul>
 *     <li>Overridden on purpose (pool overload): {@code acquireRetry} defaults to 1 and the pending-acquire queue is
 *     unbounded. Left alone, a starved borrow waits {@code 2 x max-acquire-time} in an unbounded queue — far beyond the
 *     caller's deadline. {@code ConnectionPoolFactory} therefore sets {@code pool.acquire-retry} (default 0) and
 *     {@code pool.pending-acquire-max-size} (default 2 x max-size) explicitly.</li>
 *     <li>Relied on implicitly: {@code ConnectionPoolFactory.applyIfPositive} skips a {@link Duration#ZERO}
 *     {@code max-idle-time} / {@code max-life-time}, so {@code 0s} in the conf means "keep the library default" — and
 *     the two defaults differ (30 minutes vs. no timeout).</li>
 * </ul>
 * <p>
 * Originally the 2026-08-21 pool-starvation investigation's {@code R2dbcPoolDefaultsProbeTest}.
 * </p>
 */
public final class R2dbcPoolLibraryDefaultsTest {

    private static final ConnectionFactory NOOP_FACTORY = new ConnectionFactory() {
        @Override
        public Publisher<? extends Connection> create() {
            return Mono.error(new UnsupportedOperationException("library-defaults test"));
        }

        @Override
        public ConnectionFactoryMetadata getMetadata() {
            return () -> "library-defaults-test";
        }
    };

    /** A configuration with none of the settings under test touched. */
    private static ConnectionPoolConfiguration libraryDefaults() {
        return ConnectionPoolConfiguration.builder(NOOP_FACTORY).build();
    }

    @Test
    public void acquireRetryDefaultsToOneSoAnUnOverriddenAcquireIsAttemptedTwice() {
        assertThat(libraryDefaults().getAcquireRetry())
                .as("r2dbc-pool re-subscribes the whole acquire, timeout included, once by default — which doubles "
                        + "the worst-case time-to-error unless acquire-retry is overridden")
                .isEqualTo(1);
    }

    @Test
    public void pendingAcquireQueueIsUnboundedWithoutACustomizer() {
        final ConnectionPool pool = new ConnectionPool(ConnectionPoolConfiguration.builder(NOOP_FACTORY)
                .initialSize(0)
                .build());
        try {
            assertThat(pool.getMetrics().orElseThrow().getMaxPendingAcquireSize())
                    .as("reactor-pool's maxPendingAcquire defaults to -1 (unbounded), reported as Integer.MAX_VALUE; "
                            + "r2dbc-pool exposes no option for it, only the PoolBuilder customizer")
                    .isEqualTo(Integer.MAX_VALUE);
        } finally {
            pool.dispose();
        }
    }

    @Test
    public void maxIdleTimeDefaultsToThirtyMinutes() {
        assertThat(libraryDefaults().getMaxIdleTime())
                .as("`max-idle-time = 0s` is skipped by applyIfPositive, so r2dbc-pool's 30 minute builder default "
                        + "applies — neither 'no limit' nor 'immediate disposal'")
                .isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    public void maxLifeTimeDefaultsToNoTimeout() {
        assertThat(libraryDefaults().getMaxLifeTime())
                .as("max-life-time's builder default is 'no timeout', so `max-life-time = 0s` means no limit")
                .isEqualTo(ConnectionPoolConfiguration.NO_TIMEOUT);
    }

    @Test
    public void anExplicitZeroMaxIdleTimeWouldMeanImmediateDisposalNotNoLimit() {
        final ConnectionPoolConfiguration configuration = ConnectionPoolConfiguration.builder(NOOP_FACTORY)
                .maxIdleTime(Duration.ZERO)
                .build();
        assertThat(configuration.getMaxIdleTime())
                .as("Duration.ZERO is r2dbc-pool's 'evict immediately'; 'no limit' would need a NEGATIVE duration")
                .isEqualTo(Duration.ZERO);
    }

    @Test
    public void maxCreateConnectionTimeIsUnboundedByDefault() {
        assertThat(libraryDefaults().getMaxCreateConnectionTime())
                .as("no per-allocation cap; only the r2dbc SPI CONNECT_TIMEOUT bounds the TCP/TLS handshake")
                .isEqualTo(ConnectionPoolConfiguration.NO_TIMEOUT);
    }

}
