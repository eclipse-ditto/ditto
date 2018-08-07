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
     *
     * @param message the message.
     */
    void apply(T message);

    /**
     * Extends the {@link ReceiveStrategy} interface with a {@code defined} function that returns {@code true} if the
     * strategy is defined for the given message.
     *
     * @param <T> type of the class this strategy matches against.
     */
    interface WithDefined<T> extends ReceiveStrategy<T> {

        /**
         * Determines whether this strategy get applied or not.
         *
         * @param message the message
         * @return {@code true} if the strategy is defined for the given message
         */
        default boolean isDefined(final T message) {
            return true;
        }

    }

    /**
     * Extends the {@link ReceiveStrategy.WithDefined} interface with an {@code unhandled} function that is called if
     * the strategy is not defined for the given message.
     *
     * @param <T> type of the class this strategy matches against.
     */
    interface WithUnhandledFunction<T> extends WithDefined<T> {

        /**
         * Function to perform if the {@link ReceiveStrategy.WithDefined#isDefined} evaluated to {@code false}.
         *
         * @param message the message.
         */
        void unhandled(T message);

    }

}
