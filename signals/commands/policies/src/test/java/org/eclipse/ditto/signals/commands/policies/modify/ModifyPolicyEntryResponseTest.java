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
package org.eclipse.ditto.signals.commands.policies.modify;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.signals.commands.policies.PolicyCommandResponse;
import org.eclipse.ditto.signals.commands.policies.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ModifyPolicyEntryResponse}.
 */
public final class ModifyPolicyEntryResponseTest {

    private static final JsonObject KNOWN_JSON_CREATED = JsonFactory.newObjectBuilder()
            .set(PolicyCommandResponse.JsonFields.TYPE, ModifyPolicyEntryResponse.TYPE)
            .set(PolicyCommandResponse.JsonFields.STATUS, HttpStatusCode.CREATED.toInt())
            .set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, TestConstants.Policy.POLICY_ID)
            .set(ModifyPolicyEntryResponse.JSON_LABEL, TestConstants.Policy.POLICY_ENTRY.getLabel().toString())
            .set(ModifyPolicyEntryResponse.JSON_POLICY_ENTRY,
                    TestConstants.Policy.POLICY_ENTRY.toJson(FieldType.regularOrSpecial()))
            .build();

    private static final JsonObject KNOWN_JSON_UPDATED = JsonFactory.newObjectBuilder()
            .set(PolicyCommandResponse.JsonFields.TYPE, ModifyPolicyEntryResponse.TYPE)
            .set(PolicyCommandResponse.JsonFields.STATUS, HttpStatusCode.NO_CONTENT.toInt())
            .set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, TestConstants.Policy.POLICY_ID)
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyPolicyEntryResponse.class,
                areImmutable(),
                provided(PolicyEntry.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ModifyPolicyEntryResponse.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void toJsonReturnsExpected() {
        final ModifyPolicyEntryResponse underTestCreated =
                ModifyPolicyEntryResponse.created(TestConstants.Policy.POLICY_ID, TestConstants.Policy.POLICY_ENTRY,
                        TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJsonCreated = underTestCreated.toJson(FieldType.regularOrSpecial());

        assertThat(actualJsonCreated).isEqualTo(KNOWN_JSON_CREATED);

        final ModifyPolicyEntryResponse underTestUpdated =
                ModifyPolicyEntryResponse.modified(TestConstants.Policy.POLICY_ID, TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJsonUpdated = underTestUpdated.toJson(FieldType.regularOrSpecial());

        assertThat(actualJsonUpdated).isEqualTo(KNOWN_JSON_UPDATED);
    }

    @Test
    public void createInstanceFromValidJson() {
        final ModifyPolicyEntryResponse underTestCreated =
                ModifyPolicyEntryResponse.fromJson(KNOWN_JSON_CREATED, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTestCreated).isNotNull();
        assertThat(underTestCreated.getPolicyEntryCreated()).hasValue(TestConstants.Policy.POLICY_ENTRY);

        final ModifyPolicyEntryResponse underTestUpdated =
                ModifyPolicyEntryResponse.fromJson(KNOWN_JSON_UPDATED, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTestUpdated).isNotNull();
        assertThat(underTestUpdated.getPolicyEntryCreated()).isEmpty();
    }

}
