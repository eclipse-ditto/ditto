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
package org.eclipse.ditto.thingsearch.api.commands.sudo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.thingsearch.model.signals.commands.ThingSearchCommand;
import org.junit.Test;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests the {@link SudoRetrieveNamespaceReport}.
 */
public final class SudoRetrieveNamespaceReportTest {

    private static final String JSON_V2 = JsonFactory.newObjectBuilder()
            .set(ThingSearchCommand.JsonFields.TYPE, SudoRetrieveNamespaceReport.TYPE)
            .build().toString();

    @Test
    public void assertImmutability() {
        assertInstancesOf(SudoRetrieveNamespaceReport.class,
                MutabilityMatchers.areImmutable(),
                provided(AuthorizationContext.class, JsonFieldSelector.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SudoRetrieveNamespaceReport.class)
                .usingGetClass()
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void toJsonWithSchemaVersion2ReturnsExpected() {
        final SudoRetrieveNamespaceReport underTest = SudoRetrieveNamespaceReport.of(DittoHeaders.empty());
        final JsonValue jsonValue = underTest.toJson(JsonSchemaVersion.V_2, FieldType.regularOrSpecial());

        assertThat(jsonValue.toString()).hasToString(JSON_V2);
    }

}
