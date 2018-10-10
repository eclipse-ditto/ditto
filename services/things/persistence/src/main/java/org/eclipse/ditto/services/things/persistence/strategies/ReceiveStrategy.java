/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.things.persistence.strategies;

import java.util.function.Consumer;

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
     * Create a simple receive strategy.
     *
     * @param clazz the class to match on.
     * @param consumer the message handler.
     * @param <T> type of messages handled.
     * @return a simple receive strategy.
     */
    static <T> ReceiveStrategy<T> simple(final Class<T> clazz, final Consumer<T> consumer) {
        return new SimpleReceiveStrategy<>(clazz, consumer);
    }

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

    /**
     * A simple receive strategy handling messages of a type via a consumer.
     *
     * @param <T> type of messages handled.
     */
    final class SimpleReceiveStrategy<T> implements ReceiveStrategy<T> {

        private final Class<T> clazz;
        private final Consumer<T> handler;

        private SimpleReceiveStrategy(final Class<T> clazz, final Consumer<T> handler) {
            this.clazz = clazz;
            this.handler = handler;
        }

        @Override
        public Class<T> getMatchingClass() {
            return clazz;
        }

        @Override
        public void apply(final T message) {
            handler.accept(message);
        }
    }

}
