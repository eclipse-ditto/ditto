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
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.signals.commands.policies.TestConstants;
import org.junit.Test;

/**
 * Unit test for {@link PolicyNotAccessibleException}.
 */
public class PolicyNotAccessibleExceptionTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(DittoRuntimeException.JsonFields.STATUS, HttpStatusCode.NOT_FOUND.toInt())
            .set(DittoRuntimeException.JsonFields.ERROR_CODE, PolicyNotAccessibleException.ERROR_CODE)
            .set(DittoRuntimeException.JsonFields.MESSAGE,
                    TestConstants.Policy.POLICY_NOT_ACCESSIBLE_EXCEPTION.getMessage())
            .set(DittoRuntimeException.JsonFields.DESCRIPTION,
                    TestConstants.Policy.POLICY_NOT_ACCESSIBLE_EXCEPTION.getDescription().get())
            .set(DittoRuntimeException.JsonFields.HREF,
                    TestConstants.Policy.POLICY_NOT_ACCESSIBLE_EXCEPTION.getHref().toString())
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(PolicyNotAccessibleException.class, areImmutable());
    }


    @Test
    public void checkPolicyErrorCodeWorks() {
        final DittoRuntimeException actual =
                PolicyErrorRegistry.newInstance().parse(KNOWN_JSON, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(actual).isEqualTo(TestConstants.Policy.POLICY_NOT_ACCESSIBLE_EXCEPTION);
    }

}
