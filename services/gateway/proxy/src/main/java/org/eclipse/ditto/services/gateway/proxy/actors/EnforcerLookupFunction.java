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
package org.eclipse.ditto.services.gateway.proxy.actors;

import java.util.concurrent.CompletionStage;

/**
 * Implementations of this interface provide a function for looking up entities specified by an ID and provide a
 * {@link LookupResult}.
 */
@FunctionalInterface
public interface EnforcerLookupFunction {

    /**
     * Performs a enforcer lookup for the given {@code id} and {@code correlationId}.
     *
     * @param id the ID of the entity to lookup.
     * @param correlationId the ID to correlate the lookup.
     * @return a LookupResult containing the shardId and actorRef of the responsible enforcer.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if any argument is empty.
     */
    CompletionStage<LookupResult> lookup(CharSequence id, CharSequence correlationId);

}
