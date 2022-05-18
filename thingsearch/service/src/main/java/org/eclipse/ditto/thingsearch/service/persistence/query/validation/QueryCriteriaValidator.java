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

import org.eclipse.ditto.base.service.DittoExtensionPoint;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.ThingSearchQueryCommand;

import akka.actor.ActorSystem;

/**
 * Search Query Validator to be loaded by reflection.
 * Can be used as an extension point to use custom validation of search queries.
 * Implementations MUST have a public constructor taking an actorSystem as argument.
 */
public interface QueryCriteriaValidator extends DittoExtensionPoint {

    /**
     * Gets the criteria of a {@link org.eclipse.ditto.thingsearch.model.signals.commands.query.ThingSearchQueryCommand} and
     * validates it.
     * <p>
     * May throw an exception depending on the implementation in the used QueryCriteriaValidator.
     *
     * @param command the command to validate.
     * @return the validated command in a future if it is valid, or a failed future if it is not.
     */
    CompletionStage<ThingSearchQueryCommand<?>> validateCommand(final ThingSearchQueryCommand<?> command);

    /**
     * Loads the implementation of {@code QueryCriteriaValidator} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code QueryCriteriaValidator} should be loaded.
     * @return the {@code QueryCriteriaValidator} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     */
    static QueryCriteriaValidator get(final ActorSystem actorSystem) {
        checkNotNull(actorSystem, "actorSystem");
        return ExtensionId.INSTANCE.get(actorSystem);
    }

    final class ExtensionId extends DittoExtensionPoint.ExtensionId<QueryCriteriaValidator> {

        private static final String CONFIG_PATH = "ditto.things-search.query-criteria-validator.implementation";
        private static final ExtensionId INSTANCE = new ExtensionId(QueryCriteriaValidator.class);

        private ExtensionId(final Class<QueryCriteriaValidator> parentClass) {
            super(parentClass);
        }

        @Override
        protected String getConfigPath() {
            return CONFIG_PATH;
        }

    }

}
