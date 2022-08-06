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
package org.eclipse.ditto.policies.service.signaltransformation.placeholdersubstitution;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Resource;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifyResource;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;

/**
 * Tests specifics of {@link PoliciesPlaceholderSubstitution}.
 * Command-specific details are tested in concrete strategy tests
 * (see implementations of
 * {@link AbstractPolicySubstitutionStrategyTestBase}.
 */
public final class PoliciesPlaceholderSubstitutionTest {

    private static final String SUBJECT_ID = "nginx:ditto";
    private static final String CUSTOM_HEADER_KEY = "customHeaderKey";
    private static final String ANOTHER_SUBJECT_ID = "custom:subjectId";

    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.newBuilder()
            .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                    AuthorizationSubject.newInstance(SUBJECT_ID)))
            .putHeader(CUSTOM_HEADER_KEY, ANOTHER_SUBJECT_ID)
            .build();

    private PoliciesPlaceholderSubstitution underTest;

    @Before
    public void init() {
        underTest =
                new PoliciesPlaceholderSubstitution(Mockito.mock(ActorSystem.class), ConfigFactory.empty());
    }

    @Test
    public void applyWithNonHandledCommandReturnsTheSameCommandInstance() {
        final ModifyResource nonHandledCommand = ModifyResource.of(PolicyId.of("org.eclipse.ditto:my-thing"),
                Label.of("foo"), Resource.newInstance(ResourceKey.newInstance("policy:/"),
                        EffectedPermissions.newInstance(null, null)), DITTO_HEADERS);

        final WithDittoHeaders response = applyBlocking(nonHandledCommand);

        assertThat(response).isSameAs(nonHandledCommand);
    }

    @Test
    public void applyWithHandledCommandReturnsTheReplacedCommandInstanceWhenCustomPlaceholderIsSpecified() {
        final String customPlaceholderKey = "request:customPlaceholder";
        final Map<String, Function<DittoHeaders, String>> additionalReplacementDefinitions =
                Collections.singletonMap(customPlaceholderKey,
                        dittoHeaders -> dittoHeaders.get(CUSTOM_HEADER_KEY));
        final PoliciesPlaceholderSubstitution extendedPlaceholderSubstitution =
                new PoliciesPlaceholderSubstitution(Mockito.mock(ActorSystem.class), ConfigFactory.empty()) {
                    @Override
                    protected Map<String, Function<DittoHeaders, String>> createReplacementDefinitions() {
                        final Map<String, Function<DittoHeaders, String>> definitions =
                                super.createReplacementDefinitions();
                        final Map<String, Function<DittoHeaders, String>> mergedDefinitions =
                                new HashMap<>(definitions);
                        mergedDefinitions.putAll(additionalReplacementDefinitions);
                        return mergedDefinitions;
                    }
                };
        final ModifySubject commandWithoutPlaceholders = ModifySubject.of(PolicyId.of("org.eclipse.ditto:my-policy"),
                Label.of("my-label"), Subject.newInstance("{{ " + customPlaceholderKey + " }}",
                        SubjectType.GENERATED), DITTO_HEADERS);

        final WithDittoHeaders response =
                applyBlocking(commandWithoutPlaceholders, extendedPlaceholderSubstitution);

        final ModifySubject expectedCommandWithPlaceholders = ModifySubject.of(commandWithoutPlaceholders.getEntityId(),
                commandWithoutPlaceholders.getLabel(), Subject.newInstance(ANOTHER_SUBJECT_ID, SubjectType.GENERATED),
                DITTO_HEADERS);
        assertThat(response).isEqualTo(expectedCommandWithPlaceholders);
    }

    @Test
    public void applyWithHandledCommandReturnsTheReplacedCommandInstanceWhenLegacyPlaceholderIsSpecified() {
        final ModifySubject commandWithoutPlaceholders = ModifySubject.of(PolicyId.of("org.eclipse.ditto:my-policy"),
                Label.of("my-label"), Subject.newInstance("${request.subjectId}", SubjectType.GENERATED),
                DITTO_HEADERS);

        final WithDittoHeaders response = applyBlocking(commandWithoutPlaceholders);

        final ModifySubject expectedCommandWithPlaceholders = ModifySubject.of(commandWithoutPlaceholders.getEntityId(),
                commandWithoutPlaceholders.getLabel(), Subject.newInstance(SUBJECT_ID, SubjectType.GENERATED),
                DITTO_HEADERS);
        assertThat(response).isEqualTo(expectedCommandWithPlaceholders);
    }

    private Signal<?> applyBlocking(final Signal<?> input) {
        return applyBlocking(input, underTest);
    }

    private Signal<?> applyBlocking(final Signal<?> input,
            final PoliciesPlaceholderSubstitution substitution) {
        final CompletionStage<Signal<?>> responseFuture = substitution.apply(input);
        try {
            return responseFuture.toCompletableFuture().get();
        } catch (final InterruptedException | ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }

}
