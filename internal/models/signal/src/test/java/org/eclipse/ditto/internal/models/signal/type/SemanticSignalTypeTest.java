/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.models.signal.type;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link SemanticSignalType}.
 */
@RunWith(Enclosed.class)
public final class SemanticSignalTypeTest {

    public static final class GeneralFunctionalityTest {

        @Test
        public void assertImmutability() {
            assertInstancesOf(SemanticSignalType.class, areImmutable());
        }

        @Test
        public void testHashCodeAndEquals() {
            EqualsVerifier.forClass(SemanticSignalType.class)
                    .usingGetClass()
                    .verify();
        }

        @Test
        public void parseNullCharSequenceThrowsException() {
            Assertions.assertThatExceptionOfType(SignalTypeFormatException.class)
                    .isThrownBy(() -> SemanticSignalType.parseSemanticSignalType(null))
                    .withMessage("<null> is not a valid signal type.")
                    .withNoCause();
        }

        @Test
        public void parseEmptyCharSequenceThrowsException() {
            Assertions.assertThatExceptionOfType(SignalTypeFormatException.class)
                    .isThrownBy(() -> SemanticSignalType.parseSemanticSignalType(""))
                    .withMessage("<\"\"> is not a valid signal type.")
                    .withNoCause();
        }

        @Test
        public void parseCharSequenceWithInvalidDomainDelimiterIndexThrowsException() {
            final var signalType = ".commands:myCommand";
            Assertions.assertThatExceptionOfType(SignalTypeFormatException.class)
                    .isThrownBy(() -> SemanticSignalType.parseSemanticSignalType(signalType))
                    .withMessage("Signal type <%s> has wrong index of domain delimiter <%s>: 0",
                            signalType,
                            SemanticSignalType.SIGNAL_DOMAIN_DELIMITER)
                    .withNoCause();
        }

        @Test
        public void parseCharSequenceWithInvalidNameDelimiterIndexThrowsException() {
            final var signalType = "domain.commands:myCommand:";
            Assertions.assertThatExceptionOfType(SignalTypeFormatException.class)
                    .isThrownBy(() -> SemanticSignalType.parseSemanticSignalType(signalType))
                    .withMessage("Signal type <%s> has wrong index of name delimiter <%s>: %d",
                            signalType,
                            SemanticSignalType.SIGNAL_NAME_DELIMITER,
                            signalType.length() - 1)
                    .withNoCause();
        }

    }

    @RunWith(Parameterized.class)
    public static final class ParameterizedValidSignalTypeCharSequencesTest {

        @Rule
        public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

        @Parameterized.Parameter
        public String signalTypeCharSequence;

        @Parameterized.Parameter(1)
        public String expectedSignalDomain;

        @Parameterized.Parameter(2)
        public String expectedSignalName;

        @Parameterized.Parameters(name = "{0}")
        public static List<Object[]> parameters() {
            return Arrays.asList(new String[][]{
                    {"cleanup.commands:cleanupPersistence", "cleanup", "cleanupPersistence"},
                    {"cleanup.responses:cleanupPersistence", "cleanup", "cleanupPersistence"},
                    {"connectivity.commands:resetConnectionLogs", "connectivity", "resetConnectionLogs"},
                    {"connectivity.responses:resetConnectionLogs", "connectivity", "resetConnectionLogs"},
                    {"devops.commands:changeLogLevel", "devops", "changeLogLevel"},
                    {"devops.responses:changeLogLevel", "devops", "changeLogLevel"},
                    {"messages.commands:featureMessage", "messages", "featureMessage"},
                    {"messages.responses:featureResponseMessage", "messages", "featureResponseMessage"},
                    {"namespaces.commands:blockNamespace", "namespaces", "blockNamespace"},
                    {"namespaces.responses:blockNamespace", "namespaces", "blockNamespace"},
                    {"policies.commands:modifyPolicy", "policies", "modifyPolicy"},
                    {"policies.responses:modifyPolicy", "policies", "modifyPolicy"},
                    {"policies.sudo.commands:sudoRetrievePolicy", "policies.sudo", "sudoRetrievePolicy"},
                    {"policies.sudo.responses:sudoRetrievePolicy", "policies.sudo", "sudoRetrievePolicy"},
                    {"things.commands:modifyAttributes", "things", "modifyAttributes"},
                    {"things.responses:modifyAttributes", "things", "modifyAttributes"},
                    {"things.sudo.commands:sudoRetrieveThing", "things.sudo", "sudoRetrieveThing"},
                    {"things.sudo.responses:sudoRetrieveThingResponse", "things.sudo", "sudoRetrieveThingResponse"},
                    {"thing-search.commands:countThings", "thing-search", "countThings"},
                    {"thing-search.responses:countThings", "thing-search", "countThings"},
                    {"thing-search.sudo.commands:sudoRetrieveNamespaceReport", "thing-search.sudo",
                            "sudoRetrieveNamespaceReport"},
                    {"thing-search.sudo.responses:sudoRetrieveNamespaceReport", "thing-search.sudo",
                            "sudoRetrieveNamespaceReport"}
            });
        }

        @Test
        public void parseSemanticSignalTypeReturnsExpected() throws SignalTypeFormatException {
            final var underTest = SemanticSignalType.parseSemanticSignalType(signalTypeCharSequence);

            softly.assertThat(underTest.getSignalDomain()).as("signal domain").isEqualTo(expectedSignalDomain);
            softly.assertThat(underTest.getSignalName()).as("signal name").isEqualTo(expectedSignalName);
        }

    }

}