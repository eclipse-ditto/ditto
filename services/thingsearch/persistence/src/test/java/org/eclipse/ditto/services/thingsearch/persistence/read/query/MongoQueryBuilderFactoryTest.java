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

import org.junit.Test;

import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Criteria;
import org.eclipse.ditto.services.thingsearch.querymodel.query.QueryBuilder;
import org.eclipse.ditto.services.thingsearch.querymodel.query.QueryBuilderFactory;

/**
 * Unit test for {@link MongoQueryBuilderFactory}.
 */
public final class MongoQueryBuilderFactoryTest {

    private final QueryBuilderFactory fac = new MongoQueryBuilderFactory();

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
