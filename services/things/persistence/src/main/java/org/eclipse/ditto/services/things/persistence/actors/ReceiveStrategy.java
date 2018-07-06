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
package org.eclipse.ditto.services.things.persistence.actors;

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
     * Apply the strategy to the given message.
     */
    void apply(T message);


    interface WithDefined<T> extends ReceiveStrategy<T> {

        /**
         * predicate which determines whether this strategy get applied or not.
         */
        default boolean isDefined(T message) {
            return true;
        }
    }

    interface WithUnhandledFunction<T> extends WithDefined<T> {

        /**
         * function to perform if the predicate evaluated to {@code false}.
         */
        void unhandled(T message);
    }
}
