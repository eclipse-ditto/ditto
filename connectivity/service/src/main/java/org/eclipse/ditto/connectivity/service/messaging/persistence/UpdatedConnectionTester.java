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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.service.DittoExtensionIds;
import org.eclipse.ditto.base.service.DittoExtensionPoint;
import org.eclipse.ditto.base.service.RootActorStarter;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.TestConnectionResponse;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Allows testing of an updated connection without the need for authentication/authorization on API level.
 */
public interface UpdatedConnectionTester extends DittoExtensionPoint {

    /**
     * Tests the given connection.
     *
     * @param updatedConnection the connection with it's updated content. This connection needs to still have the
     * original connection ID.
     * @param dittoHeaders the ditto headers that should be used for the test connection command
     * @return A completion stage resolving to true, in case the connection could be tested successfully or false if not.
     */
    CompletionStage<Optional<TestConnectionResponse>> testConnection(Connection updatedConnection,
            DittoHeaders dittoHeaders);

    static UpdatedConnectionTester get(final ActorSystem actorSystem, final Config config) {
        checkNotNull(actorSystem, "actorSystem");
        checkNotNull(config, "config");
        final var extensionIdConfig = ExtensionId.computeConfig(config);
        return DittoExtensionIds.get(actorSystem)
                .computeIfAbsent(extensionIdConfig, ExtensionId::new)
                .get(actorSystem);
    }

    final class ExtensionId extends DittoExtensionPoint.ExtensionId<UpdatedConnectionTester> {

        private static final String CONFIG_KEY = "updated-connection-tester";

        private ExtensionId(final ExtensionIdConfig<UpdatedConnectionTester> extensionIdConfig) {
            super(extensionIdConfig);
        }

        @Override
        protected String getConfigKey() {
            return CONFIG_KEY;
        }

        static ExtensionIdConfig<UpdatedConnectionTester> computeConfig(final Config config) {
            return ExtensionIdConfig.of(UpdatedConnectionTester.class, config, CONFIG_KEY);
        }

    }

}
