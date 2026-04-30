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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link ImmutableEntryReference}.
 */
final class ImmutableEntryReferenceTest {

    private static final PolicyId POLICY_ID = PolicyId.of("ns", "id");
    private static final Label LABEL = Label.of("myLabel");

    @Test
    void testImportReference() {
        final EntryReference ref = ImmutableEntryReference.of(POLICY_ID, LABEL);

        assertThat(ref.isImportReference()).isTrue();
        assertThat(ref.isLocalReference()).isFalse();
        assertThat(ref.getImportedPolicyId()).contains(POLICY_ID);
        assertThat(ref.getEntryLabel()).isEqualTo(LABEL);
    }

    @Test
    void testLocalReference() {
        final EntryReference ref = ImmutableEntryReference.ofLocal(LABEL);

        assertThat(ref.isLocalReference()).isTrue();
        assertThat(ref.isImportReference()).isFalse();
        assertThat(ref.getImportedPolicyId()).isEmpty();
        assertThat(ref.getEntryLabel()).isEqualTo(LABEL);
    }

    @Test
    void testFromJsonImportReference() {
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set(EntryReference.JsonFields.IMPORT, "ns:id")
                .set(EntryReference.JsonFields.ENTRY, "myLabel")
                .build();

        final EntryReference ref = ImmutableEntryReference.fromJson(json);

        assertThat(ref.isImportReference()).isTrue();
        assertThat(ref.isLocalReference()).isFalse();
        assertThat(ref.getImportedPolicyId()).contains(PolicyId.of("ns:id"));
        assertThat(ref.getEntryLabel()).isEqualTo(Label.of("myLabel"));
    }

    @Test
    void testFromJsonLocalReference() {
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set(EntryReference.JsonFields.ENTRY, "myLabel")
                .build();

        final EntryReference ref = ImmutableEntryReference.fromJson(json);

        assertThat(ref.isLocalReference()).isTrue();
        assertThat(ref.isImportReference()).isFalse();
        assertThat(ref.getImportedPolicyId()).isEmpty();
        assertThat(ref.getEntryLabel()).isEqualTo(Label.of("myLabel"));
    }

    @Test
    void testToJsonImportReference() {
        final EntryReference ref = ImmutableEntryReference.of(POLICY_ID, LABEL);

        final JsonObject json = ref.toJson();

        assertThat(json.getValue(EntryReference.JsonFields.IMPORT)).contains("ns:id");
        assertThat(json.getValue(EntryReference.JsonFields.ENTRY)).contains("myLabel");
    }

    @Test
    void testToJsonLocalReference() {
        final EntryReference ref = ImmutableEntryReference.ofLocal(LABEL);

        final JsonObject json = ref.toJson();

        assertThat(json.getValue(EntryReference.JsonFields.ENTRY)).contains("myLabel");
        assertThat(json.getValue(EntryReference.JsonFields.IMPORT)).isEmpty();
    }

    @Test
    void testJsonRoundtrip() {
        final EntryReference importRef = ImmutableEntryReference.of(POLICY_ID, LABEL);
        final JsonObject json = importRef.toJson();
        final EntryReference deserialized = ImmutableEntryReference.fromJson(json);

        assertThat(deserialized).isEqualTo(importRef);

        final EntryReference localRef = ImmutableEntryReference.ofLocal(LABEL);
        final JsonObject localJson = localRef.toJson();
        final EntryReference localDeserialized = ImmutableEntryReference.fromJson(localJson);

        assertThat(localDeserialized).isEqualTo(localRef);
    }

    @Test
    void testEqualsAndHashCode() {
        final EntryReference ref1 = ImmutableEntryReference.of(POLICY_ID, LABEL);
        final EntryReference ref2 = ImmutableEntryReference.of(POLICY_ID, LABEL);
        final EntryReference refDifferentLabel = ImmutableEntryReference.of(POLICY_ID, Label.of("other"));
        final EntryReference refDifferentPolicy = ImmutableEntryReference.of(PolicyId.of("other", "id"), LABEL);
        final EntryReference localRef = ImmutableEntryReference.ofLocal(LABEL);

        // same values are equal
        assertThat(ref1).isEqualTo(ref2);
        assertThat(ref1.hashCode()).isEqualTo(ref2.hashCode());

        // different label
        assertThat(ref1).isNotEqualTo(refDifferentLabel);

        // different policy ID
        assertThat(ref1).isNotEqualTo(refDifferentPolicy);

        // import ref vs local ref with same label
        assertThat(ref1).isNotEqualTo(localRef);

        // local references with same label
        final EntryReference localRef2 = ImmutableEntryReference.ofLocal(LABEL);
        assertThat(localRef).isEqualTo(localRef2);
        assertThat(localRef.hashCode()).isEqualTo(localRef2.hashCode());

        // not equal to null
        assertThat(ref1).isNotEqualTo(null);
    }

    @Test
    void testFromJsonRejectsNullImportField() {
        // {"import": null, "entry": "x"} must NOT be silently coerced to a local reference.
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("import", JsonFactory.nullLiteral())
                .set("entry", LABEL.toString())
                .build();

        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> ImmutableEntryReference.fromJson(json))
                .withMessageContaining("'import'");
    }

    @Test
    void testFromJsonRejectsEmptyImportField() {
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("import", "")
                .set("entry", LABEL.toString())
                .build();

        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> ImmutableEntryReference.fromJson(json))
                .withMessageContaining("'import'");
    }

}
