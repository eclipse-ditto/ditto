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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Predicate;

import javax.annotation.Nonnull;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapterTest;
import org.eclipse.ditto.protocol.TestConstants;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.UnknownCommandException;
import org.eclipse.ditto.thingsearch.model.signals.commands.ThingSearchCommand;
import org.eclipse.ditto.thingsearch.model.signals.commands.subscription.CancelSubscription;
import org.eclipse.ditto.thingsearch.model.signals.commands.subscription.CreateSubscription;
import org.eclipse.ditto.thingsearch.model.signals.commands.subscription.RequestFromSubscription;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.protocol.adapter.things.ThingSearchCommandAdapter}.
 */
public final class ThingSearchCommandAdapterTest implements ProtocolAdapterTest {

    private static final JsonArray NAMESPACES_ARRAY = TestConstants.NAMESPACES.stream()
            .map(JsonValue::of)
            .collect(JsonCollectors.valuesToArray());

    private ThingSearchCommandAdapter underTest;

    @Before
    public void setUp() {
        underTest = ThingSearchCommandAdapter.of(DittoProtocolAdapter.getHeaderTranslator());
    }

    @Test(expected = UnknownCommandException.class)
    public void unknownCommandToAdaptable() {
        underTest.toAdaptable(new UnknownThingSearchCommand(), TopicPath.Channel.TWIN);
    }

    @Test
    public void createSubscriptionFromAdaptable() {
        final CreateSubscription expected =
                CreateSubscription.of(TestConstants.FILTER, TestConstants.OPTIONS, null,
                        TestConstants.NAMESPACES, TestConstants.DITTO_HEADERS_V_2_NO_STATUS);

        final TopicPath topicPath = TopicPath.fromNamespace(TopicPath.ID_PLACEHOLDER)
                .things()
                .twin()
                .search()
                .subscribe()
                .build();
        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonObject.of(String.format(
                                "{\"filter\": \"%s\", \"options\": \"%s\", \"namespaces\": %s}",
                                TestConstants.FILTER,
                                TestConstants.OPTIONS,
                                NAMESPACES_ARRAY
                        )))
                        .build())
                .withHeaders(TestConstants.DITTO_HEADERS_V_2_NO_STATUS)
                .build();
        final ThingSearchCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void createSubscriptionToAdaptable() {
        final TopicPath topicPath = TopicPath.fromNamespace(TopicPath.ID_PLACEHOLDER)
                .things()
                .twin()
                .search()
                .subscribe()
                .build();

        final JsonPointer path = JsonPointer.empty();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonObject.of(String.format(
                                "{\"filter\": \"%s\", \"options\": \"%s\", \"namespaces\": %s}",
                                TestConstants.FILTER,
                                TestConstants.OPTIONS,
                                NAMESPACES_ARRAY
                        )))
                        .build())
                .withHeaders(TestConstants.DITTO_HEADERS_V_2_NO_STATUS)
                .build();

        final CreateSubscription createSubscription =
                CreateSubscription.of(TestConstants.FILTER, TestConstants.OPTIONS, null,
                        TestConstants.NAMESPACES, TestConstants.DITTO_HEADERS_V_2_NO_STATUS);
        final Adaptable actual = underTest.toAdaptable(createSubscription, TopicPath.Channel.TWIN);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void createSubscriptionFilterOnlyToAdaptable() {
        final String filter = "exists(attributes/test)";
        final CreateSubscription createSubscription =
                CreateSubscription.of(filter, null, null, null, DittoHeaders.empty());
        final Adaptable actual = underTest.toAdaptable(createSubscription);
        assertThat(actual.getPayload().getValue()).contains(JsonObject.newBuilder()
                .set("filter", filter)
                .build());
    }

    @Test
    public void createSubscriptionWithFieldsFromAdaptable() {
        final CreateSubscription expected =
                CreateSubscription.of(TestConstants.FILTER, TestConstants.OPTIONS, TestConstants.FIELDS,
                        TestConstants.NAMESPACES, TestConstants.DITTO_HEADERS_V_2_NO_STATUS);

        final TopicPath topicPath = TopicPath.fromNamespace(TopicPath.ID_PLACEHOLDER)
                .things()
                .twin()
                .search()
                .subscribe()
                .build();

        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonObject.of(String.format(
                                "{\"filter\": \"%s\", \"options\": \"%s\", \"namespaces\": %s}",
                                TestConstants.FILTER,
                                TestConstants.OPTIONS,
                                NAMESPACES_ARRAY
                        )))
                        .withFields(TestConstants.FIELDS)
                        .build())
                .withHeaders(TestConstants.DITTO_HEADERS_V_2_NO_STATUS)
                .build();
        final ThingSearchCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void createSubscriptionWithFieldsToAdaptable() {
        final TopicPath topicPath = TopicPath.fromNamespace(TopicPath.ID_PLACEHOLDER)
                .things()
                .twin()
                .search()
                .subscribe()
                .build();

        final JsonPointer path = JsonPointer.empty();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonObject.of(String.format(
                                "{\"filter\": \"%s\", \"options\": \"%s\", \"namespaces\": %s}",
                                TestConstants.FILTER,
                                TestConstants.OPTIONS,
                                NAMESPACES_ARRAY
                        )))
                        .withFields(TestConstants.FIELDS)
                        .build())
                .withHeaders(TestConstants.DITTO_HEADERS_V_2_NO_STATUS)
                .build();

        final CreateSubscription createSubscription =
                CreateSubscription.of(TestConstants.FILTER, TestConstants.OPTIONS, TestConstants.FIELDS,
                        TestConstants.NAMESPACES, TestConstants.DITTO_HEADERS_V_2_NO_STATUS);
        final Adaptable actual = underTest.toAdaptable(createSubscription, TopicPath.Channel.TWIN);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void requestSubscriptionFromAdaptable() {
        final RequestFromSubscription expected =
                RequestFromSubscription.of(TestConstants.SUBSCRIPTION_ID, TestConstants.DEMAND,
                        TestConstants.DITTO_HEADERS_V_2_NO_STATUS);

        final TopicPath topicPath = TopicPath.fromNamespace(String.join(",", TestConstants.NAMESPACES))
                .things()
                .twin()
                .search()
                .request()
                .build();

        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonObject.of(String.format(
                                "{\"subscriptionId\": \"%s\", \"demand\": %s}", TestConstants.SUBSCRIPTION_ID,
                                TestConstants.DEMAND)))
                        .build())
                .withHeaders(TestConstants.DITTO_HEADERS_V_2_NO_STATUS)
                .build();
        final ThingSearchCommand actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void requestSubscriptionToAdaptable() {
        final TopicPath topicPath = TopicPath.fromNamespace("_")
                .things()
                .twin()
                .search()
                .request()
                .build();

        final JsonPointer path = JsonPointer.empty();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonObject.of(String.format(
                                "{\"subscriptionId\": \"%s\", \"demand\": %s}", TestConstants.SUBSCRIPTION_ID,
                                TestConstants.DEMAND)))
                        .build())
                .withHeaders(TestConstants.DITTO_HEADERS_V_2_NO_STATUS)
                .build();

        final RequestFromSubscription requestFromSubscription =
                RequestFromSubscription.of(TestConstants.SUBSCRIPTION_ID, TestConstants.DEMAND,
                        TestConstants.DITTO_HEADERS_V_2_NO_STATUS);
        final Adaptable actual = underTest.toAdaptable(requestFromSubscription, TopicPath.Channel.TWIN);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void cancelSubscriptionFromAdaptable() {
        final CancelSubscription expected =
                CancelSubscription.of(TestConstants.SUBSCRIPTION_ID, TestConstants.DITTO_HEADERS_V_2_NO_STATUS);

        final TopicPath topicPath = TopicPath.fromNamespace("_")
                .things()
                .twin()
                .search()
                .cancel()
                .build();

        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonObject.of(String.format(
                                "{\"subscriptionId\": \"%s\"}", TestConstants.SUBSCRIPTION_ID)))
                        .build())
                .withHeaders(TestConstants.DITTO_HEADERS_V_2_NO_STATUS)
                .build();
        final ThingSearchCommand actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void cancelSubscriptionToAdaptable() {
        final TopicPath topicPath = TopicPath.fromNamespace("_")
                .things()
                .twin()
                .search()
                .cancel()
                .build();

        final JsonPointer path = JsonPointer.empty();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonObject.of(String.format(
                                "{\"subscriptionId\": \"%s\"}", TestConstants.SUBSCRIPTION_ID)))
                        .build())
                .withHeaders(TestConstants.DITTO_HEADERS_V_2_NO_STATUS)
                .build();

        final CancelSubscription cancelSubscription =
                CancelSubscription.of(TestConstants.SUBSCRIPTION_ID, TestConstants.DITTO_HEADERS_V_2_NO_STATUS);
        final Adaptable actual = underTest.toAdaptable(cancelSubscription, TopicPath.Channel.TWIN);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    private static class UnknownThingSearchCommand implements ThingSearchCommand {

        @Override
        public String getType() {
            return "things.commands:fooscribe";
        }

        @Nonnull
        @Override
        public String getManifest() {
            return getType();
        }

        @Override
        public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate predicate) {
            return JsonObject.newBuilder()
                    .set(JsonFields.TYPE, getType())
                    .build();
        }

        @Override
        public DittoHeaders getDittoHeaders() {
            return TestConstants.DITTO_HEADERS_V_2_NO_STATUS;
        }

        @Override
        public JsonPointer getResourcePath() {
            return JsonPointer.of("/");
        }

        @Override
        public Category getCategory() {
            return null;
        }

        @Override
        public ThingSearchCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
            return this;
        }

    }

}
