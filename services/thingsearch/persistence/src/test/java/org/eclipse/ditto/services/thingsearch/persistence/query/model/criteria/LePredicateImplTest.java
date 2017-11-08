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
package org.eclipse.ditto.services.thingsearch.persistence.query.model.criteria;

import javax.annotation.Nullable;

import org.bson.conversions.Bson;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.LePredicateImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Predicate;

import com.mongodb.client.model.Filters;

/**
 * Unit test for {@link LePredicateImpl}.
 */
public final class LePredicateImplTest extends AbstractPredicateTest {

    @Override
    protected Bson getFilter(@Nullable final String value) {
        return Filters.lte(KNOWN_FIELD_NAME, value);
    }

    @Override
    protected Predicate getPredicate(@Nullable final String value) {
        return new LePredicateImpl(value);
    }

}
