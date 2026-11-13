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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.eclipse.ditto.base.service.config.limits.LimitsConfig;
import org.eclipse.ditto.rql.query.Query;
import org.eclipse.ditto.rql.query.QueryBuilder;
import org.eclipse.ditto.rql.query.SortDirection;
import org.eclipse.ditto.rql.query.SortOption;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.criteria.CriteriaFactory;
import org.eclipse.ditto.rql.query.expression.FieldExpressionUtil;
import org.eclipse.ditto.rql.query.expression.SimpleFieldExpression;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;
import org.junit.Test;

/**
 * Unit tests for {@link PostgresQueryBuilderFactory}/{@code PostgresQueryBuilder}/{@link PostgresQuery} — the
 * MongoQueryBuilder-parity transcription: default {@code _id ASC}, thing-id truncation, page-size validation from
 * {@link LimitsConfig}.
 */
public final class PostgresQueryBuilderFactoryTest {

    private static final int MAX_PAGE_SIZE = 200;
    private static final int DEFAULT_PAGE_SIZE = 25;

    private static final SimpleFieldExpression ID_EXPRESSION = SimpleFieldExpression.of(FieldExpressionUtil.FIELD_ID);

    private final CriteriaFactory cf = CriteriaFactory.getInstance();
    private final Criteria any = CriteriaFactory.getInstance().any();
    private final ThingsFieldExpressionFactory fef = ThingsFieldExpressionFactory.of(Map.of("thingId", "_id"));
    private final PostgresQueryBuilderFactory factory = new PostgresQueryBuilderFactory(limitsConfig());

    @Test
    public void defaultSortIsIdAscAndDefaultLimitSkip() {
        final Query query = factory.newBuilder(any).build();
        assertThat(query.getSortOptions()).containsExactly(new SortOption(ID_EXPRESSION, SortDirection.ASC));
        assertThat(query.getLimit()).isEqualTo(DEFAULT_PAGE_SIZE);
        assertThat(query.getSkip()).isEqualTo(0);
        assertThat(query).isInstanceOf(PostgresQuery.class);
    }

    @Test
    public void unlimitedBuilderHasZeroDefaultLimit() {
        final Query query = factory.newUnlimitedBuilder(any).build();
        assertThat(query.getLimit()).isEqualTo(0);
    }

    @Test
    public void userSortWithoutThingIdAppendsDefaultIdAsc() {
        final SortOption attrA = new SortOption(fef.sortByAttribute("a"), SortDirection.DESC);
        final Query query = factory.newBuilder(any).sort(List.of(attrA)).build();
        assertThat(query.getSortOptions()).containsExactly(attrA, new SortOption(ID_EXPRESSION, SortDirection.ASC));
    }

    @Test
    public void userSortIsTruncatedAfterThingIdEntry() {
        // Everything after a total-order thing-id key is dead weight and would break keyset paging → dropped.
        final SortOption attrA = new SortOption(fef.sortByAttribute("a"), SortDirection.ASC);
        final SortOption byId = new SortOption(fef.sortByThingId(), SortDirection.DESC);
        final SortOption attrB = new SortOption(fef.sortByAttribute("b"), SortDirection.ASC);
        final Query query = factory.newBuilder(any).sort(List.of(attrA, byId, attrB)).build();
        assertThat(query.getSortOptions()).containsExactly(attrA, byId);
    }

    @Test
    public void thingIdSortNeedsNoAppendedTiebreak() {
        final SortOption byId = new SortOption(fef.sortByThingId(), SortDirection.ASC);
        final Query query = factory.newBuilder(any).sort(List.of(byId)).build();
        assertThat(query.getSortOptions()).containsExactly(byId);
    }

    @Test
    public void limitBeyondMaxIsRejected() {
        assertThatThrownBy(() -> factory.newBuilder(any).limit(MAX_PAGE_SIZE + 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
    }

    @Test
    public void negativeSkipIsRejected() {
        assertThatThrownBy(() -> factory.newBuilder(any).skip(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("skip");
    }

    @Test
    public void sizeSetsLimitWithinBounds() {
        final Query query = factory.newBuilder(any).size(10).build();
        assertThat(query.getLimit()).isEqualTo(10);
    }

    @Test
    public void queryCarriesCriteriaAndBuildsSortClause() {
        final Criteria criteria = cf.fieldCriteria(fef.filterByAttribute("a"), cf.eq(1));
        final Query query = factory.newBuilder(criteria).build();
        assertThat(query.getCriteria()).isEqualTo(criteria);
        assertThat(((PostgresQuery) query).getSortClause()).isNotNull();
    }

    private static LimitsConfig limitsConfig() {
        return new LimitsConfig() {
            @Override
            public long getThingsMaxSize() {
                return 0;
            }

            @Override
            public long getPoliciesMaxSize() {
                return 0;
            }

            @Override
            public long getMessagesMaxSize() {
                return 0;
            }

            @Override
            public int getThingsSearchDefaultPageSize() {
                return DEFAULT_PAGE_SIZE;
            }

            @Override
            public int getThingsSearchMaxPageSize() {
                return MAX_PAGE_SIZE;
            }

            @Override
            public int getPolicyImportsLimit() {
                return 0;
            }
        };
    }
}
