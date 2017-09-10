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

import javax.annotation.concurrent.NotThreadSafe;

import akka.actor.AbstractActor;
import akka.japi.pf.ReceiveBuilder;
import scala.PartialFunction;

/**
 * A simple wrapper for {@link ReceiveBuilder} which accepts {@link ReceiveStrategy} objects.
 */
@NotThreadSafe
public final class StrategyAwareReceiveBuilder {

    private final ReceiveBuilder delegationTarget;

    /**
     * Constructs a new {@code StrategyAwareReceiveBuilder} object.
     */
    public StrategyAwareReceiveBuilder() {
        delegationTarget = ReceiveBuilder.create();
    }

    /**
     * Adds a new case strategy to this builder.
     *
     * @param <T> type of the class the specified strategy matches against.
     * @param strategy a strategy which provides an action to apply to the argument if the type matches and the
     * predicate returns {@code true}.
     * @return this builder with the strategy added.
     */
    public <T> StrategyAwareReceiveBuilder match(final ReceiveStrategy<T> strategy) {
        delegationTarget.match(strategy.getMatchingClass(), strategy.getPredicate(), strategy.getApplyFunction());
        delegationTarget.match(strategy.getMatchingClass(), strategy.getUnhandledFunction());
        return this;
    }

    /**
     * Add a new case strategy to this builder, that matches any argument.
     *
     * @param strategy a strategy which provides an action to apply to the argument.
     * @return this builder with the strategy added.
     */
    public StrategyAwareReceiveBuilder matchAny(final ReceiveStrategy<Object> strategy) {
        delegationTarget.matchAny(strategy.getApplyFunction());
        return this;
    }

    /**
     * Builds a {@link PartialFunction} from this builder. After this call the builder will be reset.
     *
     * @return a PartialFunction from this builder.
     */
    public AbstractActor.Receive build() {
        return delegationTarget.build();
    }
}
