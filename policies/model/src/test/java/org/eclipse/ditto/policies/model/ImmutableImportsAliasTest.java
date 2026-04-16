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

import java.util.Collections;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableImportsAlias}.
 */
public final class ImmutableImportsAliasTest {

    private static final Label LABEL = Label.of("operator");
    private static final PolicyId IMPORTED_POLICY_ID = PolicyId.of("com.example", "template");
    private static final Label ENTRY_LABEL = Label.of("admin");

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableImportsAlias.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void fromJsonWithValidTargets() {
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("targets", JsonFactory.newArrayBuilder()
                        .add(JsonFactory.newObjectBuilder()
                                .set("import", IMPORTED_POLICY_ID.toString())
                                .set("entry", ENTRY_LABEL.toString())
                                .build())
                        .build())
                .build();

        final ImportsAlias alias = ImmutableImportsAlias.fromJson(LABEL, json);

        assertThat((CharSequence) alias.getLabel()).isEqualTo(LABEL);
        assertThat(alias.getTargets()).hasSize(1);
        assertThat((CharSequence) alias.getTargets().get(0).getImportedPolicyId()).isEqualTo(IMPORTED_POLICY_ID);
        assertThat((CharSequence) alias.getTargets().get(0).getEntryLabel()).isEqualTo(ENTRY_LABEL);
    }

    @Test
    public void fromJsonRejectsStringElementInTargetsArray() {
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("targets", JsonFactory.newArrayBuilder()
                        .add("bogus")
                        .build())
                .build();

        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> ImmutableImportsAlias.fromJson(LABEL, json))
                .withMessageContaining("index 0")
                .withMessageContaining(LABEL.toString());
    }

    @Test
    public void fromJsonRejectsNumberElementInTargetsArray() {
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("targets", JsonFactory.newArrayBuilder()
                        .add(42)
                        .build())
                .build();

        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> ImmutableImportsAlias.fromJson(LABEL, json))
                .withMessageContaining("index 0")
                .withMessageContaining("not a JSON object");
    }

    @Test
    public void fromJsonRejectsMixedArrayWithValidAndInvalidElements() {
        final JsonObject validTarget = JsonFactory.newObjectBuilder()
                .set("import", IMPORTED_POLICY_ID.toString())
                .set("entry", ENTRY_LABEL.toString())
                .build();
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("targets", JsonFactory.newArrayBuilder()
                        .add(validTarget)
                        .add(42)
                        .build())
                .build();

        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> ImmutableImportsAlias.fromJson(LABEL, json))
                .withMessageContaining("index 1");
    }

    @Test
    public void fromJsonWithEmptyTargetsArraySucceeds() {
        final JsonObject json = JsonFactory.newObjectBuilder()
                .set("targets", JsonFactory.newArrayBuilder().build())
                .build();

        final ImportsAlias alias = ImmutableImportsAlias.fromJson(LABEL, json);

        assertThat(alias.getTargets()).isEmpty();
    }

    @Test
    public void roundTripJsonSerialization() {
        final ImportsAlias original = ImmutableImportsAlias.of(LABEL,
                Collections.singletonList(ImmutableImportsAliasTarget.of(IMPORTED_POLICY_ID, ENTRY_LABEL)));

        final JsonObject json = original.toJson();
        final ImportsAlias deserialized = ImmutableImportsAlias.fromJson(LABEL, json);

        assertThat(deserialized).isEqualTo(original);
    }
}
