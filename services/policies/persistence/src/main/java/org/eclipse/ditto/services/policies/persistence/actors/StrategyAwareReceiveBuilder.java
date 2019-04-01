/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.policies.persistence.actors;

import java.util.function.Consumer;

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
     * Add a simple message handler for a dedicated message class.
     *
     * @param clazz the class of handled messages.
     * @param handler the handler.
     * @param <T> the type of handled messages.
     * @return this builder.
     */
    public <T> StrategyAwareReceiveBuilder match(final Class<T> clazz, final Consumer<T> handler) {
        delegationTarget.match(clazz, handler::accept);
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
