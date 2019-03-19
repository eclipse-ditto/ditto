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

package org.eclipse.ditto.signals.commands.devops;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.base.GlobalCommandResponseRegistry;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class AggregatedDevOpsCommandResponseTest {

    private static final String RESPONSES_TYPE = DevOpsCommandResponse.TYPE_PREFIX + ":" + ExecutePiggybackCommand.NAME;
    private final GlobalCommandResponseRegistry underTest = GlobalCommandResponseRegistry.getInstance();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(AggregatedDevOpsCommandResponse.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(AggregatedDevOpsCommandResponse.class, areImmutable(),
                provided(JsonObject.class).isAlsoImmutable());
    }

    @Test
    public void testAggregatedPiggybackCommandResponseFromListOfCommandResponsesSerialization() {

        final ChangeLogLevelResponse changeLogLevelResponseForThings1 =
                ChangeLogLevelResponse.of("things", "1", true, DittoHeaders.empty());
        final ChangeLogLevelResponse changeLogLevelResponseForThings2 =
                ChangeLogLevelResponse.of("things", "2", true, DittoHeaders.empty());
        final ChangeLogLevelResponse changeLogLevelResponseForGateway1 =
                ChangeLogLevelResponse.of("gateway", "1", true, DittoHeaders.empty());

        final AggregatedDevOpsCommandResponse aggregatedDevOpsCommandResponse = AggregatedDevOpsCommandResponse.of(
                Arrays.asList(changeLogLevelResponseForThings1, changeLogLevelResponseForThings2,
                        changeLogLevelResponseForGateway1),
                RESPONSES_TYPE,
                HttpStatusCode.OK, DittoHeaders.empty());

        final JsonObject responseToJson = aggregatedDevOpsCommandResponse.toJson();

        final CommandResponse parsedCommandResponse = underTest.parse(responseToJson, DittoHeaders.empty());

        Assertions.assertThat(parsedCommandResponse).isEqualTo(aggregatedDevOpsCommandResponse);
        DittoJsonAssertions.assertThat(parsedCommandResponse.toJson()).isEqualTo(responseToJson);
    }
}
