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
import static org.mockito.Mockito.mock;

import org.eclipse.ditto.model.query.QueryBuilder;
import org.eclipse.ditto.model.query.QueryBuilderFactory;
import org.eclipse.ditto.model.query.criteria.Criteria;
import org.eclipse.ditto.services.base.config.DittoLimitsConfigReader;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link MongoQueryBuilderFactory}.
 */
public final class MongoQueryBuilderFactoryTest {

    private final QueryBuilderFactory fac = new MongoQueryBuilderFactory
            (DittoLimitsConfigReader.fromRawConfig(ConfigFactory.load("test")));

    /** */
    @Test
    public void newBuilderWithValidCriteria() {
        final Criteria crit = mock(Criteria.class);

        final QueryBuilder builder = fac.newBuilder(crit);

        assertThat(builder).isInstanceOf(MongoQueryBuilder.class);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void newBuilderWithNullCriteria() {
        fac.newBuilder(null);
    }

    /** */
    @Test
    public void newUnlimitedBuilderWithValidCriteria() {
        final Criteria crit = mock(Criteria.class);

        final QueryBuilder builder = fac.newUnlimitedBuilder(crit);

        assertThat(builder).isInstanceOf(MongoQueryBuilder.class);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void newUnlimitedBuilderWithNullCriteria() {
        fac.newUnlimitedBuilder(null);
    }

}
