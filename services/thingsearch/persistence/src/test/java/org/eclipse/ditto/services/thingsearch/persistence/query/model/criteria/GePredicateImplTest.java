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
package org.eclipse.ditto.services.thingsearch.persistence.query.model.criteria;

import javax.annotation.Nullable;

import org.bson.conversions.Bson;
import org.eclipse.ditto.model.query.criteria.GePredicateImpl;
import org.eclipse.ditto.model.query.criteria.Predicate;

import com.mongodb.client.model.Filters;
/**
 * Unit test for {@link GePredicateImpl}.
 */
public final class GePredicateImplTest extends AbstractPredicateTest {

    @Override
    protected Bson getFilter(@Nullable final String value) {
        return Filters.gte(KNOWN_FIELD_NAME, value);
    }

    @Override
    protected Predicate getPredicate(@Nullable final String value) {
        return new GePredicateImpl(value);
    }

}
