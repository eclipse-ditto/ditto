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
package org.eclipse.ditto.policies.service.enforcement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.enforcement.config.NamespacePoliciesConfig;
import org.eclipse.ditto.policies.model.AllowedAddition;
import org.eclipse.ditto.policies.model.EffectedImports;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.eclipse.ditto.policies.model.EntryReference;
import org.eclipse.ditto.policies.model.ImportableType;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.PolicyImports;
import org.eclipse.ditto.policies.model.Resource;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.Resources;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.SubjectIssuer;
import org.eclipse.ditto.policies.model.Subjects;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicyResponse;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Regression tests for the source-side READ check in {@link PolicyCommandEnforcement#filterResponse} when
 * {@code policy-view=resolved} is requested. Locks in the protection against the disclosure leak where a caller
 * with broad READ on the importing policy could read full contents of imported / namespace-root source policies
 * they have no permission on. See PR review on #2429 / #2354.
 *
 * <p>Post-#2431 the ns-root entries appear in the merged policy under the rewritten label
 * {@code nsimported-<rootId>-<originalLabel>}; the assertions reflect that form.</p>
 */
public final class PolicyCommandEnforcementResolvedViewTest {

    private static final SubjectId VIEWER_ID = SubjectId.newInstance(SubjectIssuer.GOOGLE, "viewer");
    private static final SubjectId ADMIN_ID = SubjectId.newInstance(SubjectIssuer.GOOGLE, "admin");
    private static final Subject VIEWER = Subject.newInstance(VIEWER_ID);
    private static final Subject ADMIN = Subject.newInstance(ADMIN_ID);

    private static final ResourceKey POLICY_ROOT = PoliciesResourceType.policyResource("/");
    private static final ResourceKey THING_ROOT = PoliciesResourceType.thingResource("/");

    private static final PolicyId IMPORTING_ID = PolicyId.of("test.nsleak", "importing");
    private static final PolicyId DECLARED_SOURCE_ID = PolicyId.of("test.nsleak", "declared-source");
    private static final PolicyId NS_ROOT_ID = PolicyId.of("test.nsleak", "ns-root");
    private static final String IMPORTING_NAMESPACE = "test.nsleak";

    private static final DittoHeaders RESOLVED_VIEW_HEADERS = DittoHeaders.newBuilder()
            .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                    AuthorizationSubject.newInstance(VIEWER_ID)))
            .putHeader(DittoHeaderDefinition.POLICY_VIEW.getKey(), "resolved")
            .correlationId("resolved-view-test")
            .build();

    /**
     * VIEWER has READ+WRITE on policy:/ of the importing policy and zero presence in the declared-import source.
     * Without the source-side check, the merged JSON exposes SECRET (with ADMIN's subject) in cleartext to VIEWER.
     * With the check, SECRET must be absent.
     */
    @Test
    public void resolvedViewDropsImportedEntriesWhenCallerLacksSourceSideRead() throws Exception {
        final Policy importing = Policy.newBuilder(IMPORTING_ID)
                .forLabel("VIEWER")
                .setSubject(VIEWER)
                .setGrantedPermissions(POLICY_ROOT, Permission.READ, Permission.WRITE)
                .setImportable(ImportableType.NEVER)
                .setPolicyImports(PolicyImports.newInstance(PoliciesModelFactory.newPolicyImport(
                        DECLARED_SOURCE_ID, EffectedImports.newInstance(Collections.emptyList()))))
                .build();
        final Policy declaredSource = Policy.newBuilder(DECLARED_SOURCE_ID)
                .forLabel("ADMIN")
                .setSubject(ADMIN)
                .setGrantedPermissions(POLICY_ROOT, Permission.READ, Permission.WRITE)
                .setImportable(ImportableType.NEVER)
                .forLabel("SECRET")
                .setSubject(ADMIN)
                .setGrantedPermissions(THING_ROOT, Permission.READ)
                .setImportable(ImportableType.IMPLICIT)
                .build();

        final JsonObject filteredEntries = runResolvedView(importing,
                policyResolver(importing, declaredSource), emptyNamespacePoliciesConfig());

        // Declared-import label format: imported-<sourceId>-<originalLabel>.
        final String secretLabel = "imported-" + DECLARED_SOURCE_ID + "-SECRET";
        assertThat(filteredEntries.contains(JsonPointer.of(secretLabel)))
                .as("imported SECRET entry must be hidden from caller without source-side READ")
                .isFalse();
        assertThat(filteredEntries.toString())
                .as("ADMIN subject must not leak via imported entry")
                .doesNotContain(ADMIN_ID.toString());
    }

    /**
     * VIEWER has READ+WRITE on policy:/ of the importing policy and zero presence in the namespace-root source.
     * Post-#2431 the ns-root entry would land in merged JSON under {@code nsimported-<rootId>-AUDIT}; the
     * source-side check must drop it because VIEWER has no READ on AUDIT in the ns-root.
     */
    @Test
    public void resolvedViewDropsNamespaceRootEntriesWhenCallerLacksSourceSideRead() throws Exception {
        final Policy importing = Policy.newBuilder(IMPORTING_ID)
                .forLabel("VIEWER")
                .setSubject(VIEWER)
                .setGrantedPermissions(POLICY_ROOT, Permission.READ, Permission.WRITE)
                .setImportable(ImportableType.NEVER)
                .build();
        final Policy nsRoot = Policy.newBuilder(NS_ROOT_ID)
                .forLabel("ADMIN")
                .setSubject(ADMIN)
                .setGrantedPermissions(POLICY_ROOT, Permission.READ, Permission.WRITE)
                .setImportable(ImportableType.NEVER)
                .forLabel("AUDIT")
                .setSubject(ADMIN)
                .setGrantedPermissions(THING_ROOT, Permission.READ)
                .setImportable(ImportableType.IMPLICIT)
                .build();

        final JsonObject filteredEntries = runResolvedView(importing,
                policyResolver(importing, nsRoot),
                namespacePoliciesConfigBinding(IMPORTING_NAMESPACE, NS_ROOT_ID));
        // Post-#2431 the ns-root entry's merged label is the rewritten nsimported-<rootId>-<originalLabel> form.
        final String auditLabel = PoliciesModelFactory.newNsImportedLabel(NS_ROOT_ID, "AUDIT").toString();
        assertThat(filteredEntries.contains(JsonPointer.of(auditLabel)))
                .as("namespace-root AUDIT entry must be hidden from caller without source-side READ")
                .isFalse();
        assertThat(filteredEntries.toString())
                .as("ADMIN subject must not leak via namespace-root entry")
                .doesNotContain(ADMIN_ID.toString());
    }

    /**
     * Customer-shaped 3-policy graph driven end-to-end through {@link PolicyCommandEnforcement#filterResponse}
     * for a resolved-view request: template defines per-resource access entries, an intermediate "main" policy
     * imports the template and uses local + import references to combine a manager subject with the template's
     * resources, and a leaf policy imports main with {@code transitiveImports=[template]} and no entries of its
     * own.
     *
     * <p>Verifies that the rebuilt response JSON contains main's per-resource access entries (with the
     * manager subject and template resources merged in via references) under the {@code imported-<mainId>-<label>}
     * prefix, that the EXPLICIT manager-only entry is not pulled into the leaf (leaf imports main with empty
     * entries list), and that none of these entries are dropped by the source-side READ filter — the viewer
     * has source-side READ on main via an IMPLICIT VIEWER entry that grants {@code policy:/ READ} to the
     * caller subject.</p>
     */
    @Test
    public void resolvedViewMergesTransitiveImportAndReferencesEndToEnd() throws Exception {
        final PolicyId leafId = IMPORTING_ID;
        final PolicyId mainId = PolicyId.of("test.nsleak", "main");
        final PolicyId templateId = PolicyId.of("test.nsleak", "template");

        final Label viewerLabel = Label.of("VIEWER");
        final Label managerLabel = Label.of("MANAGER");
        final Label roomAccessLabel = Label.of("ROOM_ACCESS");
        final Label buildingAccessLabel = Label.of("BUILDING_ACCESS");

        final ResourceKey roomThing = ResourceKey.newInstance("thing", JsonPointer.of("features/room"));
        final ResourceKey roomInbox = ResourceKey.newInstance("message", JsonPointer.of("features/room/inbox"));
        final ResourceKey buildingThing = ResourceKey.newInstance("thing", JsonPointer.of("features/building"));

        // --- template: resource-bearing entries, no subjects, allowedAdditions=[SUBJECTS]. ---
        final PolicyEntry templateRoom = PoliciesModelFactory.newPolicyEntry(roomAccessLabel,
                PoliciesModelFactory.emptySubjects(),
                Resources.newInstance(
                        Resource.newInstance(roomThing, EffectedPermissions.newInstance(
                                Permissions.newInstance(Permission.READ), Permissions.none())),
                        Resource.newInstance(roomInbox, EffectedPermissions.newInstance(
                                Permissions.newInstance(Permission.WRITE), Permissions.none()))),
                null, ImportableType.IMPLICIT,
                Collections.singleton(AllowedAddition.SUBJECTS), null);
        final PolicyEntry templateBuilding = PoliciesModelFactory.newPolicyEntry(buildingAccessLabel,
                PoliciesModelFactory.emptySubjects(),
                Resources.newInstance(
                        Resource.newInstance(buildingThing, EffectedPermissions.newInstance(
                                Permissions.newInstance(Permission.READ), Permissions.none()))),
                null, ImportableType.IMPLICIT,
                Collections.singleton(AllowedAddition.SUBJECTS), null);
        final Policy template = PoliciesModelFactory.newPolicyBuilder(templateId)
                .set(templateRoom)
                .set(templateBuilding)
                .build();

        // --- main: imports template; carries the manager subject in an EXPLICIT-only entry (not pulled
        //     into the leaf), per-resource IMPLICIT entries with references only, plus an IMPLICIT VIEWER
        //     entry granting the caller policy:/ READ so the source-side READ check passes for every entry. ---
        final SubjectId managerSubjectId = SubjectId.newInstance(SubjectIssuer.GOOGLE, "manager");
        final PolicyEntry mainViewer = PoliciesModelFactory.newPolicyEntry(viewerLabel,
                Subjects.newInstance(Subject.newInstance(VIEWER_ID)),
                Resources.newInstance(Resource.newInstance(POLICY_ROOT, EffectedPermissions.newInstance(
                        Permissions.newInstance(Permission.READ), Permissions.none()))),
                ImportableType.IMPLICIT);
        final PolicyEntry mainManager = PoliciesModelFactory.newPolicyEntry(managerLabel,
                Subjects.newInstance(Subject.newInstance(managerSubjectId)),
                PoliciesModelFactory.emptyResources(),
                ImportableType.EXPLICIT);
        final List<EntryReference> roomRefs = Arrays.asList(
                PoliciesModelFactory.newLocalEntryReference(managerLabel),
                PoliciesModelFactory.newEntryReference(templateId, roomAccessLabel));
        final List<EntryReference> buildingRefs = Arrays.asList(
                PoliciesModelFactory.newLocalEntryReference(managerLabel),
                PoliciesModelFactory.newEntryReference(templateId, buildingAccessLabel));
        final PolicyEntry mainRoom = PoliciesModelFactory.newPolicyEntry(roomAccessLabel,
                PoliciesModelFactory.emptySubjects(),
                PoliciesModelFactory.emptyResources(),
                null, ImportableType.IMPLICIT, null, roomRefs);
        final PolicyEntry mainBuilding = PoliciesModelFactory.newPolicyEntry(buildingAccessLabel,
                PoliciesModelFactory.emptySubjects(),
                PoliciesModelFactory.emptyResources(),
                null, ImportableType.IMPLICIT, null, buildingRefs);
        final Policy main = PoliciesModelFactory.newPolicyBuilder(mainId)
                .set(mainViewer)
                .set(mainManager)
                .set(mainRoom)
                .set(mainBuilding)
                .setPolicyImports(PolicyImports.newInstance(PoliciesModelFactory.newPolicyImport(
                        templateId, (EffectedImports) null)))
                .build();

        // --- leaf: imports main with empty entries list + transitiveImports=[template], no own entries. ---
        final EffectedImports leafImport = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.emptyList(), Collections.singletonList(templateId));
        final Policy leaf = PoliciesModelFactory.newPolicyBuilder(leafId)
                .setPolicyImports(PolicyImports.newInstance(
                        PoliciesModelFactory.newPolicyImport(mainId, leafImport)))
                .build();

        final JsonObject filteredEntries = runResolvedView(leaf,
                policyResolver(leaf, main, template), emptyNamespacePoliciesConfig());

        // Per-resource entries from main appear under imported-<mainId>-<label> with references resolved:
        // manager's subject from the local reference + template's resources from the import reference.
        // Resource keys ("thing:/features/room") contain slashes that JsonPointer interprets as path
        // separators, so navigate level by level via getField rather than dotted pointer paths.
        final String mainPrefixedRoom = "imported-" + mainId + "-" + roomAccessLabel;
        assertThat(filteredEntries.getField(mainPrefixedRoom))
                .as("resolved leaf must expose main's ROOM_ACCESS under the imported-<mainId>- prefix")
                .isPresent();
        final JsonObject roomEntry = filteredEntries.getField(mainPrefixedRoom)
                .orElseThrow().getValue().asObject();
        final JsonObject roomSubjects = roomEntry.getField("subjects").orElseThrow().getValue().asObject();
        assertThat(roomSubjects.getField(managerSubjectId.toString()))
                .as("ROOM_ACCESS subject must be merged in from the local reference to MANAGER")
                .isPresent();
        // Resource keys carry '/' (e.g. "thing:/features/room"), which both getValue() and getField()
        // would parse as JsonPointer separators, so check membership via the literal key list instead.
        final List<String> roomResourceKeys = roomEntry.getField("resources").orElseThrow().getValue().asObject()
                .getKeys().stream().map(JsonKey::toString).toList();
        assertThat(roomResourceKeys)
                .as("ROOM_ACCESS must inherit template's resources")
                .contains("thing:/features/room", "message:/features/room/inbox");

        final String mainPrefixedBuilding = "imported-" + mainId + "-" + buildingAccessLabel;
        final JsonObject buildingEntry = filteredEntries.getField(mainPrefixedBuilding)
                .orElseThrow().getValue().asObject();
        final JsonObject buildingSubjects = buildingEntry.getField("subjects").orElseThrow().getValue().asObject();
        assertThat(buildingSubjects.getField(managerSubjectId.toString())).isPresent();
        final List<String> buildingResourceKeys = buildingEntry.getField("resources").orElseThrow().getValue()
                .asObject().getKeys().stream().map(JsonKey::toString).toList();
        assertThat(buildingResourceKeys).contains("thing:/features/building");

        // Bare template entries land transitively under imported-<mainId>-imported-<templateId>-<label>.
        // They carry the template's resources but no subjects (no contribution to enforcement).
        final String mainPrefixedTemplateRoom = "imported-" + mainId + "-imported-" + templateId
                + "-" + roomAccessLabel;
        assertThat(filteredEntries.getField(mainPrefixedTemplateRoom))
                .as("template's ROOM_ACCESS must be present as a transitive nested-prefix entry")
                .isPresent();

        // The EXPLICIT MANAGER entry is not selected (leaf's import has entries:[]), so it must not appear.
        final String mainPrefixedManager = "imported-" + mainId + "-" + managerLabel;
        assertThat(filteredEntries.getField(mainPrefixedManager))
                .as("EXPLICIT MANAGER entry must not leak through implicit-only import")
                .isNotPresent();

        // Source-side READ filter must NOT have dropped the imported entries — the viewer has READ on
        // policy:/ via main's VIEWER entry, which passes hasSourceSideRead() for every entry in main.
        assertThat(filteredEntries.toString())
                .as("manager subject must be exposed because viewer has source-side READ on main")
                .contains(managerSubjectId.toString());
    }

    /**
     * Regression test for the production symptom captured in {@code /tmp/cit-rg-resolved-{before,after}.json}:
     * a leaf policy with seven imports — one of which uses {@code transitiveImports} into a "main" policy that
     * holds the manager subject in an {@code importable=explicit} entry and exposes resources only via
     * {@code importable=implicit} entries with local + import {@code references} — was returning a resolved
     * view that contained entries from six of the seven imports but was missing every entry contributed by the
     * {@code main} import.
     *
     * <p>Root cause: {@link PolicyCommandEnforcement#resolveSourceEnforcer} built each source enforcer with
     * {@link PolicyEnforcer#withResolvedImports} — without namespace-root policies. The source-side READ filter
     * inside {@link PolicyCommandEnforcement#mergeAndDropUnreadable} therefore evaluated the caller's
     * {@code policy:/entries/&lt;label&gt;} READ against the source's own subjects only. A caller whose READ on
     * main comes solely from the operator-configured namespace-root (e.g. a global devops admin) failed
     * {@code hasSourceSideRead} for every entry in main, and every {@code imported-&lt;mainId&gt;-…} entry was
     * stripped. Fix: source enforcers are now built with
     * {@link PolicyEnforcer#withResolvedImportsAndNamespacePolicies} so namespace-root subjects participate in
     * the source-side READ evaluation the same way they do in the importing-policy evaluation. The two existing
     * tests in this class pin the security invariant — a caller with no legitimate path to the source still
     * gets the entries dropped.</p>
     */
    @Test
    public void resolvedViewExposesImportedEntriesWhenCallerHasReadOnSourceViaNamespaceRoot() throws Exception {
        // Caller has no direct subject in 'main'; their access on main comes only from the namespace-root policy.
        // The leafId reused from IMPORTING_ID stays in the same "test.nsleak" namespace as main, so both are
        // covered by the same namespace-root binding below.
        final PolicyId leafId = IMPORTING_ID;
        final PolicyId mainId = PolicyId.of("test.nsleak", "main");
        final PolicyId templateId = PolicyId.of("test.nsleak", "template");
        final PolicyId nsRootId = PolicyId.of("global", "admin-access");

        final Label managerLabel = Label.of("MANAGER");
        final Label roomAccessLabel = Label.of("ROOM_ACCESS");
        final Label devopsLabel = Label.of("DEVOPS_ADMIN");
        final ResourceKey roomThing = ResourceKey.newInstance("thing", JsonPointer.of("features/room"));

        // --- namespace-root policy: grants the caller policy:/ READ+WRITE across the namespace. ---
        final SubjectId devopsId = SubjectId.newInstance(SubjectIssuer.GOOGLE, "devops-admin");
        final Policy nsRoot = Policy.newBuilder(nsRootId)
                .forLabel(devopsLabel)
                .setSubject(Subject.newInstance(devopsId))
                .setGrantedPermissions(POLICY_ROOT, Permission.READ, Permission.WRITE)
                .setGrantedPermissions(THING_ROOT, Permission.READ, Permission.WRITE)
                .setImportable(ImportableType.IMPLICIT)
                .build();

        // --- template: IMPLICIT access entry with resources, no subjects, allowedAdditions=[SUBJECTS]. ---
        final PolicyEntry templateRoom = PoliciesModelFactory.newPolicyEntry(roomAccessLabel,
                PoliciesModelFactory.emptySubjects(),
                Resources.newInstance(Resource.newInstance(roomThing, EffectedPermissions.newInstance(
                        Permissions.newInstance(Permission.READ), Permissions.none()))),
                null, ImportableType.IMPLICIT,
                Collections.singleton(AllowedAddition.SUBJECTS), null);
        final Policy template = PoliciesModelFactory.newPolicyBuilder(templateId)
                .set(templateRoom)
                .build();

        // --- main: imports template; manager subject lives in an EXPLICIT entry (never pulled into leaf via
        //     entries:[], but referenced locally by ROOM_ACCESS); resource access lives in an IMPLICIT entry
        //     with no own subjects/resources and only references. Crucially, NO entry in main grants the caller
        //     any policy:/ permission — the caller's source-side READ depends entirely on the namespace-root
        //     policy that the current resolveSourceEnforcer fails to merge in. ---
        final SubjectId managerId = SubjectId.newInstance(SubjectIssuer.GOOGLE, "manager");
        final PolicyEntry mainManager = PoliciesModelFactory.newPolicyEntry(managerLabel,
                Subjects.newInstance(Subject.newInstance(managerId)),
                PoliciesModelFactory.emptyResources(),
                ImportableType.EXPLICIT);
        final List<EntryReference> roomRefs = Arrays.asList(
                PoliciesModelFactory.newLocalEntryReference(managerLabel),
                PoliciesModelFactory.newEntryReference(templateId, roomAccessLabel));
        final PolicyEntry mainRoom = PoliciesModelFactory.newPolicyEntry(roomAccessLabel,
                PoliciesModelFactory.emptySubjects(),
                PoliciesModelFactory.emptyResources(),
                null, ImportableType.IMPLICIT, null, roomRefs);
        final Policy main = PoliciesModelFactory.newPolicyBuilder(mainId)
                .set(mainManager)
                .set(mainRoom)
                .setPolicyImports(PolicyImports.newInstance(PoliciesModelFactory.newPolicyImport(
                        templateId, (EffectedImports) null)))
                .build();

        // --- leaf: imports main with empty entries list + transitiveImports=[template], no own entries.
        //     The caller's only avenue to READ leaf is via the namespace-root binding configured below. ---
        final EffectedImports leafImport = PoliciesModelFactory.newEffectedImportedLabels(
                Collections.emptyList(), Collections.singletonList(templateId));
        final Policy leaf = PoliciesModelFactory.newPolicyBuilder(leafId)
                .setPolicyImports(PolicyImports.newInstance(
                        PoliciesModelFactory.newPolicyImport(mainId, leafImport)))
                .build();

        // --- Call as devops admin; bind the namespace test.nsleak to the namespace-root policy so the caller
        //     gets policy:/ READ+WRITE in leaf via the merged namespace entries. ---
        final DittoHeaders devopsHeaders = DittoHeaders.newBuilder()
                .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                        AuthorizationSubject.newInstance(devopsId)))
                .putHeader(DittoHeaderDefinition.POLICY_VIEW.getKey(), "resolved")
                .correlationId("ns-admin-source-side-read-bug")
                .build();
        final Function<PolicyId, CompletionStage<Optional<Policy>>> resolver =
                policyResolver(leaf, main, template, nsRoot);
        final NamespacePoliciesConfig nsConfig =
                namespacePoliciesConfigBinding("test.nsleak", nsRootId);
        final PolicyCommandEnforcement enforcement = new PolicyCommandEnforcement(resolver, nsConfig);
        final PolicyEnforcer importingEnforcer = PolicyEnforcer
                .withResolvedImportsAndNamespacePolicies(leaf, resolver, nsConfig)
                .toCompletableFuture().get();
        final RetrievePolicyResponse rawResponse = RetrievePolicyResponse.of(leafId, leaf.toJson(), devopsHeaders);

        final PolicyCommandResponse<?> filtered = enforcement.filterResponse(rawResponse, importingEnforcer)
                .toCompletableFuture().get();
        final JsonObject filteredEntries = ((RetrievePolicyResponse) filtered).getEntity()
                .asObject().getValue("entries").orElseThrow().asObject();

        // The namespace-root entry must appear (the caller can read leaf at all only via this entry).
        final String nsImportedDevops = "nsimported-" + nsRootId + "-" + devopsLabel;
        assertThat(filteredEntries.getField(nsImportedDevops))
                .as("namespace-root admin entry must be exposed in the resolved view")
                .isPresent();

        // EXPECTED (currently failing): main's IMPLICIT ROOM_ACCESS — with manager subject merged from the local
        // reference and template's resource merged from the import reference — must appear in the resolved view.
        // ACTUAL: stripped, because resolveSourceEnforcer builds main without namespace-policy merge so the caller's
        // namespace-root-granted READ is invisible to hasSourceSideRead.
        final String mainPrefixedRoom = "imported-" + mainId + "-" + roomAccessLabel;
        assertThat(filteredEntries.getField(mainPrefixedRoom))
                .as("main's IMPLICIT ROOM_ACCESS must be exposed — caller has READ on every policy in the " +
                        "namespace via the namespace-root admin entry, so the source-side READ filter " +
                        "must NOT drop main's contributed entries")
                .isPresent();
        final JsonObject roomEntry = filteredEntries.getField(mainPrefixedRoom)
                .orElseThrow().getValue().asObject();
        final JsonObject roomSubjects = roomEntry.getField("subjects").orElseThrow().getValue().asObject();
        assertThat(roomSubjects.getField(managerId.toString()))
                .as("manager subject must be merged in from main's local reference to MANAGER")
                .isPresent();
        final List<String> roomResourceKeys = roomEntry.getField("resources").orElseThrow().getValue().asObject()
                .getKeys().stream().map(JsonKey::toString).toList();
        assertThat(roomResourceKeys)
                .as("template's resource must be merged in via the import reference")
                .contains("thing:/features/room");
    }

    /**
     * Drives {@link PolicyCommandEnforcement#filterResponse} end-to-end for a resolved-view request and returns the
     * filtered {@code entries} JSON object so each test can assert on it. The importing-policy enforcer passed to
     * {@code filterResponse} is built via {@link PolicyEnforcer#withResolvedImportsAndNamespacePolicies} with the
     * same resolver + namespace config the production cache loader uses, mirroring runtime behaviour exactly.
     */
    private static JsonObject runResolvedView(final Policy importing,
            final Function<PolicyId, CompletionStage<Optional<Policy>>> resolver,
            final NamespacePoliciesConfig namespacePoliciesConfig) throws Exception {
        final PolicyCommandEnforcement enforcement =
                new PolicyCommandEnforcement(resolver, namespacePoliciesConfig);
        final PolicyEnforcer importingEnforcer = PolicyEnforcer
                .withResolvedImportsAndNamespacePolicies(importing, resolver, namespacePoliciesConfig)
                .toCompletableFuture().get();
        final RetrievePolicyResponse rawResponse = RetrievePolicyResponse.of(IMPORTING_ID,
                importing.toJson(), RESOLVED_VIEW_HEADERS);

        final PolicyCommandResponse<?> filtered = enforcement.filterResponse(rawResponse, importingEnforcer)
                .toCompletableFuture().get();

        final RetrievePolicyResponse retrieveResp = (RetrievePolicyResponse) filtered;
        return retrieveResp.getEntity().asObject().getValue("entries").orElseThrow().asObject();
    }

    private static Function<PolicyId, CompletionStage<Optional<Policy>>> policyResolver(final Policy... policies) {
        return id -> {
            for (final Policy policy : policies) {
                if (policy.getEntityId().filter(pid -> pid.equals(id)).isPresent()) {
                    return CompletableFuture.completedFuture(Optional.of(policy));
                }
            }
            return CompletableFuture.completedFuture(Optional.empty());
        };
    }

    private static NamespacePoliciesConfig emptyNamespacePoliciesConfig() {
        final NamespacePoliciesConfig config = Mockito.mock(NamespacePoliciesConfig.class);
        when(config.isEmpty()).thenReturn(true);
        when(config.getNamespacePolicies()).thenReturn(Collections.emptyMap());
        when(config.getRootPoliciesForNamespace(Mockito.anyString())).thenReturn(Collections.emptyList());
        when(config.getAllNamespaceRootPolicyIds()).thenReturn(Collections.emptySet());
        when(config.getNamespacesForRootPolicy(Mockito.any())).thenReturn(Collections.emptySet());
        return config;
    }

    private static NamespacePoliciesConfig namespacePoliciesConfigBinding(final String namespace,
            final PolicyId nsRootPolicyId) {
        final NamespacePoliciesConfig config = Mockito.mock(NamespacePoliciesConfig.class);
        when(config.isEmpty()).thenReturn(false);
        when(config.getRootPoliciesForNamespace(namespace)).thenReturn(List.of(nsRootPolicyId));
        when(config.getRootPoliciesForNamespace(Mockito.argThat(ns -> !namespace.equals(ns))))
                .thenReturn(Collections.emptyList());
        when(config.getAllNamespaceRootPolicyIds()).thenReturn(Collections.singleton(nsRootPolicyId));
        return config;
    }
}
