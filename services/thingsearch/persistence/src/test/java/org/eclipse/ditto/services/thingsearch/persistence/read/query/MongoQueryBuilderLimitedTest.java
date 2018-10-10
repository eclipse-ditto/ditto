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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.model.query.Query;
import org.eclipse.ditto.model.query.SortDirection;
import org.eclipse.ditto.model.query.SortOption;
import org.eclipse.ditto.model.query.criteria.Criteria;
import org.eclipse.ditto.model.query.expression.SortFieldExpression;
import org.eclipse.ditto.services.base.config.DittoLimitsConfigReader;
import org.eclipse.ditto.services.base.config.LimitsConfigReader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.typesafe.config.ConfigFactory;

/**
 * Tests limited instances of {@link MongoQueryBuilder}.
 */
public final class MongoQueryBuilderLimitedTest {

    private static final SortOption KNOWN_SORT_OPTION =
            new SortOption(mock(SortFieldExpression.class), SortDirection.ASC);

    private Criteria criteria = Mockito.mock(Criteria.class);
    private MongoQueryBuilder underTest;
    private int maxPageSizeFromConfig;
    private int defaultPageSizeFromConfig;

    /** */
    @Before
    public void setUp() {
        final LimitsConfigReader limitsConfigReader = DittoLimitsConfigReader.fromRawConfig(ConfigFactory.load("test"));
        maxPageSizeFromConfig = limitsConfigReader.thingsSearchMaxPageSize();
        defaultPageSizeFromConfig = limitsConfigReader.thingsSearchDefaultPageSize();
        underTest = MongoQueryBuilder.limited(criteria, maxPageSizeFromConfig, defaultPageSizeFromConfig);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void createWithNullCriteria() {
        MongoQueryBuilder.limited(null, maxPageSizeFromConfig, defaultPageSizeFromConfig);
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
        final int limit = maxPageSizeFromConfig - 1;
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
        final long limitTooHigh = (long) maxPageSizeFromConfig + 1;
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
