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
package org.eclipse.ditto.base.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.helpers.NOPAppender;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.status.Status;
import net.logstash.logback.appender.LogstashTcpSocketAppender;

/**
 * Verifies that the {@code logback.xml} of a service is picked up by logback without complaints and that its
 * conditional blocks attach the expected appenders to the root logger.
 * <p>
 * Logback removed the Janino backed {@code <if condition="..."/>} attribute; a configuration still using it is parsed
 * without failure but silently evaluates to no branch at all, leaving every conditionally defined appender undefined.
 * These tests fail loudly on that - and on a conditional which is wired to the wrong branch.
 */
public abstract class AbstractServiceLogbackConfigurationTest {

    private static final String CONFIGURATION_RESOURCE_NAME = "logback.xml";

    private static final String DISABLE_SYSOUT_LOG = "DITTO_LOGGING_DISABLE_SYSOUT_LOG";
    private static final String LOGSTASH_SERVER = "DITTO_LOGGING_LOGSTASH_SERVER";
    private static final String FILE_APPENDER = "DITTO_LOGGING_FILE_APPENDER";
    private static final String FILE_NAME_PATTERN = "DITTO_LOGGING_FILE_NAME_PATTERN";

    private static final List<String> MANAGED_PROPERTIES =
            List.of(DISABLE_SYSOUT_LOG, LOGSTASH_SERVER, FILE_APPENDER, FILE_NAME_PATTERN);

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private LoggerContext loggerContext;

    /**
     * Returns the value of the {@code appname} field with which this service tags its JSON log events. Asserting on it
     * makes sure that each sub-class really exercises the {@code logback.xml} of its own module.
     *
     * @return the expected app name.
     */
    protected abstract String getExpectedAppName();

    @After
    public void tearDown() {
        MANAGED_PROPERTIES.forEach(System::clearProperty);
        if (null != loggerContext) {
            loggerContext.stop();
        }
    }

    @Test
    public void configurationBelongsToExpectedService() throws IOException {
        assertThat(readConfiguration()).contains("\"appname\":\"" + getExpectedAppName() + "\"");
    }

    @Test
    public void defaultConfigurationLogsToConsoleOnly() {
        configure();

        assertNoProblems();
        assertRootAppenders(ConsoleAppender.class, ConsoleAppender.class, NOPAppender.class, NOPAppender.class);
    }

    @Test
    public void disablingSysoutLogReplacesStdoutByNopAppender() {
        System.setProperty(DISABLE_SYSOUT_LOG, "true");

        configure();

        assertNoProblems();
        assertRootAppenders(NOPAppender.class, ConsoleAppender.class, NOPAppender.class, NOPAppender.class);
    }

    @Test
    public void definedLogstashServerActivatesLogstashAppender() {
        System.setProperty(LOGSTASH_SERVER, "localhost:5044");

        configure();

        // deliberately no assertion on the status list: the appender connects asynchronously and would race with it
        assertRootAppenders(ConsoleAppender.class, ConsoleAppender.class, LogstashTcpSocketAppender.class,
                NOPAppender.class);
    }

    @Test
    public void enabledFileAppenderActivatesRollingFileAppender() throws IOException {
        System.setProperty(FILE_APPENDER, "true");
        System.setProperty(FILE_NAME_PATTERN,
                temporaryFolder.newFolder().toPath().resolve("service.log.%d{yyyy-MM-dd}.gz").toString());

        configure();

        assertNoProblems();
        assertRootAppenders(ConsoleAppender.class, ConsoleAppender.class, NOPAppender.class,
                RollingFileAppender.class);
    }

    private void configure() {
        loggerContext = new LoggerContext();
        final JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(loggerContext);
        try {
            configurator.doConfigure(getConfigurationUrl());
        } catch (final JoranException e) {
            throw new IllegalStateException("Failed to configure logback.", e);
        }
    }

    private void assertNoProblems() {
        final List<String> problems = loggerContext.getStatusManager().getCopyOfStatusList().stream()
                .filter(status -> Status.WARN <= status.getEffectiveLevel())
                .map(Status::toString)
                .toList();

        assertThat(problems).as("warnings and errors reported while configuring logback").isEmpty();
    }

    private void assertRootAppenders(final Class<?> stdout, final Class<?> stderr, final Class<?> stash,
            final Class<?> file) {

        assertThat(getRootAppenders())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("STDOUT", stdout, "STDERR", stderr, "stash", stash, "file", file));
    }

    private Map<String, Class<?>> getRootAppenders() {
        final Map<String, Class<?>> result = new LinkedHashMap<>();
        final Logger rootLogger = loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        for (final Iterator<Appender<ILoggingEvent>> it = rootLogger.iteratorForAppenders(); it.hasNext(); ) {
            final Appender<ILoggingEvent> appender = it.next();
            result.put(appender.getName(), appender.getClass());
        }
        return result;
    }

    private static String readConfiguration() throws IOException {
        try (final InputStream inputStream = getConfigurationUrl().openStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static URL getConfigurationUrl() {
        final URL url = Thread.currentThread().getContextClassLoader().getResource(CONFIGURATION_RESOURCE_NAME);
        assertThat(url).as("<%s> on the test classpath", CONFIGURATION_RESOURCE_NAME).isNotNull();
        return url;
    }

}
