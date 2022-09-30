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
package org.eclipse.ditto.base.api.devops;

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
    List<LoggerConfig> getLoggerConfig(boolean includeDisabledLoggers);

    /**
     * Returns the {@code LoggerConfig}s for the specified {@code loggerNames}.
     *
     * @param loggerNames the logger names.
     * @return the LoggerConfigs for the specified logger names.
     */
    List<LoggerConfig> getLoggerConfig(Iterable<String> loggerNames);
}
