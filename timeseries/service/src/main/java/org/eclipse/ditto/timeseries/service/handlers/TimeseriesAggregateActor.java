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
package org.eclipse.ditto.timeseries.service.handlers;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.Status;
import org.apache.pekko.japi.pf.ReceiveBuilder;
import org.apache.pekko.pattern.Patterns;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.pekko.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcerProvider;
import org.eclipse.ditto.policies.enforcement.config.NamespacePoliciesConfig;
import org.eclipse.ditto.policies.model.ImportableType;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.policies.model.enforcers.PolicyEnforcers;
import org.eclipse.ditto.rql.model.ParserException;
import org.eclipse.ditto.rql.model.predicates.ast.RootNode;
import org.eclipse.ditto.rql.parser.RqlPredicateParser;
import org.eclipse.ditto.rql.query.things.FieldNamesPredicateVisitor;
import org.eclipse.ditto.timeseries.model.GroupBy;
import org.eclipse.ditto.things.api.Permission;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.timeseries.api.TimeseriesAdapter;
import org.eclipse.ditto.timeseries.api.TimeseriesMessagingConstants;
import org.eclipse.ditto.timeseries.model.CrossThingTimeseriesQuery;
import org.eclipse.ditto.timeseries.model.TimeseriesAggregationForbiddenException;
import org.eclipse.ditto.timeseries.model.TimeseriesQueryInvalidException;
import org.eclipse.ditto.timeseries.model.signals.commands.RetrieveAggregatedTimeseries;
import org.eclipse.ditto.timeseries.model.signals.commands.RetrieveAggregatedTimeseriesResponse;

/**
 * Handles {@link RetrieveAggregatedTimeseries} — timeseries aggregations spanning many Things of one
 * namespace.
 *
 * <h2>Why this is not the per-Thing shard entity</h2>
 * {@link org.eclipse.ditto.timeseries.model.signals.commands.RetrieveTimeseries} is routed through
 * the timeseries shard region keyed on its {@code thingId}. A cross-Thing query has no Thing to key
 * on, so it is delivered to this per-node actor by path via pub/sub — the same shape thing-search
 * uses for its query commands. Registering a well-known path rather than inventing a synthetic
 * entity ID keeps the shard region meaning exactly one thing: per-Thing work.
 *
 * <h2>Enforcement: live, and never read from storage</h2>
 * Authorization is a statement about <em>now</em>, while data points are historical. A subject
 * granted access today must see history ingested before the grant, and a revoked subject must
 * immediately lose history it could previously read. Any snapshot of grants written alongside the
 * data points would be wrong in at least one of those directions, and — because the store is
 * append-only — there would be no reindex path to repair it. So nothing about authorization is
 * stored: every request is decided against current policy state.
 *
 * <h2>Two stages, and why both are needed</h2>
 * <ol>
 *   <li><b>The gate.</b> The subject must hold the required permission <em>namespace-wide</em>,
 *   via a {@linkplain NamespacePoliciesConfig namespace root policy}. One policy, one check. Without
 *   it the request is rejected with {@link TimeseriesAggregationForbiddenException} — which also
 *   bounds the work an unauthorized caller can provoke, since nothing downstream runs.
 *   <p>
 *   A caller whose grants are instead scattered across individual Thing policies is rejected here
 *   too. Serving them needs the readable Things enumerated from a live, policy-aware source
 *   (thing-search, whose grants are reindexed on policy change); until then a 403 beats silently
 *   returning a partial answer.</li>
 *   <li><b>Per-Thing, per-path narrowing.</b> Passing the gate does <em>not</em> mean every Thing
 *   grants: root entries are merged <b>additively</b>, so a Thing's own policy can still revoke, and
 *   a revoke wins. So every Thing that has matching data is verified against its own live policy,
 *   per requested path.</li>
 * </ol>
 * <b>This costs one {@code SudoRetrieveThing} plus one (cached) enforcer load per contributing
 * Thing, on every request.</b> There is no O(1) shortcut: the additive merge above is precisely why
 * checking the root policy alone cannot settle the namespace. That per-Thing cost is what
 * {@code ditto.timeseries.max-verified-things} bounds, and why exceeding it fails the request rather
 * than authorizing a truncated set.
 *
 * @since 4.0.0
 */
public final class TimeseriesAggregateActor extends AbstractActor {

    /**
     * Name of this actor.
     */
    public static final String ACTOR_NAME = TimeseriesMessagingConstants.AGGREGATE_ACTOR_NAME;

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(TimeseriesAggregateActor.class);

    /** Timeout for resolving a contributing Thing's policy id, mirroring TimeseriesIngestActor. */
    private static final Duration THING_LOOKUP_TIMEOUT = Duration.ofSeconds(10);

    /**
     * The only field the per-Thing authorization check reads. Keeps {@code SudoRetrieveThing} from
     * transferring whole Things when all that is wanted is which policy governs them.
     */
    private static final JsonFieldSelector POLICY_ID_SELECTOR =
            JsonFactory.newFieldSelector(Thing.JsonFields.POLICY_ID);

    /** Cap on how many Thing IDs a single log line will name. */
    private static final int LOGGED_THING_ID_LIMIT = 10;

    /** Applied when the operator configures no ceiling on Things authorized per request. */
    public static final int DEFAULT_MAX_VERIFIED_THINGS = 1_000;

    private final TimeseriesAdapter adapter;
    private final ActorRef thingsShardRegion;
    private final PolicyEnforcerProvider policyEnforcerProvider;
    private final NamespacePoliciesConfig namespacePoliciesConfig;
    private final int maxVerifiedThings;

    /**
     * Mirrors {@code TimeseriesIngestActor}: when {@code false} (the default, strict) the check
     * requires {@link Permission#READ_TS}; when {@code true} plain {@link Permission#READ} suffices.
     */
    private final boolean simplifiedReadPermission;

    private TimeseriesAggregateActor(final TimeseriesAdapter adapter,
            @Nullable final ActorRef thingsShardRegion,
            @Nullable final PolicyEnforcerProvider policyEnforcerProvider,
            final NamespacePoliciesConfig namespacePoliciesConfig,
            final boolean simplifiedReadPermission,
            final int maxVerifiedThings) {

        this.adapter = adapter;
        this.thingsShardRegion = thingsShardRegion;
        this.policyEnforcerProvider = policyEnforcerProvider;
        this.namespacePoliciesConfig = namespacePoliciesConfig;
        this.simplifiedReadPermission = simplifiedReadPermission;
        // No silent correction: a non-positive ceiling means "authorize nothing", and quietly
        // substituting 1000 would be the opposite of what the operator wrote. Validated here as well
        // as in TimeseriesRootActor so direct construction cannot bypass it.
        if (maxVerifiedThings <= 0) {
            throw new IllegalArgumentException(
                    "maxVerifiedThings must be positive, but was <" + maxVerifiedThings + ">.");
        }
        this.maxVerifiedThings = maxVerifiedThings;
    }

    /**
     * Creates {@link Props} for a fully-wired instance.
     *
     * @param adapter the configured timeseries backend.
     * @param policyEnforcerProvider provider of the (cached) policy enforcers.
     * @param namespacePoliciesConfig the namespace-root policy mapping.
     * @param simplifiedReadPermission whether plain {@code READ} suffices instead of {@code READ_TS}.
     * @return the Props.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static Props props(final TimeseriesAdapter adapter,
            final ActorRef thingsShardRegion,
            final PolicyEnforcerProvider policyEnforcerProvider,
            final NamespacePoliciesConfig namespacePoliciesConfig,
            final boolean simplifiedReadPermission,
            final int maxVerifiedThings) {

        checkNotNull(adapter, "adapter");
        checkNotNull(thingsShardRegion, "thingsShardRegion");
        checkNotNull(policyEnforcerProvider, "policyEnforcerProvider");
        checkNotNull(namespacePoliciesConfig, "namespacePoliciesConfig");
        return Props.create(TimeseriesAggregateActor.class,
                () -> new TimeseriesAggregateActor(adapter, thingsShardRegion, policyEnforcerProvider,
                        namespacePoliciesConfig, simplifiedReadPermission, maxVerifiedThings));
    }


    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(RetrieveAggregatedTimeseries.class, this::handleRetrieveAggregated)
                .matchAny(message -> {
                    LOGGER.warn("Received unknown message <{}>.", message);
                    unhandled(message);
                })
                .build();
    }

    private void handleRetrieveAggregated(final RetrieveAggregatedTimeseries command) {
        // Capture the sender before any async hop — getSender() is meaningless inside a callback.
        final ActorRef sender = getSender();
        Patterns.pipe(authorizeAndQuery(command), getContext().getDispatcher()).to(sender);
    }

    private CompletionStage<Object> authorizeAndQuery(final RetrieveAggregatedTimeseries command) {
        final CompletionStage<Object> stage;
        try {
            requireCrossThingCapability(command);
            // Gate on a namespace-wide grant, then narrow to the Things the caller may actually
            // read: the gate bounds the work an unauthorized caller can provoke, the narrowing is
            // what makes the answer correct.
            stage = authorizeFilterAndGroupByFields(command)
                    .thenCompose(ignored -> authorizeNamespaceWide(command))
                    .thenCompose(ignored -> resolvePermittedThings(command))
                    .thenCompose(d -> runQuery(command, d.allowedPerPath(),
                            d.contributingThings(), d.fullyExcludedThings(),
                            d.withheldByPath()));
        } catch (final DittoRuntimeException e) {
            return CompletableFuture.completedFuture(e.setDittoHeaders(command.getDittoHeaders()));
        }
        return stage.exceptionally(throwable -> toFailure(command, throwable));
    }

    private void requireCrossThingCapability(final RetrieveAggregatedTimeseries command) {
        if (!adapter.capabilities().supportsNativeCrossThingQuery()) {
            LOGGER.withCorrelationId(command.getDittoHeaders())
                    .info("Rejecting cross-Thing aggregation: backend <{}> does not advertise " +
                            "cross-Thing support.", adapter.getClass().getSimpleName());
            // No kernel fallback: computing the grouping portably would scan every matching series
            // into heap, the fan-out the guard rails exist to prevent.
            throw TimeseriesQueryInvalidException.newBuilder(
                            "The configured timeseries backend does not support cross-Thing " +
                                    "aggregation.")
                    .dittoHeaders(command.getDittoHeaders())
                    .build();
        }
    }

    /**
     * Gate: requires a namespace-wide grant from the namespace root policies before any storage is
     * touched. This is an <em>entry condition</em>, not the authorization decision — a caller with no
     * namespace-level standing is rejected here without provoking a discovery query or a single
     * policy load per Thing. Completes normally when every requested path is granted namespace-wide;
     * fails with {@link TimeseriesAggregationForbiddenException} otherwise.
     */

    /**
     * Authorization layer 1: the caller must hold {@code READ} on every Thing field the query
     * <em>selects or groups by</em>, independently of the {@code READ_TS} check on the requested paths.
     * <p>
     * Filtering by a tag discloses which points carry which value, and grouping by one discloses the
     * distinct values — so a subject who may not read {@code attributes/building} must not be able to
     * slice a timeseries by it. Tag keys are the Thing paths declared in the WoT model, which is
     * precisely what makes them checkable as policy resources.
     * <p>
     * Evaluated against the namespace root policy, the same enforcer the namespace-wide gate uses:
     * this is a statement about the caller's entitlement to the <em>dimension</em>, not about any one
     * Thing's data, and it runs before discovery so an unauthorized slice provokes no work.
     */
    private CompletionStage<Void> authorizeFilterAndGroupByFields(
            final RetrieveAggregatedTimeseries command) {

        final Set<String> fields = new LinkedHashSet<>();
        command.getQuery().getFilter().ifPresent(rql -> fields.addAll(filterFieldNames(rql, command)));
        for (final GroupBy dimension : command.getQuery().getGroupBy()) {
            if (dimension.getKind() == GroupBy.Kind.TAG) {
                dimension.getTagKey().ifPresent(fields::add);
            }
        }
        if (fields.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        final DittoHeaders headers = command.getDittoHeaders();
        final List<PolicyId> rootPolicyIds = namespacePoliciesConfig
                .getRootPoliciesForNamespace(command.getQuery().getNamespace());
        if (rootPolicyIds.isEmpty()) {
            return CompletableFuture.failedFuture(fieldsForbidden(fields, command));
        }
        return anyRootPolicyGrantsReadOnFields(rootPolicyIds, fields, command)
                .thenCompose(granted -> granted
                        ? CompletableFuture.<Void>completedFuture(null)
                        : CompletableFuture.<Void>failedFuture(fieldsForbidden(fields, command)));
    }

    private CompletionStage<Boolean> anyRootPolicyGrantsReadOnFields(final List<PolicyId> rootPolicyIds,
            final Set<String> fields, final RetrieveAggregatedTimeseries command) {

        final DittoHeaders headers = command.getDittoHeaders();
        final Permissions read = Permissions.newInstance(Permission.READ);
        CompletionStage<Boolean> combined = CompletableFuture.completedFuture(false);
        for (final PolicyId rootPolicyId : rootPolicyIds) {
            final CompletionStage<Boolean> check = policyEnforcerProvider.getPolicyEnforcer(rootPolicyId)
                    .thenApply(enforcerOpt -> enforcerOpt
                            .map(policyEnforcer -> grantsReadOnAllFields(policyEnforcer, fields,
                                    command.getQuery().getNamespace(), headers, read))
                            .orElse(false));
            combined = combined.thenCombine(check, (a, b) -> a || b);
        }
        return combined;
    }

    private static boolean grantsReadOnAllFields(final PolicyEnforcer policyEnforcer,
            final Set<String> fields, final String namespace, final DittoHeaders headers,
            final Permissions read) {

        if (policyEnforcer.getPolicy().isEmpty()) {
            return false;
        }
        final Enforcer enforcer = policyEnforcer.forNamespace(namespace).getEnforcer();
        for (final String field : fields) {
            final String pointer = field.startsWith("/") ? field : "/" + field;
            final ResourceKey resourceKey =
                    PoliciesResourceType.thingResource(JsonPointer.of(pointer));
            if (!enforcer.hasUnrestrictedPermissions(resourceKey, headers.getAuthorizationContext(),
                    read)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Extracts the field references from an RQL filter. Reuses the same visitor
     * {@code ThingCommandEnforcement} uses for the {@code condition} header, so a filter is authorized
     * the same way a condition is.
     */
    private static Set<String> filterFieldNames(final String rql,
            final RetrieveAggregatedTimeseries command) {

        final RootNode rootNode;
        try {
            rootNode = RqlPredicateParser.getInstance().parse(rql);
        } catch (final ParserException e) {
            throw TimeseriesQueryInvalidException
                    .newBuilder("The 'filter' is not a valid RQL predicate: " + e.getMessage())
                    .dittoHeaders(command.getDittoHeaders())
                    .build();
        }
        final FieldNamesPredicateVisitor visitor = FieldNamesPredicateVisitor.getNewInstance();
        visitor.visit(rootNode);
        return visitor.getFieldNames();
    }

    private static DittoRuntimeException fieldsForbidden(final Set<String> fields,
            final RetrieveAggregatedTimeseries command) {

        LOGGER.withCorrelationId(command.getDittoHeaders())
                .info("Subject denied 'READ' on one of the filter/groupBy fields {} of a cross-Thing " +
                        "aggregation on namespace <{}>.", fields, command.getQuery().getNamespace());
        return TimeseriesAggregationForbiddenException
                .forNamespace(command.getQuery().getNamespace(), Permission.READ)
                .dittoHeaders(command.getDittoHeaders())
                .build();
    }

    private CompletionStage<Void> authorizeNamespaceWide(final RetrieveAggregatedTimeseries command) {
        final CrossThingTimeseriesQuery query = command.getQuery();
        final String namespace = query.getNamespace();
        final DittoHeaders headers = command.getDittoHeaders();

        final List<PolicyId> rootPolicyIds =
                namespacePoliciesConfig.getRootPoliciesForNamespace(namespace);
        if (rootPolicyIds.isEmpty()) {
            LOGGER.withCorrelationId(headers)
                    .info("Rejecting cross-Thing aggregation on <{}>: no namespace root policy is " +
                            "configured for that namespace.", namespace);
            return CompletableFuture.failedFuture(
                    TimeseriesAggregationForbiddenException.forNamespace(namespace, requiredPermission())
                            .dittoHeaders(headers)
                            .build());
        }

        // Any single root policy granting every requested path passes the gate. Note this does NOT
        // establish that every Thing grants: the merge is additive, so a Thing's own policy can still
        // revoke — which resolvePermittedThings() below is what actually handles.
        return anyRootPolicyGrantsAllPaths(rootPolicyIds, query, headers)
                .thenCompose(granted -> {
                    if (granted) {
                        return CompletableFuture.completedFuture(null);
                    }
                    LOGGER.withCorrelationId(headers)
                            .info("Subject <{}> denied cross-Thing '{}' on namespace <{}> paths {}.",
                                    headers.getAuthorizationContext().getAuthorizationSubjectIds(),
                                    requiredPermission(), namespace, query.getPaths());
                    return CompletableFuture.failedFuture(
                            TimeseriesAggregationForbiddenException
                                    .forNamespace(namespace, requiredPermission())
                                    .dittoHeaders(headers)
                                    .build());
                });
    }

    private CompletionStage<Boolean> anyRootPolicyGrantsAllPaths(final List<PolicyId> rootPolicyIds,
            final CrossThingTimeseriesQuery query,
            final DittoHeaders headers) {

        final List<CompletionStage<Boolean>> checks = new ArrayList<>(rootPolicyIds.size());
        for (final PolicyId rootPolicyId : rootPolicyIds) {
            checks.add(policyEnforcerProvider.getPolicyEnforcer(rootPolicyId)
                    .thenApply(enforcerOpt -> enforcerOpt
                            .map(policyEnforcer -> implicitEntriesGrantAllPaths(policyEnforcer,
                                    rootPolicyId, query, headers))
                            .orElseGet(() -> {
                                LOGGER.withCorrelationId(headers)
                                        .warn("Namespace root policy <{}> could not be loaded; " +
                                                "treating it as granting nothing.", rootPolicyId);
                                return false;
                            })));
        }

        CompletionStage<Boolean> combined = CompletableFuture.completedFuture(false);
        for (final CompletionStage<Boolean> check : checks) {
            combined = combined.thenCombine(check, (a, b) -> a || b);
        }
        return combined;
    }

    /**
     * Evaluates the root policy's <em>implicit</em> entries only.
     * <p>
     * This is the crux of why checking one policy may stand in for the whole namespace:
     * {@code PolicyImporter.mergeImplicitNamespaceRootEntries} merges an entry into each Thing's
     * policy <b>only</b> when its {@code importable} type is {@link ImportableType#IMPLICIT}. An
     * {@code explicit} or {@code never} entry is never merged, so a grant it carries confers nothing
     * on any Thing in the namespace — evaluating the root policy's <em>full</em> enforcer would
     * therefore authorize an aggregation the per-Thing policies do not actually permit.
     * <p>
     * Fails closed: if the {@link Policy} itself is unavailable (a {@code PolicyEnforcer} built via
     * {@code embed(...)} carries only the pre-built {@link Enforcer}), importability cannot be
     * verified and the grant is not honoured.
     */
    private boolean implicitEntriesGrantAllPaths(final PolicyEnforcer policyEnforcer,
            final PolicyId rootPolicyId,
            final CrossThingTimeseriesQuery query,
            final DittoHeaders headers) {

        final Optional<Policy> policyOpt = policyEnforcer.getPolicy();
        if (policyOpt.isEmpty()) {
            LOGGER.withCorrelationId(headers)
                    .warn("Namespace root policy <{}> resolved to an enforcer without its Policy; " +
                            "cannot verify that its entries are implicitly importable, so treating " +
                            "it as granting nothing.", rootPolicyId);
            return false;
        }

        final List<PolicyEntry> implicitEntries = new ArrayList<>();
        for (final PolicyEntry entry : policyOpt.get()) {
            // Two independent conditions, and both are required:
            //  - IMPLICIT, because only implicit entries are merged into the namespace's Things;
            //  - appliesToNamespace, because an entry may be scoped to a subset of namespaces and
            //    PolicyImporter preserves that scoping when it injects the entry. Honouring a grant
            //    scoped to another namespace would authorize a read the single-Thing path denies.
            if (ImportableType.IMPLICIT.equals(entry.getImportableType())
                    && entry.appliesToNamespace(query.getNamespace())) {
                implicitEntries.add(entry);
            }
        }
        if (implicitEntries.isEmpty()) {
            LOGGER.withCorrelationId(headers)
                    .info("Namespace root policy <{}> has no implicitly-importable entries applying " +
                            "to namespace <{}>; it grants nothing namespace-wide there.",
                            rootPolicyId, query.getNamespace());
            return false;
        }

        // defaultEvaluator takes Iterable<PolicyEntry>; wrapping the list in a Policy only to have it
        // iterated straight back out adds a failure surface for nothing.
        return grantsAllPaths(PolicyEnforcers.defaultEvaluator(implicitEntries),
                headers.getAuthorizationContext(), query.getPaths());
    }

    /**
     * Narrows the namespace-wide gate to the Things the caller may actually read.
     * <p>
     * Passing the gate proves the namespace root <em>grants</em> the permission; it does not prove no
     * Thing <em>revokes</em> it. Namespace-root entries are merged additively into each Thing's
     * policy, and a local revoke beats an injected grant — so a Thing can be unreadable through the
     * single-Thing endpoint while its measurements would still shape an unfiltered aggregate. This
     * step closes that gap by discovering the Things that would contribute and checking each against
     * live policy before any value is aggregated.
     * <p>
     * Filtering afterwards is impossible: once values are folded into a bucket average, one Thing's
     * contribution cannot be subtracted back out.
     */
    private CompletionStage<AccessDecision> resolvePermittedThings(
            final RetrieveAggregatedTimeseries command) {

        final DittoHeaders headers = command.getDittoHeaders();
        final long discoveryStart = System.nanoTime();
        return adapter.discoverContributors(command.getQuery(), maxVerifiedThings)
                .thenCompose(contributorsPerPath -> {
                    final Set<ThingId> distinct = new LinkedHashSet<>();
                    contributorsPerPath.values().forEach(distinct::addAll);
                    LOGGER.withCorrelationId(headers)
                            .debug("Cross-Thing discovery on <{}> found {} distinct contributing " +
                                            "Thing(s) across {} path(s) in {}ms (ceiling {}).",
                                    command.getQuery().getNamespace(), distinct.size(),
                                    contributorsPerPath.size(), elapsedMs(discoveryStart),
                                    maxVerifiedThings);
                    if (distinct.size() > maxVerifiedThings) {
                        LOGGER.withCorrelationId(headers)
                                .warn("Rejecting cross-Thing aggregation on <{}>: it spans more than " +
                                                "the {} Things this service will authorize per request.",
                                        command.getQuery().getNamespace(), maxVerifiedThings);
                        // Verifying a truncated set would silently authorize whatever fell off the
                        // end. Fail loudly instead, as the group cap does.
                        return CompletableFuture.failedFuture(TimeseriesQueryInvalidException
                                .newBuilder("The query spans more than " + maxVerifiedThings +
                                        " Things, which exceeds the number this service will " +
                                        "authorize per request. Narrow it with 'filter' or a " +
                                        "shorter time range.")
                                .dittoHeaders(headers)
                                .build());
                    }
                    if (distinct.isEmpty()) {
                        return CompletableFuture.completedFuture(AccessDecision.nothingMatched());
                    }
                    return verifyEach(contributorsPerPath, distinct, command);
                });
    }

    /**
     * Resolves, for every discovered Thing, <em>which of the requested paths</em> it may be read on,
     * then intersects that with the paths it actually has data for.
     * <p>
     * Path-granular on purpose. {@code READ_TS} can be granted per property, so a Thing may be
     * readable for one requested path and withheld from another; excluding it from the whole query
     * would silently drop data the caller is entitled to. This mirrors how thing-search authorizes an
     * RQL predicate per field rather than per document.
     */
    private CompletionStage<AccessDecision> verifyEach(
            final Map<JsonPointer, List<ThingId>> contributorsPerPath,
            final Set<ThingId> distinct,
            final RetrieveAggregatedTimeseries command) {

        final long verifyStart = System.nanoTime();
        final List<CompletionStage<Map.Entry<ThingId, Set<JsonPointer>>>> checks =
                new ArrayList<>(distinct.size());
        for (final ThingId thingId : distinct) {
            checks.add(permittedPathsFor(thingId, command)
                    .thenApply(paths -> Map.entry(thingId, paths)));
        }

        CompletionStage<Map<ThingId, Set<JsonPointer>>> combined =
                CompletableFuture.completedFuture(new LinkedHashMap<>());
        for (final CompletionStage<Map.Entry<ThingId, Set<JsonPointer>>> check : checks) {
            combined = combined.thenCombine(check, (acc, entry) -> {
                acc.put(entry.getKey(), entry.getValue());
                return acc;
            });
        }

        return combined.thenApply(permittedPathsByThing -> {
            final Map<JsonPointer, Collection<ThingId>> allowed = new LinkedHashMap<>();
            final Map<JsonPointer, List<ThingId>> withheld = new LinkedHashMap<>();
            for (final Map.Entry<JsonPointer, List<ThingId>> entry : contributorsPerPath.entrySet()) {
                final JsonPointer path = entry.getKey();
                for (final ThingId thingId : entry.getValue()) {
                    if (permittedPathsByThing.getOrDefault(thingId, Set.of()).contains(path)) {
                        allowed.computeIfAbsent(path, k -> new ArrayList<>()).add(thingId);
                    } else {
                        withheld.computeIfAbsent(path, k -> new ArrayList<>()).add(thingId);
                    }
                }
            }

            final Set<ThingId> contributing = new LinkedHashSet<>();
            allowed.values().forEach(contributing::addAll);
            final Set<ThingId> fullyExcluded = new LinkedHashSet<>(distinct);
            fullyExcluded.removeAll(contributing);

            LOGGER.withCorrelationId(command.getDittoHeaders())
                    .debug("Verified {} contributing Thing(s) on <{}> in {}ms.",
                            distinct.size(), command.getQuery().getNamespace(),
                            elapsedMs(verifyStart));
            if (!withheld.isEmpty()) {
                // Name what was withheld, per path, at INFO: an operator asking "why is this average
                // lower than I expect?" needs the identities, and by the time they can enable DEBUG the
                // policy may already have changed.
                final List<String> perPath = new ArrayList<>();
                withheld.forEach((path, things) -> perPath.add(path + " -> " + abbreviate(things)));
                LOGGER.withCorrelationId(command.getDittoHeaders())
                        .info("Cross-Thing aggregation on <{}> withheld data for lack of '{}'; the " +
                                        "result is partial. Withheld per path: {}.",
                                command.getQuery().getNamespace(), requiredPermission(),
                                String.join("; ", perPath));
            }
            return AccessDecision.of(allowed, contributing.size(), fullyExcluded.size(),
                    countsByPath(withheld));
        });
    }

    /** Milliseconds since the given {@code System.nanoTime()} reading. */
    private static long elapsedMs(final long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /**
     * Renders a Thing-ID list for a log line, capping it so one pathological request cannot emit an
     * unbounded line.
     */
    private static String abbreviate(final List<ThingId> thingIds) {
        if (thingIds.size() <= LOGGED_THING_ID_LIMIT) {
            return thingIds.toString();
        }
        return thingIds.subList(0, LOGGED_THING_ID_LIMIT) + " …and " +
                (thingIds.size() - LOGGED_THING_ID_LIMIT) + " more";
    }

    private static Map<JsonPointer, Integer> countsByPath(final Map<JsonPointer, List<ThingId>> byPath) {
        final Map<JsonPointer, Integer> counts = new LinkedHashMap<>();
        byPath.forEach((path, things) -> counts.put(path, things.size()));
        return counts;
    }

    /**
     * Resolves the Thing's governing policy and checks the required permission on every requested
     * path against it. Any failure — Thing missing, no policy, enforcer unavailable — excludes the
     * Thing rather than including it: fail closed.
     */
    private CompletionStage<Set<JsonPointer>> permittedPathsFor(final ThingId thingId,
            final RetrieveAggregatedTimeseries command) {

        final DittoHeaders headers = command.getDittoHeaders();
        // Only the policyId is needed. Without a selector this ships every attribute and feature of
        // every contributing Thing across the wire, once per Thing per request, to read one field.
        final SudoRetrieveThing sudo =
                SudoRetrieveThing.of(thingId, POLICY_ID_SELECTOR, headers);
        return Patterns.ask(thingsShardRegion, sudo, THING_LOOKUP_TIMEOUT)
                .<Set<JsonPointer>>thenCompose(reply -> {
                    if (!(reply instanceof SudoRetrieveThingResponse response)) {
                        LOGGER.withCorrelationId(headers)
                                .warn("Could not resolve Thing <{}> while authorizing a cross-Thing " +
                                        "aggregation; excluding it. Reply was <{}>.", thingId, reply);
                        return CompletableFuture.completedFuture(Set.<JsonPointer>of());
                    }
                    final Optional<PolicyId> policyIdOpt = response.getThing().getPolicyId();
                    if (policyIdOpt.isEmpty()) {
                        LOGGER.withCorrelationId(headers)
                                .warn("Thing <{}> has no policyId; excluding it from the aggregation.",
                                        thingId);
                        return CompletableFuture.completedFuture(Set.<JsonPointer>of());
                    }
                    final PolicyId policyId = policyIdOpt.get();
                    return policyEnforcerProvider.getPolicyEnforcer(policyId)
                            .thenApply(enforcerOpt -> {
                                if (enforcerOpt.isEmpty()) {
                                    LOGGER.withCorrelationId(headers)
                                            .warn("Policy <{}> of Thing <{}> could not be loaded; " +
                                                    "excluding it from the aggregation.",
                                                    policyId, thingId);
                                    return Set.<JsonPointer>of();
                                }
                                final PolicyEnforcer policyEnforcer = enforcerOpt.get();
                                // Fail closed when the Policy itself is absent: forNamespace cannot
                                // filter what it cannot see, and returns `this` unfiltered in that
                                // case — which would honour a grant scoped to another namespace.
                                if (policyEnforcer.getPolicy().isEmpty()) {
                                    LOGGER.withCorrelationId(headers)
                                            .warn("Policy <{}> of Thing <{}> resolved to an enforcer " +
                                                    "without its Policy, so per-entry namespace " +
                                                    "scoping cannot be applied; excluding it.",
                                                    policyId, thingId);
                                    return Set.<JsonPointer>of();
                                }
                                final Set<JsonPointer> permitted = permittedPaths(
                                        policyEnforcer.forNamespace(thingId.getNamespace())
                                                .getEnforcer(),
                                        headers.getAuthorizationContext(),
                                        command.getQuery().getPaths());
                                if (permitted.size() < command.getQuery().getPaths().size()) {
                                    // The namespace root granted, but this Thing's own policy does not
                                    final List<JsonPointer> denied =
                                            new ArrayList<>(command.getQuery().getPaths());
                                    denied.removeAll(permitted);
                                    LOGGER.withCorrelationId(headers)
                                            .debug("Thing <{}> (policy <{}>): '{}' granted on {}, " +
                                                            "denied on {}.",
                                                    thingId, policyId, requiredPermission(),
                                                    permitted, denied);
                                }
                                return permitted;
                            });
                })
                .exceptionally(throwable -> {
                    LOGGER.withCorrelationId(headers)
                            .warn("Authorization check for Thing <{}> failed ({}); excluding it.",
                                    thingId, unwrap(throwable).getMessage());
                    return Set.<JsonPointer>of();
                });
    }

    /**
     * Outcome of the per-Thing, per-path narrowing.
     *
     * @param allowedPerPath the Things the caller may read, per requested path.
     * @param contributingThings distinct Things contributing to at least one path.
     * @param fullyExcludedThings Things withheld from <em>every</em> requested path.
     * @param withheldByPath per path, how many discovered Things were withheld from it.
     */
    private record AccessDecision(Map<JsonPointer, Collection<ThingId>> allowedPerPath,
                                  int contributingThings,
                                  int fullyExcludedThings,
                                  Map<JsonPointer, Integer> withheldByPath) {

        static AccessDecision of(final Map<JsonPointer, Collection<ThingId>> allowedPerPath,
                final int contributingThings, final int fullyExcludedThings,
                final Map<JsonPointer, Integer> withheldByPath) {
            return new AccessDecision(allowedPerPath, contributingThings, fullyExcludedThings,
                    withheldByPath);
        }

        /**
         * Nothing matched the query, so there is nothing to authorize and nothing to return.
         * <p>
         * An empty allow-list, never an "unrestricted" marker: the adapter short-circuits on it, so
         * no second query runs at all.
         */
        static AccessDecision nothingMatched() {
            return of(Map.of(), 0, 0, Map.of());
        }
    }

    /** The gate's all-or-nothing test: every requested path must be granted namespace-wide. */
    private boolean grantsAllPaths(final Enforcer enforcer,
            final AuthorizationContext authorizationContext,
            final List<JsonPointer> paths) {

        return permittedPaths(enforcer, authorizationContext, paths).size() == paths.size();
    }

    /**
     * Returns the subset of {@code paths} the subject may read.
     * <p>
     * {@code hasUnrestrictedPermissions} per path, not {@code hasPartialPermissions}: a timeseries path
     * resolves to a scalar leaf, so there is no sub-structure left to filter — either the whole value
     * is readable or none of it is, and a revoke deeper in the tree must still count. This computes
     * the same grant/revoke resolution that thing-search pushes into its per-field index filter.
     */
    private Set<JsonPointer> permittedPaths(final Enforcer enforcer,
            final AuthorizationContext authorizationContext,
            final List<JsonPointer> paths) {

        final Permissions required = Permissions.newInstance(requiredPermission());
        final Set<JsonPointer> permitted = new LinkedHashSet<>();
        for (final JsonPointer path : paths) {
            final ResourceKey resourceKey = PoliciesResourceType.thingResource(path);
            if (enforcer.hasUnrestrictedPermissions(resourceKey, authorizationContext, required)) {
                permitted.add(path);
            }
        }
        return permitted;
    }

    private String requiredPermission() {
        return simplifiedReadPermission ? Permission.READ : Permission.READ_TS;
    }

    /**
     * @param allowedPerPath the Things permitted per path. Always present: the adapter takes no
     * "unrestricted" sentinel, so reading a whole namespace means enumerating it.
     */
    private CompletionStage<Object> runQuery(final RetrieveAggregatedTimeseries command,
            final Map<JsonPointer, Collection<ThingId>> allowedPerPath,
            final int contributingThings,
            final int fullyExcludedThings,
            final Map<JsonPointer, Integer> withheldByPath) {

        final CrossThingTimeseriesQuery query = command.getQuery();
        LOGGER.withCorrelationId(command.getDittoHeaders())
                .debug("Running cross-Thing aggregation on <{}>: paths={}, step={}, agg={}, " +
                                "groupBy={}, filter={}, allowList={}, maxGroups={}.",
                        query.getNamespace(), query.getPaths(), query.getStep(),
                        query.getAggregation().getName(), query.getGroupBy(),
                        query.getFilter().orElse("-"),
                        allowedPerPath,
                        query.getMaxGroups().map(String::valueOf).orElse("<default>"));
        final long aggregateStart = System.nanoTime();
        return adapter.queryCrossThing(command.getQuery(), allowedPerPath)
                .thenApply(results -> {
                    LOGGER.withCorrelationId(command.getDittoHeaders())
                            .info("Cross-Thing aggregation on <{}> returned {} series from {} " +
                                            "Thing(s) in {}ms; {} fully excluded, withheldByPath={} " +
                                            "(partial={}).",
                                    command.getQuery().getNamespace(), results.size(),
                                    contributingThings, elapsedMs(aggregateStart),
                                    fullyExcludedThings, withheldByPath, !withheldByPath.isEmpty());
                    final Map<String, Integer> withheld = new LinkedHashMap<>();
                    withheldByPath.forEach((path, count) -> withheld.put(path.toString(), count));
                    return RetrieveAggregatedTimeseriesResponse.of(
                            command.getQuery().getNamespace(), results, contributingThings,
                            fullyExcludedThings, withheld, command.getDittoHeaders());
                });
    }

    private Object toFailure(final RetrieveAggregatedTimeseries command, final Throwable throwable) {
        final Throwable cause = unwrap(throwable);
        LOGGER.withCorrelationId(command.getDittoHeaders())
                .warn("RetrieveAggregatedTimeseries on namespace <{}> failed: {}",
                        command.getQuery().getNamespace(), cause.getMessage());
        if (cause instanceof DittoRuntimeException dre) {
            return dre.setDittoHeaders(command.getDittoHeaders());
        }
        return new Status.Failure(cause);
    }

    private static Throwable unwrap(@Nullable final Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable == null ? new IllegalStateException("Unknown failure") : throwable;
    }
}
