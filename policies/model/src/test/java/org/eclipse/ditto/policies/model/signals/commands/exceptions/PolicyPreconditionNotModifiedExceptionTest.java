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

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Objects;

import org.eclipse.ditto.base.model.assertions.DittoBaseAssertions;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.signals.GlobalErrorRegistry;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.signals.commands.TestConstants;
import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyPreconditionNotModifiedException}.
 */
public class PolicyPreconditionNotModifiedExceptionTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(DittoRuntimeException.JsonFields.STATUS, HttpStatus.NOT_MODIFIED.getCode())
            .set(DittoRuntimeException.JsonFields.ERROR_CODE, PolicyPreconditionNotModifiedException.ERROR_CODE)
            .set(DittoRuntimeException.JsonFields.MESSAGE,
                    TestConstants.Policy.POLICY_PRECONDITION_NOT_MODIFIED_EXCEPTION.getMessage())
            .set(DittoRuntimeException.JsonFields.DESCRIPTION,
                    TestConstants.Policy.POLICY_PRECONDITION_NOT_MODIFIED_EXCEPTION.getDescription().get())
            .set(DittoRuntimeException.JsonFields.HREF,
                    TestConstants.Policy.POLICY_PRECONDITION_NOT_MODIFIED_EXCEPTION.getHref()
                            .map(Objects::toString).orElse(null))
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(PolicyPreconditionNotModifiedException.class, areImmutable());
    }


    @Test
    public void checkErrorCodeWorks() {
        final DittoRuntimeException actual =
                GlobalErrorRegistry.getInstance().parse(KNOWN_JSON, TestConstants.EMPTY_DITTO_HEADERS);

        DittoBaseAssertions.assertThat(actual)
                .isEqualTo(TestConstants.Policy.POLICY_PRECONDITION_NOT_MODIFIED_EXCEPTION);
    }

}
