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
package org.eclipse.ditto.protocol.adapter.things;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Predicate;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapterTest;
import org.eclipse.ditto.protocol.TestConstants;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.UnknownEventException;
import org.eclipse.ditto.base.model.signals.GlobalErrorRegistry;
import org.eclipse.ditto.thingsearch.model.signals.commands.exceptions.SubscriptionProtocolErrorException;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionComplete;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionCreated;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionEvent;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionFailed;
import org.eclipse.ditto.thingsearch.model.signals.events.SubscriptionHasNextPage;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.protocol.adapter.things.SubscriptionEventAdapter}.
 */
public final class SubscriptionEventAdapterTest implements ProtocolAdapterTest {

    private SubscriptionEventAdapter underTest;

    @Before
    public void setUp() {
        underTest = SubscriptionEventAdapter.of(DittoProtocolAdapter.getHeaderTranslator(),
                GlobalErrorRegistry.getInstance());
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

        final SubscriptionProtocolErrorException error = SubscriptionProtocolErrorException.newBuilder()
                .message("Mock error")
                .build();

        final SubscriptionFailed expected =
                SubscriptionFailed.of(TestConstants.SUBSCRIPTION_ID, error, TestConstants.DITTO_HEADERS_V_2_NO_STATUS);


        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonObject.of(String.format("{\"subscriptionId\": \"%s\", \"error\": %s}",
                                TestConstants.SUBSCRIPTION_ID, error.toJson())))
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

        final SubscriptionHasNextPage expected =
                SubscriptionHasNextPage.of(TestConstants.SUBSCRIPTION_ID, TestConstants.ITEMS,
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

        final SubscriptionHasNextPage subscriptionHasNextPage =
                SubscriptionHasNextPage.of(TestConstants.SUBSCRIPTION_ID, TestConstants.ITEMS,
                        TestConstants.DITTO_HEADERS_V_2_NO_STATUS);
        final Adaptable actual = underTest.toAdaptable(subscriptionHasNextPage, TopicPath.Channel.TWIN);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @AllValuesAreNonnullByDefault
    private static final class UnknownSubscriptionEvent implements SubscriptionEvent<UnknownSubscriptionEvent> {

        @Override
        public String getType() {
            return "thing-search.subscription:foobar";
        }

        @Override
        public Optional<Instant> getTimestamp() {
            return Optional.empty();
        }

        @Override
        public Optional<Metadata> getMetadata() {
            return Optional.empty();
        }

        @Override
        public DittoHeaders getDittoHeaders() {
            return TestConstants.DITTO_HEADERS_V_2;
        }

        @Override
        public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate predicate) {
            return JsonObject.newBuilder()
                    .set(Event.JsonFields.TYPE, getType())
                    .build();
        }

        @Override
        public JsonPointer getResourcePath() {
            return JsonPointer.of("/");
        }

        @Override
        public String getSubscriptionId() {
            return "dummy-subscription-id";
        }

        @Override
        public UnknownSubscriptionEvent setDittoHeaders(final DittoHeaders dittoHeaders) {
            return this;
        }

        @Override
        public String getManifest() {
            return getType();
        }
    }
}
