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
package org.eclipse.ditto.policies.model.signals.commands.actions;

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
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.policies.model.signals.commands.actions.TopLevelPolicyActionCommand}.
 */
public final class TopLevelPolicyActionCommandTest {

    private static final List<Label> LABELS = Collections.singletonList(TestConstants.Policy.LABEL);

    private static final ActivateTokenIntegration POLICY_ACTION_COMMAND =
            ActivateTokenIntegration.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.LABEL,
                    Collections.singleton(TestConstants.Policy.SUBJECT_ID), Instant.EPOCH,
                    TestConstants.EMPTY_DITTO_HEADERS);

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(PolicyCommand.JsonFields.TYPE, TopLevelPolicyActionCommand.TYPE)
            .set(TopLevelPolicyActionCommand.JSON_ACTION, POLICY_ACTION_COMMAND.toJson())
            .set(TopLevelPolicyActionCommand.JSON_LABELS, JsonArray.of(TestConstants.Policy.LABEL))
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(TopLevelPolicyActionCommand.class,
                areImmutable(),
                provided(PolicyActionCommand.class).areAlsoImmutable(),
                assumingFields("authorizedLabels").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(TopLevelPolicyActionCommand.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullCommand() {
        TopLevelPolicyActionCommand.of(null, LABELS);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullLabels() {
        TopLevelPolicyActionCommand.of(POLICY_ACTION_COMMAND, null);
    }

    @Test
    public void toJsonReturnsExpected() {
        final TopLevelPolicyActionCommand underTest =
                TopLevelPolicyActionCommand.of(POLICY_ACTION_COMMAND, LABELS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final TopLevelPolicyActionCommand underTest =
                TopLevelPolicyActionCommand.fromJson(KNOWN_JSON, TestConstants.EMPTY_DITTO_HEADERS,
                        jsonObject -> ActivateTokenIntegration.fromJson(jsonObject, DittoHeaders.empty()));

        final TopLevelPolicyActionCommand expectedCommand =
                TopLevelPolicyActionCommand.of(POLICY_ACTION_COMMAND, LABELS);
        assertThat(underTest).isEqualTo(expectedCommand);
    }
}
