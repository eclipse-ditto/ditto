/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.devops;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.Nonnull;

import org.eclipse.ditto.model.devops.ImmutableLoggerConfig;
import org.eclipse.ditto.model.devops.LogLevel;
import org.eclipse.ditto.model.devops.LoggerConfig;
import org.eclipse.ditto.model.devops.LoggingFacade;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

/**
 * Implementation of {@link LoggingFacade} for logback.
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
        final String loggerName = loggerConfig.getLogger().orElse(Logger.ROOT_LOGGER_NAME);
        final Logger logger = (Logger) LoggerFactory.getLogger(loggerName);

        logger.setLevel(level);

        return logger.isEnabledFor(level);
    }

    @Override
    @Nonnull
    public List<LoggerConfig> getLoggerConfig() {
        final Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        final List<Logger> loggerList = rootLogger.getLoggerContext().getLoggerList();

        return loggerConfigsFor(loggerList);
    }

    @Override
    @Nonnull
    public List<LoggerConfig> getLoggerConfig(@Nonnull final Iterable<String> loggerNames) {
        final List<Logger> loggerList = StreamSupport.stream(loggerNames.spliterator(), false) //
                .map(logger -> (Logger) LoggerFactory.getLogger(logger)) //
                .collect(Collectors.toList());

        return loggerConfigsFor(loggerList);
    }

    private List<LoggerConfig> loggerConfigsFor(final Iterable<Logger> loggers) {
        final List<LoggerConfig> loggerConfigList = new ArrayList<>();

        loggers.forEach(logger -> {
            final Level level = Optional.ofNullable(logger.getLevel()).orElse(Level.OFF);
            final Optional<LogLevel> logLevelOptional = LogLevel.forIdentifier(level.toString());
            logLevelOptional
                    .filter(logLevel -> !logLevel.equals(LogLevel.OFF)) // filter out the "off" loggers
                    .ifPresent(logLevel -> loggerConfigList.add(ImmutableLoggerConfig.of(logLevel, logger.getName())));
        });

        return loggerConfigList;
    }
}
