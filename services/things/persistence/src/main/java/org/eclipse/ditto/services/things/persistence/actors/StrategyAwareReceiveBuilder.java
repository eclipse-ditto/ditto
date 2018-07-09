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

import java.util.function.Consumer;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import akka.actor.AbstractActor;
import akka.japi.pf.PFBuilder;
import akka.japi.pf.ReceiveBuilder;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

/**
 * A simple wrapper for {@link ReceiveBuilder} which accepts {@link ReceiveStrategy} objects.
 */
@NotThreadSafe
final class StrategyAwareReceiveBuilder {

    private final ReceiveBuilder delegationTarget;
    private PartialFunction<Object, Object> peekStep;

    /**
     * Constructs a new {@code StrategyAwareReceiveBuilder} object.
     */
    StrategyAwareReceiveBuilder() {
        delegationTarget = ReceiveBuilder.create();
        peekStep = null;
    }

    StrategyAwareReceiveBuilder(final ReceiveBuilder receiveBuilder) {
        this.delegationTarget = receiveBuilder;
        peekStep = null;
    }

    /**
     * Adds a new case strategy to this builder.
     *
     * @param <T> type of the class the specified strategy matches against.
     * @param strategy a strategy which provides an action to apply to the argument if the type matches and the
     * predicate returns {@code true}.
     * @return this builder with the strategy added.
     */
    <T> StrategyAwareReceiveBuilder match(final ReceiveStrategy<T> strategy) {
        if (strategy instanceof ReceiveStrategy.WithUnhandledFunction) {
            final ReceiveStrategy.WithUnhandledFunction<T> withUnhandled =
                    (ReceiveStrategy.WithUnhandledFunction<T>) strategy;
            delegationTarget.match(strategy.getMatchingClass(), withUnhandled::isDefined, withUnhandled::apply);
            delegationTarget.match(strategy.getMatchingClass(), withUnhandled::unhandled);
        }
        if (strategy instanceof ReceiveStrategy.WithDefined) {
            final ReceiveStrategy.WithDefined<T> withDefined = (ReceiveStrategy.WithDefined<T>) strategy;
            delegationTarget.match(withDefined.getMatchingClass(), withDefined::isDefined, withDefined::apply);
        } else {
            delegationTarget.match(strategy.getMatchingClass(), strategy::apply);
        }
        return this;
    }

    /**
     * Add a new case strategy to this builder, that matches any argument.
     *
     * @param strategy a strategy which provides an action to apply to the argument.
     * @return this builder with the strategy added.
     */
    StrategyAwareReceiveBuilder matchAny(final ReceiveStrategy<Object> strategy) {
        delegationTarget.matchAny(strategy::apply);
        return this;
    }

    /**
     * Sets a consumer to peek at messages before they are processed. This method replaces a beforehand set consumer.
     *
     * @param peekingConsumer the consumer which peeks at messages.
     * @return this builder with the peeking consumer set.
     */
    StrategyAwareReceiveBuilder setPeekConsumer(@Nullable final Consumer<Object> peekingConsumer) {
        if (null == peekingConsumer) {
            peekStep = null;
        } else {
            peekStep = new PFBuilder<>()
                    .matchAny(message -> {
                        peekingConsumer.accept(message);
                        return message;
                    })
                    .build();
        }

        return this;
    }

    /**
     * Builds a {@link PartialFunction} from this builder. After this call the builder will be reset.
     *
     * @return a PartialFunction from this builder.
     */
    public AbstractActor.Receive build() {
        return applyPeekStepIfSet(delegationTarget.build());
    }

    private AbstractActor.Receive applyPeekStepIfSet(final AbstractActor.Receive receive) {
        if (null != peekStep) {
            final PartialFunction<Object, BoxedUnit> onMessage = peekStep.andThen(receive.onMessage());
            return new AbstractActor.Receive(onMessage);
        }
        return receive;
    }

}
