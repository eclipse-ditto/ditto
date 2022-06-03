/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.persistence.read;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.rql.query.Query;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.api.SearchNamespaceReportResult;
import org.eclipse.ditto.thingsearch.service.common.model.ResultList;
import org.eclipse.ditto.thingsearch.service.common.model.ResultListImpl;
import org.eclipse.ditto.thingsearch.service.common.model.TimestampedThingId;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;

import akka.NotUsed;
import akka.stream.javadsl.Source;

/**
 * Interface for thing operations on the persistence used within the search service.
 *
 * @since 1.0.0
 */
public interface ThingsSearchPersistence {

    /**
     * Initializes the search index if necessary.
     *
     * @return a {@link java.util.concurrent.CompletionStage} which can be either used for blocking or non-blocking initialization.
     */
    CompletionStage<Void> initializeIndices();

    /**
     * Generate a report of things per Namespace.
     *
     * @return Source that emits the report.
     */
    Source<SearchNamespaceReportResult, NotUsed> generateNamespaceCountReport();

    /**
     * Returns the count of documents found by the given {@code query}.
     *
     * @param query the query for matching.
     * @param authorizationSubjectIds authorization subject IDs.
     * @return an {@link Source} which emits the count.
     * @throws NullPointerException if {@code query} is {@code null}.
     */
    Source<Long, NotUsed> count(Query query, List<String> authorizationSubjectIds);

    /**
     * Returns the count of documents found by the given {@code query} regardless of visibility.
     *
     * @param query the query for matching.
     * @return an {@link Source} which emits the count.
     * @throws NullPointerException if {@code query} is {@code null}.
     */
    Source<Long, NotUsed> sudoCount(Query query);

    /**
     * Returns the IDs for all found documents.
     *
     * @param query the query for matching.
     * @param authorizationSubjectIds authorization subject IDs.
     * @param namespaces namespaces to execute searches in, or null to search in all namespaces.
     * @return an {@link Source} which emits the IDs.
     * @throws NullPointerException if {@code query} is {@code null}.
     */
    Source<ResultList<TimestampedThingId>, NotUsed> findAll(Query query, List<String> authorizationSubjectIds,
            @Nullable Set<String> namespaces);

    /**
     * Stream the IDs for all found documents without result size limit.
     *
     * @param query the query for matching.
     * @param authorizationSubjectIds authorization subject IDs.
     * @param namespaces namespaces to execute searches in, or null to search in all namespaces.
     * @return an {@link Source} which emits the IDs.
     * @throws NullPointerException if {@code query} is {@code null}.
     * @since 1.1.0
     */
    Source<ThingId, NotUsed> findAllUnlimited(Query query, List<String> authorizationSubjectIds,
            @Nullable Set<String> namespaces);

    /**
     * Start a stream of metadata of all search index entries not marked for deletion.
     * Do not consider authorization.
     *
     * @param lowerBound lower bound of the stream for resumption. Stream the entire search index if the lower bound
     * is a dummy entity ID.
     * @return the source of metadata of all search index entries.
     */
    Source<Metadata, NotUsed> sudoStreamMetadata(final EntityId lowerBound);

    /**
     * Returns the IDs for all found documents.
     *
     * @param query the query for matching.
     * @param authorizationSubjectIds authorization subject IDs.
     * @return an {@link Source} which emits the IDs.
     * @throws NullPointerException if {@code query} is {@code null}.
     */
    default Source<ResultList<ThingId>, NotUsed> findAll(final Query query,
            final List<String> authorizationSubjectIds) {
        return findAll(query, authorizationSubjectIds, null)
                .map(resultList -> {
                    final var thingIds = resultList.stream().map(TimestampedThingId::thingId).toList();
                    return new ResultListImpl<>(thingIds, resultList.nextPageOffset(),
                            resultList.lastResultSortValues().orElse(null));
                });
    }

}
