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
package org.eclipse.ditto.connectivity.service.messaging.persistence;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.TestConnectionResponse;

import akka.actor.ActorSystem;

public final class AlwaysFailingUpdatedConnectionTester implements UpdatedConnectionTester{

    private AlwaysFailingUpdatedConnectionTester(final ActorSystem actorSystem) {}

    @Override
    public CompletionStage<Optional<TestConnectionResponse>> testConnection(final Connection updatedConnection,
            final DittoHeaders dittoHeaders) {
        return CompletableFuture.completedStage(Optional.empty());
    }
}
