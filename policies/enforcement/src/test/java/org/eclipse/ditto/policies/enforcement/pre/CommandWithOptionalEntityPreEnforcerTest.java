/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.enforcement.pre;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Resource;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyResource;
import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.policies.enforcement.pre.CommandWithOptionalEntityPreEnforcer}.
 */
public final class CommandWithOptionalEntityPreEnforcerTest {

    private static final String NULL_CHARACTER = "\u0000";

    @Test
    public void illegalNullCharacterIsInvalidInString() {
        final JsonValue jsonValue = JsonValue.of(NULL_CHARACTER);

        assertThatExceptionOfType(DittoRuntimeException.class)
                .isThrownBy(
                        () -> new CommandWithOptionalEntityPreEnforcer(null, null).apply(createTestCommand(jsonValue)));
    }

    private Signal<?> createTestCommand(final JsonValue jsonValue) {
        final PolicyId policyId = PolicyId.of("org.eclipse.ditto.test:myPolicy");
        return ModifyResource.of(policyId, Label.of("test"),
                Resource.newInstance(
                        ResourceKey.newInstance("policy:/" + jsonValue.asString()),
                        EffectedPermissions.newInstance(null, null)), DittoHeaders.empty());
    }

}
