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
package org.eclipse.ditto.services.policies.persistence.actors.policies;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.services.policies.persistence.actors.AbstractReceiveStrategy;
import org.eclipse.ditto.services.policies.persistence.actors.policy.PolicyPersistenceActor;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;

import akka.actor.ActorContext;
import akka.event.DiagnosticLoggingAdapter;

/**
 * Abstract extension of {@link AbstractReceiveStrategy}. This implementation is meant to reduce redundancy. It
 * facilitates writing strategies for {@link PolicyCommand}s.
 *
 * @param <T> type of the class this strategy matches against.
 */
@NotThreadSafe
abstract class AbstractReceivePolicyCommandStrategy<T extends PolicyCommand> extends AbstractReceiveStrategy<T> {

    private final ActorContext actorContext;

    /**
     * Constructs a new {@code AbstractReceiveThingCommandStrategy} object.
     *
     * @param theMatchingClass the class of the command this strategy reacts to.
     * @param context the Actor context.
     * @param loggingAdapter the adapter which is used for logging. {@link PolicyPersistenceActor}s.
     */
    AbstractReceivePolicyCommandStrategy(final Class<T> theMatchingClass, final ActorContext context,
            final DiagnosticLoggingAdapter loggingAdapter) {
        super(theMatchingClass, loggingAdapter);
        actorContext = context;
    }

    @Override
    protected void doApply(final T command) {
        doApply(command, actorContext);
    }

    protected abstract void doApply(T command, ActorContext context);

    /**
     * Tries to forward the specified command to the
     * {@link PolicyPersistenceActor} for the Policy whose ID is
     * provided by the command. If no actor for that Policy ID can be found a warning is logged.
     *
     * @param command the command to be forwarded but also to provide the Policy ID for finding the associated Policy
     * Persistence Actor.
     */
    void forwardToPolicyPersistenceActor(final PolicyCommand command) {
        PolicyPersistenceActor.getShardRegion(actorContext.system()).forward(command, actorContext);
    }
}
