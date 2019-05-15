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
package org.eclipse.ditto.services.utils.ddata;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

import com.typesafe.config.Config;

/**
 * Unit test for {@link org.eclipse.ditto.services.utils.ddata.DistributedDataConfigReader}.
 */
public final class DistributedDataConfigReaderTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(DistributedDataConfigReader.class, areImmutable(), provided(Config.class).isAlsoImmutable());
    }

}
