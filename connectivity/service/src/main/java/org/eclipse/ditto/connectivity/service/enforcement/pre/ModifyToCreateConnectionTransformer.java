/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.enforcement.pre;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.pattern.Patterns;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.service.signaltransformer.SignalTransformer;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.WithConnectionId;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionNotAccessibleException;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CreateConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.ModifyConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionResponse;
import org.eclipse.ditto.json.JsonFieldSelector;

import com.typesafe.config.Config;

/**
 * Transforms a ModifyConnection into a CreateConnection if the connection does not exist already.
 */
public final class ModifyToCreateConnectionTransformer implements SignalTransformer {

    private static final Duration LOCAL_ASK_TIMEOUT = Duration.ofSeconds(5);

    @SuppressWarnings("unused")
    ModifyToCreateConnectionTransformer(final ActorSystem actorSystem, final Config config) {
    }

    @Override
    public CompletionStage<Signal<?>> apply(final Signal<?> signal, final ActorRef thisRef) {
        if (signal instanceof ModifyConnection modifyConnection) {
            return checkExistence(modifyConnection, thisRef)
                    .thenApply(exists -> {
                        if (Boolean.FALSE.equals(exists)) {
                            return CreateConnection.of(
                                    modifyConnection.getConnection(),
                                    modifyConnection.getDittoHeaders()
                            );
                        } else {
                            return modifyConnection;
                        }
                    });
        } else {
            return CompletableFuture.completedFuture(signal);
        }
    }

    private static CompletionStage<Boolean> checkExistence(final WithConnectionId signal, final ActorRef thisRef) {
        return Patterns.ask(thisRef, RetrieveConnection.of(signal.getEntityId(),
                        JsonFieldSelector.newInstance(Connection.JsonFields.REVISION.getPointer()), DittoHeaders.empty()
                ), LOCAL_ASK_TIMEOUT
        ).thenApply(ModifyToCreateConnectionTransformer::handleRetrieveConnectionResponse);
    }

    private static Boolean handleRetrieveConnectionResponse(final Object response) {
        if (response instanceof RetrieveConnectionResponse) {
            return Boolean.TRUE;
        } else if (response instanceof ConnectionNotAccessibleException) {
            return Boolean.FALSE;
        } else {
            throw new IllegalStateException("expect RetrieveConnectionResponse, got: " + response);
        }
    }

}
