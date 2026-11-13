/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.starter.actors;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.rql.query.Query;
import org.eclipse.ditto.rql.query.criteria.CriteriaFactory;
import org.eclipse.ditto.thingsearch.model.SortOption;
import org.eclipse.ditto.thingsearch.persistence.api.model.ResultList;
import org.eclipse.ditto.thingsearch.persistence.api.model.TimestampedThingId;

/**
 * Pages a query to exhaustion by driving the REAL, package-private {@link ThingsSearchCursor}
 * (encode -&gt; decode -&gt; {@code adjust}) each hop — the identical chain {@code PostgresCursorResumeIT} exercises —
 * for the Phase-G parity matrix IT, which walks BOTH backends through this single helper and compares the page
 * sequences. Lives in the cursor's own package (split-package test source, same precedent as
 * {@code PostgresCursorResumeIT}) because {@link ThingsSearchCursor} is package-private.
 */
public final class ParityCursorWalker {

    private static final int MAX_PAGES = 100;

    private ParityCursorWalker() {
        throw new AssertionError();
    }

    /**
     * Walk a query to exhaustion.
     *
     * @param baseQuerySupplier supplies a FRESH base query per hop (the cursor encodes the absolute position and
     * {@code adjust} is applied to the original base query, exactly as the service does).
     * @param modelSortOption the API-model sort option matching the base query's sort dimensions (the cursor
     * validates its sort-value count against it).
     * @param pageExecutor executes one page against the backend under test.
     * @param system the actor system for the cursor's decode stream.
     * @return the walked pages (each page = the thing IDs it returned, in order).
     */
    public static List<List<String>> walk(final Supplier<Query> baseQuerySupplier,
            final SortOption modelSortOption,
            final Function<Query, ResultList<TimestampedThingId>> pageExecutor,
            final ActorSystem system) {

        final List<List<String>> pages = new ArrayList<>();
        Query pageQuery = baseQuerySupplier.get();
        int hop = 0;
        while (hop++ < MAX_PAGES) {
            final ResultList<TimestampedThingId> page = pageExecutor.apply(pageQuery);
            final List<String> ids = new ArrayList<>();
            page.forEach(entry -> ids.add(entry.thingId().toString()));
            if (!ids.isEmpty()) {
                pages.add(ids);
            }
            final Optional<JsonArray> sortValues = page.lastResultSortValues();
            if (sortValues.isEmpty()) {
                return pages; // no next page
            }
            final ThingsSearchCursor cursor =
                    new ThingsSearchCursor(null, "parity-" + hop, modelSortOption, null, sortValues.get());
            final ThingsSearchCursor decoded = runSingle(ThingsSearchCursor.decode(cursor.encode(), system), system);
            pageQuery = ThingsSearchCursor.adjust(Optional.of(decoded), baseQuerySupplier.get(),
                    CriteriaFactory.getInstance());
        }
        throw new AssertionError("cursor walk did not terminate within " + MAX_PAGES + " pages");
    }

    private static <T> T runSingle(final Source<T, ?> source, final ActorSystem system) {
        try {
            return source.runWith(Sink.head(), system).toCompletableFuture().get(30, TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new AssertionError("cursor decode failed", e);
        }
    }
}
