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
package org.eclipse.ditto.policies.model;

import static org.eclipse.ditto.policies.model.assertions.DittoPolicyAssertions.assertThat;

import java.util.Arrays;
import java.util.Collections;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.junit.Before;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableEffectedImports}.
 */
public final class ImmutableEffectedImportsTest {

    private EffectedImports underTest = null;

    @Before
    public void setUp() {
        underTest = ImmutableEffectedImports.of(
            Arrays.asList(Label.of("IncludedEntry1"), Label.of("IncludedEntry2")));
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableEffectedImports.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testToAndFromJson() {
        final JsonObject effectedImportsJson = underTest.toJson();
        final EffectedImports effectedImports1 = ImmutableEffectedImports.fromJson(effectedImportsJson);

        assertThat(underTest).isEqualTo(effectedImports1);
    }

    @Test
    public void testGetImportedEntries() {
        assertThat(underTest.getImportedLabels()).isEqualTo(ImportedLabels.newInstance("IncludedEntry2",
                "IncludedEntry1"));

        assertThat(underTest.getImportedLabels()).isNotEqualTo(ImportedLabels.newInstance("ExcludedEntry1",
                "ExcludedEntry3"));
    }

    @Test
    public void testGetEntriesAdditionsEmpty() {
        assertThat(underTest.getEntriesAdditions()).isEmpty();
    }

    @Test
    public void testToAndFromJsonWithEntriesAdditions() {
        final EntriesAdditions entriesAdditions = ImmutableEntriesAdditions.of(Collections.singletonList(
                ImmutableEntryAddition.of(Label.of("IncludedEntry1"),
                        Subjects.newInstance(Subject.newInstance(SubjectIssuer.GOOGLE, "extraUser")),
                        Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                                JsonPointer.of("attributes"),
                                EffectedPermissions.newInstance(
                                        Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                        Permissions.none()))))));

        final EffectedImports withAdditions = ImmutableEffectedImports.of(
                Arrays.asList(Label.of("IncludedEntry1"), Label.of("IncludedEntry2")),
                entriesAdditions);

        final JsonObject json = withAdditions.toJson();
        final EffectedImports fromJson = ImmutableEffectedImports.fromJson(json);

        assertThat(withAdditions).isEqualTo(fromJson);
        assertThat(fromJson.getEntriesAdditions()).isPresent();
        assertThat(fromJson.getEntriesAdditions().get().getSize()).isEqualTo(1);
    }

}
