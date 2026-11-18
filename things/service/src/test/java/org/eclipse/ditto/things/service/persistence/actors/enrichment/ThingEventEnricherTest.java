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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
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
import org.eclipse.ditto.things.model.signals.events.AttributeDeleted;
import org.eclipse.ditto.things.model.signals.events.AttributeModified;
import org.eclipse.ditto.things.service.common.config.DefaultPreDefinedExtraFieldsConfig;
import org.eclipse.ditto.things.service.common.config.PreDefinedExtraFieldsConfig;
import org.eclipse.ditto.messages.model.Message;
import org.eclipse.ditto.messages.model.MessageDirection;
import org.eclipse.ditto.messages.model.MessageHeaders;
import org.eclipse.ditto.messages.model.signals.commands.SendThingMessage;
import org.eclipse.ditto.things.model.signals.events.ThingDefinitionMigrated;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.model.signals.events.ThingMerged;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

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

    // Same as KNOWN_THING but with the "public2" attribute removed → a structural change.
    private static final Thing THING_WITHOUT_PUBLIC2 = Thing.newBuilder().setId(KNOWN_THING_ID)
            .setDefinition(ThingsModelFactory.newDefinition(KNOWN_DEFINITION))
            .setAttribute(JsonPointer.of("public1"), JsonValue.of(true))
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

    private static final String KNOWN_ISSUER_GROUP_SUBJECT = "foo-issuer:group-subject";

    // the full-access subject is granted READ on everything, the group subject has READ revoked on a sub-path
    private static final Policy REVOKING_POLICY = FULL_ACCESS_POLICY.toBuilder()
            .setSubjectFor("group", Subject.newInstance(
                    SubjectId.newInstance(KNOWN_ISSUER_GROUP_SUBJECT), SubjectType.GENERATED))
            .setRevokedPermissionsFor("group", ResourceKey.newInstance("thing", "/attributes/folder"), "READ")
            .build();

    private static final String EXPECTED_REVOKED_ACCESS_PATHS = JsonObject.newBuilder()
            .set("subjects", JsonArray.of(JsonValue.of(KNOWN_ISSUER_GROUP_SUBJECT)))
            .set("paths", JsonObject.newBuilder()
                    .set(JsonKey.of("attributes/folder"), JsonArray.of(JsonValue.of(0)))
                    .build())
            .build()
            .toString();

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
        final var sut = new ThingEventEnricher(policyEnforcerProvider, true, true);
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
                                .add(KNOWN_ISSUER_FULL_SUBJECT)
                                .build()
                        ),
                preDefinedFieldsObject -> preDefinedFieldsObject.set("definition", KNOWN_DEFINITION)
        );
    }

    @Test
    public void ensureDefinitionAndAdditionalNamespaceSpecificIsEnrichedAsPreDefinedFromConfiguration() {
        final var sut = new ThingEventEnricher(policyEnforcerProvider, true, true);
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
                        .set(JsonKey.of("/definition"), JsonArray.newBuilder()
                                .add(KNOWN_ISSUER_FULL_SUBJECT)
                                .build()
                        )
                        .set(JsonKey.of("/attributes/public1"), JsonArray.newBuilder()
                                .add(KNOWN_ISSUER_FULL_SUBJECT)
                                .add(KNOWN_ISSUER_RESTRICTED_SUBJECT)
                                .build()
                        )
                        .set(JsonKey.of("/attributes/private"), JsonArray.newBuilder()
                                .add(KNOWN_ISSUER_FULL_SUBJECT)
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
        final ThingEventEnricher sut = new ThingEventEnricher(policyEnforcerProvider, true, true);

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
        final ThingEventEnricher sut = new ThingEventEnricher(policyEnforcerProvider, false, true);

        final CompletionStage<DittoHeaders> headersStage = calculateEnrichedSignalHeaders(sut, List.of());

        assertThatCompletionStage(headersStage.thenApply(headers ->
                headers.get(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey())))
                .isNotCompletedExceptionally()
                .isCompletedWithValue(null);
    }

    @Test
    public void addsRevokedAccessPathsHeaderAlsoWhenPartialAccessEventsAreDisabled() {
        final PolicyEnforcerProvider provider = mock(PolicyEnforcerProvider.class);
        when(provider.getPolicyEnforcer(KNOWN_POLICY_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(PolicyEnforcer.of(REVOKING_POLICY))));
        final ThingEventEnricher sut = new ThingEventEnricher(provider, false, true);

        final CompletionStage<DittoHeaders> headersStage = calculateEnrichedSignalHeaders(sut, List.of());

        assertThatCompletionStage(headersStage.thenApply(headers ->
                headers.get(DittoHeaderDefinition.REVOKED_ACCESS_PATHS.getKey())))
                .isNotCompletedExceptionally()
                .isCompletedWithValue(EXPECTED_REVOKED_ACCESS_PATHS);
    }

    @Test
    public void skipsRevokedAccessPathsHeaderWithoutReadRevokes() {
        final ThingEventEnricher sut = new ThingEventEnricher(policyEnforcerProvider, true, true);

        final CompletionStage<DittoHeaders> headersStage = calculateEnrichedSignalHeaders(sut, List.of());

        assertThatCompletionStage(headersStage.thenApply(headers ->
                headers.get(DittoHeaderDefinition.REVOKED_ACCESS_PATHS.getKey())))
                .isNotCompletedExceptionally()
                .isCompletedWithValue(null);
    }

    @Test
    public void recalculatesRevokedAccessPathsWhenPolicyEnforcerChanges() {
        final PolicyEnforcerProvider provider = mock(PolicyEnforcerProvider.class);
        when(provider.getPolicyEnforcer(KNOWN_POLICY_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(PolicyEnforcer.of(KNOWN_POLICY))))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(PolicyEnforcer.of(REVOKING_POLICY))));
        final ThingEventEnricher sut = new ThingEventEnricher(provider, false, true);

        final String first = calculateEnrichedSignalHeaders(sut, List.of()).toCompletableFuture().join()
                .get(DittoHeaderDefinition.REVOKED_ACCESS_PATHS.getKey());
        final String second = calculateEnrichedSignalHeaders(sut, List.of()).toCompletableFuture().join()
                .get(DittoHeaderDefinition.REVOKED_ACCESS_PATHS.getKey());

        assertThat(first).isNull();
        assertThat(second).isEqualTo(EXPECTED_REVOKED_ACCESS_PATHS);
    }

    @Test
    public void ignoresGrantsOfPolicyEntriesForOtherNamespacesWhenCalculatingRevokedAccessPaths() {
        // without the namespace filter, the grant on folder/public would lift the revoke for the group subject
        final Policy policy = REVOKING_POLICY.toBuilder()
                .setSubjectFor("group-other-namespace", Subject.newInstance(
                        SubjectId.newInstance(KNOWN_ISSUER_GROUP_SUBJECT), SubjectType.GENERATED))
                .setGrantedPermissionsFor("group-other-namespace",
                        ResourceKey.newInstance("thing", "/attributes/folder/public"), "READ")
                .setNamespacesFor("group-other-namespace", List.of("some.other.namespace"))
                .build();
        final PolicyEnforcerProvider provider = mock(PolicyEnforcerProvider.class);
        when(provider.getPolicyEnforcer(KNOWN_POLICY_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(PolicyEnforcer.of(policy))));
        final ThingEventEnricher sut = new ThingEventEnricher(provider, false, true);

        final CompletionStage<DittoHeaders> headersStage = calculateEnrichedSignalHeaders(sut, List.of());

        assertThatCompletionStage(headersStage.thenApply(headers ->
                headers.get(DittoHeaderDefinition.REVOKED_ACCESS_PATHS.getKey())))
                .isNotCompletedExceptionally()
                .isCompletedWithValue(EXPECTED_REVOKED_ACCESS_PATHS);
    }

    @Test
    public void recalculatesRevokedAccessPathsWhenThingStructureChanges() {
        recalculatesRevokedAccessPathsWhenThingStructureChanges(true);
        recalculatesRevokedAccessPathsWhenThingStructureChanges(false);
    }

    private void recalculatesRevokedAccessPathsWhenThingStructureChanges(final boolean cacheEnabled) {
        final PolicyEnforcer policyEnforcer = PolicyEnforcer.of(REVOKING_POLICY);
        final PolicyEnforcerProvider provider = mock(PolicyEnforcerProvider.class);
        when(provider.getPolicyEnforcer(KNOWN_POLICY_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(policyEnforcer)));
        final ThingEventEnricher sut = new ThingEventEnricher(provider, false, cacheEnabled);
        final AttributeModified event = AttributeModified.of(
                KNOWN_THING_ID, JsonPointer.of("something"), JsonValue.of(true), 4L,
                Instant.now(), DittoHeaders.empty(), null);
        final Thing thingWithoutFolder = KNOWN_THING.removeAttribute(JsonPointer.of("folder"));

        final String first = sut.enrichWithPredefinedExtraFields(List.of(), KNOWN_THING_ID, null, KNOWN_THING,
                        KNOWN_POLICY_ID, event)
                .toCompletableFuture().join()
                .getDittoHeaders().get(DittoHeaderDefinition.REVOKED_ACCESS_PATHS.getKey());
        final String second = sut.enrichWithPredefinedExtraFields(List.of(), KNOWN_THING_ID, null, thingWithoutFolder,
                        KNOWN_POLICY_ID, event)
                .toCompletableFuture().join()
                .getDittoHeaders().get(DittoHeaderDefinition.REVOKED_ACCESS_PATHS.getKey());

        assertThat(first).isEqualTo(EXPECTED_REVOKED_ACCESS_PATHS);
        assertThat(second).isNull();
    }

    @Test
    public void coversFieldsDeletedByMerge() {
        final Thing thingWithoutFolder = KNOWN_THING.removeAttribute(JsonPointer.of("folder"));
        final ThingMerged merged = ThingMerged.of(KNOWN_THING_ID, JsonPointer.empty(),
                JsonObject.of("{\"attributes\":{\"folder\":null}}"), 5L, Instant.now(), DittoHeaders.empty(),
                null);

        final DittoHeaders headers = enrich(new ThingEventEnricher(revokingProvider(), true, true), thingWithoutFolder,
                merged);

        assertThat(headers.get(DittoHeaderDefinition.REVOKED_ACCESS_PATHS.getKey()))
                .isEqualTo(EXPECTED_REVOKED_ACCESS_PATHS);
        // the restricted subject may only read public1, public2 and folder/public: "attributes" must not collapse
        assertThat(headers.get(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey()))
                .doesNotContain("\"attributes\":");
    }

    @Test
    public void coversDeletedPaths() {
        final Thing thingWithoutFolder = KNOWN_THING.removeAttribute(JsonPointer.of("folder"));
        final AttributeDeleted deleted = AttributeDeleted.of(KNOWN_THING_ID, JsonPointer.of("folder"), 5L,
                Instant.now(), DittoHeaders.empty(), null);

        final DittoHeaders headers = enrich(new ThingEventEnricher(revokingProvider(), true, true), thingWithoutFolder,
                deleted);

        assertThat(headers.get(DittoHeaderDefinition.REVOKED_ACCESS_PATHS.getKey()))
                .isEqualTo(EXPECTED_REVOKED_ACCESS_PATHS);
        assertThat(headers.get(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey()))
                .doesNotContain("\"attributes\":");
    }

    @Test
    public void addsRevokedAccessPathsToMessages() {
        final SendThingMessage<String> message = SendThingMessage.of(KNOWN_THING_ID,
                Message.<String>newBuilder(MessageHeaders.newBuilder(MessageDirection.TO, KNOWN_THING_ID, "hello")
                        .build()).build(),
                DittoHeaders.empty());

        final DittoHeaders headers = new ThingEventEnricher(revokingProvider(), true, true)
                .enrichWithPredefinedExtraFields(List.of(), KNOWN_THING_ID, null, KNOWN_THING, KNOWN_POLICY_ID,
                        message)
                .toCompletableFuture().join()
                .getDittoHeaders();

        assertThat(headers.get(DittoHeaderDefinition.REVOKED_ACCESS_PATHS.getKey()))
                .isEqualTo(EXPECTED_REVOKED_ACCESS_PATHS);
        assertThat(headers.get(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey())).isNull();
    }

    @Test
    public void ignoresGrantsOfPolicyEntriesForOtherNamespacesWhenCalculatingPartialAccessPaths() {
        final Policy policy = KNOWN_POLICY.toBuilder()
                .setSubjectFor("restricted-other-namespace", Subject.newInstance(
                        SubjectId.newInstance(KNOWN_ISSUER_RESTRICTED_SUBJECT), SubjectType.GENERATED))
                .setGrantedPermissionsFor("restricted-other-namespace",
                        ResourceKey.newInstance("thing", "/attributes/private"), "READ")
                .setNamespacesFor("restricted-other-namespace", List.of("some.other.namespace"))
                .build();
        final ThingEventEnricher sut = new ThingEventEnricher(provider(policy), true, true);

        final JsonObject header = partialAccessHeader(sut, KNOWN_THING);

        assertThat(header.toString()).contains("attributes/public1").doesNotContain("attributes/private");
    }

    @Test
    public void ignoresGrantsOfPolicyEntriesForOtherNamespacesInPredefinedExtraFieldsReadGrant() {
        final Policy policy = KNOWN_POLICY.toBuilder()
                .setSubjectFor("restricted-other-namespace", Subject.newInstance(
                        SubjectId.newInstance(KNOWN_ISSUER_RESTRICTED_SUBJECT), SubjectType.GENERATED))
                .setGrantedPermissionsFor("restricted-other-namespace",
                        ResourceKey.newInstance("thing", "/attributes/private"), "READ")
                .setNamespacesFor("restricted-other-namespace", List.of("some.other.namespace"))
                .build();
        final ThingEventEnricher sut = new ThingEventEnricher(provider(policy), true, true);
        final var configs = getPreDefinedExtraFieldsConfigs("extra-fields = [\"attributes/private\"]");

        final DittoHeaders headers = calculateEnrichedSignalHeaders(sut, configs).toCompletableFuture().join();

        assertThat(JsonObject.of(headers.get(
                DittoHeaderDefinition.PRE_DEFINED_EXTRA_FIELDS_READ_GRANT_OBJECT.getKey())))
                .isEqualTo(JsonObject.newBuilder()
                        .set(JsonKey.of("/attributes/private"), JsonArray.of(JsonValue.of(KNOWN_ISSUER_FULL_SUBJECT)))
                        .build());
    }

    @Test
    public void coversFieldsOfADeletedObjectFromThePreviousThing() {
        final Policy policy = FULL_ACCESS_POLICY.toBuilder()
                .setSubjectFor("group", Subject.newInstance(
                        SubjectId.newInstance(KNOWN_ISSUER_GROUP_SUBJECT), SubjectType.GENERATED))
                .setRevokedPermissionsFor("group", ResourceKey.newInstance("thing", "/attributes/obj/secret"), "READ")
                .build();
        final Thing previousThing = KNOWN_THING.setAttribute(JsonPointer.of("obj/secret"), JsonValue.of("s"));
        final AttributeDeleted deleted = AttributeDeleted.of(KNOWN_THING_ID, JsonPointer.of("obj"), 5L,
                Instant.now(), DittoHeaders.empty(), null);

        final DittoHeaders headers = new ThingEventEnricher(provider(policy), true, true)
                .enrichWithPredefinedExtraFields(List.of(), KNOWN_THING_ID, previousThing, KNOWN_THING,
                        KNOWN_POLICY_ID, deleted)
                .toCompletableFuture().join()
                .getDittoHeaders();

        assertThat(JsonObject.of(headers.get(DittoHeaderDefinition.REVOKED_ACCESS_PATHS.getKey())))
                .isEqualTo(JsonObject.of("{\"subjects\":[\"" + KNOWN_ISSUER_GROUP_SUBJECT +
                        "\"],\"paths\":{\"attributes/obj\":[0]}}"));
    }

    @Test
    public void coversFieldsOfAnObjectDeletedByMergeFromThePreviousThing() {
        final Policy policy = FULL_ACCESS_POLICY.toBuilder()
                .setSubjectFor("group", Subject.newInstance(
                        SubjectId.newInstance(KNOWN_ISSUER_GROUP_SUBJECT), SubjectType.GENERATED))
                .setRevokedPermissionsFor("group", ResourceKey.newInstance("thing", "/attributes/obj/secret"), "READ")
                .build();
        final Thing previousThing = KNOWN_THING.setAttribute(JsonPointer.of("obj/secret"), JsonValue.of("s"));
        final ThingMerged merged = ThingMerged.of(KNOWN_THING_ID, JsonPointer.of("/attributes"),
                JsonObject.of("{\"obj\":null}"), 5L, Instant.now(), DittoHeaders.empty(), null);

        final DittoHeaders headers = new ThingEventEnricher(provider(policy), true, true)
                .enrichWithPredefinedExtraFields(List.of(), KNOWN_THING_ID, previousThing, KNOWN_THING,
                        KNOWN_POLICY_ID, merged)
                .toCompletableFuture().join()
                .getDittoHeaders();

        assertThat(JsonObject.of(headers.get(DittoHeaderDefinition.REVOKED_ACCESS_PATHS.getKey())))
                .isEqualTo(JsonObject.of("{\"subjects\":[\"" + KNOWN_ISSUER_GROUP_SUBJECT +
                        "\"],\"paths\":{\"attributes/obj\":[0]}}"));
    }

    @Test
    public void coversFieldsDeletedByThingDefinitionMigration() {
        final Thing thingWithoutFolder = KNOWN_THING.removeAttribute(JsonPointer.of("folder"));
        final ThingDefinitionMigrated migrated = ThingDefinitionMigrated.of(Thing.newBuilder()
                        .setId(KNOWN_THING_ID)
                        .setAttribute(JsonPointer.of("folder"), JsonValue.nullLiteral())
                        .build(),
                5L, Instant.now(), DittoHeaders.empty(), null);

        final DittoHeaders headers = enrich(new ThingEventEnricher(revokingProvider(), true, true),
                thingWithoutFolder, migrated);

        assertThat(headers.get(DittoHeaderDefinition.REVOKED_ACCESS_PATHS.getKey()))
                .isEqualTo(EXPECTED_REVOKED_ACCESS_PATHS);
    }

    @Test
    public void removesRestrictedSubjectWhichMayReadNothingOfTheThingFromReadGrantedSubjects() {
        final Policy policy = KNOWN_POLICY.toBuilder()
                .setSubjectFor("features-only", Subject.newInstance(
                        SubjectId.newInstance("foo-issuer:features-only"), SubjectType.GENERATED))
                .setGrantedPermissionsFor("features-only", ResourceKey.newInstance("thing", "/features"), "READ")
                .build();
        final AttributeModified event = AttributeModified.of(KNOWN_THING_ID, JsonPointer.of("public1"),
                JsonValue.of(false), 4L, Instant.now(), DittoHeaders.newBuilder()
                        .readGrantedSubjects(List.of(
                                AuthorizationSubject.newInstance(KNOWN_ISSUER_FULL_SUBJECT),
                                AuthorizationSubject.newInstance(KNOWN_ISSUER_RESTRICTED_SUBJECT),
                                AuthorizationSubject.newInstance("foo-issuer:features-only")))
                        .build(), null);

        final DittoHeaders headers = enrich(new ThingEventEnricher(provider(policy), true, true), KNOWN_THING, event);

        assertThat(headers.getReadGrantedSubjects()).containsExactlyInAnyOrder(
                AuthorizationSubject.newInstance(KNOWN_ISSUER_FULL_SUBJECT),
                AuthorizationSubject.newInstance(KNOWN_ISSUER_RESTRICTED_SUBJECT));
        assertThat(headers.get(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey()))
                .doesNotContain("foo-issuer:features-only");
    }

    @Test
    public void marksSubjectsWithRevokedThingRoot() {
        final Policy policy = FULL_ACCESS_POLICY.toBuilder()
                .setSubjectFor("group", Subject.newInstance(
                        SubjectId.newInstance(KNOWN_ISSUER_GROUP_SUBJECT), SubjectType.GENERATED))
                .setRevokedPermissionsFor("group", ResourceKey.newInstance("thing", "/"), "READ")
                .build();

        final DittoHeaders headers = calculateEnrichedSignalHeaders(
                new ThingEventEnricher(provider(policy), false, true), List.of()).toCompletableFuture().join();

        final JsonObject revoked = JsonObject.of(headers.get(DittoHeaderDefinition.REVOKED_ACCESS_PATHS.getKey()));
        assertThat(revoked.getValue("subjects")).contains(JsonArray.of(JsonValue.of(KNOWN_ISSUER_GROUP_SUBJECT)));
        assertThat(revoked.getValue("root")).contains(JsonArray.of(JsonValue.of(0)));
    }

    @Test
    public void failsWithoutPolicyEnforcerAlsoWhenPartialAccessEventsAreDisabled() {
        final PolicyEnforcerProvider provider = mock(PolicyEnforcerProvider.class);
        when(provider.getPolicyEnforcer(KNOWN_POLICY_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        assertThatCompletionStage(calculateEnrichedSignalHeaders(new ThingEventEnricher(provider, false, true),
                List.of())).isCompletedExceptionally();
        assertThatCompletionStage(calculateEnrichedSignalHeaders(new ThingEventEnricher(provider, true, true),
                List.of())).isCompletedExceptionally();
    }

    @Test
    public void failsWithFailedPolicyEnforcerLookup() {
        final PolicyEnforcerProvider provider = mock(PolicyEnforcerProvider.class);
        when(provider.getPolicyEnforcer(KNOWN_POLICY_ID))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("lookup failed")));

        assertThatCompletionStage(calculateEnrichedSignalHeaders(new ThingEventEnricher(provider, true, true),
                List.of())).isCompletedExceptionally();
    }

    @Test
    public void eventsAndMessagesDoNotEvictEachOthersRestrictions() {
        final PolicyEnforcer namespaceEnforcer = Mockito.spy(PolicyEnforcer.of(REVOKING_POLICY.toBuilder()
                .set(KNOWN_POLICY.getEntryFor(KNOWN_LABEL_RESTRICTED).orElseThrow())
                .build()));
        final PolicyEnforcer policyEnforcer = mock(PolicyEnforcer.class);
        when(policyEnforcer.forNamespace(KNOWN_THING_ID.getNamespace())).thenReturn(namespaceEnforcer);
        final PolicyEnforcerProvider provider = mock(PolicyEnforcerProvider.class);
        when(provider.getPolicyEnforcer(KNOWN_POLICY_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(policyEnforcer)));
        final ThingEventEnricher sut = new ThingEventEnricher(provider, true, true);
        final SendThingMessage<String> message = SendThingMessage.of(KNOWN_THING_ID,
                Message.<String>newBuilder(MessageHeaders.newBuilder(MessageDirection.TO, KNOWN_THING_ID, "hello")
                        .build()).build(),
                DittoHeaders.empty());

        calculateEnrichedSignalHeaders(sut, List.of()).toCompletableFuture().join();
        sut.enrichWithPredefinedExtraFields(List.of(), KNOWN_THING_ID, null, KNOWN_THING, KNOWN_POLICY_ID, message)
                .toCompletableFuture().join();
        final int calculations = Mockito.mockingDetails(namespaceEnforcer).getInvocations().stream()
                .filter(invocation -> "getEnforcer".equals(invocation.getMethod().getName()))
                .mapToInt(invocation -> 1)
                .sum();
        final DittoHeaders headers = calculateEnrichedSignalHeaders(sut, List.of()).toCompletableFuture().join();
        final DittoHeaders messageHeaders = sut.enrichWithPredefinedExtraFields(List.of(), KNOWN_THING_ID, null,
                KNOWN_THING, KNOWN_POLICY_ID, message).toCompletableFuture().join().getDittoHeaders();

        verify(namespaceEnforcer, times(calculations)).getEnforcer();
        assertThat(messageHeaders.get(DittoHeaderDefinition.REVOKED_ACCESS_PATHS.getKey())).isNotNull();
        assertThat(messageHeaders.get(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey())).isNull();
        assertThat(headers.get(DittoHeaderDefinition.REVOKED_ACCESS_PATHS.getKey()))
                .isEqualTo(EXPECTED_REVOKED_ACCESS_PATHS);
        assertThat(headers.get(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey())).isNotNull();
    }

    private static PolicyEnforcerProvider provider(final Policy policy) {
        final PolicyEnforcerProvider provider = mock(PolicyEnforcerProvider.class);
        when(provider.getPolicyEnforcer(KNOWN_POLICY_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(PolicyEnforcer.of(policy))));
        return provider;
    }

    private static PolicyEnforcerProvider revokingProvider() {
        // REVOKING_POLICY plus the restricted subject of KNOWN_POLICY
        final Policy policy = REVOKING_POLICY.toBuilder()
                .set(KNOWN_POLICY.getEntryFor(KNOWN_LABEL_RESTRICTED).orElseThrow())
                .build();
        final PolicyEnforcerProvider provider = mock(PolicyEnforcerProvider.class);
        when(provider.getPolicyEnforcer(KNOWN_POLICY_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(PolicyEnforcer.of(policy))));
        return provider;
    }

    private static DittoHeaders enrich(final ThingEventEnricher sut, final Thing thing, final ThingEvent<?> event) {
        return sut.enrichWithPredefinedExtraFields(List.of(), KNOWN_THING_ID, null, thing, KNOWN_POLICY_ID, event)
                .toCompletableFuture().join()
                .getDittoHeaders();
    }

    @Test
    public void returnsFailedStageWhenThingSerializationFails() {
        final Thing thing = mock(Thing.class);
        when(thing.toJson()).thenThrow(new IllegalStateException("serialization failure"));
        final ThingEventEnricher sut = new ThingEventEnricher(policyEnforcerProvider, true, true);
        final AttributeModified event = AttributeModified.of(
                KNOWN_THING_ID, JsonPointer.of("something"), JsonValue.of(true), 4L,
                Instant.now(), DittoHeaders.empty(), null);

        final CompletionStage<AttributeModified> resultStage =
                sut.enrichWithPredefinedExtraFields(List.of(), KNOWN_THING_ID, null, thing, KNOWN_POLICY_ID, event);

        assertThatCompletionStage(resultStage).isCompletedExceptionally();
    }

    @Test
    public void skipsPartialAccessHeaderWhenNoPartialSubjectsExist() {
        final PolicyEnforcerProvider fullAccessProvider = mock(PolicyEnforcerProvider.class);
        when(fullAccessProvider.getPolicyEnforcer(KNOWN_POLICY_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(PolicyEnforcer.of(FULL_ACCESS_POLICY))));

        final ThingEventEnricher sut = new ThingEventEnricher(fullAccessProvider, true, true);

        final CompletionStage<DittoHeaders> headersStage = calculateEnrichedSignalHeaders(sut, List.of());

        assertThatCompletionStage(headersStage.thenApply(headers ->
                headers.get(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey())))
                .isNotCompletedExceptionally()
                .isCompletedWithValue(null);
    }

    @Test
    public void ensureConditionBasedEnrichmentAsPreDefinedFromConfiguration() {
        // GIVEN: the configuration to enrich all things with their definition and some with an attribute public1
        final var sut = new ThingEventEnricher(policyEnforcerProvider, true, true);
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
                                .add(KNOWN_ISSUER_FULL_SUBJECT)
                                .add(KNOWN_ISSUER_RESTRICTED_SUBJECT)
                                .build()
                        )
                        .set(JsonKey.of("/attributes/folder"), JsonArray.newBuilder()
                                .add(KNOWN_ISSUER_FULL_SUBJECT)
                                .build()
                        )
                        .set(JsonKey.of("/attributes/folder/public"), JsonArray.newBuilder()
                                .add(KNOWN_ISSUER_ANOTHER_SUBJECT)
                                .add(KNOWN_ISSUER_RESTRICTED_SUBJECT)
                                .build()
                        ),
                preDefinedFieldsObject -> preDefinedFieldsObject
                        .set(JsonPointer.of("attributes/public1"), true)
                        .set(JsonPointer.of("attributes/folder/public"), true)
                        .set(JsonPointer.of("attributes/folder/private"), false)
        );
    }

    /**
     * Reproduces the production scenario: connection with {@code thing:/ READ} requesting
     * {@code attributes} as extraFields. Verifies that the read grant header contains string
     * subject IDs that {@code DittoCachingSignalEnrichmentFacade} can match against.
     */
    @Test
    public void readGrantHeaderContainsStringSubjectIdsForRootReadPolicy() {
        // GIVEN: a policy with only thing:/ READ (like lora-downlink-processor in production)
        final PolicyEnforcerProvider fullAccessProvider = mock(PolicyEnforcerProvider.class);
        when(fullAccessProvider.getPolicyEnforcer(KNOWN_POLICY_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(PolicyEnforcer.of(FULL_ACCESS_POLICY))));
        final ThingEventEnricher sut = new ThingEventEnricher(fullAccessProvider, true, true);
        final var configs = getPreDefinedExtraFieldsConfigs(
                "namespaces = [\"org.eclipse.ditto.some\"]\n" +
                "extra-fields = [\"attributes\"]"
        );

        // WHEN: enriched headers are calculated
        final CompletionStage<DittoHeaders> resultHeadersStage = calculateEnrichedSignalHeaders(sut, configs);
        final DittoHeaders headers = resultHeadersStage.toCompletableFuture().join();

        // THEN: the read grant header must contain string subject IDs, not integer indices
        final String readGrantHeader = headers.get(
                DittoHeaderDefinition.PRE_DEFINED_EXTRA_FIELDS_READ_GRANT_OBJECT.getKey());
        assertThat(readGrantHeader)
                .as("read grant header should be present")
                .isNotNull();
        final JsonObject readGrant = JsonFactory.readFrom(readGrantHeader).asObject();
        // Verify it contains string subject IDs (e.g. "foo-issuer:full-subject"), not integers
        readGrant.forEach(field -> {
            final JsonArray subjects = field.getValue().asArray();
            subjects.forEach(value ->
                    assertThat(value.isString())
                            .as("read grant array values must be strings, not integers: %s", value)
                            .isTrue()
            );
        });
        // Verify the specific subject is present for /attributes
        assertThat(readGrantHeader).contains(KNOWN_ISSUER_FULL_SUBJECT);
    }

    @Test
    public void cacheReusesResultForSameEnforcerInstanceAndStructure() {
        final PolicyEnforcerProvider provider = mock(PolicyEnforcerProvider.class);
        // Same enforcer instance returned for every call (value-only Thing update, no policy change).
        when(provider.getPolicyEnforcer(KNOWN_POLICY_ID))
                .thenReturn(enforcerFuture(restrictedPolicy(1L, "/attributes/public1", "/attributes/public2")));
        final ThingEventEnricher sut = new ThingEventEnricher(provider, true, true);

        final JsonObject header1 = partialAccessHeader(sut, KNOWN_THING);
        final JsonObject header2 = partialAccessHeader(sut, KNOWN_THING);

        assertThat(header1.toString()).contains("attributes/public2");
        assertThat(header2).isEqualTo(header1); // reused for the identical enforcer instance + structure
    }

    @Test
    public void cacheRecomputesWhenGrantsChangeWithSameRevisionViaNewEnforcerInstance() {
        // Reproduces the imported / namespace-root-policy revoke scenario reported in review: the enforcer is
        // rebuilt with narrower grants (a NEW instance, via cascade invalidation) while the importing policy's
        // own revision does NOT move. Keying on the enforcer instance must treat this as a miss so a revoked
        // field is never served from the stale cache (security regression guard).
        final PolicyEnforcerProvider provider = mock(PolicyEnforcerProvider.class);
        when(provider.getPolicyEnforcer(KNOWN_POLICY_ID)).thenReturn(
                enforcerFuture(restrictedPolicy(1L, "/attributes/public1", "/attributes/public2")),
                enforcerFuture(restrictedPolicy(1L, "/attributes/public1"))); // same revision 1, public2 revoked
        final ThingEventEnricher sut = new ThingEventEnricher(provider, true, true);

        final JsonObject header1 = partialAccessHeader(sut, KNOWN_THING); // public2 still granted
        final JsonObject header2 = partialAccessHeader(sut, KNOWN_THING); // public2 revoked -> must be withheld

        assertThat(header1.toString()).contains("attributes/public2");
        assertThat(header2.toString()).doesNotContain("attributes/public2");
        assertThat(header2).isNotEqualTo(header1);
    }

    @Test
    public void cacheRecomputesWhenThingStructureChanges() {
        final PolicyEnforcerProvider provider = mock(PolicyEnforcerProvider.class);
        when(provider.getPolicyEnforcer(KNOWN_POLICY_ID)).thenReturn(
                enforcerFuture(restrictedPolicy(1L, "/attributes/public1", "/attributes/public2")));
        final ThingEventEnricher sut = new ThingEventEnricher(provider, true, true);

        final JsonObject header1 = partialAccessHeader(sut, KNOWN_THING);           // public2 accessible
        final JsonObject header2 = partialAccessHeader(sut, THING_WITHOUT_PUBLIC2); // public2 removed

        assertThat(header1.toString()).contains("attributes/public2");
        assertThat(header2.toString()).doesNotContain("attributes/public2");
        assertThat(header2).isNotEqualTo(header1);
    }

    @Test
    public void cacheRecomputesWhenPolicyRevisionChanges() {
        final PolicyEnforcerProvider provider = mock(PolicyEnforcerProvider.class);
        when(provider.getPolicyEnforcer(KNOWN_POLICY_ID)).thenReturn(
                enforcerFuture(restrictedPolicy(1L, "/attributes/public1")),
                enforcerFuture(restrictedPolicy(2L, "/attributes/public1", "/attributes/public2")));
        final ThingEventEnricher sut = new ThingEventEnricher(provider, true, true);

        final JsonObject header1 = partialAccessHeader(sut, KNOWN_THING); // rev 1: public1 only
        final JsonObject header2 = partialAccessHeader(sut, KNOWN_THING); // rev 2: public1 + public2

        assertThat(header1.toString()).doesNotContain("attributes/public2");
        assertThat(header2.toString()).contains("attributes/public2");
        assertThat(header2).isNotEqualTo(header1);
    }

    @Test
    public void cacheDisabledRecomputesEachTime() {
        final PolicyEnforcerProvider provider = mock(PolicyEnforcerProvider.class);
        when(provider.getPolicyEnforcer(KNOWN_POLICY_ID)).thenReturn(
                enforcerFuture(restrictedPolicy(1L, "/attributes/public1", "/attributes/public2")),
                enforcerFuture(restrictedPolicy(1L, "/attributes/public1")));
        final ThingEventEnricher sut = new ThingEventEnricher(provider, true, false); // cache disabled

        final JsonObject header1 = partialAccessHeader(sut, KNOWN_THING); // policy A: public1 + public2
        final JsonObject header2 = partialAccessHeader(sut, KNOWN_THING); // policy B: public1 (recomputed)

        assertThat(header1.toString()).contains("attributes/public2");
        assertThat(header2.toString()).doesNotContain("attributes/public2");
        assertThat(header2).isNotEqualTo(header1);
    }

    private static Policy restrictedPolicy(final long revision, final String... restrictedGrantThingPaths) {
        final var builder = Policy.newBuilder(KNOWN_POLICY_ID)
                .setRevision(revision)
                .setSubjectFor(KNOWN_LABEL_FULL, Subject.newInstance(
                        SubjectId.newInstance(KNOWN_ISSUER_FULL_SUBJECT), SubjectType.GENERATED))
                .setGrantedPermissionsFor(KNOWN_LABEL_FULL, ResourceKey.newInstance("thing", "/"), "READ", "WRITE")
                .setSubjectFor(KNOWN_LABEL_RESTRICTED, Subject.newInstance(
                        SubjectId.newInstance(KNOWN_ISSUER_RESTRICTED_SUBJECT), SubjectType.GENERATED));
        for (final String path : restrictedGrantThingPaths) {
            builder.setGrantedPermissionsFor(KNOWN_LABEL_RESTRICTED, ResourceKey.newInstance("thing", path), "READ");
        }
        return builder.build();
    }

    private static CompletableFuture<Optional<PolicyEnforcer>> enforcerFuture(final Policy policy) {
        return CompletableFuture.completedFuture(Optional.of(PolicyEnforcer.of(policy)));
    }

    private static JsonObject partialAccessHeader(final ThingEventEnricher sut, final Thing thing) {
        final AttributeModified event = AttributeModified.of(
                KNOWN_THING_ID, JsonPointer.of("something"), JsonValue.of(true), 4L,
                Instant.now(), DittoHeaders.empty(), null);
        final DittoHeaders headers = sut.enrichWithPredefinedExtraFields(
                        List.of(), KNOWN_THING_ID, null, thing, KNOWN_POLICY_ID, event)
                .thenApply(AttributeModified::getDittoHeaders)
                .toCompletableFuture().join();
        final String value = headers.get(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey());
        return value == null ? JsonFactory.newObject() : JsonFactory.readFrom(value).asObject();
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
                sut.enrichWithPredefinedExtraFields(preDefinedExtraFieldsConfigs, KNOWN_THING_ID, null, KNOWN_THING,
                        KNOWN_POLICY_ID, event);
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
