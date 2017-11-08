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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Criteria;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.SortFieldExpression;
import org.eclipse.ditto.services.thingsearch.querymodel.query.Query;
import org.eclipse.ditto.services.thingsearch.querymodel.query.QueryConstants;
import org.eclipse.ditto.services.thingsearch.querymodel.query.SortDirection;
import org.eclipse.ditto.services.thingsearch.querymodel.query.SortOption;

/**
 * Tests limited instances of {@link MongoQueryBuilder}.
 */
public final class MongoQueryBuilderLimitedTest {

    private static final SortOption KNOWN_SORT_OPTION =
            new SortOption(mock(SortFieldExpression.class), SortDirection.ASC);

    private Criteria criteria = Mockito.mock(Criteria.class);
    private MongoQueryBuilder underTest;

    /** */
    @Before
    public void setUp() {
        underTest = MongoQueryBuilder.limited(criteria);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void createWithNullCriteria() {
        MongoQueryBuilder.limited(null);
    }

    /** */
    @Test
    public void buildWithCriteriaOnly() {
        final Query query = underTest.build();

        assertThat(query.getCriteria()).isEqualTo(criteria);
    }

    /** */
    @Test
    public void buildWithSort() {
        final List<SortOption> sortOptions = Collections.singletonList(KNOWN_SORT_OPTION);
        final Query query = underTest.sort(sortOptions).build();

        assertThat(query.getSortOptions()).isEqualTo(sortOptions);
    }

    /** */
    @Test
    public void buildWithLimit() {
        final int limit = QueryConstants.MAX_LIMIT - 1;
        final Query query = underTest.limit(limit).build();

        assertThat(query.getLimit()).isEqualTo(limit);
    }

    /** */
    @Test
    public void buildWithSkip() {
        final int skip = 4;
        final Query query = underTest.skip(skip).build();

        assertThat(query.getSkip()).isEqualTo(skip);
    }

    /** */
    @Test(expected = IllegalArgumentException.class)
    public void buildWithLimitGreaterThanMaxValue() {
        final long limitTooHigh = (long) QueryConstants.MAX_LIMIT + 1;
        underTest.limit(limitTooHigh);
    }

    /** */
    @Test(expected = IllegalArgumentException.class)
    public void buildWithLimitLessThanZero() {
        underTest.limit(-1);
    }

    /** */
    @Test(expected = IllegalArgumentException.class)
    public void buildWithSkipLessThanZero() {
        underTest.skip(-1);
    }

}
