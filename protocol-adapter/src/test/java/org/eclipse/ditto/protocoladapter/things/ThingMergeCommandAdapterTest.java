/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocoladapter.things;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocoladapter.LiveTwinTest;
import org.eclipse.ditto.protocoladapter.Payload;
import org.eclipse.ditto.protocoladapter.ProtocolAdapterTest;
import org.eclipse.ditto.protocoladapter.TestConstants;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.protocoladapter.UnknownPathException;
import org.eclipse.ditto.signals.commands.base.CommandNotSupportedException;
import org.eclipse.ditto.signals.commands.things.exceptions.PolicyIdNotDeletableException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingIdNotDeletableException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingIdNotExplicitlySettableException;
import org.eclipse.ditto.signals.commands.things.modify.MergeThing;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.protocoladapter.things.ThingModifyCommandAdapter}.
 */
public final class ThingMergeCommandAdapterTest extends LiveTwinTest implements ProtocolAdapterTest {

    private ThingMergeCommandAdapter underTest;
    private TopicPath topicPath;

    @Before
    public void setUp() {
        underTest = ThingMergeCommandAdapter.of(DittoProtocolAdapter.getHeaderTranslator());
        topicPath = topicPath(TopicPath.Action.MERGE);
    }

    @Test(expected = UnknownPathException.class)
    public void unknownCommandFromAdaptable() {
        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(JsonPointer.of("/_policy"))
                        .withValue(TestConstants.ATTRIBUTE_VALUE)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        underTest.fromAdaptable(adaptable);
    }

    @Test(expected = CommandNotSupportedException.class)
    public void invalidSchemaVersion() {
        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(TestConstants.THING_ATTRIBUTE_POINTER)
                        .withValue(TestConstants.ATTRIBUTE_VALUE)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_1)
                .build();

        underTest.fromAdaptable(adaptable);
    }

    @Test(expected = ThingIdNotDeletableException.class)
    public void mergeThingWithNullThingIdFromAdaptable() {
        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(JsonPointer.empty())
                        .withValue(JsonFactory.newObject(Thing.JsonFields.ID.getPointer(), JsonFactory.nullLiteral()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        underTest.fromAdaptable(adaptable);
    }

    @Test(expected = ThingIdNotExplicitlySettableException.class)
    public void mergeThingWithWrongThingIdFromAdaptable() {
        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(JsonPointer.empty())
                        .withValue(JsonFactory.newObject(Thing.JsonFields.ID.getPointer(), JsonValue.of("wrong:id")))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        underTest.fromAdaptable(adaptable);
    }

    @Test
    public void mergeThingToAdaptable() {
        final Thing thing = TestConstants.THING.setFeature(TestConstants.FEATURE);
        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(TestConstants.THING_POINTER)
                        .withValue(thing.toJson())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final MergeThing mergeThing = MergeThing.withThing(TestConstants.THING_ID,
                thing,
                TestConstants.DITTO_HEADERS_V_2);

        final Adaptable actual = underTest.toAdaptable(mergeThing, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void mergeThingWithPolicyIdFromAdaptable() {
        final MergeThing expected =
                MergeThing.withPolicyId(TestConstants.THING_ID, TestConstants.POLICY_ID,
                        TestConstants.DITTO_HEADERS_V_2);
        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(TestConstants.POLICY_ID_POINTER)
                        .withValue(JsonValue.of(TestConstants.POLICY_ID))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final MergeThing actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test(expected = PolicyIdNotDeletableException.class)
    public void mergeThingWithNullPolicyIdFromAdaptable() {
        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(TestConstants.POLICY_ID_POINTER)
                        .withValue(JsonValue.nullLiteral())
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        underTest.fromAdaptable(adaptable);
    }

    @Test(expected = PolicyIdNotDeletableException.class)
    public void mergeThingAtRootWithNullPolicyIdFromAdaptable() {
        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(JsonPointer.empty())
                        .withValue(JsonFactory.newObject(TestConstants.POLICY_ID_POINTER, JsonValue.nullLiteral()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        underTest.fromAdaptable(adaptable);
    }

    @Test
    public void mergeThingWithPolicyIdToAdaptable() {
        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(TestConstants.POLICY_ID_POINTER)
                        .withValue(JsonValue.of(TestConstants.POLICY_ID))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final MergeThing mergeThing = MergeThing.withPolicyId(TestConstants.THING_ID,
                TestConstants.POLICY_ID,
                TestConstants.DITTO_HEADERS_V_2);

        final Adaptable actual = underTest.toAdaptable(mergeThing, channel);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void mergeCommandFromAdaptableWithoutPayloadValueThrowsException() {
        TestConstants.THING_POINTERS.forEach(path -> Assertions.assertThatExceptionOfType(DittoJsonException.class)
                .as("fromAdaptable without payload at path '%s' should throw DittoJsonException", path)
                .isThrownBy(() -> {
                            final Adaptable adaptable = Adaptable.newBuilder(TestConstants.TOPIC_PATH_MERGE_THING)
                                    .withPayload(Payload.newBuilder(path).build())
                                    .withHeaders(TestConstants.HEADERS_V_2)
                                    .build();
                            underTest.fromAdaptable(adaptable);
                        }
                ));
    }
}
