/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.model.signals.events;

import static org.eclipse.ditto.base.model.assertions.DittoBaseAssertions.assertThat;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.model.signals.events.EventsourcedEvent;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link PolicyImportEntryAdditionCreated}.
 */
public final class PolicyImportEntryAdditionCreatedTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(Event.JsonFields.TIMESTAMP, TestConstants.TIMESTAMP.toString())
            .set(Event.JsonFields.TYPE, PolicyImportEntryAdditionCreated.TYPE)
            .set(Event.JsonFields.METADATA, TestConstants.METADATA.toJson())
            .set(EventsourcedEvent.JsonFields.REVISION, TestConstants.Policy.REVISION_NUMBER)
            .set(PolicyEvent.JsonFields.POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .set(PolicyImportEntryAdditionCreated.JSON_IMPORTED_POLICY_ID,
                    TestConstants.Policy.IMPORTED_POLICY_ID.toString())
            .set(PolicyImportEntryAdditionCreated.JSON_LABEL, TestConstants.Policy.LABEL.toString())
            .set(PolicyImportEntryAdditionCreated.JSON_ENTRY_ADDITION,
                    TestConstants.Policy.ENTRY_ADDITION.toJson(FieldType.regularOrSpecial()))
            .build();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(PolicyImportEntryAdditionCreated.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPolicyId() {
        PolicyImportEntryAdditionCreated.of(null, TestConstants.Policy.IMPORTED_POLICY_ID,
                TestConstants.Policy.ENTRY_ADDITION, TestConstants.Policy.REVISION_NUMBER,
                TestConstants.TIMESTAMP, TestConstants.EMPTY_DITTO_HEADERS, TestConstants.METADATA);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullImportedPolicyId() {
        PolicyImportEntryAdditionCreated.of(TestConstants.Policy.POLICY_ID, null,
                TestConstants.Policy.ENTRY_ADDITION, TestConstants.Policy.REVISION_NUMBER,
                TestConstants.TIMESTAMP, TestConstants.EMPTY_DITTO_HEADERS, TestConstants.METADATA);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullEntryAddition() {
        PolicyImportEntryAdditionCreated.of(TestConstants.Policy.POLICY_ID,
                TestConstants.Policy.IMPORTED_POLICY_ID, null, TestConstants.Policy.REVISION_NUMBER,
                TestConstants.TIMESTAMP, TestConstants.EMPTY_DITTO_HEADERS, TestConstants.METADATA);
    }

    @Test
    public void toJsonReturnsExpected() {
        final PolicyImportEntryAdditionCreated underTest = PolicyImportEntryAdditionCreated.of(
                TestConstants.Policy.POLICY_ID, TestConstants.Policy.IMPORTED_POLICY_ID,
                TestConstants.Policy.ENTRY_ADDITION, TestConstants.Policy.REVISION_NUMBER,
                TestConstants.TIMESTAMP, TestConstants.EMPTY_DITTO_HEADERS, TestConstants.METADATA);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final PolicyImportEntryAdditionCreated underTest =
                PolicyImportEntryAdditionCreated.fromJson(KNOWN_JSON.toString(),
                        TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat((CharSequence) underTest.getPolicyEntityId()).isEqualTo(TestConstants.Policy.POLICY_ID);
        assertThat((CharSequence) underTest.getImportedPolicyId()).isEqualTo(
                TestConstants.Policy.IMPORTED_POLICY_ID);
        assertThat(underTest.getEntryAddition()).isEqualTo(TestConstants.Policy.ENTRY_ADDITION);
    }

}
