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
package org.eclipse.ditto.connectivity.service.messaging.internal;

import java.util.Optional;

import javax.annotation.Nullable;

import akka.actor.ActorRef;

/**
 * Abstract base implementation for {@link WithOrigin}.
 */
public abstract class AbstractWithOrigin implements WithOrigin {

    @Nullable private final ActorRef origin;

    protected AbstractWithOrigin(@Nullable final ActorRef origin) {
        this.origin = origin;
    }

    @Override
    public Optional<ActorRef> getOrigin() {
        return Optional.ofNullable(origin);
    }

    @Override
    public String toString() {
        return "origin=" + origin;
    }
}
