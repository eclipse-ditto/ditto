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
package org.eclipse.ditto.policies.service.persistence.actors.strategies.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatchers;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.eclipse.ditto.internal.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.internal.utils.persistentactors.commands.DefaultContext;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultVisitor;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyConflictException;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyPreconditionFailedException;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.policies.service.common.config.DefaultPolicyConfig;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.Mockito;

import com.typesafe.config.ConfigFactory;

/**
 * Tests {@link PolicyConflictStrategy}.
 */
@SuppressWarnings({"rawtypes", "java:S3740"})
public final class PolicyConflictStrategyTest {

    @ClassRule
    public static final ActorSystemResource ACTOR_SYSTEM_RESOURCE = ActorSystemResource.newInstance();

    @Test
    public void assertImmutability() {
        assertInstancesOf(PolicyConflictStrategy.class, areImmutable());
    }

    @Test
    public void createConflictResultWithoutPrecondition() {
        final PolicyConflictStrategy underTest = new PolicyConflictStrategy(
                DefaultPolicyConfig.of(ConfigFactory.load("policy-test")));
        final PolicyId policyId = PolicyId.of("policy:id");
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(policyId).setRevision(25L).build();
        final CommandStrategy.Context<PolicyId> context = DefaultContext.getInstance(policyId,
                mockLoggingAdapter(), ACTOR_SYSTEM_RESOURCE.getActorSystem());
        final CreatePolicy command = CreatePolicy.of(policy, DittoHeaders.empty());
        final Result<PolicyEvent<?>> result = underTest.apply(context, policy, 26L, command);
        result.accept(new ExpectErrorVisitor(PolicyConflictException.class));
    }

    @Test
    public void createPreconditionFailedResultWithPrecondition() {
        final PolicyConflictStrategy underTest = new PolicyConflictStrategy(
                DefaultPolicyConfig.of(ConfigFactory.load("policy-test")));
        final PolicyId policyId = PolicyId.of("policy:id");
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(policyId).setRevision(25L).build();
        final CommandStrategy.Context<PolicyId> context = DefaultContext.getInstance(policyId,
                mockLoggingAdapter(), ACTOR_SYSTEM_RESOURCE.getActorSystem());
        final CreatePolicy command = CreatePolicy.of(policy, DittoHeaders.newBuilder()
                .ifNoneMatch(EntityTagMatchers.fromStrings("*"))
                .build());
        final Result<PolicyEvent<?>> result = underTest.apply(context, policy, 26L, command);
        result.accept(new ExpectErrorVisitor(PolicyPreconditionFailedException.class));
    }

    private static DittoDiagnosticLoggingAdapter mockLoggingAdapter() {
        final DittoDiagnosticLoggingAdapter mock = Mockito.mock(DittoDiagnosticLoggingAdapter.class);
        doAnswer(invocation -> mock).when(mock).withCorrelationId(any(WithDittoHeaders.class));
        return mock;
    }

    private static final class ExpectErrorVisitor implements ResultVisitor<PolicyEvent<?>> {

        private final Class<? extends DittoRuntimeException> clazz;

        private ExpectErrorVisitor(final Class<? extends DittoRuntimeException> clazz) {
            this.clazz = clazz;
        }

        @Override
        public void onMutation(final Command command, final PolicyEvent event, final WithDittoHeaders response,
                final boolean becomeCreated, final boolean becomeDeleted) {
            throw new AssertionError("Expect error, got mutation: " + event);
        }

        @Override
        public void onQuery(final Command command, final WithDittoHeaders response) {
            throw new AssertionError("Expect error, got query: " + response);
        }

        @Override
        public void onError(final DittoRuntimeException error, final Command errorCausingCommand) {
            assertThat(error).isInstanceOf(clazz);
        }
    }
}
