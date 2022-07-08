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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.ModifyConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.TestConnectionResponse;
import org.eclipse.ditto.internal.utils.akka.AkkaClassLoader;

import akka.actor.AbstractExtensionId;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.actor.Extension;

/**
 * Allows testing of an updated connection without the need for authentication/authorization on API level.
 */
public interface UpdatedConnectionTester extends Extension {

    /**
     * Tests the given connection.
     *
     * @param updatedConnection the connection with it's updated content. This connection needs to still have the
     * original connection ID.
     * @param dittoHeaders the ditto headers that should be used for the test connection command
     * @return A completion stage resolving to true, in case the connection could be tested successfully or false if not.
     */
    CompletionStage<Optional<TestConnectionResponse>>  testConnection(Connection updatedConnection, DittoHeaders dittoHeaders);

    static UpdatedConnectionTester getInstance(final ActorSystem actorSystem) {
        return ExtensionId.INSTANCE.get(actorSystem);
    }

    class ExtensionId extends AbstractExtensionId<UpdatedConnectionTester> {

        private static final String IMPLEMENTATION_CONFIG_KEY = "ditto.connection-update-tester";

        private static final ExtensionId INSTANCE = new ExtensionId();

        private ExtensionId() {}

        @Override

        public UpdatedConnectionTester createExtension(final ExtendedActorSystem system) {
            return AkkaClassLoader.instantiate(system, UpdatedConnectionTester.class,
                    getImplementation(system),
                    List.of(ActorSystem.class),
                    List.of(system));
        }

        private String getImplementation(final ExtendedActorSystem actorSystem) {
            return actorSystem.settings().config().getString(IMPLEMENTATION_CONFIG_KEY);
        }

    }

}
