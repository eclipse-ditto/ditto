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
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
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
import org.eclipse.ditto.base.model.entity.metadata.MetadataModelFactory;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.metadata.MetadataHeader;
import org.eclipse.ditto.base.model.headers.metadata.MetadataHeaderKey;
import org.eclipse.ditto.base.model.headers.metadata.MetadataHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.internal.utils.test.Retry;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseOptions;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicyResponse;
import org.eclipse.ditto.things.api.Permission;
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
import org.eclipse.ditto.things.model.signals.commands.exceptions.MetadataHeadersConflictException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingUnavailableException;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThingResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteAttribute;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteAttributeResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteThing;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteThingResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThing;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThingResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttribute;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttributes;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeature;
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
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

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

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    private static final JsonParseOptions JSON_PARSE_OPTIONS =
            JsonFactory.newParseOptionsBuilder().withoutUrlDecoding().build();

    private static final Instant TIMESTAMP = Instant.EPOCH;

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
        final Config customConfig = ConfigFactory.empty()
                .withValue("akka.actor.provider",
                        ConfigValueFactory.fromAnyRef("akka.cluster.ClusterActorRefProvider"));
        setup(customConfig);
    }

    @Test
    public void unavailableExpectedIfPersistenceActorTerminates() throws Exception {
        final Policy inlinePolicy = PoliciesModelFactory.newPolicyBuilder(POLICY_ID)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(SubjectIssuer.newInstance("test"), AUTH_SUBJECT)
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .setGrantedPermissions(PoliciesResourceType.policyResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .build();
        final CreatePolicyResponse createPolicyResponse = CreatePolicyResponse.of(POLICY_ID, inlinePolicy,
                DittoHeaders.empty());
        when(policyEnforcerProvider.getPolicyEnforcer(POLICY_ID))
                .thenReturn(CompletableFuture.completedStage(Optional.of(PolicyEnforcer.of(inlinePolicy))));

        new TestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final ThingId thingId = getIdOrThrow(thing);

                final ActorRef underTest = createSupervisorActorFor(thingId);


                // as the "createThing" shall be handled by the supervisor which now also does enforcement, pass along
                // an initial policy which will also be used for enforcement:
                final CreateThing createThing = CreateThing.of(thing, inlinePolicy.toJson(FieldType.all()),
                        dittoHeadersV2);
                underTest.tell(createThing, getRef());

                policiesShardRegionTestProbe.expectMsgClass(CreatePolicy.class);
                policiesShardRegionTestProbe.reply(createPolicyResponse);

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thing);
                when(policyEnforcerProvider.getPolicyEnforcer(POLICY_ID))
                        .thenReturn(CompletableFuture.completedStage(Optional.of(PolicyEnforcer.of(inlinePolicy))));
                // retrieve created thing
                final RetrieveThing retrieveThing = RetrieveThing.of(thingId, dittoHeadersV2);
                underTest.tell(retrieveThing, getRef());
                final DittoHeaders expectedHeaders = dittoHeadersV2.toBuilder()
                        .readGrantedSubjects(List.of(AUTHORIZED_SUBJECT))
                        .build();
                expectMsgEquals(ETagTestUtils.retrieveThingResponse(thing, null, expectedHeaders));

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

        final Props props = ThingPersistenceActor.props(thingIdOfActor, Mockito.mock(MongoReadJournal.class),
                getDistributedPub(), null);
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
                final PolicyId policyId = initialThing.getPolicyId().orElseThrow(IllegalStateException::new);
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
                        ETagTestUtils.modifyAttributeResponse(thingId, attributesToModify, headersMockWithOtherAuth,
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
                        JsonFactory.newFieldSelector(Thing.JsonFields.REVISION.getPointer().toString(),
                                JSON_PARSE_OPTIONS);
                final long versionExpected = 2;
                final Thing thingExpected = ThingsModelFactory.newThingBuilder(thingToModify)
                        .setRevision(versionExpected)
                        .build();
                final RetrieveThing retrieveThing = RetrieveThing.getBuilder(thingId, dittoHeadersV2)
                        .withSelectedFields(versionFieldSelector)
                        .build();
                underTest.tell(retrieveThing, getRef());
                expectMsgEquals(
                        ETagTestUtils.retrieveThingResponse(thingExpected, versionFieldSelector, dittoHeadersV2));
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
                        JsonFactory.newFieldSelector(Thing.JsonFields.REVISION.getPointer().toString(),
                                JSON_PARSE_OPTIONS);
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
            pubSubTestProbe.fishForMessage(Duration.apply(5, TimeUnit.SECONDS), "subscribe for shutdown",
                    PartialFunction.fromFunction(msg ->
                            msg instanceof DistributedPubSubMediator.Subscribe foundSubs && foundSubs.topic()
                                    .equals(Shutdown.TYPE))
            );
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

    @Test
    public void modifyAttributesMetadata() {
        final var thing = createThingV2WithRandomId();
        final var headers = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.PUT_METADATA.getKey(),
                        "[{\"key\":\"/attrKey/meta\",\"value\":{\"type\":\"bumlux\"}}]")
                .build();
        final var modifyAttributes = ModifyAttributes.of(getIdOrThrow(thing), THING_ATTRIBUTES, headers);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            // modify attributes with metadata
            underTest.tell(modifyAttributes, getRef());
            expectMsgEquals(
                    ETagTestUtils.modifyAttributeResponse(getIdOrThrow(thing), THING_ATTRIBUTES, headers, false));

            // assert that metadata was modified as expected
            final Metadata expectedMetadata = Metadata.newBuilder()
                    .set("/attributes/attrKey/meta", JsonObject.newBuilder()
                            .set("type", "bumlux")
                            .build())
                    .build();
            assertMetadataAsExpected(this, underTest, getIdOrThrow(thing), expectedMetadata);
        }};
    }

    @Test
    public void modifyAttributeMetadata() {
        final var thing = createThingV2WithRandomId();
        final var headers = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.PUT_METADATA.getKey(),
                        "[{\"key\":\"/sub\",\"value\":{\"type\":\"bumlux\"}}]")
                .build();
        final var modifyAttribute =
                ModifyAttribute.of(getIdOrThrow(thing), JsonPointer.of(ATTRIBUTE_KEY), JsonValue.of(ATTRIBUTE_VALUE),
                        headers);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            // modify attribute with metadata
            underTest.tell(modifyAttribute, getRef());
            expectMsgEquals(ETagTestUtils.modifyAttributeResponse(getIdOrThrow(thing), JsonPointer.of(ATTRIBUTE_KEY),
                    JsonValue.of(ATTRIBUTE_VALUE), headers, false));

            // assert that metadata was modified as expected
            final Metadata expectedMetadata = Metadata.newBuilder()
                    .set("/attributes/attrKey/sub", JsonObject.newBuilder()
                            .set("type", "bumlux")
                            .build())
                    .build();
            assertMetadataAsExpected(this, underTest, getIdOrThrow(thing), expectedMetadata);
        }};
    }

    @Test
    public void modifyFeaturesMetadata() {
        final var thing = createThingV2WithRandomId();
        final var headers = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.PUT_METADATA.getKey(),
                        "[{\"key\":\"/featureId\",\"value\":{\"type\":\"bumlux\"}}]")
                .build();
        final var modifyFeatures = ModifyFeatures.of(getIdOrThrow(thing), THING_FEATURES, headers);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            // modify features with metadata
            underTest.tell(modifyFeatures, getRef());
            expectMsgEquals(ETagTestUtils.modifyFeaturesResponse(getIdOrThrow(thing), THING_FEATURES, headers, false));

            // assert that metadata was modified as expected
            final Metadata expectedMetadata = Metadata.newBuilder()
                    .set("/features/featureId", JsonObject.newBuilder()
                            .set("type", "bumlux")
                            .build())
                    .build();
            assertMetadataAsExpected(this, underTest, getIdOrThrow(thing), expectedMetadata);
        }};
    }

    @Test
    public void modifyFeatureMetadata() {
        final var thing = createThingV2WithRandomId();
        final var headers = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.PUT_METADATA.getKey(),
                        "[{\"key\":\"/sub\",\"value\":{\"type\":\"bumlux\"}}]")
                .build();
        final var modifyFeature = ModifyFeature.of(getIdOrThrow(thing), THING_FEATURE, headers);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            // modify features with metadata
            underTest.tell(modifyFeature, getRef());
            expectMsgEquals(ETagTestUtils.modifyFeatureResponse(getIdOrThrow(thing), THING_FEATURE, headers, false));

            // assert that metadata was modified as expected
            final Metadata expectedMetadata = Metadata.newBuilder()
                    .set("/features/featureId/sub", JsonObject.newBuilder()
                            .set("type", "bumlux")
                            .build())
                    .build();
            assertMetadataAsExpected(this, underTest, getIdOrThrow(thing), expectedMetadata);
        }};
    }

    @Test
    public void modifyThingWithWildcardInMetadata() {
        final var thing = createThingV2WithRandomId();
        final var headers = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.PUT_METADATA.getKey(),
                        "[{\"key\":\"*/modified\",\"value\":\"2022-06-23T06:49:05\"}]")
                .build();
        final var modifyThing = ModifyThing.of(getIdOrThrow(thing), thing, null, headers);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            // modify features with metadata
            underTest.tell(modifyThing, getRef());
            expectMsgEquals(ETagTestUtils.modifyThingResponse(thing, thing, headers, false));

            final var modifiedMetadata = JsonObject.newBuilder()
                    .set("modified", "2022-06-23T06:49:05")
                    .build();
            // assert that metadata was created as expected
            final Metadata expectedMetadata = Metadata.newBuilder()
                    .set("thingId", modifiedMetadata)
                    .set("policyId", modifiedMetadata)
                    .set("/attributes/attrKey", modifiedMetadata)
                    .set("/features/featureId/definition", modifiedMetadata)
                    .set("/features/featureId/properties/featurePropertyKey", modifiedMetadata)
                    .build();

            assertMetadataAsExpected(this, underTest, getIdOrThrow(thing), expectedMetadata);
        }};
    }

    private static void assertMetadataAsExpected(final TestKit testKit, final ActorRef underTest, final ThingId thingId,
            final Metadata expectedMetadata) {
        final var retrieveThing = RetrieveThing.getBuilder(thingId, DittoHeaders.empty())
                .withSelectedFields(JsonFactory.newFieldSelector("_metadata"))
                .build();
        underTest.tell(retrieveThing, testKit.getRef());
        final var retrieveThingResponse = testKit.expectMsgClass(RetrieveThingResponse.class);
        final var metadata = retrieveThingResponse.getThing().getMetadata();
        assertThat(metadata).isPresent();
        assertThat(metadata).contains(expectedMetadata);
    }

    @Test
    public void retrieveFeaturesMetadataWithGetMetadataHeader() {
        final Thing thing = createThingV2WithRandomIdAndMetadata();
        final DittoHeaders dittoHeaders = dittoHeadersV2.toBuilder()
                .putHeader(DittoHeaderDefinition.GET_METADATA.getKey(), "/features")
                .build();
        final ThingCommand<?> retrieveThingCommand = RetrieveThing.of(getIdOrThrow(thing), dittoHeaders);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            // retrieve thing with metadata
            final Metadata expectedMetadata = Metadata.newBuilder()
                    .set("features", JsonObject.newBuilder()
                            .set(FEATURE_ID, JsonObject.newBuilder()
                                    .set("definition", JsonObject.newBuilder()
                                            .set("issuedBy", "the epic Ditto team")
                                            .build())
                                    .set("properties", JsonObject.newBuilder()
                                            .set(FEATURE_PROPERTY_KEY, JsonObject.newBuilder()
                                                    .set("issuedBy", "the epic Ditto team")
                                                    .set("unit", "Quarks")
                                                    .build())
                                            .build())
                                    .build())
                            .build())
                    .build();
            final DittoHeaders expectedHeaders = dittoHeaders.toBuilder()
                    .putHeader(DittoHeaderDefinition.DITTO_METADATA.getKey(), expectedMetadata.toJsonString())
                    .build();

            underTest.tell(retrieveThingCommand, getRef());
            expectMsgEquals(ETagTestUtils.retrieveThingResponse(thing, null, expectedHeaders));
        }};
    }

    @Test
    public void retrieveFeatureMetadataWithGetMetadataHeader() {
        final Thing thing = createThingV2WithRandomIdAndMetadata()
                .setFeatureProperty("featureId2", "featureKey2", "someValue");
        final DittoHeaders dittoHeaders = dittoHeadersV2.toBuilder()
                .putHeader(DittoHeaderDefinition.GET_METADATA.getKey(), "/features/featureId")
                .build();
        final ThingCommand<?> retrieveThingCommand = RetrieveThing.of(getIdOrThrow(thing), dittoHeaders);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            // retrieve thing with metadata
            final Metadata expectedMetadata = Metadata.newBuilder()
                    .set("features", JsonObject.newBuilder()
                            .set("featureId", JsonObject.newBuilder()
                                    .set("definition", JsonObject.newBuilder()
                                            .set("issuedBy", "the epic Ditto team")
                                            .build())
                                    .set("properties", JsonObject.newBuilder()
                                            .set("featurePropertyKey", JsonObject.newBuilder()
                                                    .set("issuedBy", "the epic Ditto team")
                                                    .set("unit", "Quarks")
                                                    .build())
                                            .build())
                                    .build())
                            .build())
                    .build();
            final DittoHeaders expectedHeaders = dittoHeaders.toBuilder()
                    .putHeader(DittoHeaderDefinition.DITTO_METADATA.getKey(), expectedMetadata.toJsonString())
                    .build();

            underTest.tell(retrieveThingCommand, getRef());
            expectMsgEquals(ETagTestUtils.retrieveThingResponse(thing, null, expectedHeaders));
        }};
    }

    @Test
    public void retrieveAttributesWithGetMetadataHeader() {
        final Thing thing = createThingV2WithRandomIdAndMetadata();
        final DittoHeaders dittoHeaders = dittoHeadersV2.toBuilder()
                .putHeader(DittoHeaderDefinition.GET_METADATA.getKey(), "attrKey")
                .build();
        final ThingCommand<?> retrieveAttributesCommand = RetrieveAttributes.of(getIdOrThrow(thing), dittoHeaders);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            // retrieve attributes with metadata
            final Metadata expectedMetadata = Metadata.newBuilder()
                    .set("attrKey", JsonObject.newBuilder()
                            .set("issuedBy", "the epic Ditto team")
                            .set("edited", "2022-05-31 15:55:55")
                            .build())
                    .build();
            final DittoHeaders expectedHeaders = dittoHeaders.toBuilder()
                    .putHeader(DittoHeaderDefinition.DITTO_METADATA.getKey(), expectedMetadata.toJsonString())
                    .build();

            underTest.tell(retrieveAttributesCommand, getRef());
            expectMsgEquals(ETagTestUtils.retrieveAttributesResponse(getIdOrThrow(thing), THING_ATTRIBUTES,
                    THING_ATTRIBUTES.toJson(retrieveAttributesCommand.getImplementedSchemaVersion()), expectedHeaders));
        }};
    }

    @Test
    public void retrieveFeaturePropertyAndAttributeMetadataWithGetMetadataWildcardHeader() {
        final Thing thing = createThingV2WithRandomIdAndMetadata();
        final DittoHeaders dittoHeaders = dittoHeadersV2.toBuilder()
                .putHeader(DittoHeaderDefinition.GET_METADATA.getKey(),
                        "attributes/*/edited,features/*/properties/*/unit")
                .build();
        final ThingCommand<?> retrieveThingCommand = RetrieveThing.of(getIdOrThrow(thing), dittoHeaders);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            // retrieve thing with metadata
            final Metadata expectedMetadata = Metadata.newBuilder()
                    .set("attributes", JsonObject.newBuilder()
                            .set("attrKey", JsonObject.newBuilder()
                                    .set("edited", "2022-05-31 15:55:55")
                                    .build())
                            .build())
                    .set("features", JsonObject.newBuilder()
                            .set("featureId", JsonObject.newBuilder()
                                    .set("properties", JsonObject.newBuilder()
                                            .set("featurePropertyKey", JsonObject.newBuilder()
                                                    .set("unit", "Quarks")
                                                    .build())
                                            .build())
                                    .build())
                            .build())
                    .build();
            final DittoHeaders expectedHeaders = dittoHeaders.toBuilder()
                    .putHeader(DittoHeaderDefinition.DITTO_METADATA.getKey(), expectedMetadata.toJsonString())
                    .build();

            underTest.tell(retrieveThingCommand, getRef());
            expectMsgEquals(ETagTestUtils.retrieveThingResponse(thing, null, expectedHeaders));
        }};
    }

    @Test
    public void retrieveLeafMetadataWithGetMetadataWildcardHeader() {
        final Thing thing = createThingV2WithRandomId();
        final DittoHeaders dittoHeaders = dittoHeadersV2.toBuilder()
                .putHeader(DittoHeaderDefinition.GET_METADATA.getKey(), "*/issuedBy")
                .build();
        final ThingCommand<?> retrieveThingCommand = RetrieveThing.of(getIdOrThrow(thing), dittoHeaders);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            // modify thing
            final MetadataHeaders metadataHeaders = MetadataHeaders.newInstance();
            metadataHeaders.add(
                    MetadataHeader.of(MetadataHeaderKey.parse("*/issuedBy"), JsonValue.of("the epic Ditto team"))
            );

            final DittoHeaders modifyHeaders = dittoHeadersV2.toBuilder()
                    .putHeader(DittoHeaderDefinition.PUT_METADATA.getKey(), metadataHeaders.toJsonString())
                    .build();
            final ModifyThing modifyThing = ModifyThing.of(getIdOrThrow(thing), thing, null, modifyHeaders);
            underTest.tell(modifyThing, getRef());
            expectMsgClass(ModifyThingResponse.class);

            // retrieve thing with metadata
            final Thing thingExpected = ThingsModelFactory.newThingBuilder(thing)
                    .setRevision(2L)
                    .build();
            final Metadata expectedMetadata = Metadata.newBuilder()
                    .set("thingId", JsonObject.newBuilder()
                            .set("issuedBy", "the epic Ditto team")
                            .build())
                    .set("policyId", JsonObject.newBuilder()
                            .set("issuedBy", "the epic Ditto team")
                            .build())
                    .set("attributes", JsonObject.newBuilder()
                            .set(ATTRIBUTE_KEY, JsonObject.newBuilder()
                                    .set("issuedBy", "the epic Ditto team")
                                    .build())
                            .build())
                    .set("features", JsonObject.newBuilder()
                            .set(FEATURE_ID, JsonObject.newBuilder()
                                    .set("definition", JsonObject.newBuilder()
                                            .set("issuedBy", "the epic Ditto team")
                                            .build())

                                    .set("properties", JsonObject.newBuilder()
                                            .set(FEATURE_PROPERTY_KEY, JsonObject.newBuilder()
                                                    .set("issuedBy", "the epic Ditto team")
                                                    .build())
                                            .build())
                                    .build())
                            .build())
                    .build();
            final DittoHeaders expectedHeaders = dittoHeaders.toBuilder()
                    .putHeader(DittoHeaderDefinition.DITTO_METADATA.getKey(), expectedMetadata.toJsonString())
                    .build();

            underTest.tell(retrieveThingCommand, getRef());
            expectMsgEquals(ETagTestUtils.retrieveThingResponse(thingExpected, null, expectedHeaders));
        }};
    }

    @Test
    public void deleteAttributesMetadataWithDeleteMetadataHeader() {
        final Thing thing = createThingV2WithRandomIdAndMetadata();
        final DittoHeaders dittoHeaders = dittoHeadersV2.toBuilder()
                .putHeader(DittoHeaderDefinition.DELETE_METADATA.getKey(), "attributes/")
                .build();
        final ThingCommand<?> modifyThingCommand = ModifyThing.of(getIdOrThrow(thing), thing, null, dittoHeaders);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing from json to use initial metadata creation
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            underTest.tell(modifyThingCommand, getRef());
            expectMsgClass(ModifyThingResponse.class);

            // retrieve thing with metadata
            final Metadata expectedMetadata = METADATA.toBuilder()
                    .remove("attributes")
                    .build();

            final RetrieveThing retrieveModifiedThing =
                    RetrieveThing.getBuilder(getIdOrThrow(thing), dittoHeadersV2)
                            .withSelectedFields(ALL_FIELDS_SELECTOR_WITH_METADATA)
                            .build();
            underTest.tell(retrieveModifiedThing, getRef());
            final RetrieveThingResponse retrieveModifiedThingResponse = expectMsgClass(RetrieveThingResponse.class);
            assertThat(retrieveModifiedThingResponse.getThing().getMetadata().orElseThrow())
                    .isEqualTo(expectedMetadata);
        }};
    }

    @Test
    public void deleteFeaturePropertyMetadataWithDeleteMetadataWildcardHeader() {
        final Thing thing = createThingV2WithRandomIdAndMetadata();
        final DittoHeaders dittoHeaders = dittoHeadersV2.toBuilder()
                .putHeader(DittoHeaderDefinition.DELETE_METADATA.getKey(), "features/*/properties/*/unit")
                .build();
        final ThingCommand<?> modifyThingCommand = ModifyThing.of(getIdOrThrow(thing), thing, null, dittoHeaders);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing from json to use initial metadata creation
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            underTest.tell(modifyThingCommand, getRef());
            expectMsgClass(ModifyThingResponse.class);

            // retrieve thing with metadata
            final Metadata expectedMetadata = METADATA.toBuilder()
                    .remove("features/featureId/properties/featurePropertyKey/unit")
                    .build();

            final RetrieveThing retrieveModifiedThing =
                    RetrieveThing.getBuilder(getIdOrThrow(thing), dittoHeadersV2)
                            .withSelectedFields(ALL_FIELDS_SELECTOR_WITH_METADATA)
                            .build();
            underTest.tell(retrieveModifiedThing, getRef());
            final RetrieveThingResponse retrieveModifiedThingResponse = expectMsgClass(RetrieveThingResponse.class);
            assertThat(retrieveModifiedThingResponse.getThing().getMetadata().orElseThrow())
                    .isEqualTo(expectedMetadata);
        }};
    }

    @Test
    public void deleteFeaturePropertyMetadataWithDeleteMetadataWildcardHeaderForMergeCommand() {
        final Thing thing = createThingV2WithRandomIdAndMetadata();
        final DittoHeaders dittoHeaders = dittoHeadersV2.toBuilder()
                .putHeader(DittoHeaderDefinition.DELETE_METADATA.getKey(), "/unit")
                .build();
        final ThingCommand<?> mergeThingCommand =
                MergeThing.withFeatureProperty(getIdOrThrow(thing), FEATURE_ID, JsonPointer.of(FEATURE_PROPERTY_KEY),
                        JsonFactory.newValue("bumlux"), dittoHeaders);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing from json to use initial metadata creation
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            underTest.tell(mergeThingCommand, getRef());
            expectMsgClass(MergeThingResponse.class);

            // retrieve thing with metadata
            final Metadata expectedMetadata = METADATA.toBuilder()
                    .remove("features/featureId/properties/featurePropertyKey/unit")
                    .build();

            final RetrieveThing retrieveModifiedThing =
                    RetrieveThing.getBuilder(getIdOrThrow(thing), dittoHeadersV2)
                            .withSelectedFields(ALL_FIELDS_SELECTOR_WITH_METADATA)
                            .build();
            underTest.tell(retrieveModifiedThing, getRef());
            final RetrieveThingResponse retrieveModifiedThingResponse = expectMsgClass(RetrieveThingResponse.class);
            assertThat(retrieveModifiedThingResponse.getThing().getMetadata().orElseThrow())
                    .isEqualTo(expectedMetadata);
        }};
    }

    @Test
    public void deleteExistingMetadataWithMergeCommand() {
        final Thing thing = createThingV2WithRandomIdAndMetadata();
        final ThingCommand<?> mergeThingCommand =
                MergeThing.withFeatureProperty(getIdOrThrow(thing), FEATURE_ID, JsonPointer.of(FEATURE_PROPERTY_KEY),
                        JsonFactory.nullLiteral(), dittoHeadersV2);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing from json to use initial metadata creation
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            underTest.tell(mergeThingCommand, getRef());
            expectMsgClass(MergeThingResponse.class);

            // retrieve thing with metadata
            final Metadata expectedMetadata = METADATA.toBuilder()
                    .remove("features/featureId/properties/featurePropertyKey")
                    .build();

            final RetrieveThing retrieveModifiedThing =
                    RetrieveThing.getBuilder(getIdOrThrow(thing), dittoHeadersV2)
                            .withSelectedFields(ALL_FIELDS_SELECTOR_WITH_METADATA)
                            .build();
            underTest.tell(retrieveModifiedThing, getRef());
            final RetrieveThingResponse retrieveModifiedThingResponse = expectMsgClass(RetrieveThingResponse.class);
            assertThat(retrieveModifiedThingResponse.getThing().getMetadata().orElseThrow())
                    .isEqualTo(expectedMetadata);
        }};
    }

    @Test
    public void deleteMetadataForDeleteAttributeCommand() {
        final Thing thing = createThingV2WithRandomIdAndMetadata();
        final ThingCommand<?> deleteAttributeCommand =
                DeleteAttribute.of(getIdOrThrow(thing), JsonPointer.of(ATTRIBUTE_KEY), dittoHeadersV2);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing from json to use initial metadata creation
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            underTest.tell(deleteAttributeCommand, getRef());
            expectMsgClass(DeleteAttributeResponse.class);

            // retrieve thing with metadata
            final Metadata expectedMetadata = METADATA.toBuilder()
                    .remove("attributes/attrKey")
                    .build();

            final RetrieveThing retrieveModifiedThing =
                    RetrieveThing.getBuilder(getIdOrThrow(thing), dittoHeadersV2)
                            .withSelectedFields(ALL_FIELDS_SELECTOR_WITH_METADATA)
                            .build();
            underTest.tell(retrieveModifiedThing, getRef());
            final RetrieveThingResponse retrieveThingResponse = expectMsgClass(RetrieveThingResponse.class);
            assertThat(retrieveThingResponse.getThing().getMetadata().orElseThrow())
                    .isEqualTo(expectedMetadata);
        }};
    }

    @Test
    public void multipleMetadataHeadersResultsInException() {
        final Thing thing = createThingV2WithRandomIdAndMetadata();
        final MetadataHeaders metadataHeaders = MetadataHeaders.newInstance();
        metadataHeaders.add(MetadataHeader.of(MetadataHeaderKey.of(JsonPointer.of("*/foo")), JsonValue.of("bar")));
        final DittoHeaders dittoHeaders = dittoHeadersV2.toBuilder()
                .putHeader(DittoHeaderDefinition.DELETE_METADATA.getKey(), "attributes/")
                .putHeader(DittoHeaderDefinition.PUT_METADATA.getKey(), metadataHeaders.toJsonString())
                .build();
        final ThingCommand<?> modifyAttributeCommand =
                ModifyAttribute.of(getIdOrThrow(thing), JsonPointer.of(ATTRIBUTE_KEY), JsonValue.nullLiteral(),
                        dittoHeaders);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing from json to use initial metadata creation
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            underTest.tell(modifyAttributeCommand, getRef());
            expectMsgClass(MetadataHeadersConflictException.class);
        }};
    }

    @Test
    public void testRemovalOfEmptyMetadataAfterDeletion() {
        final var thing = createThingV2WithRandomId();
        final var putHeaders = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.PUT_METADATA.getKey(),
                        "[{\"key\":\"*/modified\",\"value\":\"2022-06-23T06:49:05\"}]")
                .build();
        final var modifyThing = ModifyThing.of(getIdOrThrow(thing), thing, null, putHeaders);

        final var deleteHeaders = DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.DELETE_METADATA.getKey(), "*/modified")
                .build();
        final var modifyThing1 = ModifyThing.of(getIdOrThrow(thing), thing, null, deleteHeaders);

        new TestKit(actorSystem) {{
            final ActorRef underTest = createPersistenceActorFor(thing);

            // create thing
            final JsonObject commandJson = getJsonCommand(thing);
            final CreateThing createThing = CreateThing.fromJson(commandJson, dittoHeadersV2);
            underTest.tell(createThing, getRef());
            expectMsgClass(CreateThingResponse.class);

            // modify features with metadata
            underTest.tell(modifyThing, getRef());
            expectMsgEquals(ETagTestUtils.modifyThingResponse(thing, thing, putHeaders, false));

            underTest.tell(modifyThing1, getRef());
            expectMsgEquals(ETagTestUtils.modifyThingResponse(thing.toBuilder().setRevision(2).build(), thing,
                    deleteHeaders, false));

            // assert that metadata is empty
            final Metadata expectedEmptyMetadata = MetadataModelFactory.emptyMetadata();

            assertMetadataAsExpected(this, underTest, getIdOrThrow(thing), expectedEmptyMetadata);
        }};
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

    private static JsonObject getJsonCommand(final Thing thing) {
        return JsonFactory.newObjectBuilder()
                .set(ThingCommand.JsonFields.TYPE, CreateThing.TYPE)
                .set(CreateThing.JSON_THING, thing.toJson(FieldType.all()))
                .build();
    }

}
