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
package org.eclipse.ditto.signals.commands.policies.exceptions;

import static org.eclipse.ditto.model.base.assertions.DittoBaseAssertions.assertThat;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicy;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link PolicyCommandToAccessExceptionRegistry}.
 */
public final class PolicyCommandToAccessExceptionRegistryTest {

    private PolicyCommandToAccessExceptionRegistry registryUnderTest;

    @Before
    public void setup() {
        registryUnderTest = PolicyCommandToAccessExceptionRegistry.getInstance();
    }

    @Test
    public void mapRetrievePolicyToPolicyNotAccessible() {
        final RetrievePolicy retrieveAttribute = RetrievePolicy.of(":thingId",
                DittoHeaders.empty());
        final DittoRuntimeException mappedException = registryUnderTest.exceptionFrom(retrieveAttribute);
        final DittoRuntimeException expectedException = PolicyNotAccessibleException.newBuilder(":thingId").build();
        assertThat(mappedException).isEqualTo(expectedException);
    }

}
