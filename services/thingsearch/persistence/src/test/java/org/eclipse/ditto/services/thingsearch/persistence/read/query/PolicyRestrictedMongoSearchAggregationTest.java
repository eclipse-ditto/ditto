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


import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.Optional;

import org.bson.conversions.Bson;
import org.eclipse.ditto.model.query.criteria.Criteria;
import org.eclipse.ditto.model.query.criteria.CriteriaFactory;
import org.eclipse.ditto.model.query.criteria.CriteriaFactoryImpl;
import org.eclipse.ditto.model.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.model.query.expression.ThingsFieldExpressionFactoryImpl;
import org.junit.Test;

/**
 * Tests {@link PolicyRestrictedMongoSearchAggregation}.
 */
public final class PolicyRestrictedMongoSearchAggregationTest {

    private final CriteriaFactory cf = new CriteriaFactoryImpl();
    private final ThingsFieldExpressionFactory tf = new ThingsFieldExpressionFactoryImpl();

    @Test
    public void createUnwoundBsonShouldWorkForNonexistentThingId() {
        final Criteria criteria = cf.nor(cf.existsCriteria(tf.existsBy("thingId")));
        final Optional<Bson> actualBson =
                PolicyRestrictedMongoSearchAggregation.createSecondaryMatchStage(criteria);

        assertThat(actualBson).isEmpty();
    }

}
