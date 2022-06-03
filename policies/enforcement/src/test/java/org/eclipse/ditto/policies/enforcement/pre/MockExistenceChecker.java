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
package org.eclipse.ditto.policies.enforcement.pre;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.signals.Signal;

import akka.actor.ActorSystem;

public final class MockExistenceChecker implements ExistenceChecker{

    public MockExistenceChecker(final ActorSystem actorSystem) {

    }

    @Override
    public CompletionStage<Boolean> checkExistence(final Signal<?> signal) {
        return CompletableFuture.completedStage(Boolean.FALSE);
    }
}
