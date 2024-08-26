/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 */
package org.eclipse.ditto.thingsearch.service.persistence.read;


import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.javadsl.Source;
import org.bson.Document;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.AggregateThingsMetrics;

/**
 * Interface for thing aggregations on the search collection.
 *
 * @since 3.6.0
 */
public interface ThingsAggregationPersistence {

    /**
     * Aggregate things based on the given aggregateCommand.
     *
     * @param aggregateCommand the aggregateCommand to aggregate things
     * @return the aggregated things
     */
    Source<Document, NotUsed> aggregateThings(AggregateThingsMetrics aggregateCommand);

}
