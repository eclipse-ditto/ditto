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

import java.time.Instant;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocoladapter.Payload;
import org.eclipse.ditto.protocoladapter.ProtocolAdapterTest;
import org.eclipse.ditto.protocoladapter.TestConstants;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.protocoladapter.UnknownEventException;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionComplete;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionCreated;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionEvent;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionFailed;
import org.eclipse.ditto.signals.events.thingsearch.SubscriptionHasNext;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.protocoladapter.things.SubscriptionEventAdapter}.
 */
public final class SubscriptionEventAdapterTest implements ProtocolAdapterTest {

    private SubscriptionEventAdapter underTest;

    @Before
    public void setUp() {
        underTest = SubscriptionEventAdapter.of(DittoProtocolAdapter.getHeaderTranslator());
    }

    @Test(expected = UnknownEventException.class)
    public void unknownSubscriptionEventToAdaptable() {
        underTest.toAdaptable(new UnknownSubscriptionEvent(), TopicPath.Channel.TWIN);
    }

    @Test
    public void subscriptioncreatedFromAdaptable() {

        final TopicPath topicPath = TopicPath.fromNamespace("_")
                .things()
                .twin()
                .search()
                .generated()
                .build();

        final JsonPointer path = JsonPointer.empty();

        final SubscriptionCreated expected =
                SubscriptionCreated.of(TestConstants.SUBSCRIPTION_ID, TestConstants.DITTO_HEADERS_V_2_NO_STATUS);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonObject.of(
                                String.format("{\"subscriptionId\": \"%s\"}", TestConstants.SUBSCRIPTION_ID)))
                        .build())
                .withHeaders(TestConstants.DITTO_HEADERS_V_2_NO_STATUS)
                .build();
        final SubscriptionEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void subscriptionCreatedToAdaptable() {

        final TopicPath topicPath = TopicPath.fromNamespace("_")
                .things()
                .twin()
                .search()
                .generated()
                .build();

        final JsonPointer path = JsonPointer.empty();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonObject.of(
                                String.format("{\"subscriptionId\": \"%s\"}", TestConstants.SUBSCRIPTION_ID)))
                        .build())
                .withHeaders(TestConstants.DITTO_HEADERS_V_2_NO_STATUS)
                .build();

        final SubscriptionCreated subscriptionCreated =
                SubscriptionCreated.of(TestConstants.SUBSCRIPTION_ID, TestConstants.DITTO_HEADERS_V_2_NO_STATUS);
        final Adaptable actual = underTest.toAdaptable(subscriptionCreated, TopicPath.Channel.TWIN);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void subscriptionCompleteFromAdaptable() {

        final TopicPath topicPath = TopicPath.fromNamespace("_")
                .things()
                .twin()
                .search()
                .complete()
                .build();

        final JsonPointer path = JsonPointer.empty();

        final SubscriptionComplete expected =
                SubscriptionComplete.of(TestConstants.SUBSCRIPTION_ID, TestConstants.DITTO_HEADERS_V_2);

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonObject.of(
                                String.format("{\"subscriptionId\": \"%s\"}", TestConstants.SUBSCRIPTION_ID)))
                        .withRevision(TestConstants.REVISION)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final SubscriptionEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void subscriptionCompleteToAdaptable() {

        final TopicPath topicPath = TopicPath.fromNamespace("_")
                .things()
                .twin()
                .search()
                .complete()
                .build();

        final JsonPointer path = JsonPointer.empty();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonObject.of(
                                String.format("{\"subscriptionId\": \"%s\"}", TestConstants.SUBSCRIPTION_ID)))
                        .build())
                .withHeaders(TestConstants.DITTO_HEADERS_V_2_NO_STATUS)
                .build();

        final SubscriptionComplete subscriptionComplete =
                SubscriptionComplete.of(TestConstants.SUBSCRIPTION_ID, TestConstants.DITTO_HEADERS_V_2_NO_STATUS);
        final Adaptable actual = underTest.toAdaptable(subscriptionComplete, TopicPath.Channel.TWIN);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void subscriptionFailedFromAdaptable() {

        final TopicPath topicPath = TopicPath.fromNamespace("_")
                .things()
                .twin()
                .search()
                .failed()
                .build();

        final JsonPointer path = JsonPointer.empty();

        final SubscriptionFailed expected =
                SubscriptionFailed.of(TestConstants.SUBSCRIPTION_ID, TestConstants.EXCEPTION,
                        TestConstants.DITTO_HEADERS_V_2_NO_STATUS);


        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonObject.of(String.format("{\"subscriptionId\": \"%s\", \"error\": %s}",
                                TestConstants.SUBSCRIPTION_ID, TestConstants.EXCEPTION.toJson())))
                        .build())
                .withHeaders(TestConstants.DITTO_HEADERS_V_2_NO_STATUS)
                .build();
        final SubscriptionEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void subscriptionFailedToAdaptable() {

        final TopicPath topicPath = TopicPath.fromNamespace("_")
                .things()
                .twin()
                .search()
                .failed()
                .build();

        final JsonPointer path = JsonPointer.empty();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonObject.of(String.format("{\"subscriptionId\": \"%s\", \"error\": %s}",
                                TestConstants.SUBSCRIPTION_ID, TestConstants.EXCEPTION.toJson())))
                        .build())
                .withHeaders(TestConstants.DITTO_HEADERS_V_2_NO_STATUS)
                .build();

        final SubscriptionFailed subscriptionFailed =
                SubscriptionFailed.of(TestConstants.SUBSCRIPTION_ID, TestConstants.EXCEPTION,
                        TestConstants.DITTO_HEADERS_V_2_NO_STATUS);
        final Adaptable actual = underTest.toAdaptable(subscriptionFailed, TopicPath.Channel.TWIN);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void subscriptionHasNextFromAdaptable() {

        final TopicPath topicPath = TopicPath.fromNamespace("_")
                .things()
                .twin()
                .search()
                .hasNext()
                .build();

        final SubscriptionHasNext expected =
                SubscriptionHasNext.of(TestConstants.SUBSCRIPTION_ID, TestConstants.ITEMS,
                        TestConstants.DITTO_HEADERS_V_2_NO_STATUS);

        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonObject.of(String.format("{\"subscriptionId\": \"%s\", \"items\": %s}",
                                TestConstants.SUBSCRIPTION_ID, TestConstants.ITEMS)))
                        .build())
                .withHeaders(TestConstants.DITTO_HEADERS_V_2_NO_STATUS)
                .build();
        final SubscriptionEvent<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void subscriptionHasNextToAdaptable() {

        final TopicPath topicPath = TopicPath.fromNamespace("_")
                .things()
                .twin()
                .search()
                .hasNext()
                .build();

        final JsonPointer path = JsonPointer.empty();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonObject.of(String.format("{\"subscriptionId\": \"%s\", \"items\": %s}",
                                TestConstants.SUBSCRIPTION_ID, TestConstants.ITEMS)))
                        .build())
                .withHeaders(TestConstants.DITTO_HEADERS_V_2_NO_STATUS)
                .build();

        final SubscriptionHasNext subscriptionHasNext =
                SubscriptionHasNext.of(TestConstants.SUBSCRIPTION_ID, TestConstants.ITEMS,
                        TestConstants.DITTO_HEADERS_V_2_NO_STATUS);
        final Adaptable actual = underTest.toAdaptable(subscriptionHasNext, TopicPath.Channel.TWIN);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }


    private static final class UnknownSubscriptionEvent implements SubscriptionEvent<UnknownSubscriptionEvent> {

        @Override
        public String getType() {
            return "thing-search.subscription:foobar";
        }

        @Override
        public long getRevision() {
            return TestConstants.REVISION;
        }

        @Override
        public UnknownSubscriptionEvent setRevision(final long revision) {
            return this;
        }

        @Override
        public Optional<Instant> getTimestamp() {
            return Optional.empty();
        }

        @Override
        public DittoHeaders getDittoHeaders() {
            return TestConstants.DITTO_HEADERS_V_1;
        }

        @Override
        public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate predicate) {
            return JsonObject.newBuilder()
                    .set(Event.JsonFields.TYPE, getType())
                    .set(Event.JsonFields.REVISION, getRevision())
                    .build();
        }

        @Override
        public JsonPointer getResourcePath() {
            return JsonPointer.of("/");
        }

        @Override
        public UnknownSubscriptionEvent setDittoHeaders(final DittoHeaders dittoHeaders) {
            return this;
        }

        @Nonnull
        @Override
        public String getManifest() {
            return getType();
        }

    }

}
