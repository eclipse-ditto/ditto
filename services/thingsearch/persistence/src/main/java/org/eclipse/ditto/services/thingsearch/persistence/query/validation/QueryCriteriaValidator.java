/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.thingsearch.persistence.query.validation;

import org.eclipse.ditto.model.query.criteria.Criteria;
import org.eclipse.ditto.model.query.filter.QueryFilterCriteriaFactory;
import org.eclipse.ditto.services.thingsearch.common.config.DittoSearchConfig;
import org.eclipse.ditto.services.thingsearch.common.config.SearchConfig;
import org.eclipse.ditto.services.utils.akka.AkkaClassLoader;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.signals.commands.thingsearch.query.ThingSearchQueryCommand;

import akka.actor.AbstractExtensionId;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.actor.Extension;

/**
 * Search Query Validator to be loaded by reflection.
 * Can be used as an extension point to use custom validation of search queries.
 * Implementations MUST have a public constructor.
 */
public abstract class QueryCriteriaValidator implements Extension {

    /**
     * Gets the criteria of a {@link org.eclipse.ditto.signals.commands.thingsearch.query.ThingSearchQueryCommand} and
     * validates it.
     *
     * May throw an exception depending on the implementation in the used QueryCriteriaValidator.
     *
     * @return the criteria of the query command.
     */
    public abstract Criteria parseCriteria(final ThingSearchQueryCommand<?> command,
            final QueryFilterCriteriaFactory factory);

    /**
     * Load a {@code QueryCriteriaValidator} dynamically according to the search configuration.
     *
     * @param actorSystem The actor system in which to load the validator.
     * @return The validator.
     */
    public static QueryCriteriaValidator get(final ActorSystem actorSystem) {
        return ExtensionId.INSTANCE.get(actorSystem);
    }

    /**
     * ID of the actor system extension to validate the {@code QueryCriteriaValidator}.
     */
    private static final class ExtensionId extends AbstractExtensionId<QueryCriteriaValidator> {

        private static final ExtensionId INSTANCE = new ExtensionId();

        @Override
        public QueryCriteriaValidator createExtension(final ExtendedActorSystem system) {
            final SearchConfig searchConfig =
                    DittoSearchConfig.of(DefaultScopedConfig.dittoScoped(
                            system.settings().config()));
            return AkkaClassLoader.instantiate(system, QueryCriteriaValidator.class,
                    searchConfig.getQueryValidator());
        }
    }
}
