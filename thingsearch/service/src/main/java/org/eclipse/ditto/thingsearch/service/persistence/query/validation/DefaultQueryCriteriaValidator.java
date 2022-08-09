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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.rql.query.Query;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Default {@link QueryCriteriaValidator},
 * who parses QueryCriteria without additional validation.
 */
public final class DefaultQueryCriteriaValidator implements QueryCriteriaValidator {

    /**
     * Instantiate this provider. Called by reflection.
     * @param actorSystem the actor system in which to load the extension.
     * @param config the configuration for this extension.
     */
    @SuppressWarnings("unused")
    public DefaultQueryCriteriaValidator(final ActorSystem actorSystem, final Config config) {
        // Nothing to initialize.
    }

    @Override
    public CompletionStage<Query> validateQuery(final Command<?> command, final Query query) {
        return CompletableFuture.completedStage(query);
    }
}
