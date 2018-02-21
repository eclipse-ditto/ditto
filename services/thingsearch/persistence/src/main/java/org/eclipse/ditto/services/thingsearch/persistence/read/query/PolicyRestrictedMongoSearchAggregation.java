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
package org.eclipse.ditto.services.thingsearch.persistence.read.query;

import static com.mongodb.client.model.Aggregates.group;
import static com.mongodb.client.model.Aggregates.lookup;
import static com.mongodb.client.model.Aggregates.match;
import static com.mongodb.client.model.Aggregates.project;
import static com.mongodb.client.model.Aggregates.sort;
import static com.mongodb.client.model.Aggregates.unwind;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.exists;
import static com.mongodb.client.model.Filters.or;
import static java.util.Objects.requireNonNull;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.CONCAT;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.COUNT_RESULT_NAME;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_ATTRIBUTES;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_ATTRIBUTES_VARIABLE;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_DELETED;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_DELETED_VARIABLE;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_FEATURES;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_FEATURES_VARIABLE;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_GRANTS;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_GRANTS_VARIABLE;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_ID;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL_ACL;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL_FEATURE_VARIABLE;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL_GLOBAL_READS;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL_KEY_VARIABLE;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL_VARIABLE;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_NAMESPACE;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_REVISION;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_REVISION_VARIABLE;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIRST_PROJECTION;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.ID_VARIABLE;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.IF_NULL_CONDITION;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.NAMESPACE_VARIABLE;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.POLICIES_BASED_SEARCH_INDEX_COLLECTION_NAME;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.POLICY_INDEX_ID;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.PUSH_PROJECTION;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.SUM_GROUPING;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.ditto.services.thingsearch.persistence.read.criteria.visitors.CreateBsonVisitor;
import org.eclipse.ditto.services.thingsearch.persistence.read.criteria.visitors.CreatePolicyRestrictionBsonVisitor;
import org.eclipse.ditto.services.thingsearch.persistence.read.criteria.visitors.CreateUnwoundBsonVisitor;
import org.eclipse.ditto.services.thingsearch.persistence.read.expression.visitors.GetSortBsonVisitor;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.AnyCriteriaImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Criteria;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.CriteriaFactory;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.CriteriaFactoryImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Predicate;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.FilterFieldExpression;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.SimpleFieldExpressionImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.SortFieldExpression;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.ThingsFieldExpressionFactoryImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.query.AggregationBuilder;
import org.eclipse.ditto.services.thingsearch.querymodel.query.PolicyRestrictedSearchAggregation;
import org.eclipse.ditto.services.thingsearch.querymodel.query.QueryConstants;
import org.eclipse.ditto.services.thingsearch.querymodel.query.SortDirection;
import org.eclipse.ditto.services.thingsearch.querymodel.query.SortOption;
import org.eclipse.ditto.services.utils.persistence.mongo.BsonUtil;
import org.reactivestreams.Publisher;

import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BsonField;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UnwindOptions;
import com.mongodb.reactivestreams.client.MongoCollection;

import akka.NotUsed;
import akka.stream.javadsl.Source;

/**
 * Represents an aggregation used to search on things restricted by policies.
 */
final class PolicyRestrictedMongoSearchAggregation implements PolicyRestrictedSearchAggregation {

    private static final CriteriaFactory CRITERIA_FACTORY = new CriteriaFactoryImpl();
    private static final ThingsFieldExpressionFactory FIELD_EXPRESSION_FACTORY =
            new ThingsFieldExpressionFactoryImpl();
    private static final List<SortOption> DEFAULT_SORT_OPTIONS =
            Collections.singletonList(new SortOption(FIELD_EXPRESSION_FACTORY.sortByThingId(), SortDirection.ASC));

    private static final BsonInt32 BSON_INT_1 = new BsonInt32(1);
    private static final Bson GROUP_STAGE = createGroupStage();
    private static final Bson GROUPED_ID_PROJECT_STAGE = createGroupedIdProjectStage();
    private static final Bson PROJECTION_STAGE_1 = createFirstProjectionStage();
    private static final Bson PROJECTION_STAGE_2 = createSecondProjectionStage();
    private static final Bson UNWIND_STAGE_1 = unwind(FIELD_INTERNAL_VARIABLE);
    private static final Bson UNWIND_STAGE_2 = createSecondUnwindStage();
    private static final Bson LOOKUP_STAGE =
            lookup(POLICIES_BASED_SEARCH_INDEX_COLLECTION_NAME, POLICY_INDEX_ID, FIELD_ID, FIELD_GRANTS);

    private final List<Bson> aggregationPipeline;
    private final int skip;
    private final int limit;
    private final Criteria filterCriteria;

    private PolicyRestrictedMongoSearchAggregation(final Builder builder) {
        final boolean isSudo = builder.sudo;
        if (builder.authorizationSubjects.isEmpty() != isSudo) {
            throw new IllegalStateException("AuthorizationSubjects are required for non-sudo searches!");
        }

        final Predicate authorizationSubjectsPredicate = CRITERIA_FACTORY.in(builder.authorizationSubjects);
        final Criteria globalReadsCriteria = createCriteria(isSudo, CRITERIA_FACTORY,
                authorizationSubjectsPredicate, FIELD_EXPRESSION_FACTORY.filterByGlobalRead());
        final Criteria aclFieldCriteria = createCriteria(isSudo, CRITERIA_FACTORY,
                authorizationSubjectsPredicate, FIELD_EXPRESSION_FACTORY.filterByAcl());
        final List<Bson> pipeline = new ArrayList<>();

        // match1 filters by ACL, global-READ and search criteria.
        final Bson match1 = builder.withDeletedThings
                ? createInitialMatchStageWithDeleted(builder.filterCriteria, aclFieldCriteria, globalReadsCriteria)
                : createInitialMatchStageWithNonDeleted(builder.filterCriteria, aclFieldCriteria, globalReadsCriteria);
        pipeline.add(match1);
        pipeline.add(UNWIND_STAGE_1);

        // match2 filters out irrelevant attributes and features.
        final Optional<Bson> match2 = createSecondaryMatchStage(builder.filterCriteria);
        match2.ifPresent(pipeline::add);

        pipeline.add(PROJECTION_STAGE_1);
        pipeline.add(LOOKUP_STAGE);
        pipeline.add(UNWIND_STAGE_2);

        // match3 filters out fields the user is not supposed to see.
        final Bson match3 = createTertiaryMatchStage(builder.filterCriteria, authorizationSubjectsPredicate);
        pipeline.add(match3);

        // groupBy stage
        pipeline.add(GROUP_STAGE);
        pipeline.add(GROUPED_ID_PROJECT_STAGE);

        // filter out mismatched results after computing view
        final Bson match4 = match(CreateBsonVisitor.apply(builder.filterCriteria));
        pipeline.add(match4);

        // sort results after false positives are filtered out
        //
        // Due to MongoDB issue SERVER-7568:
        // Sort stage MUST be after some UNWIND or PROJECT---otherwise MongoDB 3.2/3.4 carries out incorrect
        // optimization and iterates through an entire index before applying the filter match1.
        addSortingStage(pipeline,
                builder.sortOptions.isEmpty() ? DEFAULT_SORT_OPTIONS : builder.sortOptions, builder.count);

        // add skip & limit to reduce aggregation result size limit problem
        addSkipAndLimit(pipeline, builder.skip, builder.limit, builder.count);
        addProjectionStage2IfCount(pipeline, builder.count);
        addCountStage(pipeline, builder.count);

        aggregationPipeline = pipeline;
        skip = builder.skip;
        limit = builder.limit;
        filterCriteria = builder.filterCriteria;
    }

    private static Bson createGroupStage() {
        return group(new BsonDocument(FIELD_ID, new BsonString(ID_VARIABLE)),
                new BsonField(FIELD_NAMESPACE, createProjectionDocument(FIRST_PROJECTION, NAMESPACE_VARIABLE)),
                new BsonField(FIELD_ATTRIBUTES, createProjectionDocument(FIRST_PROJECTION, FIELD_ATTRIBUTES_VARIABLE)),
                new BsonField(FIELD_FEATURES, createProjectionDocument(FIRST_PROJECTION, FIELD_FEATURES_VARIABLE)),
                new BsonField(FIELD_INTERNAL, createProjectionDocument(PUSH_PROJECTION, FIELD_INTERNAL_VARIABLE)),
                new BsonField(FIELD_DELETED, createProjectionDocument(FIRST_PROJECTION, FIELD_DELETED_VARIABLE)),
                new BsonField(FIELD_REVISION, createProjectionDocument(FIRST_PROJECTION, FIELD_REVISION_VARIABLE)));
    }

    private static Bson createProjectionDocument(final String projectionKey, final String projectionValue) {
        return new BsonDocument(projectionKey, new BsonString(projectionValue));
    }

    private static Bson createGroupedIdProjectStage() {
        return project(new BsonDocument()
                .append(FIELD_ID, new BsonString(ID_VARIABLE + "." + FIELD_ID))
                .append(FIELD_NAMESPACE, BsonBoolean.TRUE)
                .append(FIELD_ATTRIBUTES, BsonBoolean.TRUE)
                .append(FIELD_FEATURES, BsonBoolean.TRUE)
                .append(FIELD_INTERNAL, BsonBoolean.TRUE)
                .append(FIELD_DELETED, BsonBoolean.TRUE)
                .append(FIELD_REVISION, BsonBoolean.TRUE));
    }

    private static Bson createInitialMatchStageWithDeleted(final Criteria filterCriteria,
            final Criteria aclCriteria, final Criteria globalPolicyGrantsCriteria) {

        final Bson authorization =
                or(CreateBsonVisitor.apply(globalPolicyGrantsCriteria), CreateBsonVisitor.apply(aclCriteria));

        return match(and(authorization, CreateBsonVisitor.apply(filterCriteria)));
    }

    private static void addSkipAndLimit(final Collection<Bson> pipeline,
            final int skip,
            final int limit,
            final boolean isCount) {

        if (!isCount) {
            pipeline.add(Aggregates.skip(skip));
            pipeline.add(Aggregates.limit(limit + 1));
        }
    }

    private static void addProjectionStage2IfCount(final Collection<Bson> pipeline, final boolean isCount) {
        if (!isCount) {
            pipeline.add(PROJECTION_STAGE_2);
        }
    }

    private static void addCountStage(final Collection<Bson> pipeline, final boolean isCount) {
        if (isCount) {
            pipeline.add(group(new BsonDocument(FIELD_ID, BsonNull.VALUE), new BsonField(COUNT_RESULT_NAME,
                    new BsonDocument(SUM_GROUPING, BSON_INT_1))));
        }
    }

    private static Criteria createCriteria(final boolean isSudo,
            final CriteriaFactory criteriaFactory,
            final Predicate authorizationSubjectsPredicate,
            final FilterFieldExpression global) {

        return (isSudo
                ? criteriaFactory.any()
                : criteriaFactory.fieldCriteria(global, authorizationSubjectsPredicate));
    }

    private static void addSortingStage(final Collection<Bson> pipeline, final Collection<SortOption> sortOptions,
            final boolean isCount) {
        if (!isCount && !sortOptions.isEmpty()) {
            pipeline.add(sort(getSortOptionsAsBson(sortOptions)));
        }
    }

    private static Bson getSortOptionsAsBson(final Iterable<SortOption> sortOptions) {
        final List<Bson> sortings = new ArrayList<>();

        for (final SortOption sortOption : sortOptions) {
            final SortDirection sortDirection = sortOption.getSortDirection();

            final SortFieldExpression sortExpression = sortOption.getSortExpression();

            final List<Bson> currentSorts = GetSortBsonVisitor.apply(sortExpression, sortDirection);
            sortings.addAll(currentSorts);
        }

        return Sorts.orderBy(sortings);
    }

    private static Bson createFirstProjectionStage() {
        final BsonDocument projection = new BsonDocument()
                .append(POLICY_INDEX_ID, new BsonDocument(CONCAT,
                        new BsonArray(Arrays.asList(
                                new BsonString(ID_VARIABLE),
                                new BsonString(":"),
                                new BsonDocument(IF_NULL_CONDITION,
                                        new BsonArray(Arrays.asList(
                                                new BsonString(FIELD_INTERNAL_FEATURE_VARIABLE),
                                                new BsonString(""))
                                        )),
                                new BsonDocument(IF_NULL_CONDITION,
                                        new BsonArray(Arrays.asList(
                                                new BsonString(FIELD_INTERNAL_KEY_VARIABLE),
                                                new BsonString(""))
                                        ))
                        ))
                ))
                .append(FIELD_NAMESPACE, BsonBoolean.TRUE)
                .append(FIELD_ATTRIBUTES, BsonBoolean.TRUE)
                .append(FIELD_FEATURES, BsonBoolean.TRUE)
                .append(FIELD_INTERNAL, BsonBoolean.TRUE)
                .append(FIELD_DELETED, BsonBoolean.TRUE)
                .append(FIELD_REVISION, BsonBoolean.TRUE);
        return project(projection);
    }

    static Optional<Bson> createSecondaryMatchStage(final Criteria filterCriteria) {
        // filters relevant attributes/features.
        // an attribute/feature is relevant if it contributes to the truth of filterCriteria.
        return CreateUnwoundBsonVisitor.apply(filterCriteria).map(Aggregates::match);
    }

    private static Bson createTertiaryMatchStage(final Criteria filterCriteria,
            final Predicate authorizationSubjectsPredicate) {

        final Criteria thingV1Criteria = CRITERIA_FACTORY.fieldCriteria(
                new SimpleFieldExpressionImpl(FIELD_GRANTS), CRITERIA_FACTORY.eq(null));

        // ACL and global-READ are checked at the beginning.
        // check policy entries here, let ACL and global-Read fall through.

        final Criteria internalGrCriteria = CRITERIA_FACTORY.existsCriteria(
                new SimpleFieldExpressionImpl(FIELD_INTERNAL_GLOBAL_READS));

        final Criteria internalAclCriteria = CRITERIA_FACTORY.existsCriteria(
                new SimpleFieldExpressionImpl(FIELD_INTERNAL_ACL));

        return match(or(
                CreatePolicyRestrictionBsonVisitor.apply(filterCriteria, authorizationSubjectsPredicate)
                        .orElse(new BsonDocument()),
                CreateBsonVisitor.apply(thingV1Criteria),
                CreateBsonVisitor.apply(internalGrCriteria),
                CreateBsonVisitor.apply(internalAclCriteria)));
    }

    private static Bson createInitialMatchStageWithNonDeleted(final Criteria filterCriteria,
            final Criteria aclCriteria, final Criteria globalPolicyGrantsCriteria) {

        final Bson authorization =
                or(CreateBsonVisitor.apply(globalPolicyGrantsCriteria), CreateBsonVisitor.apply(aclCriteria));
        final Bson notDeleted = exists(FIELD_DELETED, false);

        return match(and(authorization, notDeleted, CreateBsonVisitor.apply(filterCriteria)));
    }

    private static Bson createSecondProjectionStage() {
        return project(new BsonDocument().append(FIELD_ID, BsonBoolean.TRUE));
    }

    private static Bson createSecondUnwindStage() {
        return unwind(FIELD_GRANTS_VARIABLE, new UnwindOptions().preserveNullAndEmptyArrays(true));
    }

    @Override
    public List<Bson> getAggregationPipeline() {
        return aggregationPipeline;
    }

    @Override
    public int getSkip() {
        return skip;
    }

    @Override
    public int getLimit() {
        return limit;
    }

    @Override
    public Criteria getCriteria() {
        return filterCriteria;
    }

    @Override
    public Source<Document, NotUsed> execute(final MongoCollection<Document> collection, final Duration maxTime) {
        checkNotNull(collection, "collection to be aggregated");

        final Publisher<Document> publisher = collection.aggregate(aggregationPipeline)
                .maxTime(maxTime.getSeconds(), TimeUnit.SECONDS)
                .allowDiskUse(true)
                // query is faster with cursor disabled
                .useCursor(false);

        return Source.fromPublisher(publisher);
    }

    /**
     * Pretty-prints the aggregation pipeline.
     *
     * @return String representation of the aggregation json.
     */
    public String prettyPrintPipeline() {
        return "[" + aggregationPipeline.stream()
                .map(BsonUtil::toBsonDocument)
                .map(BsonDocument::toJson)
                .collect(Collectors.joining(",\n")) + "]";
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "aggregationPipeline=" + aggregationPipeline +
                ", skip=" + skip +
                ", limit=" + limit +
                "]";
    }

    /**
     * Builder to build an aggregation pipeline for searching things.
     */
    public static final class Builder implements AggregationBuilder {

        private Criteria filterCriteria = AnyCriteriaImpl.getInstance();
        private List<Object> authorizationSubjects = Collections.emptyList();
        private List<SortOption> sortOptions = Collections.emptyList();
        private int limit = QueryConstants.DEFAULT_LIMIT;
        private int skip = 0;
        private boolean count = false;
        private boolean withDeletedThings = false;
        private boolean sudo = false;

        @Override
        public Builder filterCriteria(final Criteria filterCriteria) {
            this.filterCriteria = requireNonNull(filterCriteria);
            return this;
        }

        @Override
        public Builder authorizationSubjects(final Collection<String> authorizationSubjects) {
            this.authorizationSubjects = new ArrayList<>(requireNonNull(authorizationSubjects));
            return this;
        }

        @Override
        public Builder sortOptions(final List<SortOption> sortOptions) {
            this.sortOptions = requireNonNull(sortOptions);
            return this;
        }

        @Override
        public Builder skip(final long skip) {
            this.skip = Validator.checkSkip(skip);
            return this;
        }

        @Override
        public Builder limit(final long limit) {
            this.limit = Validator.checkLimit(limit, QueryConstants.MAX_LIMIT);
            return this;
        }

        /**
         * Sets whether this aggregation should be used for counting.
         *
         * @param count if {@code true} this aggregation is used for counting, if {@code false} it is used for normal
         * queries.
         * @return this builder.
         */
        public Builder count(final boolean count) {
            this.count = count;
            return this;
        }

        @Override
        public Builder withDeletedThings(final boolean withDeletedThings) {
            this.withDeletedThings = withDeletedThings;
            return this;
        }

        @Override
        public Builder sudo(final boolean sudo) {
            this.sudo = sudo;
            return this;
        }

        @Override
        public PolicyRestrictedSearchAggregation build() {
            return new PolicyRestrictedMongoSearchAggregation(this);
        }

    }

}
