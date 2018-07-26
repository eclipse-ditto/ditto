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
package org.eclipse.ditto.services.things.persistence.strategies;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.base.Command;

import akka.event.DiagnosticLoggingAdapter;

/**
 * This {@link ReceiveStrategy} provides already an implementation of {@link #getMatchingClass()}
 * The behaviour of the "apply function" has to be implemented by subclasses.
 *
 * @param <T> type of the class this strategy matches against.
 */
@NotThreadSafe
public abstract class AbstractReceiveStrategy<T> implements ReceiveStrategy<T> {

    private final Class<T> matchingClass;
    protected final DiagnosticLoggingAdapter logger;

    /**
     * Constructs a new {@code AbstractReceiveStrategy} object.
     *
     * @param theMatchingClass the class of the message this strategy reacts to.
     * @param theLogger the logger to use for logging.
     * @throws NullPointerException if {@code theMatchingClass} is {@code null}.
     */
    protected AbstractReceiveStrategy(final Class<T> theMatchingClass, final DiagnosticLoggingAdapter theLogger) {
        matchingClass = checkNotNull(theMatchingClass, "matching class");
        logger = checkNotNull(theLogger, "logger");
    }

    @Override
    public Class<T> getMatchingClass() {
        return matchingClass;
    }

    @Override
    public void apply(final T message) {
        preApply(message);
    }

    protected void preApply(final T message) {
        if (message instanceof Command) {
            final Command command = (Command) message;
            LogUtil.enhanceLogWithCorrelationId(logger, command.getDittoHeaders().getCorrelationId());
            if (logger.isDebugEnabled()) {
                logger.debug("Applying command <{}>: {}", command.getType(), command.toJsonString());
            }
        }
        doApply(message);
    }

    protected abstract void doApply(T message);

}
