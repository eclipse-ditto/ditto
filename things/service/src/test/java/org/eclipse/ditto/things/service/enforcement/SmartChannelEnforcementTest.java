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

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.policies.model.SubjectIssuer.GOOGLE;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.apache.pekko.cluster.pubsub.DistributedPubSubMediator;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.exceptions.CommandTimeoutException;
import org.eclipse.ditto.internal.utils.pubsub.StreamingType;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.api.Permission;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.things.model.ThingIdInvalidException;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.ThingErrorResponse;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttributes;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveAttributesResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import scala.PartialFunction;
import scala.concurrent.duration.FiniteDuration;

/**
 * Tests {@link org.eclipse.ditto.things.service.persistence.actors.ThingSupervisorActor} and its
 * {@link org.eclipse.ditto.things.service.enforcement.ThingEnforcement} for commands requiring smart channel selection.
 */
public final class SmartChannelEnforcementTest extends AbstractThingEnforcementTest {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    public static final PolicyId POLICY_ID = PolicyId.of("policy:id");
    private static final PolicyId RESTRICTED_POLICY_ID = PolicyId.of("policy:restricted");
    private static final JsonPointer SECRET_POINTER = JsonPointer.of("/attributes/secret");

    @Rule
    public final TestName testName = new TestName();

    private JsonObject thing;
    private SudoRetrieveThingResponse sudoRetrieveThingResponse;

    @Before
    public void init() {
        super.init();
        final PolicyId policyId = POLICY_ID;
        thing = TestSetup.newThingWithPolicyId(policyId);
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("authorize-self")
                .setSubject(GOOGLE, TestSetup.SUBJECT_ID)
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ, Permission.WRITE))
                .build();
        sudoRetrieveThingResponse = SudoRetrieveThingResponse.of(thing, DittoHeaders.empty());
        when(policyEnforcerProvider.getPolicyEnforcer(policyId))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(PolicyEnforcer.of(policy))));
        when(policyEnforcerProvider.getPolicyEnforcer(null))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
    }

    @Test
    public void twinResponseOfLiveChannelConditionMismatchIsFilteredByPolicy() {
        final JsonObject restrictedThing = mockRestrictedPolicyAndGetThing();

        new TestKit(system) {{
            final var retrieveThing =
                    getRetrieveThing(headers -> headers.liveChannelCondition("eq(attributes/pub,\"zzz\")"));
            supervisor.tell(retrieveThing, getRef());
            expectAndAnswerSudoRetrieveThing(SudoRetrieveThingResponse.of(restrictedThing, DittoHeaders.empty()));

            thingPersistenceActorProbe.expectMsgClass(RetrieveThing.class);
            thingPersistenceActorProbe.reply(RetrieveThingResponse.of(retrieveThing.getEntityId(), restrictedThing,
                    liveChannelConditionMatchedHeaders(retrieveThing, false)));

            final var response = TestSetup.fishForMsgClass(this, RetrieveThingResponse.class);
            assertTwinChannel(response);
            Assertions.assertThat(response.getDittoHeaders().didLiveChannelConditionMatch()).isFalse();
            Assertions.assertThat(response.getEntity().asObject().getValue(SECRET_POINTER)).isEmpty();
            Assertions.assertThat(response.getEntity().asObject().getValue("attributes/pub")).isPresent();
        }};
    }

    @Test
    public void subResourceTwinResponseOfLiveChannelConditionMismatchIsFilteredByPolicy() {
        final JsonObject restrictedThing = mockRestrictedPolicyAndGetThing();

        new TestKit(system) {{
            final var retrieveAttributes = RetrieveAttributes.of(TestSetup.THING_ID, headers().toBuilder()
                    .liveChannelCondition("eq(attributes/pub,\"zzz\")")
                    .build());
            supervisor.tell(retrieveAttributes, getRef());
            expectAndAnswerSudoRetrieveThing(SudoRetrieveThingResponse.of(restrictedThing, DittoHeaders.empty()));

            thingPersistenceActorProbe.expectMsgClass(RetrieveAttributes.class);
            thingPersistenceActorProbe.reply(RetrieveAttributesResponse.of(TestSetup.THING_ID,
                    restrictedThing.getValue("attributes").orElseThrow().asObject(),
                    retrieveAttributes.getDittoHeaders().toBuilder()
                            .putHeader(DittoHeaderDefinition.LIVE_CHANNEL_CONDITION_MATCHED.getKey(), "false")
                            .build()));

            final var response = TestSetup.fishForMsgClass(this, RetrieveAttributesResponse.class);
            Assertions.assertThat(response.getAttributes().getValue("secret")).isEmpty();
            Assertions.assertThat(response.getAttributes().getValue("pub")).isPresent();
        }};
    }

    @Test
    public void twinFallbackResponseAfterLiveChannelTimeoutIsFilteredByPolicy() {
        final JsonObject restrictedThing = mockRestrictedPolicyAndGetThing();

        new TestKit(system) {{
            final var retrieveThing = getRetrieveThing(headers -> headers.liveChannelCondition("exists(thingId)")
                    .timeout(Duration.ofMillis(1))
                    .putHeader(DittoHeaderDefinition.LIVE_CHANNEL_TIMEOUT_STRATEGY.getKey(), "use-twin"));
            supervisor.tell(retrieveThing, getRef());
            expectAndAnswerSudoRetrieveThing(SudoRetrieveThingResponse.of(restrictedThing, DittoHeaders.empty()));

            thingPersistenceActorProbe.expectMsgClass(RetrieveThing.class);
            thingPersistenceActorProbe.reply(RetrieveThingResponse.of(retrieveThing.getEntityId(), restrictedThing,
                    liveChannelConditionMatchedHeaders(retrieveThing, true)));

            expectLiveQueryCommandOnPubSub(retrieveThing);
            final var response = TestSetup.fishForMsgClass(this, RetrieveThingResponse.class);
            assertTwinChannel(response);
            Assertions.assertThat(response.getEntity().asObject().getValue(SECRET_POINTER)).isEmpty();
            Assertions.assertThat(response.getEntity().asObject().getValue("attributes/pub")).isPresent();
        }};
    }

    @Test
    public void thingNotAccessibleAfterEnforcement() {
        new TestKit(system) {{
            final var retrieveThing = getRetrieveThing(headers -> headers.liveChannelCondition("exists(thingId)"));
            supervisor.tell(retrieveThing, getRef());
            expectAndAnswerSudoRetrieveThing(ThingNotAccessibleException.newBuilder(retrieveThing.getEntityId())
                    .dittoHeaders(retrieveThing.getDittoHeaders())
                    .build());

            expectMsgClass(ThingNotAccessibleException.class);
        }};
    }

    @Test
    public void matchLiveChannelCondition() {
        new TestKit(system) {{
            final var retrieveThing = getRetrieveThing(headers -> headers.liveChannelCondition("exists(thingId)"));
            supervisor.tell(retrieveThing, getRef());
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);

            thingPersistenceActorProbe.expectMsg(addReadSubjectHeader(retrieveThing, TestSetup.GOOGLE_SUBJECT));
            thingPersistenceActorProbe.reply(getRetrieveThingResponse(retrieveThing, true, b -> {}));

            expectLiveQueryCommandOnPubSub(retrieveThing);
            pubSubMediatorProbe.reply(getRetrieveThingResponse(retrieveThing, true, b -> b.channel("live")));

            assertLiveChannel(expectMsgClass(RetrieveThingResponse.class));
        }};
    }

    @Test
    public void liveChannelTimeoutWithTwinFallback() {
        new TestKit(system) {{
            final var retrieveThing = getRetrieveThing(headers -> headers.liveChannelCondition("exists(thingId)")
                    .timeout(Duration.ofMillis(1))
                    .putHeader(DittoHeaderDefinition.LIVE_CHANNEL_TIMEOUT_STRATEGY.getKey(), "use-twin"));
            supervisor.tell(retrieveThing, getRef());
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);

            thingPersistenceActorProbe.expectMsg(addReadSubjectHeader(retrieveThing, TestSetup.GOOGLE_SUBJECT));
            final var twinResponse = getRetrieveThingResponse(retrieveThing, true, b -> {});
            thingPersistenceActorProbe.reply(twinResponse);

            expectLiveQueryCommandOnPubSub(retrieveThing);
            assertTwinChannel(expectMsgClass(RetrieveThingResponse.class));
        }};
    }

    @Test
    public void liveChannelErrorWithTwinFallback() {
        new TestKit(system) {{
            final var retrieveThing = getRetrieveThing(headers -> headers.liveChannelCondition("exists(thingId)")
                    .putHeader(DittoHeaderDefinition.LIVE_CHANNEL_TIMEOUT_STRATEGY.getKey(), "use-twin"));
            supervisor.tell(retrieveThing, getRef());
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);

            thingPersistenceActorProbe.expectMsg(addReadSubjectHeader(retrieveThing, TestSetup.GOOGLE_SUBJECT));
            final var twinResponse = getRetrieveThingResponse(retrieveThing, true, b -> {});
            thingPersistenceActorProbe.reply(twinResponse);

            expectLiveQueryCommandOnPubSub(retrieveThing);
            pubSubMediatorProbe.reply(
                    ThingErrorResponse.of(ThingIdInvalidException.newBuilder(retrieveThing.getEntityId())
                            .dittoHeaders(DittoHeaders.newBuilder().channel("live")
                                    .putHeader(DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey(),
                                            getRef().path().toSerializationFormat())
                                    .build())
                            .build()));

            final var receivedErrorResponse = expectMsgClass(ThingErrorResponse.class);
            assertLiveChannel(receivedErrorResponse);
            Assertions.assertThat(receivedErrorResponse.getDittoRuntimeException())
                    .isInstanceOf(ThingIdInvalidException.class);
        }};
    }

    @Test
    public void liveChannelTimeoutWithoutTwinFallback() {
        new TestKit(system) {{
            final var retrieveThing = getRetrieveThing(headers -> headers.liveChannelCondition("exists(thingId)")
                    .timeout(Duration.ofMillis(1)));
            supervisor.tell(retrieveThing, getRef());
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);

            thingPersistenceActorProbe.expectMsg(addReadSubjectHeader(retrieveThing, TestSetup.GOOGLE_SUBJECT));
            final var twinResponse = getRetrieveThingResponse(retrieveThing, true, b -> {});
            thingPersistenceActorProbe.reply(twinResponse);

            expectLiveQueryCommandOnPubSub(retrieveThing);
            assertLiveChannel(expectMsgClass(CommandTimeoutException.class));
        }};
    }

    @Test
    public void liveChannelConditionMismatch() {
        new TestKit(system) {{
            final var retrieveThing = getRetrieveThing(headers -> headers.liveChannelCondition("exists(thingId)")
                    .timeout(Duration.ofMillis(1)));
            supervisor.tell(retrieveThing, getRef());
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);

            thingPersistenceActorProbe.expectMsg(addReadSubjectHeader(retrieveThing, TestSetup.GOOGLE_SUBJECT));
            final var twinResponse = getRetrieveThingResponse(retrieveThing, false, b -> {});
            thingPersistenceActorProbe.reply(twinResponse);

            final var response = TestSetup.fishForMsgClass(this, RetrieveThingResponse.class);
            assertTwinChannel(response);
            Assertions.assertThat(response.getDittoHeaders().didLiveChannelConditionMatch()).isFalse();
        }};
    }

    @Test
    public void liveCommandTimeoutWithTwinFallback() {
        new TestKit(system) {{
            final var retrieveThing = getRetrieveThing(headers -> headers.channel("live")
                    .timeout(Duration.ofMillis(1))
                    .putHeader(DittoHeaderDefinition.LIVE_CHANNEL_TIMEOUT_STRATEGY.getKey(), "use-twin"));
            supervisor.tell(retrieveThing, getRef());
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);

            RetrieveThing expectedRetrieveThing = addReadSubjectHeader(retrieveThing, TestSetup.GOOGLE_SUBJECT);
            expectedRetrieveThing = expectedRetrieveThing.setDittoHeaders(expectedRetrieveThing.getDittoHeaders()
                    .toBuilder()
                    .channel(Signal.CHANNEL_TWIN)
                    .readRevokedSubjects(List.of())
                    .build());
            thingPersistenceActorProbe.expectMsg(expectedRetrieveThing);
            final var twinResponse = getRetrieveThingResponse(retrieveThing, true, b -> {});
            thingPersistenceActorProbe.reply(twinResponse);

            expectLiveQueryCommandOnPubSub(retrieveThing);
            assertTwinChannel(expectMsgClass(RetrieveThingResponse.class));
        }};
    }

    @Test
    public void liveCommandErrorWithTwinFallback() {
        new TestKit(system) {{
            final var retrieveThing = getRetrieveThing(headers -> headers.channel("live")
                    .putHeader(DittoHeaderDefinition.LIVE_CHANNEL_TIMEOUT_STRATEGY.getKey(), "use-twin"));
            supervisor.tell(retrieveThing, getRef());
            expectAndAnswerSudoRetrieveThing(sudoRetrieveThingResponse);

            RetrieveThing expectedRetrieveThing = addReadSubjectHeader(retrieveThing, TestSetup.GOOGLE_SUBJECT);
            expectedRetrieveThing = expectedRetrieveThing.setDittoHeaders(expectedRetrieveThing.getDittoHeaders()
                    .toBuilder()
                    .channel(Signal.CHANNEL_TWIN)
                    .readRevokedSubjects(List.of())
                    .build());
            thingPersistenceActorProbe.expectMsg(expectedRetrieveThing);
            final var twinResponse = getRetrieveThingResponse(retrieveThing, true, b -> {});
            thingPersistenceActorProbe.reply(twinResponse);

            expectLiveQueryCommandOnPubSub(retrieveThing);
            pubSubMediatorProbe.reply(
                    ThingErrorResponse.of(ThingIdInvalidException.newBuilder(retrieveThing.getEntityId())
                            .dittoHeaders(DittoHeaders.newBuilder().channel("live")
                                    .putHeader(DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey(),
                                            getRef().path().toSerializationFormat())
                                    .build())
                            .build()));
            final var receivedErrorResponse = expectMsgClass(ThingErrorResponse.class);
            assertLiveChannel(receivedErrorResponse);
            Assertions.assertThat(receivedErrorResponse.getDittoRuntimeException())
                    .isInstanceOf(ThingIdInvalidException.class);
        }};
    }

    private DittoHeaders headers() {
        return DittoHeaders.newBuilder()
                .authorizationContext(
                        AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED, TestSetup.SUBJECT,
                                AuthorizationSubject.newInstance(String.format("%s:%s", GOOGLE, TestSetup.SUBJECT_ID))))
                .correlationId(testName.getMethodName())
                .schemaVersion(JsonSchemaVersion.V_2)
                .build();
    }

    private RetrieveThing getRetrieveThing(final Consumer<DittoHeadersBuilder<?, ?>> headerModifier) {
        final DittoHeadersBuilder<?, ?> builder =
                headers().toBuilder().putHeader(DittoHeaderDefinition.ORIGINATOR.getKey(), TestSetup.SUBJECT.getId());
        headerModifier.accept(builder);
        return RetrieveThing.of(TestSetup.THING_ID, builder.build());
    }

    private RetrieveThingResponse getRetrieveThingResponse(final RetrieveThing retrieveThing,
            final boolean liveChannelConditionMatched, final Consumer<DittoHeadersBuilder<?, ?>> headerModifier) {
        final var builder = retrieveThing.getDittoHeaders()
                .toBuilder()
                .putHeader(DittoHeaderDefinition.ORIGINATOR.getKey(), TestSetup.SUBJECT.getId())
                .putHeader(DittoHeaderDefinition.LIVE_CHANNEL_CONDITION_MATCHED.getKey(),
                        String.valueOf(liveChannelConditionMatched));
        headerModifier.accept(builder);
        return RetrieveThingResponse.of(retrieveThing.getEntityId(), thing, builder.build());
    }

    private JsonObject mockRestrictedPolicyAndGetThing() {
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(RESTRICTED_POLICY_ID)
                .setRevision(1L)
                .forLabel("restricted")
                .setSubject(GOOGLE, TestSetup.SUBJECT_ID)
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.empty()),
                        Permissions.newInstance(Permission.READ))
                .setRevokedPermissions(PoliciesResourceType.thingResource(SECRET_POINTER),
                        Permissions.newInstance(Permission.READ))
                .build();
        when(policyEnforcerProvider.getPolicyEnforcer(RESTRICTED_POLICY_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(PolicyEnforcer.of(policy))));
        return TestSetup.newThingWithPolicyId(RESTRICTED_POLICY_ID).toBuilder()
                .set(JsonPointer.of("/attributes/pub"), "p")
                .set(SECRET_POINTER, "A7")
                .build();
    }

    private static DittoHeaders liveChannelConditionMatchedHeaders(final RetrieveThing retrieveThing,
            final boolean liveChannelConditionMatched) {

        return retrieveThing.getDittoHeaders()
                .toBuilder()
                .putHeader(DittoHeaderDefinition.LIVE_CHANNEL_CONDITION_MATCHED.getKey(),
                        String.valueOf(liveChannelConditionMatched))
                .build();
    }

    private void assertTwinChannel(final Signal<?> signal) {
        assertThat(signal.getDittoHeaders().getChannel().orElse("twin"))
                .describedAs("Expect twin channel: " + signal)
                .isEqualTo("twin");
    }

    private void expectLiveQueryCommandOnPubSub(final RetrieveThing retrieveThing) {
        final DistributedPubSubMediator.Publish publishLiveQueryCommand =
                (DistributedPubSubMediator.Publish) pubSubMediatorProbe.fishForMessage(
                        FiniteDuration.apply(5, "s"),
                        "publish live query command",
                        PartialFunction.fromFunction(msg ->
                                msg instanceof DistributedPubSubMediator.Publish publish &&
                                        publish.topic().equals(
                                                StreamingType.LIVE_COMMANDS.getDistributedPubSubTopic())
                        )
                );

        assertThat(publishLiveQueryCommand.topic()).isEqualTo(StreamingType.LIVE_COMMANDS.getDistributedPubSubTopic());
        assertThat(publishLiveQueryCommand.msg()).isInstanceOf(ThingCommand.class);
        assertThat((CharSequence) ((ThingCommand<?>) publishLiveQueryCommand.msg()).getEntityId())
                .isEqualTo(retrieveThing.getEntityId());
        assertLiveChannel((ThingCommand<?>) publishLiveQueryCommand.msg());
    }

    private void assertLiveChannel(final Class<? extends WithDittoHeaders> clazz, final Object message) {
        assertThat(message).isInstanceOf(clazz);
        Assertions.assertThat(Signal.isChannelLive(clazz.cast(message)))
                .describedAs("Expect live channel: " + message)
                .isTrue();
    }

    private void assertLiveChannel(final WithDittoHeaders signal) {
        assertLiveChannel(WithDittoHeaders.class, signal);
    }
}
