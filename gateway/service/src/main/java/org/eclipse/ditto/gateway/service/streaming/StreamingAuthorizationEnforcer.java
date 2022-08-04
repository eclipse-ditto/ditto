/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.streaming;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionIds;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;
import akka.http.javadsl.server.RequestContext;

/**
 * Enforces authorization in order to establish a Streaming connection.
 * If the authorization check is successful nothing will happen, else a
 * {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException DittoRuntimeException} is thrown.
 */
public interface StreamingAuthorizationEnforcer extends DittoExtensionPoint {

    /**
     * Ensures that the establishment of a SSE connection is authorized for the given arguments.
     *
     * @param requestContext the context of the HTTP request for opening the connection.
     * @param dittoHeaders the DittoHeaders with authentication information for opening the connection.
     * @return a successful future if validation succeeds or a failed future if validation fails.
     * @throws NullPointerException if any argument is {@code null}.
     */
    CompletionStage<DittoHeaders> checkAuthorization(RequestContext requestContext, DittoHeaders dittoHeaders);

    /**
     * Loads the implementation of {@code StreamingAuthorizationEnforcer} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code StreamingAuthorizationEnforcer} should be loaded.
     * @param config the configuration for this extension.
     * @return the {@code StreamingAuthorizationEnforcer} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     * @since 3.0.0
     */
    static StreamingAuthorizationEnforcer get(final ActorSystem actorSystem, final Config config) {
        checkNotNull(actorSystem, "actorSystem");
        checkNotNull(config, "config");
        final var extensionIdConfig = ExtensionId.computeConfig(config);
        return DittoExtensionIds.get(actorSystem)
                .computeIfAbsent(extensionIdConfig, ExtensionId::new)
                .get(actorSystem);
    }

    final class ExtensionId extends DittoExtensionPoint.ExtensionId<StreamingAuthorizationEnforcer> {

        private static final String CONFIG_KEY = "streaming-authorization-enforcer";

        private ExtensionId(final ExtensionIdConfig<StreamingAuthorizationEnforcer> extensionIdConfig) {
            super(extensionIdConfig);
        }

        static ExtensionIdConfig<StreamingAuthorizationEnforcer> computeConfig(final Config config) {
            return ExtensionIdConfig.of(StreamingAuthorizationEnforcer.class, config, CONFIG_KEY);
        }

        @Override
        protected String getConfigKey() {
            return CONFIG_KEY;
        }

    }

}
