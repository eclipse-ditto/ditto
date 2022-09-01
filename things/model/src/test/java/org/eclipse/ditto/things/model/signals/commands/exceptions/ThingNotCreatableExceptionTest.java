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
package org.eclipse.ditto.things.model.signals.commands.exceptions;

import static org.eclipse.ditto.things.model.signals.commands.TestConstants.Thing.POLICY_ID;
import static org.eclipse.ditto.things.model.signals.commands.TestConstants.Thing.THING_ID;
import static org.eclipse.ditto.things.model.signals.commands.assertions.ThingCommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.text.MessageFormat;

import org.eclipse.ditto.base.model.assertions.DittoBaseAssertions;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.GlobalErrorRegistry;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Unit test for {@link ThingNotCreatableException}.
 */
public final class ThingNotCreatableExceptionTest {

    @Rule
    public final TestNameCorrelationId testNameCorrelationId = TestNameCorrelationId.newInstance();

    private DittoHeaders dittoHeaders;

    @Before
    public void before() {
        dittoHeaders = DittoHeaders.newBuilder()
                .correlationId(testNameCorrelationId.getCorrelationId())
                .build();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ThingNotCreatableException.class, areImmutable());
    }

    @Test
    public void instanceFromBuilderForPolicyMissing() {
        final ThingNotCreatableException underTest =
                ThingNotCreatableException.newBuilderForPolicyMissing(THING_ID, POLICY_ID)
                        .dittoHeaders(dittoHeaders)
                        .build();

        assertThat(underTest)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasDittoHeaders(dittoHeaders)
                .hasMessage(MessageFormat.format(ThingNotCreatableException.MESSAGE_TEMPLATE, THING_ID, POLICY_ID))
                .hasDescription(ThingNotCreatableException.DEFAULT_DESCRIPTION_NOT_EXISTING)
                .hasNoCause()
                .hasNoHref();
    }

    @Test
    public void instanceFromBuilderForPolicyExisting() {
        final ThingNotCreatableException underTest =
                ThingNotCreatableException.newBuilderForPolicyExisting(THING_ID, POLICY_ID)
                        .dittoHeaders(dittoHeaders)
                        .build();

        assertThat(underTest)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasDittoHeaders(dittoHeaders)
                .hasMessage(MessageFormat.format(ThingNotCreatableException.MESSAGE_TEMPLATE_POLICY_CREATION_FAILURE,
                        THING_ID,
                        POLICY_ID))
                .hasDescription(ThingNotCreatableException.DEFAULT_DESCRIPTION_POLICY_CREATION_FAILED)
                .hasNoCause()
                .hasNoHref();
    }

    @Test
    public void instanceForLiveChannel() {
        final ThingNotCreatableException underTest = ThingNotCreatableException.forLiveChannel(dittoHeaders);

        assertThat(underTest)
                .hasStatus(HttpStatus.METHOD_NOT_ALLOWED)
                .hasDittoHeaders(dittoHeaders)
                .hasMessage(ThingNotCreatableException.MESSAGE_WRONG_CHANNEL)
                .hasDescription(ThingNotCreatableException.DEFAULT_DESCRIPTION_WRONG_CHANNEL)
                .hasNoCause()
                .hasNoHref();
    }

    @Test
    public void setDittoHeaders() {
        final ThingNotCreatableException thingNotCreatableException =
                ThingNotCreatableException.forLiveChannel(DittoHeaders.empty());

        final DittoRuntimeException underTest = thingNotCreatableException.setDittoHeaders(dittoHeaders);

        DittoBaseAssertions.assertThat(underTest)
                .hasStatus(HttpStatus.METHOD_NOT_ALLOWED)
                .hasDittoHeaders(dittoHeaders)
                .hasMessage(ThingNotCreatableException.MESSAGE_WRONG_CHANNEL)
                .hasDescription(ThingNotCreatableException.DEFAULT_DESCRIPTION_WRONG_CHANNEL)
                .hasNoCause()
                .hasNoHref();
    }

    @Test
    public void instanceForLiveChannelToJson() {
        final ThingNotCreatableException underTest = ThingNotCreatableException.forLiveChannel(dittoHeaders);

        final JsonObjectBuilder expectedJsonObject = JsonObject.newBuilder()
                .set(DittoRuntimeException.JsonFields.MESSAGE, underTest.getMessage())
                .set(DittoRuntimeException.JsonFields.DESCRIPTION, underTest.getDescription().orElse(null))
                .set(DittoRuntimeException.JsonFields.ERROR_CODE, underTest.getErrorCode())
                .set(DittoRuntimeException.JsonFields.STATUS, underTest.getHttpStatus().getCode());

        assertThat(underTest.toJson()).isEqualTo(expectedJsonObject);
    }

    @Test
    public void instanceForMissingPolicyFromJson() {
        final ThingNotCreatableException thingNotCreatableException =
                ThingNotCreatableException.forLiveChannel(dittoHeaders);

        final JsonObject jsonObject = thingNotCreatableException.toJson();

        DittoBaseAssertions.assertThat(ThingNotCreatableException.fromJson(jsonObject, dittoHeaders))
                .isEqualTo(thingNotCreatableException);
    }

    @Test
    public void instanceForMissingPolicyToJson() {
        final ThingNotCreatableException underTest =
                ThingNotCreatableException.newBuilderForPolicyMissing(THING_ID, POLICY_ID)
                        .dittoHeaders(dittoHeaders)
                        .build();

        final JsonObjectBuilder expectedJsonObject = JsonObject.newBuilder()
                .set(DittoRuntimeException.JsonFields.MESSAGE, underTest.getMessage())
                .set(DittoRuntimeException.JsonFields.DESCRIPTION, underTest.getDescription().orElse(null))
                .set(DittoRuntimeException.JsonFields.ERROR_CODE, underTest.getErrorCode())
                .set(DittoRuntimeException.JsonFields.STATUS, underTest.getHttpStatus().getCode());

        assertThat(underTest.toJson()).isEqualTo(expectedJsonObject);
    }

    @Test
    public void instanceForLiveChannelFromJson() {
        final ThingNotCreatableException thingNotCreatableException =
                ThingNotCreatableException.newBuilderForPolicyMissing(THING_ID, POLICY_ID)
                        .dittoHeaders(dittoHeaders)
                        .build();

        final JsonObject jsonObject = thingNotCreatableException.toJson();

        DittoBaseAssertions.assertThat(ThingNotCreatableException.fromJson(jsonObject, dittoHeaders))
                .isEqualTo(thingNotCreatableException);
    }

    @Test
    public void instanceForExistingPolicyToJson() {
        final ThingNotCreatableException underTest =
                ThingNotCreatableException.newBuilderForPolicyExisting(THING_ID, POLICY_ID)
                        .dittoHeaders(dittoHeaders)
                        .build();

        final JsonObjectBuilder expectedJsonObject = JsonObject.newBuilder()
                .set(DittoRuntimeException.JsonFields.MESSAGE, underTest.getMessage())
                .set(DittoRuntimeException.JsonFields.DESCRIPTION, underTest.getDescription().orElse(null))
                .set(DittoRuntimeException.JsonFields.ERROR_CODE, underTest.getErrorCode())
                .set(DittoRuntimeException.JsonFields.STATUS, underTest.getHttpStatus().getCode());

        assertThat(underTest.toJson()).isEqualTo(expectedJsonObject);
    }

    @Test
    public void instanceForExistingPolicyFromJson() {
        final ThingNotCreatableException thingNotCreatableException =
                ThingNotCreatableException.newBuilderForPolicyExisting(THING_ID, POLICY_ID)
                        .dittoHeaders(dittoHeaders)
                        .build();

        final JsonObject jsonObject = thingNotCreatableException.toJson();

        DittoBaseAssertions.assertThat(ThingNotCreatableException.fromJson(jsonObject, dittoHeaders))
                .isEqualTo(thingNotCreatableException);
    }

    @Test
    public void parseInstanceForMissingPolicy() {
        final ThingNotCreatableException thingNotCreatableException =
                ThingNotCreatableException.newBuilderForPolicyMissing(THING_ID, POLICY_ID)
                        .dittoHeaders(dittoHeaders)
                        .build();

        final GlobalErrorRegistry globalErrorRegistry = GlobalErrorRegistry.getInstance();

        final DittoRuntimeException parsedDittoRuntimeException =
                globalErrorRegistry.parse(thingNotCreatableException.toJson(), dittoHeaders);

        DittoBaseAssertions.assertThat(parsedDittoRuntimeException).isEqualTo(thingNotCreatableException);
    }

    @Test
    public void parseInstanceForExistingPolicy() {
        final ThingNotCreatableException thingNotCreatableException =
                ThingNotCreatableException.newBuilderForPolicyExisting(THING_ID, POLICY_ID)
                        .dittoHeaders(dittoHeaders)
                        .build();

        final GlobalErrorRegistry globalErrorRegistry = GlobalErrorRegistry.getInstance();

        final DittoRuntimeException parsedDittoRuntimeException =
                globalErrorRegistry.parse(thingNotCreatableException.toJson(), dittoHeaders);

        DittoBaseAssertions.assertThat(parsedDittoRuntimeException).isEqualTo(thingNotCreatableException);
    }

    @Test
    public void parseInstanceForLiveChannel() {
        final ThingNotCreatableException thingNotCreatableException =
                ThingNotCreatableException.forLiveChannel(dittoHeaders);

        final GlobalErrorRegistry globalErrorRegistry = GlobalErrorRegistry.getInstance();

        final DittoRuntimeException parsedDittoRuntimeException =
                globalErrorRegistry.parse(thingNotCreatableException.toJson(), dittoHeaders);

        DittoBaseAssertions.assertThat(parsedDittoRuntimeException).isEqualTo(thingNotCreatableException);
    }

}
