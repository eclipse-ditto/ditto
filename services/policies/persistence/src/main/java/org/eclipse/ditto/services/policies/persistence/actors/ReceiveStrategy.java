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
package org.eclipse.ditto.services.policies.persistence.actors;

import akka.japi.pf.FI;

/**
 * This interface represents a strategy for received messages in the Thing managing actors.
 *
 * @param <T> type of the class this strategy matches against.
 */
public interface ReceiveStrategy<T> {

    /**
     * Returns the class of the message this strategy reacts to.
     *
     * @return the message class to react to.
     */
    Class<T> getMatchingClass();

    /**
     * Returns a predicate which determines whether this strategy get applied or not.
     *
     * @return a predicate which determines whether this strategy get applied or not.
     */
    default FI.TypedPredicate<T> getPredicate() {
        return t -> true;
    }

    /**
     * Returns the function which applies the message if the predicate evaluated to {@code true}.
     *
     * @return the function which applies the message.
     * @see #getPredicate()
     */
    FI.UnitApply<T> getApplyFunction();

    /**
     * Returns the function to perform if the predicate evaluated to {@code false}.
     *
     * @return the function to be performed as this strategy is not applicable.
     * @see #getPredicate()
     */
    FI.UnitApply<T> getUnhandledFunction();
}
