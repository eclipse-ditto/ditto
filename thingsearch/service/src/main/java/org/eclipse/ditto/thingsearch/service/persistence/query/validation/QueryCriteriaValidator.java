/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.persistence.query.validation;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionIds;
import org.eclipse.ditto.rql.query.Query;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Search Query Validator to be loaded by reflection.
 * Can be used as an extension point to use custom validation of search queries.
 * Implementations MUST have a public constructor taking an actorSystem as argument.
 */
public interface QueryCriteriaValidator extends DittoExtensionPoint {

    /**
     * Validate a parsed query of a
     * {@link org.eclipse.ditto.thingsearch.model.signals.commands.query.ThingSearchQueryCommand}.
     *
     * @param query The query.
     * @return The validated query in a future if it is valid, or a failed future if it is not.
     */
    CompletionStage<Query> validateQuery(final Command<?> command, final Query query);

    /**
     * Loads the implementation of {@code QueryCriteriaValidator} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code QueryCriteriaValidator} should be loaded.
     * @param config the configuration of this extension.
     * @return the {@code QueryCriteriaValidator} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     */
    static QueryCriteriaValidator get(final ActorSystem actorSystem, final Config config) {
        checkNotNull(actorSystem, "actorSystem");
        checkNotNull(config, "config");
        final var extensionIdConfig = ExtensionId.computeConfig(config);
        return DittoExtensionIds.get(actorSystem)
                .computeIfAbsent(extensionIdConfig, ExtensionId::new)
                .get(actorSystem);
    }

    final class ExtensionId extends DittoExtensionPoint.ExtensionId<QueryCriteriaValidator> {

        private static final String CONFIG_KEY = "query-criteria-validator";

        private ExtensionId(final ExtensionIdConfig<QueryCriteriaValidator> extensionIdConfig) {
            super(extensionIdConfig);
        }

        static ExtensionIdConfig<QueryCriteriaValidator> computeConfig(final Config config) {
            return ExtensionIdConfig.of(QueryCriteriaValidator.class, config, CONFIG_KEY);
        }

        @Override
        protected String getConfigKey() {
            return CONFIG_KEY;
        }

    }

}
