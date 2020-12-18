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
package org.eclipse.ditto.services.concierge.enforcement;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.model.policies.PoliciesResourceType.thingResource;
import static org.eclipse.ditto.model.policies.SubjectIssuer.GOOGLE;
import static org.eclipse.ditto.services.concierge.enforcement.MergeThingCommandEnforcementTest.TestArgument.GRANT;
import static org.eclipse.ditto.services.concierge.enforcement.TestSetup.SUBJECT;
import static org.eclipse.ditto.services.concierge.enforcement.TestSetup.SUBJECT_ID;
import static org.eclipse.ditto.services.concierge.enforcement.TestSetup.THING_ID;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.enforcers.trie.TrieBasedPolicyEnforcer;
import org.eclipse.ditto.model.policies.EffectedPermissions;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyBuilder;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.services.models.things.Permission;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotModifiableException;
import org.eclipse.ditto.signals.commands.things.modify.MergeThing;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

public final class MergeThingCommandEnforcementTest {

    private static final JsonPointer PATH = JsonFactory.newPointer("/features/device/properties/location");
    private static final JsonObject VALUE =
            JsonFactory.newObjectBuilder().set("longitude", 44.673).set("latitude", 8.261).build();

    /**
     * The Merge Patch JSON used in this test:
     * <pre>
     * {
     *   "features": {
     *     "device" : {
     *       "properties" : {
     *         "location" : {
     *           "longitude" : 44.673,
     *           "latitude": 8.261
     *         }
     *       }
     *     }
     *   }
     * }
     * </pre>
     */
    private static final JsonObject PATCH = JsonFactory.newObject(PATH, VALUE).asObject();

    private static final Set<Set<String>> RESOURCES = Set.of(
            Set.of("/"),
            Set.of("/features"),
            Set.of("/features/device"),
            Set.of("/features/device/properties"),
            Set.of("/features/device/properties/location"),
            Set.of("/features/device/properties/location/longitude", "/features/device/properties/location" +
                    "/latitude"));

    private static final Set<String> PATHS = Set.of(
            "/",
            "/features",
            "/features/device",
            "/features/device/properties",
            "/features/device/properties/location",
            "/features/device/properties/location/longitude",
            "/features/device/properties/location/latitude");

    @ParameterizedTest
    @ArgumentsSource(AcceptPolicyProvider.class)
    @ArgumentsSource(PatchLongitudeOnlyProvider.class)
    public void acceptByPolicy(final TestArgument arg) {
        final TrieBasedPolicyEnforcer policyEnforcer = TrieBasedPolicyEnforcer.newInstance(arg.getPolicy());
        final MergeThing authorizedMergeThing =
                ThingCommandEnforcement.authorizeByPolicyOrThrow(policyEnforcer, arg.getMergeThing());
        assertThat(authorizedMergeThing.getDittoHeaders().getAuthorizationContext()).isNotNull();
    }

    @ParameterizedTest
    @ArgumentsSource(RejectPolicyProvider.class)
    @ArgumentsSource(SubFieldRevokedProvider.class)
    public void rejectByPolicy(final TestArgument arg) {
        final TrieBasedPolicyEnforcer policyEnforcer = TrieBasedPolicyEnforcer.newInstance(arg.getPolicy());
        assertThatExceptionOfType(ThingNotModifiableException.class).isThrownBy(
                () -> ThingCommandEnforcement.authorizeByPolicyOrThrow(policyEnforcer, arg.getMergeThing()));
    }

    /**
     * Generates combinations of a Policy and MergeThing commands that should be <b>accepted</b> by command enforcement.
     */
    static class AcceptPolicyProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(final ExtensionContext context) {
            return PATHS.stream()
                    .flatMap(path -> RESOURCES.stream()
                            .map(resources -> Arguments.of(
                                    TestArgument.of(path, resources, Collections.emptySet()))));
        }
    }

    /**
     * Generates combinations of a Policy and MergeThing commands that should be <b>rejected</b> by command enforcement.
     */
    static class RejectPolicyProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return PATHS.stream()
                    .flatMap(path -> RESOURCES.stream()
                            .map(resources -> Arguments.of(
                                    TestArgument.of(path, Collections.emptySet(), resources)
                                            .withAdditionalPermission("/", GRANT))));

        }
    }

    /**
     * Patch longitude field (access granted) which is beside latitude field (revoked).
     */
    static class PatchLongitudeOnlyProvider implements ArgumentsProvider {

        private static final Set<Set<String>> REVOKE_FOR_LATITUDE =
                Set.of(Set.of("/features/device/properties/location/latitude"));
        private static final JsonObject ONLY_LONGITUDE =
                JsonFactory.newObjectBuilder().set("longitude", 44.673).build();
        private static final JsonObject PATCH_ONLY_LONGITUDE = JsonFactory.newObject(PATH, ONLY_LONGITUDE).asObject();

        @Override
        public Stream<? extends Arguments> provideArguments(final ExtensionContext context) {
            return PATHS.stream()
                    .filter(p -> PATCH_ONLY_LONGITUDE.getValue(p).isPresent())
                    .flatMap(path -> REVOKE_FOR_LATITUDE.stream()
                            .map(resources -> Arguments.of(
                                    TestArgument.of(path, Collections.emptySet(), resources,
                                            PATCH_ONLY_LONGITUDE).withAdditionalPermission("/", GRANT))));
        }
    }

    /**
     * Access to field below latitude is revoked.
     */
    static class SubFieldRevokedProvider implements ArgumentsProvider {

        private static final Set<Set<String>> REVOKE_FIELD_BELOW_LATITUDE =
                Set.of(Set.of("/features/device/properties/location/latitude/no-access"));

        @Override
        public Stream<? extends Arguments> provideArguments(final ExtensionContext context) {
            return PATHS.stream()
                    .peek(System.out::println)
                    .filter(p -> !p.endsWith("longitude")) // skip longitude which is not affected by revoke
                    .peek(System.out::println)
                    .flatMap(path -> REVOKE_FIELD_BELOW_LATITUDE.stream()
                            .map(resources -> Arguments.of(
                                    TestArgument.of(path, Collections.emptySet(), resources)
                                            .withAdditionalPermission("/", GRANT))));
        }
    }

    /**
     * Prepares and holds arguments for parameterized tests.
     */
    static class TestArgument {

        private static final String MERGE_LABEL = "MERGE";
        static final EffectedPermissions GRANT =
                PoliciesModelFactory.newEffectedPermissions(List.of(Permission.WRITE), Collections.emptyList());
        static final EffectedPermissions REVOKE =
                PoliciesModelFactory.newEffectedPermissions(Collections.emptyList(), List.of(Permission.WRITE));

        private final MergeThing mergeThing;
        private final Policy policy;

        private TestArgument(final MergeThing mergeThing, final Policy policy) {
            this.mergeThing = mergeThing;
            this.policy = policy;
        }

        static TestArgument of(final String pathOfMergeCommand, final Set<String> grantedResources,
                final Set<String> revokedResources) {
            final Set<JsonPointer> granted = grantedResources.stream().map(JsonPointer::of).collect(toSet());
            final Set<JsonPointer> revoked = revokedResources.stream().map(JsonPointer::of).collect(toSet());
            return new TestArgument(toMergeCommand(JsonPointer.of(pathOfMergeCommand), PATCH),
                    toPolicyWithResources(granted, revoked));
        }

        static TestArgument of(final String pathOfMergeCommand, final Set<String> grantedResources,
                final Set<String> revokedResources, final JsonObject patch) {
            final Set<JsonPointer> granted = grantedResources.stream().map(JsonPointer::of).collect(toSet());
            final Set<JsonPointer> revoked = revokedResources.stream().map(JsonPointer::of).collect(toSet());
            return new TestArgument(toMergeCommand(JsonPointer.of(pathOfMergeCommand), patch),
                    toPolicyWithResources(granted, revoked));
        }

        Policy getPolicy() {
            return policy;
        }

        MergeThing getMergeThing() {
            return mergeThing;
        }

        TestArgument withAdditionalPermission(final String resource, final EffectedPermissions permission) {
            return new TestArgument(getMergeThing(), getPolicy().toBuilder()
                    .forLabel("ADDITIONAL")
                    .setSubject(GOOGLE, SUBJECT_ID)
                    .setPermissions(thingResource(resource), permission)
                    .build());
        }

        private static MergeThing toMergeCommand(final JsonPointer path, final JsonObject patch) {
            return MergeThing.of(THING_ID, path, patch.getValue(path).orElseThrow(), headers());
        }

        private static DittoHeaders headers() {
            return DittoHeaders.newBuilder()
                    .authorizationContext(
                            AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED, SUBJECT,
                                    AuthorizationSubject.newInstance(String.format("%s:%s", GOOGLE, SUBJECT_ID))))
                    .correlationId(UUID.randomUUID().toString())
                    .build();
        }

        private static Policy toPolicyWithResources(final Set<JsonPointer> granted, final Set<JsonPointer> revoked) {
            final PolicyId policyId = PolicyId.of("policy:id");
            final PolicyBuilder.LabelScoped builder = PoliciesModelFactory.newPolicyBuilder(policyId)
                    .setRevision(1L)
                    .forLabel(MERGE_LABEL)
                    .setSubject(GOOGLE, SUBJECT_ID);
            granted.forEach(path -> builder.setPermissionsFor(MERGE_LABEL, thingResource(path), GRANT));
            revoked.forEach(path -> builder.setPermissionsFor(MERGE_LABEL, thingResource(path), REVOKE));
            return builder.build();
        }

        @Override
        public String toString() {
            return String.format("path: %s -- value: %s -- policy: %s", getMergeThing().getPath(),
                    getMergeThing().getValue(),
                    getPolicy().toJson().getValueOrThrow(Policy.JsonFields.ENTRIES));
        }
    }
}
