/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.eclipse.ditto.things.service.enforcement.TestSetup.THING_ID;

import java.util.concurrent.CompletionException;
import java.util.function.UnaryOperator;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.exceptions.DittoHeaderInvalidException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.headers.contenttype.ContentType;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.enforcement.config.DefaultEnforcementConfig;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.things.api.Permission;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingConditionFailedException;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeature;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Tests that {@link ThingCommandEnforcement} rejects the {@code condition} and {@code live-channel-condition} headers
 * on WoT Thing Description requests, with and without WoT TD permission filtering.
 */
public final class ThingCommandEnforcementWotTdConditionTest {

    private static final String READER = "reader";
    private static final String NOBODY = "nobody";
    private static final String TD = ContentType.APPLICATION_TD_JSON.getValue();

    private ActorSystem system;
    private PolicyEnforcer policyEnforcer;

    @Before
    public void setUp() {
        system = ActorSystem.create("ThingCommandEnforcementWotTdConditionTest", ConfigFactory.load("test"));
        final PolicyId policyId = PolicyId.of("policy:id");
        policyEnforcer = PolicyEnforcer.of(PoliciesModelFactory.newPolicyBuilder(policyId)
                .setRevision(1L)
                .forLabel("reader")
                .setSubject(SubjectIssuer.GOOGLE, READER)
                .setGrantedPermissions(PoliciesResourceType.thingResource(JsonPointer.of("/attributes/pub")),
                        Permissions.newInstance(Permission.READ))
                .build());
    }

    @After
    public void tearDown() {
        TestKit.shutdownActorSystem(system);
    }

    @Test
    public void legacyModeRejectsConditionOfSubjectWithoutAnyPermission() {
        final Throwable error = authorizationError(false,
                retrieveThingTd(NOBODY, b -> b.condition("eq(attributes/secret,\"A7\")")));

        assertThat(error).isInstanceOf(DittoHeaderInvalidException.class)
                .hasMessageContaining(DittoHeaderDefinition.CONDITION.getKey());
    }

    @Test
    public void legacyModeRejectsLiveChannelConditionOfSubjectWithoutAnyPermission() {
        final Throwable error = authorizationError(false,
                retrieveThingTd(NOBODY, b -> b.liveChannelCondition("eq(attributes/secret,\"A7\")")));

        assertThat(error).isInstanceOf(DittoHeaderInvalidException.class)
                .hasMessageContaining(DittoHeaderDefinition.LIVE_CHANNEL_CONDITION.getKey());
    }

    @Test
    public void legacyModeRejectsConditionOnFeatureTd() {
        final RetrieveFeature retrieveFeatureTd = RetrieveFeature.of(THING_ID, "f",
                headers(NOBODY, b -> b.accept(TD).condition("exists(features/f)")));

        assertThat(authorizationError(false, retrieveFeatureTd)).isInstanceOf(DittoHeaderInvalidException.class);
    }

    @Test
    public void legacyModeForwardsTdRequestWithoutConditionsOfSubjectWithoutAnyPermission() {
        final RetrieveThing retrieveThingTd = retrieveThingTd(NOBODY, UnaryOperator.identity());

        final ThingCommand<?> authorized = enforcement(false).authorizeSignal(retrieveThingTd, policyEnforcer)
                .toCompletableFuture()
                .join();

        assertThat(authorized).isInstanceOf(RetrieveThing.class);
    }

    @Test
    public void filteringModeRejectsConditionOfSubjectWithoutAnyPermission() {
        final Throwable error = authorizationError(true,
                retrieveThingTd(NOBODY, b -> b.condition("exists(thingId)")));

        // the READ permission on the condition is checked before the authorization of the command
        assertThat(error).isInstanceOf(ThingConditionFailedException.class);
    }

    @Test
    public void filteringModeRejectsConditionOfPartialReader() {
        final Throwable error = authorizationError(true,
                retrieveThingTd(READER, b -> b.condition("exists(attributes/pub)")));

        assertThat(error).isInstanceOf(DittoHeaderInvalidException.class)
                .hasMessageContaining(DittoHeaderDefinition.CONDITION.getKey());
    }

    @Test
    public void filteringModeRejectsLiveChannelConditionOfPartialReader() {
        final Throwable error = authorizationError(true,
                retrieveThingTd(READER, b -> b.liveChannelCondition("exists(attributes/pub)")));

        assertThat(error).isInstanceOf(DittoHeaderInvalidException.class)
                .hasMessageContaining(DittoHeaderDefinition.LIVE_CHANNEL_CONDITION.getKey());
    }

    private ThingCommandEnforcement enforcement(final boolean wotTdPermissionFilteringEnabled) {
        return new ThingCommandEnforcement(system, ActorRef.noSender(),
                DefaultEnforcementConfig.of(DefaultScopedConfig.dittoScoped(system.settings().config())),
                false, wotTdPermissionFilteringEnabled);
    }

    private Throwable authorizationError(final boolean wotTdPermissionFilteringEnabled,
            final ThingCommand<?> command) {

        final Throwable error = catchThrowable(() -> enforcement(wotTdPermissionFilteringEnabled)
                .authorizeSignal(command, policyEnforcer)
                .toCompletableFuture()
                .join());
        return error instanceof CompletionException ? error.getCause() : error;
    }

    private static RetrieveThing retrieveThingTd(final String subjectId,
            final UnaryOperator<DittoHeadersBuilder<?, ?>> headersModifier) {

        return RetrieveThing.of(THING_ID, headers(subjectId, b -> headersModifier.apply(b.accept(TD))));
    }

    private static DittoHeaders headers(final String subjectId,
            final UnaryOperator<DittoHeadersBuilder<?, ?>> headersModifier) {

        return headersModifier.apply(DittoHeaders.newBuilder()
                        .authorizationContext(AuthorizationContext.newInstance(
                                DittoAuthorizationContextType.UNSPECIFIED,
                                AuthorizationSubject.newInstance(SubjectIssuer.GOOGLE + ":" + subjectId)))
                        .randomCorrelationId())
                .build();
    }

}
