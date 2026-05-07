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

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.base.model.entity.id.restriction.LengthRestrictionTestBase;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableImportedLabel}.
 */
public final class ImmutableImportedLabelTest extends LengthRestrictionTestBase {

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableImportedLabel.class)
                .usingGetClass()
                .verify();
    }

    @Test(expected = PolicyEntryInvalidException.class)
    public void createInvalidAttribute() {
        final String invalidLabel = "invalidLabel/";
        ImmutableImportedLabel.of(PolicyId.of("com.example", "importedPolicy"), invalidLabel);
    }

    @Test
    public void createValidLabelAttribute() {
        ImmutableImportedLabel.of(PolicyId.of("com.example", "importedPolicy"), generateStringWithMaxLength());
    }

    @Test(expected = PolicyEntryInvalidException.class)
    public void createTooLargeLabel() {
        ImmutableImportedLabel.of(PolicyId.of("com.example", "importedPolicy"), generateStringExceedingMaxLength());
    }

    @Test
    public void importedLabelFormatIncludesPolicyId() {
        final Label label = ImmutableImportedLabel.of(PolicyId.of("com.example", "importedPolicy"), "OWNER");
        assertThat(label.toString()).isEqualTo("imported-com.example:importedPolicy-OWNER");
    }

    @Test
    public void nsImportedLabelFormatIncludesPolicyId() {
        final Label label = ImmutableImportedLabel.ofNsImported(PolicyId.of("acme", "tenant-root"), "audit-team");
        assertThat(label.toString()).isEqualTo("nsimported-acme:tenant-root-audit-team");
    }

    @Test(expected = PolicyEntryInvalidException.class)
    public void nsImportedRejectsInvalidLabelValue() {
        ImmutableImportedLabel.ofNsImported(PolicyId.of("acme", "tenant-root"), "invalid/label");
    }

}
