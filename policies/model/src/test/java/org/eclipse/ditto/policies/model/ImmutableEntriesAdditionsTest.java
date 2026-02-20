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

import java.util.Arrays;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableEntriesAdditions}.
 */
public final class ImmutableEntriesAdditionsTest {

    private static final Label LABEL_1 = Label.of("entry1");
    private static final Label LABEL_2 = Label.of("entry2");
    private static final Subjects TEST_SUBJECTS = Subjects.newInstance(
            Subject.newInstance(SubjectIssuer.GOOGLE, "someUser"));
    private static final Resources TEST_RESOURCES = Resources.newInstance(
            Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE, JsonPointer.of("attributes"),
                    EffectedPermissions.newInstance(
                            Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                            Permissions.none())));

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableEntriesAdditions.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testToAndFromJson() {
        final EntriesAdditions additions = ImmutableEntriesAdditions.of(Arrays.asList(
                ImmutableEntryAddition.of(LABEL_1, TEST_SUBJECTS, null),
                ImmutableEntryAddition.of(LABEL_2, null, TEST_RESOURCES)
        ));

        final JsonObject json = additions.toJson();
        final EntriesAdditions fromJson = ImmutableEntriesAdditions.fromJson(json);

        assertThat(additions).isEqualTo(fromJson);
    }

    @Test
    public void testGetAdditionByLabel() {
        final EntryAddition addition1 = ImmutableEntryAddition.of(LABEL_1, TEST_SUBJECTS, null);
        final EntryAddition addition2 = ImmutableEntryAddition.of(LABEL_2, null, TEST_RESOURCES);
        final EntriesAdditions additions = ImmutableEntriesAdditions.of(Arrays.asList(addition1, addition2));

        assertThat(additions.getAddition(LABEL_1)).contains(addition1);
        assertThat(additions.getAddition(LABEL_2)).contains(addition2);
        assertThat(additions.getAddition(Label.of("nonExistent"))).isEmpty();
    }

    @Test
    public void testEmptyEntriesAdditions() {
        final EntriesAdditions empty = ImmutableEntriesAdditions.empty();

        assertThat(empty.isEmpty()).isTrue();
        assertThat(empty.getSize()).isEqualTo(0);
        assertThat(empty.getAddition(LABEL_1)).isEmpty();
    }

    @Test
    public void testSize() {
        final EntriesAdditions additions = ImmutableEntriesAdditions.of(Arrays.asList(
                ImmutableEntryAddition.of(LABEL_1, TEST_SUBJECTS, null),
                ImmutableEntryAddition.of(LABEL_2, null, TEST_RESOURCES)
        ));

        assertThat(additions.getSize()).isEqualTo(2);
        assertThat(additions.isEmpty()).isFalse();
    }

}
