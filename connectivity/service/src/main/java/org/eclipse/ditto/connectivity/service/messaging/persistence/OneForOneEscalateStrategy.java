/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.persistence;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkArgument;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.actor.ChildRestartStats;
import akka.actor.SupervisorStrategy;
import scala.PartialFunction;
import scala.collection.Iterable;

/**
 * Implementation of {@link SupervisorStrategy}, which restarts supervised actors {@code maxRetries} times and
 * afterwards escalates failures. This stands in contrast to the original {@link akka.actor.OneForOneStrategy}
 * which stops instead of escalating.
 */
final class OneForOneEscalateStrategy extends SupervisorStrategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(OneForOneEscalateStrategy.class);

    private final int maxRetries;
    private int currentRetries = 0;

    private OneForOneEscalateStrategy(final int maxRetries) {
        this.maxRetries = maxRetries;
    }

    /**
     * Create a supervisor strategy which tries to restart failing children {@code maxRetries} and afterwards
     * will escalate errors.
     *
     * @param maxRetries how often a failing child should be restarted.
     * @throws IllegalArgumentException if the passed in {@code maxRetries} is negative.
     * @return the strategy.
     */
    static OneForOneEscalateStrategy withRetries(final int maxRetries) {
        checkArgument(maxRetries, max -> max >= 0, () -> "Maximum retries must not be negative.");
        return new OneForOneEscalateStrategy(maxRetries);
    }

    /**
     * Create a supervisor strategy which immediately escalates when a child fails.
     *
     * @return the strategy.
     */
    static OneForOneEscalateStrategy escalateStrategy() {
        return withRetries(0);
    }

    @Override
    public void handleChildTerminated(final akka.actor.ActorContext context, final ActorRef child,
            final Iterable<ActorRef> children) {
        LOGGER.debug("Child <{}> terminated. Ignoring. Remaining children: {}", child, children);
    }

    @Override
    public PartialFunction<Throwable, Directive> decider() {
        return PartialFunction.fromFunction(throwable -> {
            if (currentRetries >= maxRetries) {
                LOGGER.info("Child failed <{}> times, which exceeds the maximum allowed number of <{}> failures." +
                        " Will escalate the failure.", currentRetries, maxRetries, throwable);
                return (Directive) SupervisorStrategy.escalate();
            }
            LOGGER.info("Child failed <{}> times, which is less than the maximum allowed number of <{}> failures." +
                    " Will restart the child.", currentRetries, maxRetries, throwable);
            return (Directive) SupervisorStrategy.restart();
        });
    }

    @Override
    public void processFailure(final akka.actor.ActorContext context, final boolean restart,
            final ActorRef child,
            final Throwable cause, final ChildRestartStats stats, final Iterable<ChildRestartStats> children) {
        // ignoring the arguments, because #decider will either escalate and this code is never reached, or it
        // will use the restart directive, which will call this method with restart = true.
        ++currentRetries;
        LOGGER.debug("Restarting child <{}>", child);
        restartChild(child, cause, false);
    }

}
