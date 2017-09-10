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
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.policies.modify.ModifyPolicy;
import org.eclipse.ditto.signals.commands.policies.modify.PolicyModifyCommandRegistry;
import org.eclipse.ditto.signals.commands.policies.query.PolicyQueryCommandRegistry;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicy;
import org.junit.Test;


/**
 * Unit test for {@link PolicyCommandRegistryTest}.
 */
public class PolicyCommandRegistryTest {


    @Test
    public void parsePolicyModifyCommand() {
        final PolicyModifyCommandRegistry commandRegistry = PolicyModifyCommandRegistry.newInstance();

        final ModifyPolicy command = ModifyPolicy.of(TestConstants.Policy.POLICY_ID,
                TestConstants.Policy.POLICY, TestConstants.DITTO_HEADERS);
        final JsonObject jsonObject = command.toJson(FieldType.regularOrSpecial());

        final Command parsedCommand = commandRegistry.parse(jsonObject, TestConstants.DITTO_HEADERS);

        assertThat(parsedCommand).isEqualTo(command);
    }


    @Test
    public void parsePolicyQueryCommand() {
        final PolicyQueryCommandRegistry commandRegistry = PolicyQueryCommandRegistry.newInstance();

        final RetrievePolicy command = RetrievePolicy.of(
                TestConstants.Policy.POLICY_ID, TestConstants.DITTO_HEADERS);
        final JsonObject jsonObject = command.toJson(FieldType.regularOrSpecial());

        final Command parsedCommand = commandRegistry.parse(jsonObject, TestConstants.DITTO_HEADERS);

        assertThat(parsedCommand).isEqualTo(command);
    }

}
