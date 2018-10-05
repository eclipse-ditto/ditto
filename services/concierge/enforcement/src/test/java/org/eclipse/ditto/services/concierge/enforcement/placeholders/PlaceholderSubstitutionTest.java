/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.concierge.enforcement.placeholders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectType;
import org.eclipse.ditto.services.concierge.enforcement.placeholders.strategies.SubstitutionStrategyRegistry;
import org.eclipse.ditto.signals.commands.policies.modify.ModifySubject;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttribute;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests specifics of {@link PlaceholderSubstitution}. Command-specific details are tested in concrete strategy tests
 * (see implementations of
 * {@link org.eclipse.ditto.services.concierge.enforcement.placeholders.strategies.AbstractSubstitutionStrategyTestBase}.
 */
public class PlaceholderSubstitutionTest {

    private static final String SUBJECT_ID = "nginx:ditto";
    private static final String CUSTOM_HEADER_KEY = "customHeaderKey";
    private static final String ANOTHER_SUBJECT_ID = "custom:subjectId";

    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.newBuilder()
            .authorizationContext(AuthorizationContext.newInstance(AuthorizationSubject.newInstance(SUBJECT_ID)))
            .putHeader(CUSTOM_HEADER_KEY, ANOTHER_SUBJECT_ID)
            .build();

    private PlaceholderSubstitution underTest;

    @Before
    public void init() {
        underTest = PlaceholderSubstitution.newInstance();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(PlaceholderSubstitution.class, areImmutable(),
                provided(HeaderBasedPlaceholderSubstitutionAlgorithm.class, SubstitutionStrategyRegistry.class)
                        .areAlsoImmutable());
    }

    @Test
    public void applyWithNonHandledCommandReturnsTheSameCommandInstance() {
        final ModifyAttribute nonHandledCommand = ModifyAttribute.of("org.eclipse.ditto:my-thing",
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
        final PlaceholderSubstitution extendedPlaceholderSubstitution =
                PlaceholderSubstitution.newExtendedInstance(additionalReplacementDefinitions);
        final ModifySubject commandWithoutPlaceholders = ModifySubject.of("org.eclipse.ditto:my-policy",
                Label.of("my-label"), Subject.newInstance("{{ " + customPlaceholderKey + " }}",
                        SubjectType.GENERATED), DITTO_HEADERS);

        final WithDittoHeaders response =
                applyBlocking(commandWithoutPlaceholders, extendedPlaceholderSubstitution);

        final ModifySubject expectedCommandWithPlaceholders = ModifySubject.of(commandWithoutPlaceholders.getId(),
                commandWithoutPlaceholders.getLabel(), Subject.newInstance(ANOTHER_SUBJECT_ID, SubjectType.GENERATED),
                DITTO_HEADERS);
        assertThat(response).isEqualTo(expectedCommandWithPlaceholders);
    }

    @Test
    public void applyWithHandledCommandReturnsTheReplacedCommandInstanceWhenLegacyPlaceholderIsSpecified() {
        final ModifySubject commandWithoutPlaceholders = ModifySubject.of("org.eclipse.ditto:my-policy",
                Label.of("my-label"), Subject.newInstance("${request.subjectId}", SubjectType.GENERATED),
                DITTO_HEADERS);

        final WithDittoHeaders response = applyBlocking(commandWithoutPlaceholders);

        final ModifySubject expectedCommandWithPlaceholders = ModifySubject.of(commandWithoutPlaceholders.getId(),
                commandWithoutPlaceholders.getLabel(), Subject.newInstance(SUBJECT_ID, SubjectType.GENERATED),
                DITTO_HEADERS);
        assertThat(response).isEqualTo(expectedCommandWithPlaceholders);
    }

    private WithDittoHeaders applyBlocking(final WithDittoHeaders input) {
        return applyBlocking(input, underTest);
    }

    private WithDittoHeaders applyBlocking(final WithDittoHeaders input, final PlaceholderSubstitution substitution) {
        final CompletionStage<WithDittoHeaders> responseFuture = substitution.apply(input);
        try {
            return responseFuture.toCompletableFuture().get();
        } catch (final InterruptedException | ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }

}
