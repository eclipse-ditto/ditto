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

/*
 * Copyright Bosch.IO GmbH 2021
 *
 *  All rights reserved, also regarding any disposal, exploitation,
 *  reproduction, editing, distribution, as well as in the event of
 *  applications for industrial property rights.
 *
 *  This software is the confidential and proprietary information
 *  of Bosch.IO GmbH. You shall not disclose
 *  such Confidential Information and shall use it only in
 *  accordance with the terms of the license agreement you
 *  entered into with Bosch.IO GmbH.
 */
package org.eclipse.ditto.connectivity.model.signals.commands.query;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.GlobalCommandRegistry;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommand;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Test for Retrieve Connections.
 */
public final class RetrieveConnectionsTest {

    private static JsonObject knownJson;

    @Rule
    public TestName testName = new TestName();

    private DittoHeaders dittoHeaders;

    @BeforeClass
    public static void setUpClass() {

        knownJson = JsonFactory.newObjectBuilder()
                .set(ConnectivityCommand.JsonFields.TYPE, RetrieveConnections.TYPE)
                .set(RetrieveConnections.JSON_IDS_ONLY, false)
                .set(RetrieveConnections.JSON_DEFAULT_TIMEOUT, 0L)
                .build();
    }

    @Before
    public void setUp() {
        dittoHeaders = DittoHeaders.newBuilder()
                .correlationId(testName.getMethodName())
                .build();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveConnections.class,
                areImmutable(),
                assumingFields("connectionIds").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveConnections.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void toJsonReturnsExpected() {
        final RetrieveConnections underTest = RetrieveConnections.newInstance(false, Duration.ZERO, dittoHeaders);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        DittoJsonAssertions.assertThat(actualJson).isEqualTo(knownJson);
    }

    @Test
    public void getResourcePathReturnsExpected() {
        final RetrieveConnections underTest = RetrieveConnections.newInstance(false, Duration.ZERO, dittoHeaders);
        assertThat(underTest.getResourcePath()).isEqualTo(JsonPointer.of("/connections"));
    }

    @Test
    public void createInstanceFromValidJson() {
        final RetrieveConnections
                underTest = RetrieveConnections.fromJson(knownJson.toString(), dittoHeaders);

        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(underTest).isNotNull();
            softly.assertThat(underTest.getIdsOnly()).isEqualTo(false);
        }
    }

    @Test
    public void deserializeRetrieveConnections() {
        final Signal<?> actual = GlobalCommandRegistry.getInstance().parse(knownJson, dittoHeaders);
        final Signal<?> expected = RetrieveConnections.newInstance(false, Duration.ZERO, dittoHeaders);

        Assertions.assertThat(actual).isEqualTo(expected);
    }

}
