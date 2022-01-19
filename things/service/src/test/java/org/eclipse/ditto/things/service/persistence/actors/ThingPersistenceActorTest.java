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
package org.eclipse.ditto.things.service.persistence.actors;

import static org.assertj.core.api.Assertions.fail;
import static org.eclipse.ditto.things.model.assertions.DittoThingsAssertions.assertThat;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.eclipse.ditto.base.api.common.Shutdown;
import org.eclipse.ditto.base.api.common.ShutdownReasonFactory;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.entity.Revision;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.test.Retry;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseOptions;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureDefinition;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.PolicyIdMissingException;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingBuilder;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingLifecycle;
import org.eclipse.ditto.things.model.ThingRevision;
import org.eclipse.ditto.things.model.ThingTooLargeException;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.TestConstants;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.exceptions.FeatureNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingUnavailableException;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThingResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteAttribute;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteAttributeResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteThing;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteThingResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttribute;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttributes;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureProperty;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatures;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThingResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttribute;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttributeResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttributes;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeature;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureDefinition;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureDefinitionResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureProperty;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeaturePropertyResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatures;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThings;
import org.eclipse.ditto.things.model.signals.events.ThingCreated;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.model.signals.events.ThingModified;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.slf4j.LoggerFactory;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.testkit.TestActorRef;
import akka.testkit.javadsl.TestKit;
import scala.PartialFunction;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.runtime.BoxedUnit;

/**
 * Unit test for the {@link ThingPersistenceActor}.
 */
public final class ThingPersistenceActorTest extends PersistenceActorTestBase {

    private static final JsonParseOptions JSON_PARSE_OPTIONS =
            JsonFactory.newParseOptionsBuilder().withoutUrlDecoding().build();

    private static final Instant TIMESTAMP = Instant.EPOCH;
    private static final Metadata METADATA = Metadata.newBuilder()
            .set("creator", "The epic Ditto team")
            .build();


    private static void assertThingInResponse(final Thing actualThing, final Thing expectedThing) {
        // Policy entries are ignored by things-persistence.
        assertThat(actualThing).hasEqualJson(expectedThing, FieldType.notHidden()
                .and(IS_MODIFIED.negate()));

        assertThat(actualThing.getModified()).isPresent(); // we cannot check exact timestamp
        assertThat(actualThing.getCreated()).isPresent(); // we cannot check exact timestamp
    }

    private static void assertThingInResponseV2(final Thing actualThing, final Thing expectedThing) {
        assertThat(actualThing).hasEqualJson(expectedThing, FieldType.notHidden()
                .and(IS_MODIFIED.negate()));

        assertThat(actualThing.getModified()).isPresent(); // we cannot check exact timestamp
    }

    @Rule
    public final TestWatcher watchman = new TestedMethodLoggingWatcher(LoggerFactory.getLogger(getClass()));

    @Before
    public void setUp() {
        setup(ConfigFactory.empty());
    }

    @Test
    public void unavailableExpectedIfPersistenceActorTerminates() throws Exception {
        new TestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final ThingId thingId = getIdOrThrow(thing);

                final ActorRef underTest = createSupervisorActorFor(thingId);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thing);

                // retrieve created thing
                final RetrieveThing retrieveThing = RetrieveThing.of(thingId, dittoHeadersV2);
                underTest.tell(retrieveThing, getRef());
                expectMsgEquals(ETagTestUtils.retrieveThingResponse(thing, null, dittoHeadersV2));

                // terminate thing persistence actor
                final String thingActorPath = String.format("akka://AkkaTestSystem/user/%s/pa", thingId);
                final ActorSelection thingActorSelection = actorSystem.actorSelection(thingActorPath);
                final Future<ActorRef> thingActorFuture =
                        thingActorSelection.resolveOne(Duration.create(5, TimeUnit.SECONDS));
                Await.result(thingActorFuture, Duration.create(6, TimeUnit.SECONDS));
                final ActorRef thingActor = watch(thingActorFuture.value().get().get());

                watch(thingActor);
                thingActor.tell(PoisonPill.getInstance(), getRef());
                expectTerminated(thingActor);

                // wait for supervisor to react to termination message
                Thread.sleep(500L);

                // retrieve unavailable thing
                underTest.tell(retrieveThing, getRef());
                expectMsgClass(ThingUnavailableException.class);
            }
        };
    }

    @Test
    public void tryToModifyFeaturePropertyAndReceiveCorrectErrorCode() {
        final ThingId thingId = ThingId.of("org.eclipse.ditto", "myThing");
        final Thing thing = Thing.newBuilder()
                .setId(thingId)
                .setPolicyId(PolicyId.of(thingId))
                .setFeatures(JsonFactory.newObject())
                .build();
        final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);

        final String featureId = "myFeature";
        final JsonPointer jsonPointer = JsonPointer.of("/state");
        final JsonValue jsonValue = JsonFactory.newValue("on");
        final ModifyFeatureProperty modifyFeatureProperty =
                ModifyFeatureProperty.of(thingId, featureId, jsonPointer, jsonValue, dittoHeadersV2);

        final FeatureNotAccessibleException featureNotAccessibleException =
                FeatureNotAccessibleException.newBuilder(thingId, featureId)
                        .dittoHeaders(dittoHeadersV2)
                        .build();

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(thingId);

                underTest.tell(createThing, getRef());
                expectMsgClass(CreateThingResponse.class);

                underTest.tell(modifyFeatureProperty, getRef());
                final Object actual = receiveOne(scala.concurrent.duration.Duration.apply(1, TimeUnit.SECONDS));
                assertThat(actual).isInstanceOf(DittoRuntimeException.class);
                assertThat(((DittoRuntimeException) actual).getErrorCode()).isEqualTo(
                        featureNotAccessibleException.getErrorCode());
            }
        };
    }

    @Test
    public void tryToRetrieveThingWhichWasNotYetCreated() {
        final ThingId thingId = ThingId.of("test.ns", "23420815");
        final ThingCommand retrieveThingCommand = RetrieveThing.of(thingId, dittoHeadersV2);

        new TestKit(actorSystem) {
            {
                final ActorRef thingPersistenceActor = createPersistenceActorFor(thingId);
                thingPersistenceActor.tell(retrieveThingCommand, getRef());
                expectMsgClass(ThingNotAccessibleException.class);
            }
        };
    }

    /**
     * The ThingPersistenceActor is created with a Thing ID. Any command it receives which belongs to a Thing with a
     * different ID should lead to an exception as the command was obviously sent to the wrong ThingPersistenceActor.
     */
    @Test
    public void tryToCreateThingWithDifferentThingId() {
        final ThingId thingIdOfActor = ThingId.of("test.ns", "23420815");
        final Thing thing = createThingV2WithRandomId();
        final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);

        final Props props = ThingPersistenceActor.props(thingIdOfActor, getDistributedPub(), pubSubMediator);
        final TestActorRef<ThingPersistenceActor> underTest = TestActorRef.create(actorSystem, props);
        final ThingPersistenceActor thingPersistenceActor = underTest.underlyingActor();
        final PartialFunction<Object, BoxedUnit> receiveCommand = thingPersistenceActor.receiveCommand();

        try {
            receiveCommand.apply(createThing);
            fail("Expected IllegalArgumentException to be thrown.");
        } catch (final Exception e) {
            assertThat(e).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    public void createThingV2() {
        new TestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thing);
            }
        };
    }

    @Test
    public void modifyThingV2() {
        final Thing thing = createThingV2WithRandomId();

        final Thing modifiedThing = thing.setAttribute(JsonFactory.newPointer("foo/bar"), JsonFactory.newValue("baz"));

        testModifyThing(dittoHeadersV2, thing, modifiedThing);
    }

    private void testModifyThing(final DittoHeaders dittoHeaders, final Thing thing, final Thing modifiedThing) {
        final ModifyThing modifyThingCommand = ModifyThing.of(getIdOrThrow(thing), modifiedThing, null, dittoHeaders);
        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeaders);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thing);

                underTest.tell(modifyThingCommand, getRef());
                expectMsgEquals(ETagTestUtils.modifyThingResponse(thing, modifiedThing, dittoHeaders, false));
            }
        };
    }

    /**
     * Makes sure that it is not possible to modify a thing without a previous create. If this was possible, a thing
     * could contain old data (in case of a recreate).
     */
    @Test
    public void modifyThingWithoutPreviousCreate() {
        final Thing thing = createThingV2WithRandomId();
        final ModifyThing modifyThingCommand = ModifyThing.of(getIdOrThrow(thing), thing, null, dittoHeadersV2);

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(thing);

                underTest.tell(modifyThingCommand, getRef());

                expectMsgClass(ThingNotAccessibleException.class);
            }
        };
    }

    @Test
    public void modifyThingOverwritesExistingFirstLevelFieldsWhenExplicitlySpecifiedV2() {
        final Thing thingWithFirstLevelFields = createThingV2WithRandomId();
        final Thing thingWithDifferentFirstLevelFields = Thing.newBuilder()
                .setId(getIdOrThrow(thingWithFirstLevelFields))
                .setPolicyId(PolicyId.of("org.eclipse.ditto:changedPolicyId"))
                .setAttributes(Attributes.newBuilder().set("changedAttrKey", "changedAttrVal").build())
                .setFeatures(Features.newBuilder().set(Feature.newBuilder().withId("changedFeatureId").build()))
                .build();
        doTestModifyThingKeepsOverwritesExistingFirstLevelFieldsWhenExplicitlySpecified(thingWithFirstLevelFields,
                thingWithDifferentFirstLevelFields, dittoHeadersV2);
    }

    private void doTestModifyThingKeepsOverwritesExistingFirstLevelFieldsWhenExplicitlySpecified(
            final Thing thingWithFirstLevelFields, final Thing thingWithDifferentFirstLevelFields,
            final DittoHeaders dittoHeaders) {

        final ThingId thingId = getIdOrThrow(thingWithFirstLevelFields);

        final ModifyThing modifyThingCommand =
                ModifyThing.of(thingId, thingWithDifferentFirstLevelFields, null, dittoHeaders);

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(thingWithFirstLevelFields);

                final CreateThing createThing = CreateThing.of(thingWithFirstLevelFields, null, dittoHeaders);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thingWithFirstLevelFields);

                assertPublishEvent(ThingCreated.of(thingWithFirstLevelFields, 1L, TIMESTAMP, dittoHeaders,
                        null));

                underTest.tell(modifyThingCommand, getRef());

                expectMsgEquals(
                        ETagTestUtils.modifyThingResponse(thingWithFirstLevelFields, thingWithDifferentFirstLevelFields,
                                dittoHeaders, false));

                assertPublishEvent(ThingModified.of(thingWithDifferentFirstLevelFields, 2L, TIMESTAMP, dittoHeaders,
                        null));

                final RetrieveThing retrieveThing =
                        RetrieveThing.getBuilder(thingId, dittoHeaders)
                                .withSelectedFields(ALL_FIELDS_SELECTOR)
                                .build();
                underTest.tell(retrieveThing, getRef());

                final RetrieveThingResponse retrieveThingResponse = expectMsgClass(RetrieveThingResponse.class);
                assertThingInResponse(retrieveThingResponse.getThing(), thingWithDifferentFirstLevelFields);
            }
        };
    }

    @Test
    public void modifyThingOverwritesExistingFirstLevelFieldsWhenNotExplicitlySpecifiedV2() {
        final Thing thingWithFirstLevelFields = createThingV2WithRandomId();
        doTestModifyThingOverwritesExistingFirstLevelFieldsWhenNotExplicitlySpecified(thingWithFirstLevelFields,
                dittoHeadersV2);
    }

    private void doTestModifyThingOverwritesExistingFirstLevelFieldsWhenNotExplicitlySpecified(
            final Thing thingWithFirstLevelFields, final DittoHeaders dittoHeaders) {

        final ThingId thingId = getIdOrThrow(thingWithFirstLevelFields);

        final Thing minimalThing = Thing.newBuilder()
                .setId(thingId)
                .setPolicyId(PolicyId.of(thingId))
                .build();

        final Thing expectedThing = minimalThing.toBuilder().setPolicyId(PolicyId.of(thingId)).build();

        final ModifyThing modifyThingCommand = ModifyThing.of(thingId, minimalThing, null, dittoHeaders);

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(thingWithFirstLevelFields);

                final CreateThing createThing = CreateThing.of(thingWithFirstLevelFields, null, dittoHeaders);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thingWithFirstLevelFields);

                assertPublishEvent(ThingCreated.of(thingWithFirstLevelFields, 1L, TIMESTAMP, dittoHeaders, null));

                underTest.tell(modifyThingCommand, getRef());

                expectMsgEquals(ETagTestUtils.modifyThingResponse(thingWithFirstLevelFields, minimalThing, dittoHeaders,
                                false));

                assertPublishEvent(ThingModified.of(expectedThing, 2L, TIMESTAMP, dittoHeaders, null));

                final RetrieveThing retrieveThing = RetrieveThing.getBuilder(thingId, dittoHeaders)
                        .withSelectedFields(ALL_FIELDS_SELECTOR)
                        .build();
                underTest.tell(retrieveThing, getRef());

                final RetrieveThingResponse retrieveThingResponse = expectMsgClass(RetrieveThingResponse.class);
                assertThingInResponse(retrieveThingResponse.getThing(), expectedThing);
            }
        };
    }

    @Test
    public void modifyAttributeSoThatThingGetsTooLarge() {
        new TestKit(actorSystem) {
            {
                final ThingId thingId = ThingId.of("thing", "id");
                final ThingBuilder.FromScratch thingBuilder = Thing.newBuilder()
                        .setId(thingId)
                        .setPolicyId(PolicyId.of(thingId));
                int i = 0;
                Thing thing;
                do {
                    thingBuilder.setAttribute(JsonPointer.of("attr" + i), JsonValue.of(i));
                    thing = thingBuilder.build();
                    i++;
                } while (thing.toJsonString().length() < TestConstants.THING_SIZE_LIMIT_BYTES);

                thing = thing.removeAttribute("attr" + (i - 1));

                final ActorRef underTest = createPersistenceActorFor(thing);

                // creating the Thing should be possible as we are below the limit:
                final CreateThing createThing = CreateThing.of(thing, null, DittoHeaders.newBuilder()
                        .schemaVersion(JsonSchemaVersion.V_2).build());
                underTest.tell(createThing, getRef());
                expectMsgClass(CreateThingResponse.class);

                // but modifying the Thing attribute which would cause the Thing to exceed the limit should not be allowed:
                final ModifyAttribute modifyAttribute = ModifyAttribute.of(getIdOrThrow(thing), JsonPointer.of("foo"),
                        JsonValue.of("bar"), DittoHeaders.newBuilder().schemaVersion(JsonSchemaVersion.V_2).build());
                underTest.tell(modifyAttribute, getRef());

                expectMsgClass(ThingTooLargeException.class);
            }
        };
    }

    @Test
    public void retrieveThingV2() {
        final Thing thing = createThingV2WithRandomId();
        final ThingCommand retrieveThingCommand = RetrieveThing.of(getIdOrThrow(thing), dittoHeadersV2);

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thing);

                underTest.tell(retrieveThingCommand, getRef());
                expectMsgEquals(ETagTestUtils.retrieveThingResponse(thing, null, dittoHeadersV2));
            }
        };
    }

    @Test
    public void retrieveThingsWithoutThingIdOfActor() {
        final Thing thing = createThingV2WithRandomId();

        final RetrieveThings retrieveThingsCommand =
                RetrieveThings.getBuilder(ThingId.of("foo", "bar"), ThingId.of("bum", "lux")).build();

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thing);

                underTest.tell(retrieveThingsCommand, getRef());
                expectNoMessage();
            }
        };
    }

    @Test
    public void deleteThingV2() {
        new TestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thing);

                final DeleteThing deleteThing = DeleteThing.of(getIdOrThrow(thing), dittoHeadersV2);
                underTest.tell(deleteThing, getRef());
                expectMsgEquals(DeleteThingResponse.of(getIdOrThrow(thing), dittoHeadersV2));
            }
        };
    }

    /**
     * Make sure that a re-created thing does not contain data from the previously deleted thing.
     */
    @Test
    public void deleteAndRecreateThingWithMinimumData() {
        new TestKit(actorSystem) {
            {
                final Thing initialThing = createThingV2WithRandomId();
                final ThingId thingId = getIdOrThrow(initialThing);
                final PolicyId policyId = initialThing.getPolicyEntityId().orElseThrow(IllegalStateException::new);
                final ActorRef underTest = createPersistenceActorFor(initialThing);

                final CreateThing createThing = CreateThing.of(initialThing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), initialThing);

                final DeleteThing deleteThing = DeleteThing.of(thingId, dittoHeadersV2);
                underTest.tell(deleteThing, getRef());
                expectMsgEquals(DeleteThingResponse.of(thingId, dittoHeadersV2));

                final Thing minimalThing = Thing.newBuilder()
                        .setId(thingId)
                        .setPolicyId(policyId)
                        .build();
                final CreateThing recreateThing = CreateThing.of(minimalThing, null, dittoHeadersV2);
                underTest.tell(recreateThing, getRef());

                final CreateThingResponse recreateThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(recreateThingResponse.getThingCreated().orElse(null), minimalThing);

                final RetrieveThing retrieveThing =
                        RetrieveThing.getBuilder(thingId, dittoHeadersV2)
                                .withSelectedFields(ALL_FIELDS_SELECTOR)
                                .build();
                underTest.tell(retrieveThing, getRef());

                final RetrieveThingResponse retrieveThingResponse = expectMsgClass(RetrieveThingResponse.class);
                assertThingInResponse(retrieveThingResponse.getThing(), minimalThing);
            }
        };
    }

    @Test
    public void modifyFeatures() {
        new TestKit(actorSystem) {
            {
                final DittoHeaders headersMockWithOtherAuth =
                        createDittoHeadersMock(JsonSchemaVersion.V_2, AUTH_SUBJECT);

                final ThingId thingId = ThingId.of("org.eclipse.ditto", "myThing");
                final Feature smokeDetector = ThingsModelFactory.newFeature("smokeDetector");
                final Feature fireExtinguisher = ThingsModelFactory.newFeature("fireExtinguisher");
                final Thing thing = ThingsModelFactory.newThingBuilder()
                        .setId(thingId)
                        .setPolicyId(POLICY_ID)
                        .setFeature(smokeDetector)
                        .build();
                final Features featuresToModify = ThingsModelFactory.newFeatures(fireExtinguisher);

                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponseV2(createThingResponse.getThingCreated().orElse(null), thing);

                final ModifyFeatures modifyFeatures =
                        ModifyFeatures.of(thingId, featuresToModify, headersMockWithOtherAuth);
                underTest.tell(modifyFeatures, getRef());
                expectMsgEquals(
                        ETagTestUtils.modifyFeaturesResponse(thingId, featuresToModify, headersMockWithOtherAuth,
                                false));

                final RetrieveFeatures retrieveFeatures = RetrieveFeatures.of(thingId, headersMockWithOtherAuth);
                underTest.tell(retrieveFeatures, getRef());
                expectMsgEquals(
                        ETagTestUtils.retrieveFeaturesResponse(thingId, featuresToModify, featuresToModify.toJson(),
                                headersMockWithOtherAuth));
            }
        };
    }

    @Test
    public void modifyAttributes() {
        new TestKit(actorSystem) {
            {
                final DittoHeaders headersMockWithOtherAuth =
                        createDittoHeadersMock(JsonSchemaVersion.V_2, AUTH_SUBJECT);

                final ThingId thingId = ThingId.of("org.eclipse.ditto", "myThing");

                final JsonPointer fooPointer = JsonFactory.newPointer("foo");
                final JsonValue fooValue = JsonFactory.newValue("bar");
                final JsonPointer bazPointer = JsonFactory.newPointer("baz");
                final JsonValue bazValue = JsonFactory.newValue(42);

                final Thing thing = ThingsModelFactory.newThingBuilder()
                        .setId(thingId)
                        .setPolicyId(POLICY_ID)
                        .setAttribute(fooPointer, fooValue)
                        .build();
                final Attributes attributesToModify =
                        ThingsModelFactory.newAttributesBuilder().set(bazPointer, bazValue).build();

                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponseV2(createThingResponse.getThingCreated().orElse(null), thing);

                final ModifyAttributes modifyAttributes =
                        ModifyAttributes.of(thingId, attributesToModify, headersMockWithOtherAuth);
                underTest.tell(modifyAttributes, getRef());
                expectMsgEquals(
                        ETagTestUtils.modifyAttributesResponse(thingId, attributesToModify, headersMockWithOtherAuth,
                                false));

                final RetrieveAttributes retrieveAttributes = RetrieveAttributes.of(thingId, headersMockWithOtherAuth);
                underTest.tell(retrieveAttributes, getRef());
                expectMsgEquals(ETagTestUtils.retrieveAttributesResponse(thingId, attributesToModify,
                        attributesToModify.toJson(JsonSchemaVersion.LATEST), headersMockWithOtherAuth));
            }
        };
    }

    @Test
    public void modifyAttribute() {
        final JsonObjectBuilder attributesBuilder = JsonFactory.newObjectBuilder();
        attributesBuilder.set("foo", "bar").set("isValid", false).set("answer", 42);
        final JsonObject attributes = attributesBuilder.build();

        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setAttributes(ThingsModelFactory.newAttributes(attributes))
                .setId(THING_ID)
                .setPolicyId(POLICY_ID)
                .build();

        final JsonPointer attributeKey = JsonFactory.newPointer("isValid");
        final JsonValue newAttributeValue = JsonFactory.newValue(true);

        final ThingId thingId = getIdOrThrow(thing);

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponseV2(createThingResponse.getThingCreated().orElse(null), thing);

                // Modify attribute as authorized subject.
                final ThingCommand authorizedCommand =
                        ModifyAttribute.of(thingId, attributeKey, newAttributeValue, dittoHeadersV2);
                underTest.tell(authorizedCommand, getRef());
                expectMsgEquals(
                        ETagTestUtils.modifyAttributeResponse(thingId, attributeKey, newAttributeValue, dittoHeadersV2,
                                false));
            }
        };
    }

    @Test
    public void retrieveAttribute() {
        final JsonPointer attributeKey = JsonFactory.newPointer("isValid");
        final JsonValue attributeValue = JsonFactory.newValue(false);

        final JsonObjectBuilder attributesBuilder = JsonFactory.newObjectBuilder();
        attributesBuilder.set("foo", "bar").set(attributeKey, attributeValue).set("answer", 42);
        final JsonObject attributes = attributesBuilder.build();

        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setAttributes(ThingsModelFactory.newAttributes(attributes))
                .setId(THING_ID)
                .setPolicyId(POLICY_ID)
                .build();

        final ThingId thingId = getIdOrThrow(thing);

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse =
                        expectMsgClass(java.time.Duration.ofSeconds(5), CreateThingResponse.class);
                assertThingInResponseV2(createThingResponse.getThingCreated().orElse(null), thing);

                // Retrieve attribute as authorized subject.
                final ThingCommand authorizedCommand =
                        RetrieveAttribute.of(thingId, attributeKey, dittoHeadersV2);
                underTest.tell(authorizedCommand, getRef());
                expectMsgClass(RetrieveAttributeResponse.class);
            }
        };
    }

    @Test
    public void deleteAttribute() {
        final JsonPointer attributeKey = JsonFactory.newPointer("isValid");

        final JsonObjectBuilder attributesBuilder = JsonFactory.newObjectBuilder();
        attributesBuilder.set("foo", "bar").set(attributeKey, false).set("answer", 42);
        final JsonObject attributes = attributesBuilder.build();

        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setAttributes(ThingsModelFactory.newAttributes(attributes))
                .setId(THING_ID)
                .setPolicyId(POLICY_ID)
                .build();

        final ThingId thingId = getIdOrThrow(thing);

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponseV2(createThingResponse.getThingCreated().orElse(null), thing);

                // Delete attribute as authorized subject.
                final ThingCommand authorizedCommand = DeleteAttribute.of(thingId, attributeKey, dittoHeadersV2);
                underTest.tell(authorizedCommand, getRef());
                expectMsgEquals(DeleteAttributeResponse.of(thingId, attributeKey, dittoHeadersV2));
            }
        };
    }

    @Test
    public void tryToRetrieveThingAfterDeletion() {
        final Thing thing = createThingV2WithRandomId();
        final ThingId thingId = getIdOrThrow(thing);
        final DeleteThing deleteThingCommand = DeleteThing.of(thingId, dittoHeadersV2);
        final RetrieveThing retrieveThingCommand = RetrieveThing.of(thingId, dittoHeadersV2);

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thing);

                underTest.tell(deleteThingCommand, getRef());
                expectMsgEquals(DeleteThingResponse.of(thingId, dittoHeadersV2));

                underTest.tell(retrieveThingCommand, getRef());
                expectMsgClass(ThingNotAccessibleException.class);
            }
        };
    }

    @Test
    public void recoverThingCreated() {
        new TestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final ThingId thingId = getIdOrThrow(thing);

                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thing);

                // restart actor to recover thing state
                watch(underTest);
                underTest.tell(PoisonPill.getInstance(), getRef());
                expectTerminated(underTest);
                final ActorRef underTestAfterRestart = Retry.untilSuccess(() -> createPersistenceActorFor(thing));

                final RetrieveThing retrieveThing = RetrieveThing.of(thingId, dittoHeadersV2);

                Awaitility.await().atMost(10L, TimeUnit.SECONDS).untilAsserted(() -> {
                    underTestAfterRestart.tell(retrieveThing, getRef());
                    final RetrieveThingResponse retrieveThingResponse = expectMsgClass(RetrieveThingResponse.class);
                    final Thing thingAsPersisted = retrieveThingResponse.getThing();
                    assertThat(thingAsPersisted.getEntityId()).contains(getIdOrThrow(thing));
                    assertThat(thingAsPersisted.getAttributes()).isEqualTo(thing.getAttributes());
                    assertThat(thingAsPersisted.getFeatures()).isEqualTo(thing.getFeatures());
                });
            }
        };
    }

    @Test
    public void recoverThingDeleted() {
        new TestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final ThingId thingId = getIdOrThrow(thing);

                ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thing);

                final DeleteThing deleteThing = DeleteThing.of(thingId, dittoHeadersV2);
                underTest.tell(deleteThing, getRef());
                expectMsgEquals(DeleteThingResponse.of(thingId, dittoHeadersV2));

                // restart actor to recover thing state
                watch(underTest);
                underTest.tell(PoisonPill.getInstance(), getRef());
                expectTerminated(underTest);
                underTest = Retry.untilSuccess(() -> createPersistenceActorFor(thing));

                final RetrieveThing retrieveThing = RetrieveThing.of(thingId, dittoHeadersV2);
                underTest.tell(retrieveThing, getRef());

                // A deleted Thing cannot be retrieved anymore (or is not accessible during initiation on slow systems)
                expectMsgClass(ThingNotAccessibleException.class);
            }
        };
    }

    @Test
    public void ensureSequenceNumberCorrectness() {
        new TestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final ThingId thingId = getIdOrThrow(thing);

                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thing);

                // modify the thing's attributes - results in sequence number 2
                final Thing thingToModify = thing.setAttributes(THING_ATTRIBUTES.setValue("foo", "bar"));
                final ModifyThing modifyThing = ModifyThing.of(thingId, thingToModify, null, dittoHeadersV2);

                underTest.tell(modifyThing, getRef());

                expectMsgEquals(ETagTestUtils.modifyThingResponse(thing, thingToModify, dittoHeadersV2, false));

                // retrieve the thing's sequence number
                final JsonFieldSelector versionFieldSelector =
                        JsonFactory.newFieldSelector(Thing.JsonFields.REVISION.getPointer().toString(), JSON_PARSE_OPTIONS);
                final long versionExpected = 2;
                final Thing thingExpected = ThingsModelFactory.newThingBuilder(thingToModify)
                        .setRevision(versionExpected)
                        .build();
                final RetrieveThing retrieveThing = RetrieveThing.getBuilder(thingId, dittoHeadersV2)
                        .withSelectedFields(versionFieldSelector)
                        .build();
                underTest.tell(retrieveThing, getRef());
                expectMsgEquals(ETagTestUtils.retrieveThingResponse(thingExpected, versionFieldSelector, dittoHeadersV2));
            }
        };
    }

    @Test
    public void ensureSequenceNumberCorrectnessAfterRecovery() {
        new TestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final ThingId thingId = getIdOrThrow(thing);

                final ActorRef underTest = createPersistenceActorFor(thing);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thing);

                // modify the thing's attributes - results in sequence number 2
                final Thing thingToModify = thing.setAttributes(THING_ATTRIBUTES.setValue("foo", "bar"));
                final ModifyThing modifyThing = ModifyThing.of(thingId, thingToModify, null, dittoHeadersV2);
                underTest.tell(modifyThing, getRef());
                expectMsgEquals(ETagTestUtils.modifyThingResponse(thing, thingToModify, dittoHeadersV2, false));

                // retrieve the thing's sequence number from recovered actor
                final JsonFieldSelector versionFieldSelector =
                        JsonFactory.newFieldSelector(Thing.JsonFields.REVISION.getPointer().toString(), JSON_PARSE_OPTIONS);
                final long versionExpected = 2;
                final Thing thingExpected = ThingsModelFactory.newThingBuilder(thingToModify)
                        .setRevision(versionExpected)
                        .build();

                // restart actor to recover thing state
                watch(underTest);
                underTest.tell(PoisonPill.getInstance(), getRef());
                expectTerminated(underTest);
                final ActorRef underTestAfterRestart = Retry.untilSuccess(() -> createPersistenceActorFor(thing));

                final RetrieveThing retrieveThing = RetrieveThing.getBuilder(thingId, dittoHeadersV2)
                        .withSelectedFields(versionFieldSelector)
                        .build();

                Awaitility.await().atMost(10L, TimeUnit.SECONDS).untilAsserted(() -> {
                    underTestAfterRestart.tell(retrieveThing, getRef());
                    expectMsgEquals(ETagTestUtils.retrieveThingResponse(thingExpected,
                            versionFieldSelector,
                            dittoHeadersV2));
                });
            }
        };
    }

    @Test
    public void createThingInV2WithMissingPolicyIdThrowsPolicyIdMissingException() {
        final ThingId thingIdOfActor = ThingId.of("test.ns.v1", "createThingInV2WithMissingPolicyId");
        final Thing thingV2 = ThingsModelFactory.newThingBuilder()
                .setAttributes(THING_ATTRIBUTES)
                .setId(thingIdOfActor)
                .build();

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor(thingV2);

                final CreateThing createThingV2 = CreateThing.of(thingV2, null, dittoHeadersV2);
                underTest.tell(createThingV2, getRef());

                expectMsgClass(PolicyIdMissingException.class);
            }
        };
    }

    @Test
    public void responsesDuringInitializationAreSentWithDittoHeaders() {
        new TestKit(actorSystem) {
            {
                final ThingId thingId = ThingId.of("org.eclipse.ditto", "myThing");

                final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                        .authorizationContext(
                                AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                                        AuthorizationSubject.newInstance("authSubject")))
                        .correlationId("correlationId")
                        .schemaVersion(JsonSchemaVersion.LATEST)
                        .build();

                final ActorRef underTest = createPersistenceActorFor(thingId);

                final RetrieveThing retrieveThing = RetrieveThing.of(thingId, dittoHeaders);
                final ThingNotAccessibleException thingNotAccessibleException =
                        ThingNotAccessibleException.newBuilder(thingId)
                                .dittoHeaders(dittoHeaders)
                                .build();

                underTest.tell(retrieveThing, getRef());
                expectMsgEquals(thingNotAccessibleException);
            }
        };
    }

    @Test
    public void ensureModifiedCorrectnessAfterCreation() {
        new TestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final ActorRef thingPersistenceActor = createPersistenceActorFor(thing);
                final JsonFieldSelector fieldSelector = Thing.JsonFields.MODIFIED.getPointer().toFieldSelector();

                // create thing
                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                thingPersistenceActor.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                final Instant createThingResponseTimestamp = Instant.now();
                assertThat(createThingResponse.getThingCreated()).isPresent();
                assertThat(createThingResponse.getThingCreated().get())
                        .isNotModifiedAfter(createThingResponseTimestamp);

                // retrieve thing
                final RetrieveThing retrieveThing = RetrieveThing.getBuilder(getIdOrThrow(thing), dittoHeadersV2)
                        .withSelectedFields(fieldSelector)
                        .build();
                thingPersistenceActor.tell(retrieveThing, getRef());

                final RetrieveThingResponse retrieveThingResponse = expectMsgClass(RetrieveThingResponse.class);
                assertThat(retrieveThingResponse.getThing()).isNotModifiedAfter(createThingResponseTimestamp);
            }
        };
    }

    @Test
    public void ensureModifiedCorrectnessAfterModification() {
        new TestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final ActorRef thingPersistenceActor = createPersistenceActorFor(thing);
                final JsonFieldSelector fieldSelector = Thing.JsonFields.MODIFIED.getPointer().toFieldSelector();

                // create thing
                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                thingPersistenceActor.tell(createThing, getRef());
                expectMsgClass(CreateThingResponse.class);
                final Instant createThingResponseTimestamp = Instant.now();

                // retrieve thing
                final RetrieveThing retrieveThing = RetrieveThing.getBuilder(getIdOrThrow(thing), dittoHeadersV2)
                        .withSelectedFields(fieldSelector)
                        .build();
                thingPersistenceActor.tell(retrieveThing, getRef());
                final RetrieveThingResponse retrieveThingResponse = expectMsgClass(RetrieveThingResponse.class);
                assertThat(retrieveThingResponse.getThing())
                        .isNotModifiedAfter(createThingResponseTimestamp);

                // modify thing
                final ModifyThing modifyThing = ModifyThing.of(getIdOrThrow(thing), thing, null, dittoHeadersV2);
                thingPersistenceActor.tell(modifyThing, getRef());
                expectMsgClass(ModifyThingResponse.class);
                final Instant modifyThingResponseTimestamp = Instant.now();

                // retrieve thing
                final RetrieveThing retrieveModifiedThing =
                        RetrieveThing.getBuilder(getIdOrThrow(thing), dittoHeadersV2)
                                .withSelectedFields(fieldSelector)
                                .build();
                thingPersistenceActor.tell(retrieveModifiedThing, getRef());
                final RetrieveThingResponse retrieveModifiedThingResponse = expectMsgClass(RetrieveThingResponse.class);
                assertThat(retrieveModifiedThingResponse.getThing())
                        .isModifiedAfter(createThingResponseTimestamp);
                assertThat(retrieveModifiedThingResponse.getThing())
                        .isNotModifiedAfter(modifyThingResponseTimestamp);
            }
        };
    }

    @Test
    public void ensureModifiedCorrectnessAfterRecovery() {
        new TestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final ActorRef thingPersistenceActor = watch(createPersistenceActorFor(thing));
                final JsonFieldSelector fieldSelector = Thing.JsonFields.MODIFIED.getPointer().toFieldSelector();

                // create thing
                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                thingPersistenceActor.tell(createThing, getRef());
                expectMsgClass(CreateThingResponse.class);
                final Instant createThingResponseTimestamp = Instant.now();

                // restart
                thingPersistenceActor.tell(PoisonPill.getInstance(), getRef());
                expectTerminated(thingPersistenceActor);
                final ActorRef thingPersistenceActorRecovered =
                        Retry.untilSuccess(() -> createPersistenceActorFor(thing));

                final RetrieveThing retrieveThing = RetrieveThing.getBuilder(getIdOrThrow(thing), dittoHeadersV2)
                        .withSelectedFields(fieldSelector)
                        .build();

                Awaitility.await().atMost(10L, TimeUnit.SECONDS).untilAsserted(() -> {
                    thingPersistenceActorRecovered.tell(retrieveThing, getRef());
                    final RetrieveThingResponse retrieveThingResponse = expectMsgClass(RetrieveThingResponse.class);
                    assertThat(retrieveThingResponse.getThing()).isNotModifiedAfter(createThingResponseTimestamp);
                    assertThat(getLastSender()).isEqualTo(thingPersistenceActorRecovered);
                });
            }
        };
    }

    @Test
    public void retrieveFeatureReturnsExpected() {
        final ThingId thingId = ThingId.of("org.eclipse.ditto", "thing1");
        final String gyroscopeFeatureId = "Gyroscope.0";
        final Feature gyroscopeFeature = ThingsModelFactory.newFeatureBuilder()
                .properties(ThingsModelFactory.newFeaturePropertiesBuilder()
                        .set("status", JsonFactory.newObjectBuilder()
                                .set("minRangeValue", -2000)
                                .set("xValue", -0.05071427300572395)
                                .set("units", "Deg/s")
                                .set("yValue", -0.4192921817302704)
                                .set("zValue", 0.20766231417655945)
                                .set("maxRangeValue", 2000)
                                .build())
                        .build())
                .withId(gyroscopeFeatureId)
                .build();
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(thingId)
                .setPolicyId(POLICY_ID)
                .setAttributes(ThingsModelFactory.newAttributesBuilder()
                        .set("isOnline", false)
                        .set("lastUpdate", "Thu Sep 28 15:01:43 CEST 2017")
                        .build())
                .setFeature(gyroscopeFeature)
                .build();

        new TestKit(actorSystem) {{
            final ActorRef thingPersistenceActor = createPersistenceActorFor(thing);

            // create Thing
            final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
            thingPersistenceActor.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            // retrieve Feature
            final RetrieveFeature retrieveFeatureCmd = RetrieveFeature.of(thingId, gyroscopeFeatureId, dittoHeadersV2);
            thingPersistenceActor.tell(retrieveFeatureCmd, getRef());
            expectMsgEquals(
                    ETagTestUtils.retrieveFeatureResponse(thingId, gyroscopeFeature, gyroscopeFeature.toJson(),
                            dittoHeadersV2));
        }};
    }

    @Test
    public void retrieveFeaturePropertyWithLiveChannelCondition() {
        final ThingId thingId = ThingId.of("org.eclipse.ditto", "thing1");
        final String gyroscopeFeatureId = "Gyroscope.0";
        final Feature gyroscopeFeature = ThingsModelFactory.newFeatureBuilder()
                .definition(FeatureDefinition.fromJson("[\"a:b:c\"]"))
                .properties(ThingsModelFactory.newFeaturePropertiesBuilder()
                        .set("status", JsonFactory.newObjectBuilder()
                                .set("minRangeValue", -2000)
                                .set("xValue", -0.05071427300572395)
                                .set("units", "Deg/s")
                                .set("yValue", -0.4192921817302704)
                                .set("zValue", 0.20766231417655945)
                                .set("maxRangeValue", 2000)
                                .build())
                        .build())
                .withId(gyroscopeFeatureId)
                .build();
        final Thing thing = ThingsModelFactory.newThingBuilder()
                .setId(thingId)
                .setPolicyId(POLICY_ID)
                .setFeature(gyroscopeFeature)
                .build();

        new TestKit(actorSystem) {{
            final ActorRef thingPersistenceActor = createPersistenceActorFor(thing);

            // create Thing
            final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
            thingPersistenceActor.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            // query: live channel condition matched with no twin fallback
            final var matchingHeaders = dittoHeadersV2.toBuilder()
                    .liveChannelCondition(String.format("exists(/features/%s)", gyroscopeFeatureId))
                    .build();
            final var command1 =
                    RetrieveFeatureDefinition.of(thingId, gyroscopeFeatureId, matchingHeaders);
            thingPersistenceActor.tell(command1, getRef());
            final var response1 = expectMsgClass(RetrieveFeatureDefinitionResponse.class);
            assertThat(response1.getDittoHeaders().didLiveChannelConditionMatch()).isTrue();
            assertThat(response1.getDefinition()).isEqualTo(FeatureDefinition.fromJson(JsonFactory.nullArray()));

            // query: live channel condition matched with twin fallback
            final var propertiesPointer = JsonPointer.of("/status/minRangeValue");
            final var withTwinFallback = matchingHeaders.toBuilder()
                    .putHeader(DittoHeaderDefinition.LIVE_CHANNEL_TIMEOUT_STRATEGY.getKey(), "use-twin")
                    .build();
            final var command2 =
                    RetrieveFeatureProperty.of(thingId, gyroscopeFeatureId, propertiesPointer, withTwinFallback);
            thingPersistenceActor.tell(command2, getRef());
            final var response2 = expectMsgClass(RetrieveFeaturePropertyResponse.class);
            assertThat(response2.getDittoHeaders().didLiveChannelConditionMatch()).isTrue();
            assertThat(response2.getPropertyValue()).isEqualTo(JsonValue.of(-2000));

            // query: live channel condition does not match
            final var mismatchingHeaders = dittoHeadersV2.toBuilder()
                    .liveChannelCondition("exists(/features/nonexistentFeature)")
                    .build();
            final var command3 = RetrieveThing.of(thingId, mismatchingHeaders);
            thingPersistenceActor.tell(command3, getRef());
            final var response3 = expectMsgClass(RetrieveThingResponse.class);
            assertThat(response3.getDittoHeaders().didLiveChannelConditionMatch()).isFalse();
            assertThat(response3.getEntity().asObject()).isNotEmpty();
        }};
    }

    @Test
    public void createThingInV2AndUpdateWithV2AndChangedPolicyId() {
        final ThingId thingId = ThingId.of("test.ns.v1", "createThingInV2AndUpdateWithV2AndChangedPolicyId");
        final Thing thing = buildThing(thingId);
        final Thing thing_2 = thing.toBuilder().setPolicyId(PolicyId.of(thingId + ".ANY.OTHER.NAMESPACE")).build();

        new TestKit(actorSystem) {{
            final DittoHeaders headersUsed =
                    testCreateAndModify(thing,
                            JsonSchemaVersion.V_2,
                            thing_2,
                            JsonSchemaVersion.V_2,
                            modifyThing -> ETagTestUtils.modifyThingResponse(thing, thing_2,
                                    modifyThing.getDittoHeaders(),
                                    false));
            assertPublishEvent(ThingModified.of(thing_2, 2L, TIMESTAMP, headersUsed, null));
        }};
    }

    @Test
    public void shutdownOnCommand() {
        new TestKit(actorSystem) {{
            final Thing thing = createThingV2WithRandomId();
            final ActorRef underTest = watch(createSupervisorActorFor(getIdOrThrow(thing)));

            final DistributedPubSubMediator.Subscribe subscribe =
                    DistPubSubAccess.subscribe(Shutdown.TYPE, underTest);
            pubSubTestProbe.expectMsg(subscribe);
            pubSubTestProbe.reply(new DistributedPubSubMediator.SubscribeAck(subscribe));

            underTest.tell(
                    Shutdown.getInstance(ShutdownReasonFactory.getPurgeNamespaceReason(thing.getNamespace().get()),
                            DittoHeaders.empty()), pubSubTestProbe.ref());
            expectTerminated(underTest);
        }};
    }

    private DittoHeaders testCreateAndModify(final Thing toCreate,
            final JsonSchemaVersion createVersion,
            final Thing toModify,
            final JsonSchemaVersion modifyVersion,
            final Function<ModifyThing, Object> expectedMessage) {

        final CreateThing createThing = createThing(toCreate, createVersion);
        final ModifyThing modifyThing = modifyThing(toModify, modifyVersion);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(createThing.getThing());

            underTest.tell(createThing, getRef());
            final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
            assertThingInResponse(createThingResponse.getThingCreated().orElse(null), createThing.getThing());
            assertPublishEvent(ThingCreated.of(toCreate, 1L, TIMESTAMP, createThing.getDittoHeaders(), null));

            underTest.tell(modifyThing, getRef());
            expectMsgEquals(expectedMessage.apply(modifyThing));
        }};
        return modifyThing.getDittoHeaders();
    }

    private void assertPublishEvent(final ThingEvent<?> event) {
        final ThingEvent<?> msg = pubSubTestProbe.expectMsgClass(ThingEvent.class);
        Assertions.assertThat(msg.toJson())
                .isEqualTo(event.toJson().set(msg.toJson().getField(Event.JsonFields.TIMESTAMP.getPointer()).get()));
        assertThat(msg.getDittoHeaders().getSchemaVersion()).isEqualTo(event.getDittoHeaders().getSchemaVersion());
    }

    private static Thing buildThing(final ThingId thingId) {
        return ThingsModelFactory.newThingBuilder()
                .setLifecycle(ThingLifecycle.ACTIVE)
                .setAttributes(THING_ATTRIBUTES)
                .setRevision(1)
                .setId(thingId)
                .setPolicyId(POLICY_ID)
                .build();
    }

    private static CreateThing createThing(final Thing thing, final JsonSchemaVersion version) {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .schemaVersion(version)
                .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                        AuthorizationSubject.newInstance(AUTH_SUBJECT)))
                .build();

        return CreateThing.of(thing, null, dittoHeaders);
    }

    private static ModifyThing modifyThing(final Thing thing, final JsonSchemaVersion version) {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .schemaVersion(version)
                .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                        AuthorizationSubject.newInstance(AUTH_SUBJECT)))
                .build();
        return ModifyThing.of(getIdOrThrow(thing),
                thing,
                null,
                dittoHeaders);
    }

    private ActorRef createPersistenceActorFor(final Thing thing) {
        return createPersistenceActorFor(getIdOrThrow(thing));
    }

    private static ThingId getIdOrThrow(final Thing thing) {
        return thing.getEntityId().orElseThrow(() -> new NoSuchElementException("Failed to get ID from thing!"));
    }

    private static Thing incrementThingRevision(final Thing thing) {
        return thing.toBuilder()
                .setRevision(thing.getRevision()
                        .map(Revision::increment)
                        .orElseGet(() -> ThingRevision.newInstance(1L)))
                .build();
    }

}
