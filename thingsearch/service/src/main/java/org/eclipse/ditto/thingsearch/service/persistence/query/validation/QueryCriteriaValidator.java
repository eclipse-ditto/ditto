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

import java.util.List;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.internal.utils.akka.AkkaClassLoader;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.rql.query.Query;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.ThingSearchQueryCommand;
import org.eclipse.ditto.thingsearch.service.common.config.DittoSearchConfig;
import org.eclipse.ditto.thingsearch.service.common.config.SearchConfig;

import akka.actor.AbstractExtensionId;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.actor.Extension;

/**
 * Search Query Validator to be loaded by reflection.
 * Can be used as an extension point to use custom validation of search queries.
 * Implementations MUST have a public constructor taking an actorSystem as argument.
 */
public abstract class QueryCriteriaValidator implements Extension {

    private static final ExtensionId EXTENSION_ID = new ExtensionId();

    protected final ActorSystem actorSystem;

    protected QueryCriteriaValidator(final ActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    /**
     * Validate a parsed query of a
     * {@link org.eclipse.ditto.thingsearch.model.signals.commands.query.ThingSearchQueryCommand}.
     *
     * @param query The query.
     * @return The validated query in a future if it is valid, or a failed future if it is not.
     */
    public abstract CompletionStage<Query> validateQuery(final ThingSearchQueryCommand<?> command, final Query query);

    /**
     * Load a {@code QueryCriteriaValidator} dynamically according to the search configuration.
     *
     * @param actorSystem The actor system in which to load the validator.
     * @return The validator.
     */
    public static QueryCriteriaValidator get(final ActorSystem actorSystem) {
        return EXTENSION_ID.get(actorSystem);
    }

    /**
     * ID of the actor system extension to validate the {@code QueryCriteriaValidator}.
     */
    private static final class ExtensionId extends AbstractExtensionId<QueryCriteriaValidator> {

        @Override
        public QueryCriteriaValidator createExtension(final ExtendedActorSystem system) {
            final SearchConfig searchConfig =
                    DittoSearchConfig.of(DefaultScopedConfig.dittoScoped(
                            system.settings().config()));

            return AkkaClassLoader.instantiate(system, QueryCriteriaValidator.class,
                    searchConfig.getQueryValidatorImplementation(),
                    List.of(ActorSystem.class),
                    List.of(system));
        }
    }

}
