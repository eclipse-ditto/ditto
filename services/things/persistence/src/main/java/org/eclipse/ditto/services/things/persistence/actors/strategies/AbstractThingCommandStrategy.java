/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.things.persistence.actors.strategies;

import java.text.MessageFormat;
import java.time.Instant;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.services.things.persistence.actors.ThingPersistenceActor;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.ThingCommand;

import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.FI;

/**
 * This extension of {@link AbstractReceiveStrategy} is for handling {@link ThingCommand}.
 *
 * @param <T> type of the class this strategy matches against.
 */
@NotThreadSafe
public abstract class AbstractThingCommandStrategy<T extends Command> extends AbstractReceiveStrategy<T> {

    /**
     * Constructs a new {@code AbstractThingCommandStrategy} object.
     *
     * @param theMatchingClass the class of the message this strategy reacts to.
     * @param theLogger the logger to use for logging.
     * @throws NullPointerException if {@code theMatchingClass} is {@code null}.
     */
    AbstractThingCommandStrategy(final Class<T> theMatchingClass, final DiagnosticLoggingAdapter theLogger) {
        super(theMatchingClass, theLogger);
    }

    @Override
    public FI.TypedPredicate<T> getPredicate() {
        return command -> null != thing() && thing().getId()
                .filter(command.getId()::equals)
                .isPresent();
    }

    @Override
    public FI.UnitApply<T> getUnhandledFunction() {
        return command -> {
            throw new IllegalArgumentException(
                    MessageFormat.format(ThingPersistenceActor.UNHANDLED_MESSAGE_TEMPLATE, command.getId()));
        };
    }

    protected static Instant eventTimestamp() {
        return Instant.now();
    }

}
