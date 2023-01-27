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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.service.signaltransformer.SignalTransformer;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CreateConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.ModifyConnection;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Transforms a ModifyConnection into a CreateConnection if the connection does not exist already.
 */
public final class ModifyToCreateConnectionTransformer implements SignalTransformer {

    private final ConnectionExistenceChecker existenceChecker;

    @SuppressWarnings("unused")
    ModifyToCreateConnectionTransformer(final ActorSystem actorSystem, final Config config) {
        this(new ConnectionExistenceChecker(actorSystem));
    }

    ModifyToCreateConnectionTransformer(final ConnectionExistenceChecker existenceChecker) {
        this.existenceChecker = existenceChecker;
    }

    @Override
    public CompletionStage<Signal<?>> apply(final Signal<?> signal) {
        if (signal instanceof ModifyConnection modifyConnection) {
            return existenceChecker.checkExistence(modifyConnection)
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
            return CompletableFuture.completedStage(signal);
        }
    }

}
