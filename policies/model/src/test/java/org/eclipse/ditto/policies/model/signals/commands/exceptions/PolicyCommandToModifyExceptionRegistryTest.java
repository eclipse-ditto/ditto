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
package org.eclipse.ditto.policies.model.signals.commands.exceptions;

import static org.eclipse.ditto.base.model.assertions.DittoBaseAssertions.assertThat;

import java.util.HashSet;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Resource;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyResource;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyCommandToModifyExceptionRegistry}.
 */
public final class PolicyCommandToModifyExceptionRegistryTest {

    private PolicyCommandToModifyExceptionRegistry registryUnderTest;

    @Before
    public void setup() {
        registryUnderTest = PolicyCommandToModifyExceptionRegistry.getInstance();
    }

    @Test
    public void mapModifyResourceToResourceNotModifiable() {
        final ModifyResource modifyResource = ModifyResource.of(PolicyId.of("ns", "policyId"),
                Label.of("myLabel"),
                Resource.newInstance("thing", "/", EffectedPermissions.newInstance(new HashSet<>(), new HashSet<>())),
                DittoHeaders.newBuilder().randomCorrelationId().build());

        final DittoRuntimeException expectedException = ResourceNotModifiableException
                .newBuilder(modifyResource.getEntityId(), modifyResource.getLabel(), modifyResource.getResourcePath())
                .dittoHeaders(modifyResource.getDittoHeaders())
                .build();

        assertThat(registryUnderTest.exceptionFrom(modifyResource)).isEqualTo(expectedException);
    }

}
