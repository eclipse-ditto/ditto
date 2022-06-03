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
package org.eclipse.ditto.policies.enforcement.placeholders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;
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
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.enforcement.placeholders.strategies.SubstitutionStrategyRegistry;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.policies.model.signals.commands.modify.ModifySubject;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttribute;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests specifics of {@link PlaceholderSubstitutionPreEnforcer}. Command-specific details are tested in concrete strategy tests
 * (see implementations of
 * {@link org.eclipse.ditto.policies.enforcement.placeholders.strategies.AbstractSubstitutionStrategyTestBase}.
 */
public class PlaceholderSubstitutionPreEnforcerTest {

    private static final String SUBJECT_ID = "nginx:ditto";
    private static final String CUSTOM_HEADER_KEY = "customHeaderKey";
    private static final String ANOTHER_SUBJECT_ID = "custom:subjectId";

    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.newBuilder()
            .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                    AuthorizationSubject.newInstance(SUBJECT_ID)))
            .putHeader(CUSTOM_HEADER_KEY, ANOTHER_SUBJECT_ID)
            .build();

    private PlaceholderSubstitutionPreEnforcer underTest;

    @Before
    public void init() {
        underTest = PlaceholderSubstitutionPreEnforcer.newInstance();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(PlaceholderSubstitutionPreEnforcer.class, areImmutable(),
                provided(HeaderBasedPlaceholderSubstitutionAlgorithm.class, SubstitutionStrategyRegistry.class)
                        .areAlsoImmutable());
    }

    @Test
    public void applyWithNonHandledCommandReturnsTheSameCommandInstance() {
        final ModifyAttribute nonHandledCommand = ModifyAttribute.of(ThingId.of("org.eclipse.ditto:my-thing"),
                JsonPointer.of("attributePointer"), JsonValue.of("attributeValue"), DITTO_HEADERS);

        final WithDittoHeaders response = applyBlocking(nonHandledCommand);

        assertThat(response).isSameAs(nonHandledCommand);
    }

    @Test
    public void applyWithHandledCommandReturnsTheReplacedCommandInstanceWhenCustomPlaceholderIsSpecified() {
        final String customPlaceholderKey = "request:customPlaceholder";
        final Map<String, Function<DittoHeaders, String>> additionalReplacementDefinitions =
                Collections.singletonMap(customPlaceholderKey,
                        dittoHeaders -> dittoHeaders.get(CUSTOM_HEADER_KEY));
        final PlaceholderSubstitutionPreEnforcer extendedPlaceholderSubstitution =
                PlaceholderSubstitutionPreEnforcer.newExtendedInstance(additionalReplacementDefinitions);
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

    private Signal<?> applyBlocking(final Signal<?> input, final PlaceholderSubstitutionPreEnforcer substitution) {
        final CompletionStage<Signal<?>> responseFuture = substitution.apply(input);
        try {
            return responseFuture.toCompletableFuture().get();
        } catch (final InterruptedException | ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }

}
