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
        final ModifyResource modifyResource = ModifyResource.of("policyId", Label.of("myLabel"),
                Resource.newInstance("thing", "/", EffectedPermissions.newInstance(new HashSet<>(), new HashSet<>())),
                DittoHeaders.empty());

        final DittoRuntimeException mappedException = registryUnderTest.exceptionFrom(modifyResource);
        final DittoRuntimeException expectedException =
                ResourceNotModifiableException.newBuilder("policyId", "myLabel", "/")
                        .build();

        assertThat(mappedException).isEqualTo(expectedException);
    }

}
