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

import static com.mongodb.client.model.Aggregates.group;
import static com.mongodb.client.model.Aggregates.match;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.in;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.pekko.NotUsed;
import org.apache.pekko.event.LoggingAdapter;
import org.apache.pekko.stream.javadsl.Source;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoMongoClient;
import org.eclipse.ditto.rql.parser.RqlPredicateParser;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.rql.query.filter.QueryFilterCriteriaFactory;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.AggregateThingsMetrics;
import org.eclipse.ditto.thingsearch.service.common.config.SearchConfig;
import org.eclipse.ditto.thingsearch.service.common.config.SearchPersistenceConfig;
import org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants;
import org.eclipse.ditto.thingsearch.service.persistence.read.criteria.visitors.CreateBsonVisitor;

import com.mongodb.client.model.Accumulators;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;

/**
 * Persistence implementation for aggregating things.
 */
public final class MongoThingsAggregationPersistence implements ThingsAggregationPersistence {


    private final MongoCollection<Document> collection;
    private final LoggingAdapter log;
    private final Duration maxQueryTime;
    private final MongoHints hints;
    private final QueryFilterCriteriaFactory queryFilterCriteriaFactory;

    /**
     * Initializes the things search persistence with a passed in {@code persistence}.
     *
     * @param mongoClient the mongoDB persistence wrapper.
     * @param mongoHintsByNamespace the mongo hints by namespace.
     * @param simpleFieldMappings the simple field mappings.
     * @param persistenceConfig the search persistence configuration.
     * @param log the logger.
     */
    private MongoThingsAggregationPersistence(final DittoMongoClient mongoClient,
            final Optional<String> mongoHintsByNamespace,
            final Map<String, String> simpleFieldMappings, final SearchPersistenceConfig persistenceConfig,
            final LoggingAdapter log) {
        this.queryFilterCriteriaFactory =
                QueryFilterCriteriaFactory.of(ThingsFieldExpressionFactory.of(simpleFieldMappings),
                        RqlPredicateParser.getInstance());
        final MongoDatabase database = mongoClient.getDefaultDatabase();
        final var readConcern = persistenceConfig.readConcern();
        final var readPreference = persistenceConfig.readPreference().getMongoReadPreference();
        collection = database.getCollection(PersistenceConstants.THINGS_COLLECTION_NAME)
                .withReadConcern(readConcern.getMongoReadConcern())
                .withReadPreference(readPreference);
        this.log = log;
        maxQueryTime = mongoClient.getDittoSettings().getMaxQueryTime();
        hints = mongoHintsByNamespace.map(MongoHints::byNamespace).orElse(MongoHints.empty());
    }

    public static ThingsAggregationPersistence of(final DittoMongoClient mongoClient,
            final SearchConfig searchConfig, final LoggingAdapter log) {
        return new MongoThingsAggregationPersistence(mongoClient, searchConfig.getMongoHintsByNamespace(),
                searchConfig.getSimpleFieldMappings(), searchConfig.getQueryPersistenceConfig(), log);
    }

    @Override
    public Source<Document, NotUsed> aggregateThings(final AggregateThingsMetrics aggregateCommand) {
        final List<Bson> aggregatePipeline = new ArrayList<>();

        // Create namespace predicate optional for $match stage
        final Optional<Bson> nsPredicateOptional = Optional.of(aggregateCommand.getNamespaces())
                .filter(set -> !set.isEmpty())
                .map(nsSet -> in(PersistenceConstants.FIELD_NAMESPACE, aggregateCommand.getNamespaces()));

        // Create filter predicate
        final Bson filter = CreateBsonVisitor.sudoApply(
                queryFilterCriteriaFactory.filterCriteria(aggregateCommand.getFilter(),
                        aggregateCommand.getDittoHeaders()));
        // Merge the namespace predicate with the filter predicate or use just the filter
        nsPredicateOptional.ifPresentOrElse(nsPredicate -> {
            aggregatePipeline.add(match(and(nsPredicate, filter)));
        }, () -> aggregatePipeline.add(match(filter)));

        // Construct the $group stage
        final Map<String, String> groupingBy =
                aggregateCommand.getGroupingBy().entrySet().stream().collect(Collectors.toMap(
                        Map.Entry::getKey, entry -> "$t." + entry.getValue().replace("/", ".")));
        final Bson group = group(new Document(groupingBy), Accumulators.sum("count", 1));
        aggregatePipeline.add(group);
        log.debug("aggregation Pipeline: {}",
                aggregatePipeline.stream().map(bson -> bson.toBsonDocument().toJson()).collect(
                        Collectors.toList()));
        // Execute the aggregation pipeline
        return Source.fromPublisher(collection.aggregate(aggregatePipeline)
                .hint(hints.getHint(aggregateCommand.getNamespaces())
                        .orElse(null))
                .allowDiskUse(true)
                .maxTime(maxQueryTime.toMillis(), TimeUnit.MILLISECONDS));
    }
}
