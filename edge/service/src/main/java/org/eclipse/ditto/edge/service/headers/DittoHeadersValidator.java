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
package org.eclipse.ditto.edge.service.headers;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionIds;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * This extension allows to validate {@link org.eclipse.ditto.base.model.headers.DittoHeaders}.
 */
public interface DittoHeadersValidator extends DittoExtensionPoint {

    /**
     * Validates {@code dittoHeaders} against limits defined in the extension configuration.
     *
     * @param dittoHeaders the headers to validate.
     * @return a completion stage which completes successfully with the valid headers. Raises a
     * {@link org.eclipse.ditto.base.model.exceptions.DittoHeadersTooLargeException} if {@code dittoHeaders} are not
     * valid.
     */
    CompletionStage<DittoHeaders> validate(DittoHeaders dittoHeaders);

    /**
     * Truncates {@code dittoHeaders} to validate against the configured limits.
     *
     * @param dittoHeaders the headers to truncate.
     * @return the truncated headers.
     */
    DittoHeaders truncate(DittoHeaders dittoHeaders);

    /**
     * Loads the implementation of {@code DittoHeadersValidator} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code DittoHeadersValidator} should be loaded.
     * @param config the configuration for this extension.
     * @return the {@code DittoHeadersValidator} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     */
    static DittoHeadersValidator get(final ActorSystem actorSystem, final Config config) {
        checkNotNull(actorSystem, "actorSystem");
        final var extensionIdConfig = DittoHeadersValidator.ExtensionId.computeConfig(config);
        return DittoExtensionIds.get(actorSystem)
                .computeIfAbsent(extensionIdConfig, DittoHeadersValidator.ExtensionId::new)
                .get(actorSystem);
    }

    final class ExtensionId extends DittoExtensionPoint.ExtensionId<DittoHeadersValidator> {

        private static final String CONFIG_KEY = "ditto-headers-validator";

        private ExtensionId(final ExtensionIdConfig<DittoHeadersValidator> extensionIdConfig) {
            super(extensionIdConfig);
        }

        static ExtensionIdConfig<DittoHeadersValidator> computeConfig(final Config config) {
            return ExtensionIdConfig.of(DittoHeadersValidator.class, config, CONFIG_KEY);
        }

        @Override
        protected String getConfigKey() {
            return CONFIG_KEY;
        }

    }

}
