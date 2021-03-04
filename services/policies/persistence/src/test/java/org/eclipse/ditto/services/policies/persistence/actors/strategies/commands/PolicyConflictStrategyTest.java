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
package org.eclipse.ditto.services.policies.persistence.actors.strategies.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTagMatchers;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.services.policies.common.config.DefaultPolicyConfig;
import org.eclipse.ditto.services.utils.akka.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.services.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.services.utils.persistentactors.commands.DefaultContext;
import org.eclipse.ditto.services.utils.persistentactors.results.Result;
import org.eclipse.ditto.services.utils.persistentactors.results.ResultVisitor;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyConflictException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyPreconditionFailedException;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicy;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;
import org.junit.Test;
import org.mockito.Mockito;

import com.typesafe.config.ConfigFactory;

/**
 * Tests {@link org.eclipse.ditto.services.policies.persistence.actors.strategies.commands.PolicyConflictStrategy}.
 */
@SuppressWarnings("rawtypes")
public final class PolicyConflictStrategyTest {

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
        final CommandStrategy.Context<PolicyId> context = DefaultContext.getInstance(policyId, mockLoggingAdapter());
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
        final CommandStrategy.Context<PolicyId> context = DefaultContext.getInstance(policyId, mockLoggingAdapter());
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
