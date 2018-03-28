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
package org.eclipse.ditto.services.authorization.util.enforcement;

import static java.util.Objects.requireNonNull;

import java.util.function.Predicate;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;

import akka.actor.ActorRef;

/**
 * Allows to configure a "pre-enforcement" for the {@link EnforcerActor}. All messages (which have to implement
 * {@link WithDittoHeaders} will be forwarded to the configured forwardee.
 */
public class PreEnforcementConfig {
    private final Predicate<WithDittoHeaders> condition;
    private final ActorRef forwardee;

    /**
     * Constructor.
     * @param condition the condition which checks whether the pre-enforcement is necessary (e.g. if it has already
     * been done).
     * @param forwardee the {@link ActorRef} to forward messages to if the condition is fulfilled.
     */
    public PreEnforcementConfig(final Predicate<WithDittoHeaders> condition, final ActorRef forwardee) {
        this.condition = requireNonNull(condition);
        this.forwardee = requireNonNull(forwardee);
    }

    /**
     * Returns the condition which checks whether the pre-enforcement is necessary (e.g. if it has already
     * been done).
     * @return the condition.
     */
    public Predicate<WithDittoHeaders> getCondition() {
        return condition;
    }

    /**
     * Returns the {@link ActorRef} to forward messages to if the condition is fulfilled.
     * @return the {@link ActorRef}.
     */
    public ActorRef getForwardee() {
        return forwardee;
    }
}
