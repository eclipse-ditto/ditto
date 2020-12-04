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

import java.util.Set;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.query.criteria.Criteria;
import org.eclipse.ditto.model.query.filter.QueryFilterCriteriaFactory;
import org.eclipse.ditto.signals.commands.thingsearch.query.ThingSearchQueryCommand;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

/**
 * Default {@link org.eclipse.ditto.services.thingsearch.persistence.query.validation.QueryCriteriaValidator},
 * who parses QueryCriteria without additional validation
 */
public final class DefaultQueryCriteriaValidator extends QueryCriteriaValidator {

    /**
     * Instantiate this provider. Called by reflection.
     */
    public DefaultQueryCriteriaValidator(final ActorSystem actorSystem, final ActorRef pubSubMediator) {
        // Nothing to initialize.
    }

    @Override
    public Criteria parseCriteria(final ThingSearchQueryCommand<?> command, final QueryFilterCriteriaFactory factory) {

        final DittoHeaders headers = command.getDittoHeaders();
        final Set<String> namespaces = command.getNamespaces().orElse(null);
        final String filter = command.getFilter().orElse(null);
        if (namespaces == null) {
            return factory.filterCriteria(filter, command.getDittoHeaders());
        } else {
            return factory.filterCriteriaRestrictedByNamespaces(filter, headers, namespaces);
        }
    }
}
