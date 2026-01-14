/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.persistence.actors.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.CompletableFutureAssert.assertThatCompletionStage;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonArrayBuilder;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcerProvider;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.events.AttributeModified;
import org.eclipse.ditto.things.service.common.config.DefaultPreDefinedExtraFieldsConfig;
import org.eclipse.ditto.things.service.common.config.PreDefinedExtraFieldsConfig;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Unit tests for {@link ThingEventEnricher}.
 */
public final class ThingEventEnricherTest {

    private static final ThingId KNOWN_THING_ID = ThingId.of("org.eclipse.ditto.some:thing");
    private static final String KNOWN_DEFINITION = "some:known:definition";
    private static final Thing KNOWN_THING = Thing.newBuilder().setId(KNOWN_THING_ID)
            .setDefinition(ThingsModelFactory.newDefinition(KNOWN_DEFINITION))
            .setAttribute(JsonPointer.of("public1"), JsonValue.of(true))
            .setAttribute(JsonPointer.of("public2"), JsonValue.of(true))
            .setAttribute(JsonPointer.of("private"), JsonValue.of(false))
            .setAttribute(JsonPointer.of("folder/public"), JsonValue.of(true))
            .setAttribute(JsonPointer.of("folder/private"), JsonValue.of(false))
            .build();

    private static final PolicyId KNOWN_POLICY_ID = PolicyId.of("known:policy");
    private static final String KNOWN_LABEL_FULL = "full-access";
    private static final String KNOWN_LABEL_RESTRICTED = "restricted-access";
    private static final String KNOWN_LABEL_ANOTHER = "another-access";

    private static final String KNOWN_ISSUER_FULL_SUBJECT = "foo-issuer:full-subject";
    private static final String KNOWN_ISSUER_RESTRICTED_SUBJECT = "foo-issuer:restricted-subject";
    private static final String KNOWN_ISSUER_ANOTHER_SUBJECT = "foo-issuer:another-subject";

    private static final Policy KNOWN_POLICY = Policy.newBuilder(KNOWN_POLICY_ID)
            .setSubjectFor(KNOWN_LABEL_FULL, Subject.newInstance(
                    SubjectId.newInstance(KNOWN_ISSUER_FULL_SUBJECT), SubjectType.GENERATED)
            )
            .setGrantedPermissionsFor(KNOWN_LABEL_FULL, ResourceKey.newInstance("thing", "/"), "READ", "WRITE")
            .setGrantedPermissionsFor(KNOWN_LABEL_FULL, ResourceKey.newInstance("policy", "/"), "READ", "WRITE")
            .setSubjectFor(KNOWN_LABEL_RESTRICTED, Subject.newInstance(
                    SubjectId.newInstance(KNOWN_ISSUER_RESTRICTED_SUBJECT), SubjectType.GENERATED)
            )
            .setGrantedPermissionsFor(KNOWN_LABEL_RESTRICTED, ResourceKey.newInstance("thing", "/attributes/public1"), "READ")
            .setGrantedPermissionsFor(KNOWN_LABEL_RESTRICTED, ResourceKey.newInstance("thing", "/attributes/public2"), "READ")
            .setGrantedPermissionsFor(KNOWN_LABEL_RESTRICTED, ResourceKey.newInstance("thing", "/attributes/folder/public"),
                    "READ")
            .setSubjectFor(KNOWN_LABEL_ANOTHER, Subject.newInstance(
                    SubjectId.newInstance(KNOWN_ISSUER_ANOTHER_SUBJECT), SubjectType.GENERATED)
            )
            .setGrantedPermissionsFor(KNOWN_LABEL_ANOTHER, ResourceKey.newInstance("thing", "/attributes/folder/public"),
                    "READ")
            .build();

    private static final Policy FULL_ACCESS_POLICY = Policy.newBuilder(KNOWN_POLICY_ID)
            .setSubjectFor(KNOWN_LABEL_FULL, Subject.newInstance(
                    SubjectId.newInstance(KNOWN_ISSUER_FULL_SUBJECT), SubjectType.GENERATED)
            )
            .setGrantedPermissionsFor(KNOWN_LABEL_FULL, ResourceKey.newInstance("thing", "/"), "READ", "WRITE")
            .build();

    private PolicyEnforcerProvider policyEnforcerProvider;

    @Before
    public void setupTest() {
        policyEnforcerProvider = mock(PolicyEnforcerProvider.class);
        when(policyEnforcerProvider.getPolicyEnforcer(KNOWN_POLICY_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(PolicyEnforcer.of(KNOWN_POLICY))));
    }

    @Test
    public void ensureDefinitionIsEnrichedAsPreDefinedFromConfiguration() {
        // GIVEN: the configuration to enrich all things with their definition
        final var sut = new ThingEventEnricher(policyEnforcerProvider, true);
        final var configs = getPreDefinedExtraFieldsConfigs(
                "namespaces = []\n" +
                "extra-fields = [\"definition\"]"
        );

        // WHEN: enriched headers are getting calculated
        final CompletionStage<DittoHeaders> resultHeadersStage = calculateEnrichedSignalHeaders(sut, configs);

        // THEN: the expected pre-defined fields are present in the headers
        assertExpectations(resultHeadersStage,
                predefinedExtraFields -> predefinedExtraFields.add("/definition"),
                preDefinedExtraFieldsReadGrantObject -> preDefinedExtraFieldsReadGrantObject
                        .set(JsonKey.of("/definition"), JsonArray.newBuilder()
                                .add(0)
                                .build()
                        ),
                preDefinedFieldsObject -> preDefinedFieldsObject.set("definition", KNOWN_DEFINITION)
        );
    }

    @Test
    public void ensureDefinitionAndAdditionalNamespaceSpecificIsEnrichedAsPreDefinedFromConfiguration() {
        final var sut = new ThingEventEnricher(policyEnforcerProvider, true);
        final var configs = getPreDefinedExtraFieldsConfigs(
                "namespaces = [\"org.eclipse.ditto.some\"]\n" +
                "extra-fields = [\"definition\", \"attributes/public1\", \"attributes/private\"]"
        );

        // WHEN: enriched headers are getting calculated
        final CompletionStage<DittoHeaders> resultHeadersStage = calculateEnrichedSignalHeaders(sut, configs);

        // THEN: the expected pre-defined fields are present in the headers
        assertExpectations(resultHeadersStage,
                predefinedExtraFields -> predefinedExtraFields
                        .add("/definition")
                        .add("/attributes/public1")
                        .add("/attributes/private"),
                preDefinedExtraFieldsReadGrantObject -> preDefinedExtraFieldsReadGrantObject
                        .set(JsonKey.of("/attributes/public1"), JsonArray.newBuilder()
                                .add(0)
                                .add(1)
                                .build()
                        )
                        .set(JsonKey.of("/definition"), JsonArray.newBuilder()
                                .add(0)
                                .build()
                        )
                        .set(JsonKey.of("/attributes/private"), JsonArray.newBuilder()
                                .add(0)
                                .build()
                        ),
                preDefinedFieldsObject -> preDefinedFieldsObject
                        .set("definition", KNOWN_DEFINITION)
                        .set(JsonPointer.of("attributes/public1"), true)
                        .set(JsonPointer.of("attributes/private"), false)
        );
    }

    @Test
    public void addsPartialAccessHeaderWhenPartialSubjectsExist() {
        final ThingEventEnricher sut = new ThingEventEnricher(policyEnforcerProvider, true);

        final CompletionStage<JsonObject> partialHeaderStage = calculateEnrichedSignalHeaders(sut, List.of())
                .thenApply(headers -> {
                    final String headerValue = headers.get(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey());
                    if (headerValue == null) {
                        return JsonFactory.newObject();
                    }
                    return JsonFactory.readFrom(headerValue).asObject();
                });

        assertThatCompletionStage(partialHeaderStage).isNotCompletedExceptionally();
        final JsonObject header = partialHeaderStage.toCompletableFuture().join();
        assertThat(header).isNotEmpty();
        final var subjects = header.getValue("subjects")
                .map(JsonValue::asArray)
                .orElseThrow(() -> new AssertionError("Missing subjects array"));
        assertThat(subjects.stream()
                .filter(JsonValue::isString)
                .map(JsonValue::asString))
                .contains(KNOWN_ISSUER_RESTRICTED_SUBJECT);
        final var paths = header.getValue("paths")
                .map(JsonValue::asObject)
                .orElseThrow(() -> new AssertionError("Missing paths object"));
        final int restrictedSubjectIndex = subjects.stream()
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .collect(java.util.stream.Collectors.toList())
                .indexOf(KNOWN_ISSUER_RESTRICTED_SUBJECT);
        assertThat(restrictedSubjectIndex).isGreaterThanOrEqualTo(0);
        final boolean hasPathForRestrictedSubject = paths.stream()
                .anyMatch(field -> {
                    final JsonValue value = field.getValue();
                    if (value.isArray()) {
                        return value.asArray().stream()
                                .anyMatch(idx -> idx.asInt() == restrictedSubjectIndex);
                    }
                    return false;
                });
        assertThat(hasPathForRestrictedSubject).isTrue();
    }

    @Test
    public void skipsPartialAccessHeaderWhenFeatureDisabled() {
        final ThingEventEnricher sut = new ThingEventEnricher(policyEnforcerProvider, false);

        final CompletionStage<DittoHeaders> headersStage = calculateEnrichedSignalHeaders(sut, List.of());

        assertThatCompletionStage(headersStage.thenApply(headers ->
                headers.get(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey())))
                .isNotCompletedExceptionally()
                .isCompletedWithValue(null);
    }

    @Test
    public void skipsPartialAccessHeaderWhenNoPartialSubjectsExist() {
        final PolicyEnforcerProvider fullAccessProvider = mock(PolicyEnforcerProvider.class);
        when(fullAccessProvider.getPolicyEnforcer(KNOWN_POLICY_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(PolicyEnforcer.of(FULL_ACCESS_POLICY))));

        final ThingEventEnricher sut = new ThingEventEnricher(fullAccessProvider, true);

        final CompletionStage<DittoHeaders> headersStage = calculateEnrichedSignalHeaders(sut, List.of());

        assertThatCompletionStage(headersStage.thenApply(headers ->
                headers.get(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey())))
                .isNotCompletedExceptionally()
                .isCompletedWithValue(null);
    }

    @Test
    public void ensureConditionBasedEnrichmentAsPreDefinedFromConfiguration() {
        // GIVEN: the configuration to enrich all things with their definition and some with an attribute public1
        final var sut = new ThingEventEnricher(policyEnforcerProvider, true);
        final var configs = getPreDefinedExtraFieldsConfigs(
                "namespaces = [\"org.eclipse.ditto.some\"]\n" +
                "condition = \"exists(attributes/public1)\"\n" +
                "extra-fields = [\"attributes/public1\", \"attributes/folder\"]"
        );

        // WHEN: enriched headers are getting calculated
        final CompletionStage<DittoHeaders> resultHeadersStage = calculateEnrichedSignalHeaders(sut, configs);

        // THEN: the expected pre-defined fields are present in the headers
        assertExpectations(resultHeadersStage,
                predefinedExtraFields -> predefinedExtraFields
                        .add("/attributes/public1")
                        .add("/attributes/folder"),
                preDefinedExtraFieldsReadGrantObject -> preDefinedExtraFieldsReadGrantObject
                        .set(JsonKey.of("/attributes/public1"), JsonArray.newBuilder()
                                .add(0)
                                .add(1)
                                .build()
                        )
                        .set(JsonKey.of("/attributes/folder"), JsonArray.newBuilder()
                                .add(0)
                                .build()
                        )
                        .set(JsonKey.of("/attributes/folder/public"), JsonArray.newBuilder()
                                .add(2)
                                .add(1)
                                .build()
                        ),
                preDefinedFieldsObject -> preDefinedFieldsObject
                        .set(JsonPointer.of("attributes/public1"), true)
                        .set(JsonPointer.of("attributes/folder/public"), true)
                        .set(JsonPointer.of("attributes/folder/private"), false)
        );
    }

    private List<PreDefinedExtraFieldsConfig> getPreDefinedExtraFieldsConfigs(final String... configurations) {
        return Arrays.stream(configurations)
                .map(configString ->
                        DefaultPreDefinedExtraFieldsConfig.of(ConfigFactory.parseString(configString))
                )
                .map(PreDefinedExtraFieldsConfig.class::cast)
                .toList();
    }

    private static CompletionStage<DittoHeaders> calculateEnrichedSignalHeaders(
            final ThingEventEnricher sut,
            final List<PreDefinedExtraFieldsConfig> preDefinedExtraFieldsConfigs
    ) {
        final AttributeModified event = AttributeModified.of(
                KNOWN_THING_ID, JsonPointer.of("something"), JsonValue.of(true), 4L,
                Instant.now(), DittoHeaders.empty(), null);

        final CompletionStage<AttributeModified> resultStage =
                sut.enrichWithPredefinedExtraFields(
                        preDefinedExtraFieldsConfigs, KNOWN_THING_ID, KNOWN_THING, KNOWN_POLICY_ID, event);
        return resultStage.thenApply(AttributeModified::getDittoHeaders);
    }

    private static void assertExpectations(final CompletionStage<DittoHeaders> resultHeadersStage,
            final Function<JsonArrayBuilder, JsonArrayBuilder> expectedPreDefinedExtraFields,
            final Function<JsonObjectBuilder, JsonObjectBuilder> expectedPreDefinedExtraFieldsReadGrantObject,
            final Function<JsonObjectBuilder, JsonObjectBuilder> expectedPreDefinedFieldsObject
    ) {
        assertThatCompletionStage(resultHeadersStage.thenApply(headers ->
                headers.get(DittoHeaderDefinition.PRE_DEFINED_EXTRA_FIELDS.getKey())
        )).isNotCompletedExceptionally().isCompletedWithValue(
                expectedPreDefinedExtraFields.apply(JsonArray.newBuilder()).build().toString()
        );
        assertThatCompletionStage(resultHeadersStage.thenApply(headers -> {
            final String headerValue = headers.get(DittoHeaderDefinition.PRE_DEFINED_EXTRA_FIELDS_READ_GRANT_OBJECT.getKey());
            if (headerValue == null) {
                return JsonFactory.newObject();
            }
            return JsonFactory.readFrom(headerValue).asObject();
        })).isNotCompletedExceptionally().isCompletedWithValue(
                expectedPreDefinedExtraFieldsReadGrantObject.apply(JsonObject.newBuilder()).build()
        );
        assertThatCompletionStage(resultHeadersStage.thenApply(headers ->
                headers.get(DittoHeaderDefinition.PRE_DEFINED_EXTRA_FIELDS_OBJECT.getKey())
        )).isNotCompletedExceptionally().isCompletedWithValue(
                expectedPreDefinedFieldsObject.apply(JsonObject.newBuilder()).build().toString()
        );
    }
}
