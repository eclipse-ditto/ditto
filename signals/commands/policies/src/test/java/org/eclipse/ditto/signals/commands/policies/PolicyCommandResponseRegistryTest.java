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
package org.eclipse.ditto.signals.commands.policies;

import static org.eclipse.ditto.model.base.assertions.DittoBaseAssertions.assertThat;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicyResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyResponse;
import org.junit.Test;


/**
 * Unit test for {@link PolicyCommandResponseRegistry}.
 */
public class PolicyCommandResponseRegistryTest {


    @Test
    public void parsePolicyModifyCommandResponse() {
        final PolicyCommandResponseRegistry commandResponseRegistry = PolicyCommandResponseRegistry.newInstance();

        final ModifyPolicyResponse commandResponse =
                ModifyPolicyResponse.created(TestConstants.Policy.POLICY_ID, TestConstants.Policy.POLICY,
                        TestConstants.DITTO_HEADERS);
        final JsonObject jsonObject = commandResponse.toJson(FieldType.regularOrSpecial());

        final CommandResponse parsedCommandResponse =
                commandResponseRegistry.parse(jsonObject, TestConstants.DITTO_HEADERS);

        assertThat(parsedCommandResponse).isEqualTo(commandResponse);
    }


    @Test
    public void parsePolicyQueryCommandResponse() {
        final PolicyCommandResponseRegistry commandResponseRegistry = PolicyCommandResponseRegistry.newInstance();

        final RetrievePolicyResponse commandResponse =
                RetrievePolicyResponse.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.POLICY,
                        TestConstants.DITTO_HEADERS);
        final JsonObject jsonObject = commandResponse.toJson(FieldType.regularOrSpecial());

        final CommandResponse parsedCommandResponse =
                commandResponseRegistry.parse(jsonObject, TestConstants.DITTO_HEADERS);

        assertThat(parsedCommandResponse).isEqualTo(commandResponse);
    }

}
