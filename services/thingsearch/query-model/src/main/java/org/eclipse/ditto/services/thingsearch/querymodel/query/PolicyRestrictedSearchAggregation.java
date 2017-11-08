/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.thingsearch.querymodel.query;

import java.time.Duration;
import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Criteria;

import com.mongodb.reactivestreams.client.MongoCollection;

import akka.NotUsed;
import akka.stream.javadsl.Source;

/**
 * Defines an aggregation.
 */
public interface PolicyRestrictedSearchAggregation {

    /**
     * Returns the aggregation pipeline consisting of the ordered stages used for the aggregation operation.
     *
     * @return a list of aggregation stages - the aggregation pipeline
     */
    List<Bson> getAggregationPipeline();

    /**
     * Returns the used skip value for this aggregation.
     * @return the skip value
     */
    int getSkip();

    /**
     * Returns the used limit value for this aggregation.
     *
     * @return the limit value
     */
    int getLimit();

    /**
     * Returns the search criteria.
     *
     * @return a {@code Criteria} object.
     */
    Criteria getCriteria();

    /**
     * Executes this aggregation on the specified collection.
     *
     * @param collection the MongoDB collection to be aggregated.
     * @param maxTime maximum time the query is allowed to execute on MongoDB.
     * @throws NullPointerException if {@code collection} is {@code null}.
     */
    Source<Document, NotUsed> execute(MongoCollection<Document> collection, final Duration maxTime);

}
