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
package org.eclipse.ditto.policies.model.signals.commands.modify;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;
import org.eclipse.ditto.policies.model.signals.commands.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ModifyPolicyImportResponse}.
 */
public final class ModifyPolicyImportResponseTest {

    private static final JsonObject KNOWN_JSON_CREATED = JsonFactory.newObjectBuilder()
            .set(PolicyCommandResponse.JsonFields.TYPE, ModifyPolicyImportResponse.TYPE)
            .set(PolicyCommandResponse.JsonFields.STATUS, HttpStatus.CREATED.getCode())
            .set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .set(ModifyPolicyImportResponse.JSON_IMPORTED_POLICY_ID, TestConstants.Policy.POLICY_IMPORT.getImportedPolicyId().toString())
            .set(ModifyPolicyImportResponse.JSON_POLICY_IMPORT,
                    TestConstants.Policy.POLICY_IMPORT.toJson(FieldType.regularOrSpecial()))
            .build();

    private static final JsonObject KNOWN_JSON_UPDATED = JsonFactory.newObjectBuilder()
            .set(PolicyCommandResponse.JsonFields.TYPE, ModifyPolicyImportResponse.TYPE)
            .set(PolicyCommandResponse.JsonFields.STATUS, HttpStatus.NO_CONTENT.getCode())
            .set(ModifyPolicyImportResponse.JSON_IMPORTED_POLICY_ID, TestConstants.Policy.POLICY_IMPORT.getImportedPolicyId().toString())
            .set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyPolicyImportResponse.class,
                areImmutable(),
                provided(PolicyImport.class, PolicyId.class, Label.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ModifyPolicyImportResponse.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void toJsonReturnsExpected() {
        final ModifyPolicyImportResponse underTestCreated =
                ModifyPolicyImportResponse.created(TestConstants.Policy.POLICY_ID, TestConstants.Policy.POLICY_IMPORT,
                        TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJsonCreated = underTestCreated.toJson(FieldType.regularOrSpecial());

        assertThat(actualJsonCreated).isEqualTo(KNOWN_JSON_CREATED);

        final ModifyPolicyImportResponse underTestUpdated =
                ModifyPolicyImportResponse.modified(TestConstants.Policy.POLICY_ID,
                        TestConstants.Policy.POLICY_IMPORT.getImportedPolicyId(), TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJsonUpdated = underTestUpdated.toJson(FieldType.regularOrSpecial());

        assertThat(actualJsonUpdated).isEqualTo(KNOWN_JSON_UPDATED);
    }

    @Test
    public void createInstanceFromValidJson() {
        final ModifyPolicyImportResponse underTestCreated =
                ModifyPolicyImportResponse.fromJson(KNOWN_JSON_CREATED, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTestCreated).isNotNull();
        assertThat(underTestCreated.getPolicyImportCreated()).hasValue(TestConstants.Policy.POLICY_IMPORT);

        final ModifyPolicyImportResponse underTestUpdated =
                ModifyPolicyImportResponse.fromJson(KNOWN_JSON_UPDATED, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTestUpdated).isNotNull();
        assertThat(underTestUpdated.getPolicyImportCreated()).isEmpty();
    }

}
