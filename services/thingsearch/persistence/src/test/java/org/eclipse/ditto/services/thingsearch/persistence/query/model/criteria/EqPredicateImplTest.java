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
package org.eclipse.ditto.services.thingsearch.persistence.query.model.criteria;

import javax.annotation.Nullable;

import org.bson.conversions.Bson;
import org.eclipse.ditto.model.query.criteria.EqPredicateImpl;
import org.eclipse.ditto.model.query.criteria.Predicate;

import com.mongodb.client.model.Filters;
/**
 * Unit test for {@link EqPredicateImpl}.
 */
public final class EqPredicateImplTest extends AbstractPredicateTest {

    @Override
    protected Bson getFilter(@Nullable final String value) {
        return Filters.eq(KNOWN_FIELD_NAME, value);
    }

    @Override
    protected Predicate getPredicate(@Nullable final String value) {
        return new EqPredicateImpl(value);
    }

}
