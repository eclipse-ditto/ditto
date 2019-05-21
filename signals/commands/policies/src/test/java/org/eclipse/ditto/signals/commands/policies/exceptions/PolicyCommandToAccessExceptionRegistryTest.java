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
        final RetrievePolicy retrieveAttribute = RetrievePolicy.of("org.eclipse.ditto:thingId",
                DittoHeaders.empty());
        final DittoRuntimeException mappedException = registryUnderTest.exceptionFrom(retrieveAttribute);
        final DittoRuntimeException expectedException = PolicyNotAccessibleException.newBuilder(":thingId").build();
        assertThat(mappedException).isEqualTo(expectedException);
    }

}
