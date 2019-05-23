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
package org.eclipse.ditto.services.thingsearch.persistence.read.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_ID;

import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.model.query.Query;
import org.eclipse.ditto.model.query.SortDirection;
import org.eclipse.ditto.model.query.SortOption;
import org.eclipse.ditto.model.query.criteria.Criteria;
import org.eclipse.ditto.model.query.expression.SimpleFieldExpressionImpl;
import org.eclipse.ditto.services.base.DittoService;
import org.eclipse.ditto.services.base.config.limits.DefaultLimitsConfig;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Tests limited instances of {@link MongoQueryBuilder}.
 */
public final class MongoQueryBuilderLimitedTest {

    private static final SortOption KNOWN_SORT_OPTION =
            new SortOption(new SimpleFieldExpressionImpl(FIELD_ID), SortDirection.DESC);

    private static DefaultLimitsConfig limitsConfig;

    private final Criteria criteria = Mockito.mock(Criteria.class);

    private int maxPageSizeFromConfig;
    private int defaultPageSizeFromConfig;
    private MongoQueryBuilder underTest;

    @BeforeClass
    public static void initTestFixture() {
        final Config testConfig = ConfigFactory.load("test");
        limitsConfig = DefaultLimitsConfig.of(testConfig.getConfig(DittoService.DITTO_CONFIG_PATH));
    }

    @Before
    public void setUp() {
        maxPageSizeFromConfig = limitsConfig.getThingsSearchMaxPageSize();
        defaultPageSizeFromConfig = limitsConfig.getThingsSearchDefaultPageSize();
        underTest = MongoQueryBuilder.limited(criteria, maxPageSizeFromConfig, defaultPageSizeFromConfig);
    }

    @Test(expected = NullPointerException.class)
    public void createWithNullCriteria() {
        MongoQueryBuilder.limited(null, maxPageSizeFromConfig, defaultPageSizeFromConfig);
    }

    @Test
    public void buildWithCriteriaOnly() {
        final Query query = underTest.build();

        assertThat(query.getCriteria()).isEqualTo(criteria);
    }

    @Test
    public void buildWithSort() {
        final List<SortOption> sortOptions = Collections.singletonList(KNOWN_SORT_OPTION);
        final Query query = underTest.sort(sortOptions).build();

        assertThat(query.getSortOptions()).isEqualTo(sortOptions);
    }

    @Test
    public void appendDefaultSortOption() {
        final SortOption defaultSortOption =
                new SortOption(new SimpleFieldExpressionImpl(FIELD_ID), SortDirection.ASC);
        final List<SortOption> sortOptions = Collections.singletonList(Mockito.mock(SortOption.class));
        final Query query = underTest.sort(sortOptions).build();

        assertThat(query.getSortOptions()).contains(defaultSortOption);
    }

    @Test
    public void buildWithLimit() {
        final int limit = maxPageSizeFromConfig - 1;
        final Query query = underTest.limit(limit).build();

        assertThat(query.getLimit()).isEqualTo(limit);
    }

    @Test
    public void buildWithSkip() {
        final int skip = 4;
        final Query query = underTest.skip(skip).build();

        assertThat(query.getSkip()).isEqualTo(skip);
    }

    @Test(expected = IllegalArgumentException.class)
    public void buildWithLimitGreaterThanMaxValue() {
        final long limitTooHigh = (long) maxPageSizeFromConfig + 1;
        underTest.limit(limitTooHigh);
    }

    @Test(expected = IllegalArgumentException.class)
    public void buildWithLimitLessThanZero() {
        underTest.limit(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void buildWithSkipLessThanZero() {
        underTest.skip(-1);
    }

}
