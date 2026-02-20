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
package org.eclipse.ditto.policies.model;

import static org.eclipse.ditto.policies.model.assertions.DittoPolicyAssertions.assertThat;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableEntryAddition}.
 */
public final class ImmutableEntryAdditionTest {

    private static final Label LABEL = Label.of("testEntry");
    private static final Subjects TEST_SUBJECTS = Subjects.newInstance(
            Subject.newInstance(SubjectIssuer.GOOGLE, "someUser"));
    private static final Resources TEST_RESOURCES = Resources.newInstance(
            Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE, JsonPointer.of("attributes"),
                    EffectedPermissions.newInstance(
                            Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                            Permissions.none())));

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableEntryAddition.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testToAndFromJsonWithSubjectsAndResources() {
        final EntryAddition addition = ImmutableEntryAddition.of(LABEL, TEST_SUBJECTS, TEST_RESOURCES);

        final JsonObject json = addition.toJson();
        final EntryAddition fromJson = ImmutableEntryAddition.fromJson(LABEL, json);

        assertThat(addition).isEqualTo(fromJson);
    }

    @Test
    public void testToAndFromJsonWithSubjectsOnly() {
        final EntryAddition addition = ImmutableEntryAddition.of(LABEL, TEST_SUBJECTS, null);

        final JsonObject json = addition.toJson();
        final EntryAddition fromJson = ImmutableEntryAddition.fromJson(LABEL, json);

        assertThat(addition).isEqualTo(fromJson);
        assertThat(fromJson.getSubjects()).isPresent();
        assertThat(fromJson.getResources()).isEmpty();
    }

    @Test
    public void testToAndFromJsonWithResourcesOnly() {
        final EntryAddition addition = ImmutableEntryAddition.of(LABEL, null, TEST_RESOURCES);

        final JsonObject json = addition.toJson();
        final EntryAddition fromJson = ImmutableEntryAddition.fromJson(LABEL, json);

        assertThat(addition).isEqualTo(fromJson);
        assertThat(fromJson.getSubjects()).isEmpty();
        assertThat(fromJson.getResources()).isPresent();
    }

    @Test
    public void testToAndFromJsonEmpty() {
        final EntryAddition addition = ImmutableEntryAddition.of(LABEL, null, null);

        final JsonObject json = addition.toJson();
        final EntryAddition fromJson = ImmutableEntryAddition.fromJson(LABEL, json);

        assertThat(addition).isEqualTo(fromJson);
        assertThat(fromJson.getSubjects()).isEmpty();
        assertThat(fromJson.getResources()).isEmpty();
    }

    @Test(expected = NullPointerException.class)
    public void testOfWithNullLabel() {
        ImmutableEntryAddition.of(null, TEST_SUBJECTS, TEST_RESOURCES);
    }

}
