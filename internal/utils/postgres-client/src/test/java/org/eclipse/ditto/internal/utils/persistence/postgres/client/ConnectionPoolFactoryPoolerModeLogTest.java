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

import java.util.List;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.PostgresConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Unit test proving the L-12 pooler-mode cross-check {@code logPoolerModeCrossCheck} WARNs whenever a non-zero
 * prepared-statement cache is configured under any non-{@code SESSION} pooler mode — not only under {@code TRANSACTION}.
 * <p>
 * The dangerous case is {@code DIRECT} (or unset) pooling with a populated prepared-statement cache: it is safe
 * <em>today</em>, but a future flip to {@code TRANSACTION} pooling would silently break the named prepared statements.
 * </p>
 * <p>
 * Fully offline: {@code logPoolerModeCrossCheck} only inspects config and logs; it never opens a connection. WARN/INFO
 * lines are captured via a logback {@link ListAppender} attached to the {@code ConnectionPoolFactory} logger.
 * </p>
 */
public final class ConnectionPoolFactoryPoolerModeLogTest {

    private Logger logger;
    private Level originalLevel;
    private ListAppender<ILoggingEvent> appender;

    @Before
    public void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(ConnectionPoolFactory.class);
        // The shared logback-test.xml roots at WARN; raise this one logger to INFO so the safe-case INFO line is
        // captured too, then restore in @After.
        originalLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @After
    public void detachAppender() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(originalLevel);
    }

    private static PostgresConfig configWith(final String poolerMode, final int cacheQueries) {
        final Config config = ConfigFactory.parseString(
                "ditto.postgresql.uri = \"r2dbc:postgresql://localhost:5432/ditto\"\n"
                        + "ditto.postgresql.ssl.mode = \"disable\"\n"
                        + "ditto.postgresql.pooler-mode = \"" + poolerMode + "\"\n"
                        + "ditto.postgresql.pool.prepared-statement-cache-queries = " + cacheQueries + "\n");
        return DefaultPostgresConfig.of(config.getConfig("ditto"));
    }

    private List<ILoggingEvent> events() {
        return appender.list;
    }

    private ILoggingEvent onlyEvent() {
        final List<ILoggingEvent> events = events();
        assertThat(events).hasSize(1);
        return events.get(0);
    }

    @Test
    public void directModeWithPopulatedCacheWarnsAboutFutureTransactionFlip() {
        // The L-12 failing case: DIRECT pooling is safe TODAY with a named-statement cache, but a future flip to
        // TRANSACTION pooling would break it — that must be a WARN, not silence.
        final PostgresConfig config = configWith("direct", 256);

        ConnectionPoolFactory.logPoolerModeCrossCheck(config);

        final ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).as("DIRECT + populated cache must WARN").isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage().toLowerCase())
                .as("WARN conveys a future flip to TRANSACTION pooling would break this prepared-statement cache")
                .contains("transaction")
                .contains("256");
    }

    @Test
    public void transactionModeWithPopulatedCacheStillWarns() {
        final PostgresConfig config = configWith("transaction", 256);

        ConnectionPoolFactory.logPoolerModeCrossCheck(config);

        final ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).as("TRANSACTION + populated cache must WARN").isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).contains("256");
    }

    @Test
    public void transactionModeWithZeroCacheLogsInfo() {
        final PostgresConfig config = configWith("transaction", 0);

        ConnectionPoolFactory.logPoolerModeCrossCheck(config);

        final ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).as("TRANSACTION + 0 is the safe case -> INFO").isEqualTo(Level.INFO);
    }

    @Test
    public void sessionModeWithPopulatedCacheDoesNotWarn() {
        // SESSION pooling pins a server connection per client, so a named-statement cache is safe and stays safe.
        final PostgresConfig config = configWith("session", 256);

        ConnectionPoolFactory.logPoolerModeCrossCheck(config);

        assertThat(events()).noneMatch(e -> e.getLevel() == Level.WARN);
    }
}
