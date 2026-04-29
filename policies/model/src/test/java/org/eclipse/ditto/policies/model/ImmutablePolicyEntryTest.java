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
package org.eclipse.ditto.policies.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;


import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutablePolicyEntry}.
 */
public final class ImmutablePolicyEntryTest {

    private static final Label LABEL_END_USER = Label.of("EndUser");

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutablePolicyEntry.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testToAndFromJson() {
        final PolicyEntry policyEntry = ImmutablePolicyEntry.of(LABEL_END_USER,
                Subjects.newInstance(TestConstants.Policy.SUBJECT),
                Resources.newInstance(
                        Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE, TestConstants.Policy.RESOURCE_PATH, EffectedPermissions.newInstance(
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                Permissions.newInstance(TestConstants.Policy.PERMISSION_WRITE)))));

        final JsonObject policyEntryJson = policyEntry.toJson();
        final PolicyEntry policyEntry1 = ImmutablePolicyEntry.fromJson(policyEntry.getLabel(), policyEntryJson);

        assertThat(policyEntry).isEqualTo(policyEntry1);
    }

    @Test(expected = NullPointerException.class)
    public void testFromJsonWithNullLabel() {
        ImmutablePolicyEntry.fromJson(null, JsonFactory.newObjectBuilder().build());
    }

    @Test
    public void testFromJsonEmptyWithLabel() {
        final JsonObject jsonObject = JsonFactory.newObjectBuilder().build();

        final PolicyEntry entry = ImmutablePolicyEntry.fromJson(LABEL_END_USER, jsonObject);
        assertThat(entry.getSubjects()).isEmpty();
        assertThat(entry.getResources()).isEmpty();
    }

    @Test(expected = PolicyEntryInvalidException.class)
    public void testFromJsonWithInvalidImportableType() {
        ImmutablePolicyEntry.fromJson("DEFAULT", JsonObject.of(
                "{ \"subjects\": {}, \"resources\": {}, \"importable\": \"invalid\" }"));
    }

    @Test
    public void testFromJsonWithoutImportableType() {
        final PolicyEntry entry = ImmutablePolicyEntry.fromJson("DEFAULT", JsonObject.of(
                "{ \"subjects\": {}, \"resources\": {} }"));
        assertThat(entry.getImportableType()).isEqualTo(ImportableType.IMPLICIT);
    }

    @Test
    public void testFromJsonOnlySchemaVersion() {
        final JsonObject jsonObject = JsonFactory.newObjectBuilder()
                .set(JsonSchemaVersion.getJsonKey(), JsonSchemaVersion.V_2.toInt())
                .build();

        final PolicyEntry entry = ImmutablePolicyEntry.fromJson("EndUser", jsonObject);
        assertThat(entry.getSubjects()).isEmpty();
        assertThat(entry.getResources()).isEmpty();
    }

    @Test
    public void testFromJsonWithTwoEntriesGivesOnlyFirst() {
        final JsonObject jsonObject = JsonFactory.newObjectBuilder()
                .set(LABEL_END_USER, JsonFactory.newObjectBuilder()
                        .set(PolicyEntry.JsonFields.SUBJECTS,
                                Subjects.newInstance(TestConstants.Policy.SUBJECT).toJson())
                        .set(PolicyEntry.JsonFields.RESOURCES,
                                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE, TestConstants.Policy.RESOURCE_PATH,
                                        EffectedPermissions.newInstance(
                                                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                                Permissions.newInstance(TestConstants.Policy.PERMISSION_WRITE))))
                                        .toJson())
                        .build())
                .set("Support", JsonFactory.newObjectBuilder()
                        .set(PolicyEntry.JsonFields.SUBJECTS,
                                Subjects.newInstance(TestConstants.Policy.SUBJECT).toJson())
                        .set(PolicyEntry.JsonFields.RESOURCES,
                                Resources.newInstance(Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE, TestConstants.Policy.RESOURCE_PATH,
                                        EffectedPermissions.newInstance(
                                                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                                Permissions.newInstance(TestConstants.Policy.PERMISSION_WRITE))))
                                        .toJson())
                        .build())
                .build();

        final PolicyEntry policyEntry = ImmutablePolicyEntry.fromJson(LABEL_END_USER,
                jsonObject.getValueOrThrow(LABEL_END_USER.getJsonFieldDefinition()));

        assertThat(policyEntry.getLabel()).isEqualTo(LABEL_END_USER);
    }

    @Test
    public void ensureTwoPolicyEntriesAreSemanticallyTheSameIfOnlySubjectPayloadDiffers() {
        final SubjectId subjectId = SubjectId.newInstance("the:subject");
        final Resource resource = Resource.newInstance("thing", "/",
                EffectedPermissions.newInstance(Collections.singleton("READ"), null));

        final PolicyEntry entry1 = PolicyEntry.newInstance("foo",
                Collections.singleton(Subject.newInstance(subjectId, SubjectType.GENERATED)),
                Collections.singleton(resource)
        );
        final PolicyEntry entry2 = PolicyEntry.newInstance("foo",
                Collections.singleton(Subject.newInstance(subjectId, SubjectType.UNKNOWN)), // only difference is the subjectType!
                Collections.singleton(resource)
        );
        assertThat(entry1.isSemanticallySameAs(entry2)).isTrue();
    }

    @Test
    public void ensureTwoPolicyEntriesAreSemanticallyDifferentIfSubjectIdsDiffer() {
        final SubjectId subjectId1 = SubjectId.newInstance("the:subject1");
        final SubjectId subjectId2 = SubjectId.newInstance("the:subject2");
        final Resource resource = Resource.newInstance("thing", "/",
                EffectedPermissions.newInstance(Collections.singleton("READ"), null));

        final PolicyEntry entry1 = PolicyEntry.newInstance("foo",
                Collections.singleton(Subject.newInstance(subjectId1, SubjectType.GENERATED)),
                Collections.singleton(resource)
        );
        final PolicyEntry entry2 = PolicyEntry.newInstance("foo",
                Collections.singleton(Subject.newInstance(subjectId2, SubjectType.GENERATED)),
                Collections.singleton(resource)
        );
        assertThat(entry1.isSemanticallySameAs(entry2)).isFalse();
    }

    @Test
    public void ensureTwoPolicyEntriesAreSemanticallyDifferentIfImportableTypeDiffers() {
        final SubjectId subjectId = SubjectId.newInstance("the:subject");
        final Resource resource = Resource.newInstance("thing", "/",
                EffectedPermissions.newInstance(Collections.singleton("READ"), null));

        final PolicyEntry entry1 = PolicyEntry.newInstance("foo",
                Collections.singleton(Subject.newInstance(subjectId, SubjectType.UNKNOWN)),
                Collections.singleton(resource),
                ImportableType.IMPLICIT
        );
        final PolicyEntry entry2 = PolicyEntry.newInstance("foo",
                Collections.singleton(Subject.newInstance(subjectId, SubjectType.UNKNOWN)), // only difference is the subjectType!
                Collections.singleton(resource),
                ImportableType.NEVER
        );
        assertThat(entry1.isSemanticallySameAs(entry2)).isFalse();
    }

    @Test
    public void testToAndFromJsonWithAllowedAdditions() {
        final Set<AllowedAddition> allowedAdditions = new HashSet<>();
        allowedAdditions.add(AllowedAddition.SUBJECTS);
        allowedAdditions.add(AllowedAddition.RESOURCES);

        final PolicyEntry policyEntry = ImmutablePolicyEntry.of(LABEL_END_USER,
                Subjects.newInstance(TestConstants.Policy.SUBJECT),
                Resources.newInstance(
                        Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE, TestConstants.Policy.RESOURCE_PATH,
                                EffectedPermissions.newInstance(
                                        Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                        Permissions.newInstance(TestConstants.Policy.PERMISSION_WRITE)))),
                ImportableType.EXPLICIT,
                allowedAdditions);

        final JsonObject policyEntryJson = policyEntry.toJson();
        final PolicyEntry parsed = ImmutablePolicyEntry.fromJson(policyEntry.getLabel(), policyEntryJson);

        assertThat(parsed).isEqualTo(policyEntry);
        assertThat(parsed.getAllowedAdditions()).contains(allowedAdditions);
    }

    @Test
    public void testFromJsonWithoutAllowedAdditionsDefaultsToEmpty() {
        final PolicyEntry entry = ImmutablePolicyEntry.fromJson("DEFAULT", JsonObject.of(
                "{ \"subjects\": {}, \"resources\": {} }"));
        assertThat(entry.getAllowedAdditions()).isNotPresent();
    }

    @Test(expected = PolicyEntryInvalidException.class)
    public void testFromJsonWithInvalidAllowedAdditionsValue() {
        ImmutablePolicyEntry.fromJson("DEFAULT", JsonObject.of(
                "{ \"subjects\": {}, \"resources\": {}, \"allowedAdditions\": [\"invalid\"] }"));
    }

    @Test(expected = PolicyEntryInvalidException.class)
    public void testFromJsonRejectsNonStringInAllowedAdditions() {
        // Non-string elements (e.g. numbers, objects, null) must be rejected — not silently dropped —
        // so callers see a clear error rather than data loss.
        ImmutablePolicyEntry.fromJson("DEFAULT", JsonObject.of(
                "{ \"subjects\": {}, \"resources\": {}, \"allowedAdditions\": [\"subjects\", 42] }"));
    }

    @Test
    public void testToJsonOmitsAllowedAdditionsWhenEmpty() {
        final PolicyEntry policyEntry = ImmutablePolicyEntry.of(LABEL_END_USER,
                Subjects.newInstance(TestConstants.Policy.SUBJECT),
                Resources.newInstance(
                        Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE, TestConstants.Policy.RESOURCE_PATH,
                                EffectedPermissions.newInstance(
                                        Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                        Permissions.newInstance(TestConstants.Policy.PERMISSION_WRITE)))));

        final JsonObject json = policyEntry.toJson();
        assertThat(json.contains("allowedAdditions")).isFalse();
    }

    @Test
    public void ensureTwoPolicyEntriesAreSemanticallyDifferentIfAllowedAdditionsDiffer() {
        final SubjectId subjectId = SubjectId.newInstance("the:subject");
        final Resource resource = Resource.newInstance("thing", "/",
                EffectedPermissions.newInstance(Collections.singleton("READ"), null));

        final PolicyEntry entry1 = ImmutablePolicyEntry.of(Label.of("foo"),
                Subjects.newInstance(Subject.newInstance(subjectId, SubjectType.GENERATED)),
                Resources.newInstance(resource),
                ImportableType.IMPLICIT,
                Collections.singleton(AllowedAddition.SUBJECTS)
        );
        final PolicyEntry entry2 = ImmutablePolicyEntry.of(Label.of("foo"),
                Subjects.newInstance(Subject.newInstance(subjectId, SubjectType.GENERATED)),
                Resources.newInstance(resource),
                ImportableType.IMPLICIT,
                Collections.emptySet()
        );
        assertThat(entry1.isSemanticallySameAs(entry2)).isFalse();
    }

    @Test
    public void testToAndFromJsonWithNamespaces() {
        final List<String> namespaces = Arrays.asList("com.acme", "com.acme.*");
        final PolicyEntry policyEntry = ImmutablePolicyEntry.of(LABEL_END_USER,
                Subjects.newInstance(TestConstants.Policy.SUBJECT),
                Resources.newInstance(
                        Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE, TestConstants.Policy.RESOURCE_PATH,
                                EffectedPermissions.newInstance(
                                        Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                        Permissions.newInstance(TestConstants.Policy.PERMISSION_WRITE)))),
                namespaces,
                ImportableType.IMPLICIT,
                Collections.emptySet());

        final JsonObject policyEntryJson = policyEntry.toJson();
        assertThat(policyEntryJson.contains("namespaces")).isTrue();

        final PolicyEntry parsed = ImmutablePolicyEntry.fromJson(policyEntry.getLabel(), policyEntryJson);
        assertThat(parsed).isEqualTo(policyEntry);
        assertThat(parsed.getNamespaces()).contains(namespaces);
    }

    @Test
    public void testFromJsonWithoutNamespacesDefaultsToEmpty() {
        final PolicyEntry entry = ImmutablePolicyEntry.fromJson("DEFAULT", JsonObject.of(
                "{ \"subjects\": {}, \"resources\": {} }"));
        assertThat(entry.getNamespaces()).isNotPresent();
    }

    @Test
    public void testToJsonOmitsNamespacesWhenEmpty() {
        final PolicyEntry policyEntry = ImmutablePolicyEntry.of(LABEL_END_USER,
                Subjects.newInstance(TestConstants.Policy.SUBJECT),
                Resources.newInstance(
                        Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE, TestConstants.Policy.RESOURCE_PATH,
                                EffectedPermissions.newInstance(
                                        Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                        Permissions.newInstance(TestConstants.Policy.PERMISSION_WRITE)))));

        assertThat(policyEntry.toJson().contains("namespaces")).isFalse();
    }

    @Test(expected = PolicyEntryInvalidException.class)
    public void testFromJsonWithInvalidNamespacePattern() {
        ImmutablePolicyEntry.fromJson("DEFAULT", JsonObject.of(
                "{ \"subjects\": {}, \"resources\": {}, \"namespaces\": [\"*\"] }"));
    }

    @Test(expected = PolicyEntryInvalidException.class)
    public void testFromJsonWithEmptyStringNamespacePattern() {
        ImmutablePolicyEntry.fromJson("DEFAULT", JsonObject.of(
                "{ \"subjects\": {}, \"resources\": {}, \"namespaces\": [\"\"] }"));
    }

    @Test(expected = PolicyEntryInvalidException.class)
    public void testProgrammaticCreationWithInvalidNamespacePattern() {
        PoliciesModelFactory.newPolicyEntry("DEFAULT",
                PoliciesModelFactory.emptySubjects(),
                PoliciesModelFactory.emptyResources(),
                Collections.singletonList("invalid*pattern"),
                ImportableType.IMPLICIT,
                Collections.emptySet());
    }

    @Test
    public void testAppliesToNamespaceWithEmptyListMatchesAll() {
        final PolicyEntry entry = ImmutablePolicyEntry.fromJson("DEFAULT", JsonObject.of(
                "{ \"subjects\": {}, \"resources\": {} }"));
        assertThat(entry.appliesToNamespace("com.acme")).isTrue();
        assertThat(entry.appliesToNamespace("org.example")).isTrue();
    }

    @Test
    public void testAppliesToNamespaceExactMatch() {
        final PolicyEntry entry = ImmutablePolicyEntry.fromJson("DEFAULT", JsonObject.of(
                "{ \"subjects\": {}, \"resources\": {}, \"namespaces\": [\"com.acme\"] }"));
        assertThat(entry.appliesToNamespace("com.acme")).isTrue();
        assertThat(entry.appliesToNamespace("com.acme.vehicles")).isFalse();
        assertThat(entry.appliesToNamespace("org.example")).isFalse();
    }

    @Test
    public void testAppliesToNamespaceWildcardMatch() {
        final PolicyEntry entry = ImmutablePolicyEntry.fromJson("DEFAULT", JsonObject.of(
                "{ \"subjects\": {}, \"resources\": {}, \"namespaces\": [\"com.acme.*\"] }"));
        assertThat(entry.appliesToNamespace("com.acme")).isFalse();
        assertThat(entry.appliesToNamespace("com.acme.vehicles")).isTrue();
        assertThat(entry.appliesToNamespace("com.acme.vehicles.trucks")).isTrue();
        assertThat(entry.appliesToNamespace("org.example")).isFalse();
    }

    @Test
    public void testAppliesToNamespaceMultiplePatterns() {
        final PolicyEntry entry = ImmutablePolicyEntry.fromJson("DEFAULT", JsonObject.of(
                "{ \"subjects\": {}, \"resources\": {}, \"namespaces\": [\"com.acme\", \"com.acme.*\"] }"));
        assertThat(entry.appliesToNamespace("com.acme")).isTrue();
        assertThat(entry.appliesToNamespace("com.acme.vehicles")).isTrue();
        assertThat(entry.appliesToNamespace("org.example")).isFalse();
    }

    @Test
    public void ensureTwoPolicyEntriesAreSemanticallyDifferentIfNamespacesDiffer() {
        final SubjectId subjectId = SubjectId.newInstance("the:subject");
        final Resource resource = Resource.newInstance("thing", "/",
                EffectedPermissions.newInstance(Collections.singleton("READ"), null));

        final PolicyEntry entry1 = ImmutablePolicyEntry.of(Label.of("foo"),
                Subjects.newInstance(Subject.newInstance(subjectId, SubjectType.GENERATED)),
                Resources.newInstance(resource),
                Collections.singletonList("com.acme"),
                ImportableType.IMPLICIT,
                Collections.emptySet()
        );
        final PolicyEntry entry2 = ImmutablePolicyEntry.of(Label.of("foo"),
                Subjects.newInstance(Subject.newInstance(subjectId, SubjectType.GENERATED)),
                Resources.newInstance(resource),
                Collections.emptyList(),
                ImportableType.IMPLICIT,
                Collections.emptySet()
        );
        assertThat(entry1.isSemanticallySameAs(entry2)).isFalse();
    }

    @Test
    public void ensureTwoPolicyEntriesAreSemanticallyDifferentIfOnlyResourcesDiffer() {
        final SubjectId subjectId = SubjectId.newInstance("the:subject");
        final Resource resource1 = Resource.newInstance("thing", "/",
                EffectedPermissions.newInstance(Collections.singleton("READ"), null));
        final Resource resource2 = Resource.newInstance("thing", "/attributes",
                EffectedPermissions.newInstance(Collections.singleton("READ"), null));

        final PolicyEntry entry1 = PolicyEntry.newInstance("foo",
                Collections.singleton(Subject.newInstance(subjectId, SubjectType.GENERATED)),
                Collections.singleton(resource1)
        );
        final PolicyEntry entry2 = PolicyEntry.newInstance("foo",
                Collections.singleton(Subject.newInstance(subjectId, SubjectType.GENERATED)),
                Arrays.asList(resource1, resource2)
        );

        assertThat(entry1.isSemanticallySameAs(entry2)).isFalse();
    }

    @Test
    public void testJsonRoundtripWithImportReferences() {
        final EntryReference importRef = PoliciesModelFactory.newEntryReference(
                PolicyId.of("ns:imported"), Label.of("driver"));
        final List<EntryReference> references = Collections.singletonList(importRef);

        final PolicyEntry policyEntry = PoliciesModelFactory.newPolicyEntry(LABEL_END_USER,
                Subjects.newInstance(TestConstants.Policy.SUBJECT),
                Resources.newInstance(
                        Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE, TestConstants.Policy.RESOURCE_PATH,
                                EffectedPermissions.newInstance(
                                        Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                        Permissions.newInstance(TestConstants.Policy.PERMISSION_WRITE)))),
                null,
                ImportableType.IMPLICIT,
                null,
                references);

        final JsonObject policyEntryJson = policyEntry.toJson();

        // Verify JSON contains "references" array with the correct import reference object
        assertThat(policyEntryJson.contains(PolicyEntry.JsonFields.REFERENCES.getPointer())).isTrue();
        final JsonArray refsArray = policyEntryJson.getValueOrThrow(PolicyEntry.JsonFields.REFERENCES);
        assertThat(refsArray).hasSize(1);
        final JsonObject refObj = refsArray.get(0).orElseThrow(NoSuchElementException::new).asObject();
        assertThat(refObj.getValueOrThrow(EntryReference.JsonFields.IMPORT)).isEqualTo("ns:imported");
        assertThat(refObj.getValueOrThrow(EntryReference.JsonFields.ENTRY)).isEqualTo("driver");

        // Deserialize and verify round-trip
        final PolicyEntry parsed = ImmutablePolicyEntry.fromJson(policyEntry.getLabel(), policyEntryJson);
        assertThat(parsed.getReferences()).hasSize(1);
        assertThat(parsed.getReferences().get(0).getImportedPolicyId()).contains(PolicyId.of("ns:imported"));
        assertThat(parsed.getReferences().get(0).getEntryLabel()).isEqualTo(Label.of("driver"));
        assertThat(parsed).isEqualTo(policyEntry);
    }

    @Test
    public void testJsonRoundtripWithLocalReferences() {
        final EntryReference localRef = PoliciesModelFactory.newLocalEntryReference(Label.of("shared-subjects"));
        final List<EntryReference> references = Collections.singletonList(localRef);

        final PolicyEntry policyEntry = PoliciesModelFactory.newPolicyEntry(LABEL_END_USER,
                Subjects.newInstance(TestConstants.Policy.SUBJECT),
                Resources.newInstance(
                        Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE, TestConstants.Policy.RESOURCE_PATH,
                                EffectedPermissions.newInstance(
                                        Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                        Permissions.newInstance(TestConstants.Policy.PERMISSION_WRITE)))),
                null,
                ImportableType.IMPLICIT,
                null,
                references);

        final JsonObject policyEntryJson = policyEntry.toJson();

        // Verify JSON contains "references" array with only an "entry" field (no "import")
        assertThat(policyEntryJson.contains(PolicyEntry.JsonFields.REFERENCES.getPointer())).isTrue();
        final JsonArray refsArray = policyEntryJson.getValueOrThrow(PolicyEntry.JsonFields.REFERENCES);
        assertThat(refsArray).hasSize(1);
        final JsonObject refObj = refsArray.get(0).orElseThrow(NoSuchElementException::new).asObject();
        assertThat(refObj.contains(EntryReference.JsonFields.IMPORT.getPointer())).isFalse();
        assertThat(refObj.getValueOrThrow(EntryReference.JsonFields.ENTRY)).isEqualTo("shared-subjects");

        // Deserialize and verify round-trip
        final PolicyEntry parsed = ImmutablePolicyEntry.fromJson(policyEntry.getLabel(), policyEntryJson);
        assertThat(parsed.getReferences()).hasSize(1);
        assertThat(parsed.getReferences().get(0).isLocalReference()).isTrue();
        assertThat(parsed.getReferences().get(0).getEntryLabel()).isEqualTo(Label.of("shared-subjects"));
        assertThat(parsed).isEqualTo(policyEntry);
    }

    @Test
    public void testJsonRoundtripWithMixedReferences() {
        final EntryReference importRef = PoliciesModelFactory.newEntryReference(
                PolicyId.of("ns:imported"), Label.of("driver"));
        final EntryReference localRef = PoliciesModelFactory.newLocalEntryReference(Label.of("shared-subjects"));
        final List<EntryReference> references = Arrays.asList(importRef, localRef);

        final PolicyEntry policyEntry = PoliciesModelFactory.newPolicyEntry(LABEL_END_USER,
                Subjects.newInstance(TestConstants.Policy.SUBJECT),
                Resources.newInstance(
                        Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE, TestConstants.Policy.RESOURCE_PATH,
                                EffectedPermissions.newInstance(
                                        Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                        Permissions.newInstance(TestConstants.Policy.PERMISSION_WRITE)))),
                null,
                ImportableType.IMPLICIT,
                null,
                references);

        final JsonObject policyEntryJson = policyEntry.toJson();

        // Verify JSON contains "references" array with both entries
        final JsonArray refsArray = policyEntryJson.getValueOrThrow(PolicyEntry.JsonFields.REFERENCES);
        assertThat(refsArray).hasSize(2);

        // First element: import reference
        final JsonObject importRefObj = refsArray.get(0).orElseThrow(NoSuchElementException::new).asObject();
        assertThat(importRefObj.getValueOrThrow(EntryReference.JsonFields.IMPORT)).isEqualTo("ns:imported");
        assertThat(importRefObj.getValueOrThrow(EntryReference.JsonFields.ENTRY)).isEqualTo("driver");

        // Second element: local reference
        final JsonObject localRefObj = refsArray.get(1).orElseThrow(NoSuchElementException::new).asObject();
        assertThat(localRefObj.contains(EntryReference.JsonFields.IMPORT.getPointer())).isFalse();
        assertThat(localRefObj.getValueOrThrow(EntryReference.JsonFields.ENTRY)).isEqualTo("shared-subjects");

        // Deserialize and verify round-trip
        final PolicyEntry parsed = ImmutablePolicyEntry.fromJson(policyEntry.getLabel(), policyEntryJson);
        assertThat(parsed.getReferences()).hasSize(2);
        assertThat(parsed.getReferences().get(0).isImportReference()).isTrue();
        assertThat(parsed.getReferences().get(1).isLocalReference()).isTrue();
        assertThat(parsed).isEqualTo(policyEntry);
    }

    @Test
    public void testJsonRoundtripWithEmptyReferences() {
        // Create entry without references (null)
        final PolicyEntry policyEntry = PoliciesModelFactory.newPolicyEntry(LABEL_END_USER,
                Subjects.newInstance(TestConstants.Policy.SUBJECT),
                Resources.newInstance(
                        Resource.newInstance(TestConstants.Policy.RESOURCE_TYPE, TestConstants.Policy.RESOURCE_PATH,
                                EffectedPermissions.newInstance(
                                        Permissions.newInstance(TestConstants.Policy.PERMISSION_READ),
                                        Permissions.newInstance(TestConstants.Policy.PERMISSION_WRITE)))),
                null,
                ImportableType.IMPLICIT,
                null,
                null);

        final JsonObject policyEntryJson = policyEntry.toJson();

        // Verify JSON does NOT contain a "references" field
        assertThat(policyEntryJson.contains(PolicyEntry.JsonFields.REFERENCES.getPointer())).isFalse();

        // Deserialize and verify getReferences() returns empty list
        final PolicyEntry parsed = ImmutablePolicyEntry.fromJson(policyEntry.getLabel(), policyEntryJson);
        assertThat(parsed.getReferences()).isEmpty();
    }

}
