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
package org.eclipse.ditto.signals.commands.policies.actions;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.policies.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link TopLevelActionCommand}.
 */
public final class TopLevelActionCommandTest {

    private static final List<Label> LABELS = Collections.singletonList(TestConstants.Policy.LABEL);

    private static final ActivateTokenIntegration POLICY_ACTION_COMMAND =
            ActivateTokenIntegration.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.LABEL,
                    TestConstants.Policy.SUBJECT_ID, Instant.EPOCH, TestConstants.EMPTY_DITTO_HEADERS);

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(PolicyCommand.JsonFields.TYPE, TopLevelActionCommand.TYPE)
            .set(TopLevelActionCommand.JSON_ACTION, POLICY_ACTION_COMMAND.toJson())
            .set(TopLevelActionCommand.JSON_LABELS, JsonArray.of(TestConstants.Policy.LABEL))
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(TopLevelActionCommand.class,
                areImmutable(),
                provided(PolicyActionCommand.class).areAlsoImmutable(),
                assumingFields("authorizedLabels").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(TopLevelActionCommand.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullCommand() {
        TopLevelActionCommand.of(null, LABELS);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullLabels() {
        TopLevelActionCommand.of(POLICY_ACTION_COMMAND, null);
    }

    @Test
    public void toJsonReturnsExpected() {
        final TopLevelActionCommand underTest =
                TopLevelActionCommand.of(POLICY_ACTION_COMMAND, LABELS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final TopLevelActionCommand underTest =
                TopLevelActionCommand.fromJson(KNOWN_JSON, TestConstants.EMPTY_DITTO_HEADERS,
                        jsonObject -> ActivateTokenIntegration.fromJson(jsonObject, DittoHeaders.empty()));

        final TopLevelActionCommand expectedCommand =
                TopLevelActionCommand.of(POLICY_ACTION_COMMAND, LABELS);
        assertThat(underTest).isEqualTo(expectedCommand);
    }
}
