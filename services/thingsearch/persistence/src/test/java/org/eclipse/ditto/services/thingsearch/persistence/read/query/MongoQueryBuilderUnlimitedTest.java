/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.thingsearch.persistence.read.query;

import static org.eclipse.ditto.services.utils.persistence.mongo.assertions.BsonAssertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.model.query.Query;
import org.eclipse.ditto.model.query.SortDirection;
import org.eclipse.ditto.model.query.SortOption;
import org.eclipse.ditto.model.query.criteria.Criteria;
import org.eclipse.ditto.model.query.expression.SortFieldExpression;
import org.junit.Before;
import org.junit.Test;
/**
 * Tests unlimited instances of {@link MongoQueryBuilder}.
 */
public final class MongoQueryBuilderUnlimitedTest {

    private static final SortOption KNOWN_SORT_OPTION =
            new SortOption(mock(SortFieldExpression.class), SortDirection.ASC);

    private Criteria criteria;
    private MongoQueryBuilder underTest;

    /** */
    @Before
    public void setUp() {
        criteria = mock(Criteria.class);
        underTest = MongoQueryBuilder.unlimited(criteria);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void createWithNullCriteria() {
        MongoQueryBuilder.unlimited(null);
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
        final int limit = 4;

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
    @Test
    public void buildWithVeryHighLimit() {
        final int veryHighLimit = Integer.MAX_VALUE;

        final Query query = underTest.limit(veryHighLimit).build();

        assertThat(query.getLimit()).isEqualTo(veryHighLimit);
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
