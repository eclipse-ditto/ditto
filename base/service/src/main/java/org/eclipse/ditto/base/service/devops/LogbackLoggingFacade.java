/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.service.devops;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import javax.annotation.Nonnull;

import org.eclipse.ditto.base.api.devops.ImmutableLoggerConfig;
import org.eclipse.ditto.base.api.devops.LogLevel;
import org.eclipse.ditto.base.api.devops.LoggerConfig;
import org.eclipse.ditto.base.api.devops.LoggingFacade;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

/**
 * Implementation of {@link org.eclipse.ditto.base.api.devops.LoggingFacade} for logback.
 */
public final class LogbackLoggingFacade implements LoggingFacade {

    private LogbackLoggingFacade() {
        // instantiation only via newInstance()
    }

    /**
     * Returns a new {@code LogbackLoggingFacade} instance.
     *
     * @return the LogbackLoggingFacade instance.
     */
    public static LogbackLoggingFacade newInstance() {
        return new LogbackLoggingFacade();
    }

    @Override
    public boolean setLogLevel(@Nonnull final LoggerConfig loggerConfig) {
        final Level level = Level.valueOf(loggerConfig.getLevel().getIdentifier());
        final String loggerName = loggerConfig.getLogger().orElse(org.slf4j.Logger.ROOT_LOGGER_NAME);
        final Logger logger = (Logger) LoggerFactory.getLogger(loggerName);

        logger.setLevel(level);

        return logger.isEnabledFor(level);
    }

    @Override
    @Nonnull
    public List<LoggerConfig> getLoggerConfig(final boolean includeDisabledLoggers) {
        final Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        final List<Logger> loggerList = rootLogger.getLoggerContext().getLoggerList();

        return loggerConfigsFor(loggerList, includeDisabledLoggers);
    }

    @Override
    @Nonnull
    public List<LoggerConfig> getLoggerConfig(@Nonnull final Iterable<String> loggerNames) {
        final List<Logger> loggerList = StreamSupport.stream(loggerNames.spliterator(), false)
                .map(logger -> (Logger) LoggerFactory.getLogger(logger))
                .toList();

        return loggerConfigsFor(loggerList, false);
    }

    private List<LoggerConfig> loggerConfigsFor(final Iterable<Logger> loggers, final boolean includeDisabledLoggers) {
        final List<LoggerConfig> loggerConfigList = new ArrayList<>();

        loggers.forEach(logger -> {
            final Level level = Optional.ofNullable(logger.getLevel()).orElse(Level.OFF);
            final Optional<LogLevel> logLevelOptional = LogLevel.forIdentifier(level.toString());
            logLevelOptional
                    .filter(logLevel -> {
                        if (includeDisabledLoggers) {
                            return true;
                        } else {
                            // filter out the "off" loggers
                            return !logLevel.equals(LogLevel.OFF);
                        }
                    })
                    .ifPresent(logLevel -> loggerConfigList.add(ImmutableLoggerConfig.of(logLevel, logger.getName())));
        });

        return loggerConfigList;
    }

}
