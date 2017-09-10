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
package org.eclipse.ditto.services.models.thingsearch.commands.sudo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;
import org.junit.Test;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests the {@link SudoRetrieveNamespaceReport}.
 */
public final class SudoRetrieveNamespaceReportTest {

    private static final String JSON_V1 = JsonFactory.newObjectBuilder()
            .set(ThingSearchCommand.JsonFields.ID, SudoRetrieveNamespaceReport.NAME)
            .build().toString();

    private static final String JSON_V2 = JsonFactory.newObjectBuilder()
            .set(ThingSearchCommand.JsonFields.TYPE, SudoRetrieveNamespaceReport.TYPE)
            .build().toString();

    /** */
    @Test
    public void assertImmutability() {
        assertInstancesOf(SudoRetrieveNamespaceReport.class,
                MutabilityMatchers.areImmutable(),
                provided(AuthorizationContext.class, JsonFieldSelector.class).isAlsoImmutable());
    }

    /** */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SudoRetrieveNamespaceReport.class)
                .usingGetClass()
                .withRedefinedSuperclass()
                .verify();
    }

    /** */
    @Test
    public void toJsonWithSchemaVersion1ReturnsExpected() {
        final SudoRetrieveNamespaceReport underTest = SudoRetrieveNamespaceReport.of(DittoHeaders.empty());
        final JsonValue jsonValue = underTest.toJson(JsonSchemaVersion.V_1, FieldType.regularOrSpecial());

        assertThat(jsonValue.toString()).isEqualTo(JSON_V1);
    }

    /** */
    @Test
    public void toJsonWithSchemaVersion2ReturnsExpected() {
        final SudoRetrieveNamespaceReport underTest = SudoRetrieveNamespaceReport.of(DittoHeaders.empty());
        final JsonValue jsonValue = underTest.toJson(JsonSchemaVersion.V_2, FieldType.regularOrSpecial());

        assertThat(jsonValue.toString()).isEqualTo(JSON_V2);
    }

}
