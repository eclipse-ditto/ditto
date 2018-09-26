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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import akka.event.DiagnosticLoggingAdapter;

/**
 * A generic strategy that holds a mapping class &rarr; strategy and applies the correct strategy for given messages.
 * This avoids the use of orElse() statements which is applied when calling {@link akka.japi.pf.ReceiveBuilder#match}.
 */
public final class DelegateStrategy extends AbstractReceiveStrategy<Object>
        implements ReceiveStrategy.WithDefined<Object> {

    private final Map<Class<?>, ReceiveStrategy> strategies;

    public DelegateStrategy(final Map<Class<?>, ReceiveStrategy> strategies, final DiagnosticLoggingAdapter logger) {
        super(Object.class, logger);
        this.strategies = Collections.unmodifiableMap(new HashMap<>(strategies));
    }

    @Override
    public boolean isDefined(final Object message) {
        return strategies.containsKey(message.getClass());
    }

    @Override
    protected void doApply(final Object message) {
        @SuppressWarnings("unchecked")
        final ReceiveStrategy<Object> receiveStrategy = (ReceiveStrategy<Object>) strategies.get(message.getClass());
        if (receiveStrategy != null) {
            if (isStrategyDefined(receiveStrategy, message)) {
                receiveStrategy.apply(message);
            } else {
                getUnhandledFunction(receiveStrategy).accept(message);
            }
        } else {
            logger.info("No strategy for type <{}> found.", message.getClass());
        }
    }

    private static boolean isStrategyDefined(final ReceiveStrategy<Object> receiveStrategy, final Object message) {
        return !(receiveStrategy instanceof WithDefined) || ((WithDefined<Object>) receiveStrategy).isDefined(message);
    }

    private Consumer<Object> getUnhandledFunction(final ReceiveStrategy<Object> receiveStrategy) {
        if (receiveStrategy instanceof WithUnhandledFunction) {
            return ((WithUnhandledFunction<Object>) receiveStrategy)::unhandled;
        } else {
            return o -> logger.debug("Message of type <{}> was not handled.", o.getClass());
        }
    }

}
