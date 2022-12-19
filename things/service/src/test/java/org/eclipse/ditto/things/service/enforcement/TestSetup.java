/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.enforcement;

import static org.eclipse.ditto.base.model.json.JsonSchemaVersion.V_2;

import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.pubsub.DistributedPub;
import org.eclipse.ditto.internal.utils.pubsub.StreamingType;
import org.eclipse.ditto.internal.utils.pubsub.extractors.AckExtractor;
import org.eclipse.ditto.internal.utils.pubsubthings.LiveSignalPub;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingBuilder;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

import akka.actor.ActorRef;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.FiniteDuration;

public final class TestSetup {

    public static final String FEATURE_ID = "x";
    public static final String FEATURE_PROPERTY_1 = "key1";
    public static final String FEATURE_PROPERTY_2 = "key2";

    public static final ThingId THING_ID = ThingId.of("thing", "id");
    public static final PolicyId POLICY_ID = PolicyId.of("policy", "id");
    public static final Feature FEATURE = Feature.newBuilder()
            .properties(FeatureProperties.newBuilder()
                    .set(FEATURE_PROPERTY_1, 1)
                    .set(FEATURE_PROPERTY_2, "some string")
                    .build())
            .withId(FEATURE_ID)
            .build();
    public static final Thing THING = Thing.newBuilder()
            .setId(THING_ID)
            .setPolicyId(POLICY_ID)
            .setFeature(FEATURE)
            .build();

    public static final String SUBJECT_ID = "subject";
    public static final AuthorizationSubject SUBJECT = AuthorizationSubject.newInstance("dummy:" + SUBJECT_ID);

    public static final AuthorizationSubject GOOGLE_SUBJECT = AuthorizationSubject.newInstance("google:" + SUBJECT_ID);

    public static ThingBuilder.FromScratch newThing() {
        return ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setRevision(1L);
    }

    public static JsonObject newThingWithPolicyId(final PolicyId policyId) {
        return newThing()
                .setPolicyId(policyId)
                .build()
                .toJson(V_2, FieldType.all());
    }

    /**
     * Similar to {@link TestKit#expectMsgClass(Class)} but ignores other messages occurring while waiting for a
     * message of the passed {@code clazz}.
     *
     * @param testKit the TestKit to fish for messages in
     * @param clazz the type of the message to wait for
     * @param <T> the type of the waited for message
     * @return the message
     */
    public static <T> T fishForMsgClass(final TestKit testKit, final Class<T> clazz) {
        return clazz.cast(
                testKit.fishForMessage(FiniteDuration.apply(5, TimeUnit.SECONDS), clazz.getName(), clazz::isInstance));
    }

    public static final class DummyLiveSignalPub implements LiveSignalPub {

        private final ActorRef pubSubMediator;

        public DummyLiveSignalPub(final ActorRef pubSubMediator) {
            this.pubSubMediator = pubSubMediator;
        }

        @Override
        public DistributedPub<ThingCommand<?>> command() {
            return new DistributedPub<>() {
                @Override
                public ActorRef getPublisher() {
                    return pubSubMediator;
                }

                @Override
                public Object wrapForPublication(final ThingCommand<?> message, final CharSequence groupIndexKey) {
                    return DistPubSubAccess.publish(StreamingType.LIVE_COMMANDS.getDistributedPubSubTopic(), message);
                }

                @Override
                public <S extends ThingCommand<?>> Object wrapForPublicationWithAcks(final S message,
                        final CharSequence groupIndexKey, final AckExtractor<S> ackExtractor) {
                    return wrapForPublication(message, groupIndexKey);
                }
            };
        }

        @Override
        public DistributedPub<ThingEvent<?>> event() {
            return new DistributedPub<>() {
                @Override
                public ActorRef getPublisher() {
                    return pubSubMediator;
                }

                @Override
                public Object wrapForPublication(final ThingEvent<?> message, final CharSequence groupIndexKey) {
                    return DistPubSubAccess.publish(StreamingType.LIVE_EVENTS.getDistributedPubSubTopic(), message);
                }

                @Override
                public <S extends ThingEvent<?>> Object wrapForPublicationWithAcks(final S message,
                        final CharSequence groupIndexKey, final AckExtractor<S> ackExtractor) {
                    return wrapForPublication(message, groupIndexKey);
                }
            };
        }

        @Override
        public DistributedPub<SignalWithEntityId<?>> message() {
            return new DistributedPub<>() {

                @Override
                public ActorRef getPublisher() {
                    return pubSubMediator;
                }

                @Override
                public Object wrapForPublication(final SignalWithEntityId<?> message, final CharSequence groupIndexKey) {
                    return DistPubSubAccess.publish(StreamingType.MESSAGES.getDistributedPubSubTopic(), message);
                }

                @Override
                public <S extends SignalWithEntityId<?>> Object wrapForPublicationWithAcks(final S message,
                        final CharSequence groupIndexKey, final AckExtractor<S> ackExtractor) {
                    return wrapForPublication(message, groupIndexKey);
                }
            };
        }

    }

}
