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
package org.eclipse.ditto.protocoladapter;

import java.util.function.Predicate;

import javax.annotation.Nonnull;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommand;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.CancelSubscription;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.CreateSubscription;
import org.eclipse.ditto.signals.commands.thingsearch.subscription.RequestSubscription;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link ThingSearchCommandAdapter}.
 */
public final class ThingSearchCommandAdapterTest implements ProtocolAdapterTest {

    private ThingSearchCommandAdapter underTest;

    @Before
    public void setUp() {
        underTest = ThingSearchCommandAdapter.of(DittoProtocolAdapter.getHeaderTranslator());
    }

    @Test(expected = UnknownCommandException.class)
    public void unknownCommandToAdaptable() {
        underTest.toAdaptable(new UnknownThingSearchCommand());
    }

    @Test
    public void createSubscriptionFromAdaptable() {
        final CreateSubscription expected =
                CreateSubscription.of(TestConstants.FILTER, TestConstants.OPTIONS, null,
                        TestConstants.NAMESPACES, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.fromNamespace(String.join(",", TestConstants.NAMESPACES))
                .search()
                .twin()
                .commands()
                .subscribe()
                .build();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder()
                        .withValue(JsonValue.of(
                                String.format("{\"filter\": \"%s\", \"options\": \"%s\"}", TestConstants.FILTER,
                                        String.join(",", TestConstants.OPTIONS))))
                        .build())
                .withHeaders(TestConstants.DITTO_HEADERS_V_2)
                .build();
        final ThingSearchCommand actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void createSubscriptionToAdaptable() {
        final TopicPath topicPath = TopicPath.fromNamespace(String.join(",", TestConstants.NAMESPACES))
                .search()
                .twin()
                .commands()
                .subscribe()
                .build();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder()
                        .withValue(JsonValue.of(
                                String.format("{\"filter\": \"%s\", \"options\": \"%s\"}", TestConstants.FILTER,
                                        String.join(",", TestConstants.OPTIONS))))
                        .build())
                .withHeaders(TestConstants.DITTO_HEADERS_V_2)
                .build();

        final CreateSubscription createSubscription =
                CreateSubscription.of(TestConstants.FILTER, TestConstants.OPTIONS, null,
                        TestConstants.NAMESPACES, TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actual = underTest.toAdaptable(createSubscription);

        final JsonifiableAdaptable jsonifiableAdaptable =
                ProtocolFactory.wrapAsJsonifiableAdaptable(actual);
        final JsonObject jsonObject = jsonifiableAdaptable.toJson();
        System.out.println(jsonObject.toString());

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void createSubscriptionWithFieldsFromAdaptable() {
        final CreateSubscription expected =
                CreateSubscription.of(TestConstants.FILTER, TestConstants.OPTIONS, TestConstants.FIELDS,
                        TestConstants.NAMESPACES, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.fromNamespace(String.join(",", TestConstants.NAMESPACES))
                .search()
                .twin()
                .commands()
                .subscribe()
                .build();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder()
                        .withValue(JsonValue.of(
                                String.format("{\"filter\": \"%s\", \"options\": \"%s\"}", TestConstants.FILTER,
                                        String.join(",", TestConstants.OPTIONS))))
                        .withFields(TestConstants.FIELDS)
                        .build())
                .withHeaders(TestConstants.DITTO_HEADERS_V_2)
                .build();
        final ThingSearchCommand actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void createSubscriptionWithFieldsToAdaptable() {
        final TopicPath topicPath = TopicPath.fromNamespace(String.join(",", TestConstants.NAMESPACES))
                .search()
                .twin()
                .commands()
                .subscribe()
                .build();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder()
                        .withValue(JsonValue.of(
                                String.format("{\"filter\": \"%s\", \"options\": \"%s\"}", TestConstants.FILTER,
                                        String.join(",", TestConstants.OPTIONS))))
                        .withFields(TestConstants.FIELDS)
                        .build())
                .withHeaders(TestConstants.DITTO_HEADERS_V_2)
                .build();

        final CreateSubscription createSubscription =
                CreateSubscription.of(TestConstants.FILTER, TestConstants.OPTIONS, TestConstants.FIELDS,
                        TestConstants.NAMESPACES, TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actual = underTest.toAdaptable(createSubscription);

        final JsonifiableAdaptable jsonifiableAdaptable =
                ProtocolFactory.wrapAsJsonifiableAdaptable(actual);
        final JsonObject jsonObject = jsonifiableAdaptable.toJson();
        System.out.println(jsonObject.toString());

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void requestSubscriptionFromAdaptable() {
        final RequestSubscription expected =
                RequestSubscription.of(TestConstants.SUBSCRIPTION_ID, TestConstants.DEMAND,
                        TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.fromNamespace(String.join(",", TestConstants.NAMESPACES))
                .search()
                .twin()
                .commands()
                .request()
                .build();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder()
                        .withValue(JsonValue.of(String.format(
                                "{\"subscriptionId\": \"%s\", \"demand\": \"%s\"}", TestConstants.SUBSCRIPTION_ID,
                                TestConstants.DEMAND)))
                        .build())
                .withHeaders(TestConstants.DITTO_HEADERS_V_2)
                .build();
        final ThingSearchCommand actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void requestSubscriptionToAdaptable() {
        final TopicPath topicPath = TopicPath.fromNamespace(String.join(",", TestConstants.NAMESPACES))
                .search()
                .twin()
                .commands()
                .request()
                .build();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder()
                        .withValue(JsonValue.of(String.format(
                                "{\"subscriptionId\": \"%s\", \"demand\": \"%s\"}", TestConstants.SUBSCRIPTION_ID,
                                TestConstants.DEMAND)))
                        .build())
                .withHeaders(TestConstants.DITTO_HEADERS_V_2)
                .build();

        final RequestSubscription requestSubscription =
                RequestSubscription.of(TestConstants.SUBSCRIPTION_ID, TestConstants.DEMAND,
                        TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actual = underTest.toAdaptable(requestSubscription);

        final JsonifiableAdaptable jsonifiableAdaptable =
                ProtocolFactory.wrapAsJsonifiableAdaptable(actual);
        final JsonObject jsonObject = jsonifiableAdaptable.toJson();
        System.out.println(jsonObject.toString());

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void cancelSubscriptionFromAdaptable() {
        final CancelSubscription expected =
                CancelSubscription.of(TestConstants.SUBSCRIPTION_ID, TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.fromNamespace(String.join(",", TestConstants.NAMESPACES))
                .search()
                .twin()
                .commands()
                .cancel()
                .build();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder()
                        .withValue(JsonValue.of(String.format(
                                "{\"subscriptionId\": \"%s\"}", TestConstants.SUBSCRIPTION_ID)))
                        .build())
                .withHeaders(TestConstants.DITTO_HEADERS_V_2)
                .build();
        final ThingSearchCommand actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void cancelSubscriptionToAdaptable() {
        final TopicPath topicPath = TopicPath.fromNamespace(String.join(",", TestConstants.NAMESPACES))
                .search()
                .twin()
                .commands()
                .cancel()
                .build();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder()
                        .withValue(JsonValue.of(String.format(
                                "{\"subscriptionId\": \"%s\"}", TestConstants.SUBSCRIPTION_ID)))
                        .build())
                .withHeaders(TestConstants.DITTO_HEADERS_V_2)
                .build();

        final CancelSubscription cancelSubscription =
                CancelSubscription.of(TestConstants.SUBSCRIPTION_ID, TestConstants.DITTO_HEADERS_V_2);
        final Adaptable actual = underTest.toAdaptable(cancelSubscription);

        final JsonifiableAdaptable jsonifiableAdaptable =
                ProtocolFactory.wrapAsJsonifiableAdaptable(actual);
        final JsonObject jsonObject = jsonifiableAdaptable.toJson();
        System.out.println(jsonObject.toString());

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
            return TestConstants.DITTO_HEADERS_V_2;
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
