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
package org.eclipse.ditto.services.things.persistence.actors;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.services.things.persistence.strategies.DelegateStrategy;
import org.eclipse.ditto.services.things.persistence.strategies.ReceiveStrategy;

import akka.actor.AbstractActor;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.PFBuilder;
import akka.japi.pf.ReceiveBuilder;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

/**
 * A simple wrapper for {@link ReceiveBuilder} which accepts
 * {@link org.eclipse.ditto.services.things.persistence.strategies.ReceiveStrategy} objects.
 */
@NotThreadSafe
final class StrategyAwareReceiveBuilder {

    private final ReceiveBuilder delegationTarget;
    private final DiagnosticLoggingAdapter logger;
    private final Map<Class<?>, ReceiveStrategy> strategies;
    @Nullable private PartialFunction<Object, Object> peekStep;
    @Nullable private ReceiveStrategy<Object> matchAny;

    /**
     * Constructs a new {@code StrategyAwareReceiveBuilder} object.
     */
    StrategyAwareReceiveBuilder(final DiagnosticLoggingAdapter theLogger) {
        this(ReceiveBuilder.create(), theLogger);
    }

    StrategyAwareReceiveBuilder(final ReceiveBuilder receiveBuilder, final DiagnosticLoggingAdapter theLogger) {
        delegationTarget = receiveBuilder;
        logger = theLogger;
        strategies = new HashMap<>();
        peekStep = null;
        matchAny = null;
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
        if (strategies.containsKey(strategy.getMatchingClass())) {
            throw new IllegalArgumentException("Strategy for type <" + strategy.getMatchingClass() + "> already exists!");
        }
        strategies.put(strategy.getMatchingClass(), strategy);
        return this;
    }

    /**
     * Adds all given new case strategies to this builder.
     *
     * @param strategies the strategies which provide an action to apply to the argument if the type matches and the
     * predicate returns {@code true}.
     * @return this builder with the strategies added.
     */
    StrategyAwareReceiveBuilder matchEach(final Iterable<ReceiveStrategy<?>> strategies) {
        checkNotNull(strategies, "strategies to be matched");
        for (final ReceiveStrategy<?> strategy : strategies) {
            match(strategy);
        }
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
     * Sets a consumer that is called for any message. There can only be one such consumer.
     *
     * @param strategy the strategy that should ne applied for any message
     * @return this builder.
     */
    StrategyAwareReceiveBuilder matchAny(final ReceiveStrategy<Object> strategy) {
        checkNotNull(strategy, "consumer");
        if (matchAny != null) {
            throw new IllegalArgumentException("Only one matchAny consumer allowed.");
        }
        matchAny = strategy;
        return this;
    }

    /**
     * Builds a {@link PartialFunction} from this builder. After this call the builder will be reset.
     *
     * @return a PartialFunction from this builder.
     */
    public AbstractActor.Receive build() {
        final DelegateStrategy delegateStrategy = new DelegateStrategy(strategies, logger);
        delegationTarget.match(delegateStrategy.getMatchingClass(), delegateStrategy::isDefined,
                delegateStrategy::apply);
        if (matchAny != null) {
            delegationTarget.matchAny(matchAny::apply);
        }
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
