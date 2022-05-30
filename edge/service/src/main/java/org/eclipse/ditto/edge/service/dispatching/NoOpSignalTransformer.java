/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.edge.service.dispatching;

import java.util.function.UnaryOperator;

import org.eclipse.ditto.base.model.signals.Signal;

import akka.actor.ActorSystem;

public class NoOpSignalTransformer implements SignalTransformer {

    /**
     * @param actorSystem the actor system in which to load the extension.
     */
    protected NoOpSignalTransformer(final ActorSystem actorSystem) {
    }

    @Override
    public Signal<?> apply(final Signal<?> signal) {
        return UnaryOperator.<Signal<?>>identity().apply(signal);
    }

}
