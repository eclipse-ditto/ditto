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

import java.util.HashSet;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.EffectedPermissions;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.Resource;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyResource;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link PolicyCommandToModifyExceptionRegistry}.
 */
public final class PolicyCommandToModifyExceptionRegistryTest {

    private PolicyCommandToModifyExceptionRegistry registryUnderTest;

    @Before
    public void setup() {
        registryUnderTest = PolicyCommandToModifyExceptionRegistry.getInstance();
    }

    @Test
    public void mapModifyResourceToResourceNotModifiable() {
        final ModifyResource modifyResource = ModifyResource.of("ns:policyId", Label.of("myLabel"),
                Resource.newInstance("thing", "/", EffectedPermissions.newInstance(new HashSet<>(), new HashSet<>())),
                DittoHeaders.empty());

        final DittoRuntimeException mappedException = registryUnderTest.exceptionFrom(modifyResource);
        final DittoRuntimeException expectedException =
                ResourceNotModifiableException.newBuilder("ns:policyId", "myLabel", "/")
                        .build();

        assertThat(mappedException).isEqualTo(expectedException);
    }

}
