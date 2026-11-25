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

import com.typesafe.config.ConfigFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Unit test for the startup cross-check {@code ConnectionPoolFactory.logAcquireBudgetCrossCheck}: one INFO line with
 * the effective overload settings, plus a WARN for each setting an operator can break through an env override —
 * {@code acquire-retry > 0}, {@code connect-timeout} not below {@code max-acquire-time}, and an unbounded
 * pending-acquire queue.
 * <p>
 * Fully offline: the method only inspects config and logs. Log lines are captured via a logback {@link ListAppender}
 * on the {@code ConnectionPoolFactory} logger.
 * </p>
 */
public final class ConnectionPoolFactoryAcquireBudgetLogTest {

    private Logger logger;
    private Level originalLevel;
    private ListAppender<ILoggingEvent> appender;

    @Before
    public void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(ConnectionPoolFactory.class);
        // logback-test.xml roots at WARN; raise this one logger to INFO so the summary line is captured too.
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

    private static PostgresConfig configWith(final String postgresqlBlock) {
        return DefaultPostgresConfig.of(ConfigFactory.parseString("postgresql {\n" + postgresqlBlock + "\n}"));
    }

    private List<ILoggingEvent> warnings() {
        return appender.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
    }

    private List<ILoggingEvent> infos() {
        return appender.list.stream().filter(e -> e.getLevel() == Level.INFO).toList();
    }

    @Test
    public void defaultsLogTheEffectiveBudgetAndNoWarning() {
        ConnectionPoolFactory.logAcquireBudgetCrossCheck(configWith(""));

        assertThat(warnings()).as("the shipped defaults are consistent").isEmpty();
        assertThat(infos()).hasSize(1);
        assertThat(infos().get(0).getFormattedMessage())
                .as("summary names the effective acquire budget and the pending bound")
                .contains("effective acquire budget=4000ms")
                .contains("max-size=25, pending-acquire-max-size=50")
                .contains("connect-timeout=2000ms");
    }

    @Test
    public void acquireRetryAboveZeroWarnsWithTheMultipliedEffectiveBudget() {
        ConnectionPoolFactory.logAcquireBudgetCrossCheck(configWith("pool.acquire-retry = 1"));

        assertThat(infos()).hasSize(1);
        assertThat(infos().get(0).getFormattedMessage())
                .as("each acquire retry re-runs the whole max-acquire-time budget")
                .contains("effective acquire budget=8000ms");
        assertThat(warnings())
                .as("acquire-retry > 0 multiplies the budget past the callers' 5s deadline -> WARN")
                .hasSize(1);
        assertThat(warnings().get(0).getFormattedMessage())
                .contains("acquire-retry=1")
                .contains("effective acquire budget=8000ms")
                .contains("5s");
    }

    @Test
    public void connectTimeoutNotBelowMaxAcquireTimeWarns() {
        ConnectionPoolFactory.logAcquireBudgetCrossCheck(configWith("connect-timeout = 5s"));

        assertThat(warnings()).hasSize(1);
        assertThat(warnings().get(0).getFormattedMessage())
                .contains("connect-timeout=5000ms")
                .contains("max-acquire-time=4000ms");
    }

    @Test
    public void unboundedPendingQueueWarns() {
        ConnectionPoolFactory.logAcquireBudgetCrossCheck(configWith("pool.pending-acquire-max-size = -1"));

        assertThat(warnings()).hasSize(1);
        assertThat(warnings().get(0).getFormattedMessage()).contains("unbounded");
    }

}
