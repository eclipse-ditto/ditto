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
package org.eclipse.ditto.base.service;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.concurrent.Callable;

import javax.annotation.concurrent.Immutable;

import org.slf4j.Logger;

import akka.actor.ActorSystem;

/**
 * This class wraps the execution of a Runnable for catching {@code Throwable}s. The Runnable is assumed to be the
 * body of a {@code main} method. If a Throwable gets caught it will be logged and re-thrown. Catching
 * {@code Throwable} instead of {@code Exception} is done deliberately for being informed about really every
 * exception which is thrown by {@code main} method.
 */
@Immutable
final class MainMethodExceptionHandler {

    /**
     * Pattern of the log message in case a Throwable was caught.
     */
    static final String LOG_MESSAGE_PATTERN = "An exception occurred in main method of <{0}>!";

    private final Logger logger;

    private MainMethodExceptionHandler(final Logger theLogger) {
        logger = theLogger;
    }

    /**
     * Returns an instance of {@code MainMethodExceptionHandler}.
     *
     * @param logger the logger to be used for logging potential exceptions.
     * @return the instance.
     * @throws NullPointerException if {@code logger} is {@code null}.
     */
    public static MainMethodExceptionHandler getInstance(final Logger logger) {
        return new MainMethodExceptionHandler(checkNotNull(logger, "logger"));
    }

    /**
     * Executes the specified Runnable. Occurring {@code Throwables} are logged and re-thrown.
     *
     * @param mainMethodBody the body of the {@code main} method to be executed.
     * @throws NullPointerException if {@code mainMethodBody} is {@code null}.
     */
    public ActorSystem call(final Callable<ActorSystem> mainMethodBody) {
        return tryToRunMainMethodBody(checkNotNull(mainMethodBody, "Runnable to be executed"));
    }

    @SuppressWarnings("squid:S1181")
    private ActorSystem tryToRunMainMethodBody(final Callable<ActorSystem> mainMethodBody) {
        try {
            return mainMethodBody.call();
        } catch (final RuntimeException | Error t) {
            logger.error(MessageFormat.format(LOG_MESSAGE_PATTERN, logger.getName()), t);
            throw t;
        } catch (final Throwable t) {
            // Deliberately catching Throwable here for being informed about really every exception of main.
            logger.error(MessageFormat.format(LOG_MESSAGE_PATTERN, logger.getName()), t);
            throw new RuntimeException("Exception on main thread: " + t.getMessage(), t);
        }
    }

}
