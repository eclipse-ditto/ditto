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
package org.eclipse.ditto.thingsearch.service.persistence.read.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.eclipse.ditto.rql.query.QueryBuilder;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.base.service.config.limits.DefaultLimitsConfig;
import org.eclipse.ditto.base.service.config.limits.LimitsConfig;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link MongoQueryBuilderFactory}.
 */
public final class MongoQueryBuilderFactoryTest {

    private static LimitsConfig limitsConfig;

    private MongoQueryBuilderFactory underTest;

    @BeforeClass
    public static void initLimitsConfig() {
        final Config rawTestConfig = ConfigFactory.load("test");
        limitsConfig = DefaultLimitsConfig.of(rawTestConfig.getConfig("ditto"));
    }

    @Before
    public void setUp() {
        underTest = new MongoQueryBuilderFactory(limitsConfig);
    }

    @Test
    public void newBuilderWithValidCriteria() {
        final QueryBuilder builder = underTest.newBuilder(mock(Criteria.class));

        assertThat(builder).isInstanceOf(MongoQueryBuilder.class);
    }

    @Test(expected = NullPointerException.class)
    public void newBuilderWithNullCriteria() {
        underTest.newBuilder(null);
    }

    @Test
    public void newUnlimitedBuilderWithValidCriteria() {
        final QueryBuilder builder = underTest.newUnlimitedBuilder(mock(Criteria.class));

        assertThat(builder).isInstanceOf(MongoQueryBuilder.class);
    }

    @Test(expected = NullPointerException.class)
    public void newUnlimitedBuilderWithNullCriteria() {
        underTest.newUnlimitedBuilder(null);
    }

}
