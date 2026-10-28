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
package org.eclipse.ditto.thingsearch.service.persistence.read;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.rql.query.Query;
import org.eclipse.ditto.rql.query.SortDirection;
import org.eclipse.ditto.rql.query.SortOption;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.service.common.model.ResultList;
import org.eclipse.ditto.thingsearch.service.common.model.TimestampedThingId;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests search queries restricted to namespaces, whose {@code _id} the persistence bounds by the namespaces' ranges.
 */
public final class NamespacesIT extends AbstractReadPersistenceITBase {

    private static final String ATTRIBUTE = "rank";

    // "a.b.c" sorts before "a.b" by thing ID, "m" between "a.b" and "z"
    private static final List<String> NAMESPACES = List.of("", "a.b", "a.b.c", "a.b-c", "a.bc", "m", "z");
    private static final int THINGS_PER_NAMESPACE = 3;

    private final List<ThingId> allThingIds = new ArrayList<>();

    @Before
    public void insertThings() {
        int rank = 0;
        for (final String namespace : NAMESPACES) {
            for (int i = 0; i < THINGS_PER_NAMESPACE; i++) {
                final ThingId thingId = ThingId.of(namespace, "thing" + i);
                persistThing(createThing(thingId).toBuilder()
                        .setAttribute(JsonPointer.of(ATTRIBUTE), JsonValue.of(rank++))
                        .build());
                allThingIds.add(thingId);
            }
        }
    }

    @Test
    public void cursorPagingAscendingAcrossNamespaces() {
        assertCursorPaging(Set.of("a.b", "z"), SortDirection.ASC);
    }

    @Test
    public void cursorPagingDescendingAcrossNamespaces() {
        assertCursorPaging(Set.of("a.b", "z"), SortDirection.DESC);
    }

    @Test
    public void cursorPagingAcrossNamespacesSortingInsideEachOther() {
        assertCursorPaging(Set.of("a.b", "a.b.c", "a.b-c"), SortDirection.ASC);
        assertCursorPaging(Set.of("a.b", "a.b.c", "a.b-c"), SortDirection.DESC);
    }

    @Test
    public void cursorPagingInEmptyNamespace() {
        assertCursorPaging(Set.of("", "m"), SortDirection.ASC);
    }

    @Test
    public void cursorPagingInOneNamespace() {
        assertCursorPaging(Set.of("a.b"), SortDirection.ASC);
        assertCursorPaging(Set.of("a.b"), SortDirection.DESC);
    }

    @Test
    public void cursorPagingInNamespacesWithoutThings() {
        assertCursorPaging(Set.of("a.b", "unknown", "y"), SortDirection.ASC);
    }

    @Test
    public void pageOfSeveralNamespacesIsCutAtLimit() {
        final Set<String> namespaces = Set.of("a.b", "a.b.c", "m", "z");
        final int limit = THINGS_PER_NAMESPACE + 1;

        final ResultList<TimestampedThingId> page = findAll(restrictedTo(namespaces, cf.any()),
                List.of(byThingId(SortDirection.ASC)), limit, 0, namespaces);

        assertThat(thingIdsOf(page))
                .containsExactlyElementsOf(expected(namespaces, SortDirection.ASC).subList(0, limit));
        assertThat(page.lastResultSortValues()).isPresent();
    }

    @Test
    public void skipAcrossNamespaces() {
        final Set<String> namespaces = Set.of("a.b", "z");
        final int skip = THINGS_PER_NAMESPACE - 1;

        final ResultList<TimestampedThingId> page = findAll(restrictedTo(namespaces, cf.any()),
                List.of(byThingId(SortDirection.ASC)), 2, skip, namespaces);

        assertThat(thingIdsOf(page))
                .containsExactlyElementsOf(expected(namespaces, SortDirection.ASC).subList(skip, skip + 2));
    }

    @Test
    public void sortByAttributeAcrossNamespaces() {
        final Set<String> namespaces = Set.of("a.b", "a.b.c", "z");
        final List<SortOption> sortOptions = List.of(
                new SortOption(fef.sortByAttribute(ATTRIBUTE), SortDirection.DESC), byThingId(SortDirection.ASC));

        final ResultList<TimestampedThingId> page = findAll(restrictedTo(namespaces, cf.any()), sortOptions,
                THINGS_PER_NAMESPACE * NAMESPACES.size(), 0, namespaces);

        // attribute rank follows insertion order
        final List<ThingId> expected = allThingIds.stream()
                .filter(thingId -> namespaces.contains(thingId.getNamespace()))
                .toList();
        assertThat(thingIdsOf(page)).containsExactlyElementsOf(reversed(expected));
    }

    @Test
    public void streamAcrossNamespaces() {
        final Set<String> namespaces = Set.of("a.b", "a.b.c", "z");
        final Query query = qbf.newUnlimitedBuilder(restrictedTo(namespaces, cf.any()))
                .sort(List.of(byThingId(SortDirection.ASC)))
                .build();

        final List<ThingId> streamed = waitFor(readPersistence.findAllUnlimited(query, KNOWN_SUBJECTS, namespaces,
                DittoHeaders.empty()));

        assertThat(streamed).containsExactlyElementsOf(expected(namespaces, SortDirection.ASC));
    }

    @Test
    public void filterWithinNamespaces() {
        final Set<String> namespaces = Set.of("a.b", "m", "z");
        final Criteria rankAtLeastTen = cf.fieldCriteria(fef.filterByAttribute(ATTRIBUTE), cf.ge(10));

        final ResultList<TimestampedThingId> page = findAll(restrictedTo(namespaces, rankAtLeastTen),
                List.of(byThingId(SortDirection.ASC)), 100, 0, namespaces);

        // "a.b" holds ranks 3 to 5
        assertThat(thingIdsOf(page)).containsExactlyElementsOf(expected(Set.of("m", "z"), SortDirection.ASC));
    }

    /**
     * Pages through the things of the namespaces the way a cursor does: each page continues after the thing ID of the
     * last page's last thing.
     */
    private void assertCursorPaging(final Set<String> namespaces, final SortDirection direction) {
        final int limit = 2;
        final List<ThingId> paged = new ArrayList<>();
        @Nullable String lastThingId = null;
        do {
            final Criteria afterCursor = lastThingId == null ? cf.any() : cf.fieldCriteria(fef.filterByThingId(),
                    direction == SortDirection.ASC ? cf.gt(lastThingId) : cf.lt(lastThingId));
            final ResultList<TimestampedThingId> page = findAll(restrictedTo(namespaces, afterCursor),
                    List.of(byThingId(direction)), limit, 0, namespaces);
            paged.addAll(thingIdsOf(page));
            lastThingId = page.lastResultSortValues()
                    .flatMap(sortValues -> sortValues.get(0))
                    .map(JsonValue::asString)
                    .orElse(null);
        } while (lastThingId != null);

        assertThat(paged).containsExactlyElementsOf(expected(namespaces, direction));
    }

    private ResultList<TimestampedThingId> findAll(final Criteria criteria, final List<SortOption> sortOptions,
            final int limit, final int skip, final Set<String> namespaces) {

        final Query query = qbf.newBuilder(criteria).sort(sortOptions).limit(limit).skip(skip).build();
        return waitFor(readPersistence.findAll(query, KNOWN_SUBJECTS, namespaces, DittoHeaders.empty())).get(0);
    }

    private static Criteria restrictedTo(final Set<String> namespaces, final Criteria criteria) {
        return cf.and(List.of(cf.fieldCriteria(fef.filterByNamespace(), cf.in(new ArrayList<>(namespaces))),
                criteria));
    }

    private static SortOption byThingId(final SortDirection direction) {
        return new SortOption(fef.sortByThingId(), direction);
    }

    private List<ThingId> expected(final Set<String> namespaces, final SortDirection direction) {
        final Comparator<ThingId> byThingId = Comparator.comparing(ThingId::toString);
        return allThingIds.stream()
                .filter(thingId -> namespaces.contains(thingId.getNamespace()))
                .sorted(direction == SortDirection.ASC ? byThingId : byThingId.reversed())
                .toList();
    }

    private static List<ThingId> thingIdsOf(final ResultList<TimestampedThingId> page) {
        return page.stream().map(TimestampedThingId::thingId).toList();
    }

    private static <T> List<T> reversed(final List<T> list) {
        final List<T> result = new ArrayList<>(list);
        Collections.reverse(result);
        return result;
    }

}
