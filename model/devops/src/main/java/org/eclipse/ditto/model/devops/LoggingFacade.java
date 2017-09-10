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
package org.eclipse.ditto.model.devops;

import java.util.List;

/**
 * Abstraction for logging implementations.
 */
public interface LoggingFacade {

    /**
     * Sets the {@code LogLevel} specified in the given {@code loggerConfig}.
     *
     * @param loggerConfig the LoggerConfig.
     * @return whether or not the LogLevel is applied successfully.
     */
    boolean setLogLevel(LoggerConfig loggerConfig);

    /**
     * Returns the {@code LoggerConfig} for all known loggers.
     *
     * @return the LoggerConfig for all known loggers.
     */
    List<LoggerConfig> getLoggerConfig();

    /**
     * Returns the {@code LoggerConfig}s for the specified {@code loggerNames}.
     *
     * @param loggerNames the logger names.
     * @return the LoggerConfigs for the specified logger names.
     */
    List<LoggerConfig> getLoggerConfig(Iterable<String> loggerNames);
}
