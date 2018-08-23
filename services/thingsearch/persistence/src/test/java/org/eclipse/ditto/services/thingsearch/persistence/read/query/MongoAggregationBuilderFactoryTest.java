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

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

import com.typesafe.config.Config;

/**
 * Unit test for {@link MongoAggregationBuilderFactoryTest}.
 */
public final class MongoAggregationBuilderFactoryTest {

    /** */
    @Test
    public void assertImmutability() {
        assertInstancesOf(MongoAggregationBuilderFactory.class, areImmutable(),
                provided(Config.class).isAlsoImmutable());
    }

}
