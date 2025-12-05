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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.InvalidRqlExpressionException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLogger;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.placeholders.HeadersPlaceholder;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.placeholders.TimePlaceholder;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcerProvider;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.rql.parser.RqlPredicateParser;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.filter.QueryFilterCriteriaFactory;
import org.eclipse.ditto.rql.query.things.ThingPredicateVisitor;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.service.common.config.PreDefinedExtraFieldsConfig;
import org.eclipse.ditto.things.service.utils.IndexedReadGrant;
import org.eclipse.ditto.things.service.utils.PartialAccessPathCalculator;
import org.eclipse.ditto.things.service.utils.ReadGrant;
import org.eclipse.ditto.things.service.utils.ReadGrantCollector;
import org.eclipse.ditto.things.service.utils.ReadGrantIndexer;

/**
 * Encapsulates functionality in order to enrich ThingEvents with pre-defined {@code extraFields} via DittoHeaders
 * (fields defined per namespace in the Ditto things configuration) and with partial access paths for subjects
 * with restricted READ permissions.
 */
public final class ThingEventEnricher {

    private static final DittoLogger LOGGER = DittoLoggerFactory.getLogger(ThingEventEnricher.class);

    private static final TimePlaceholder TIME_PLACEHOLDER = TimePlaceholder.getInstance();
    private static final HeadersPlaceholder HEADERS_PLACEHOLDER = PlaceholderFactory.newHeadersPlaceholder();

    private static final Map<String, Criteria> RQL_CRITERIA_CACHE = new ConcurrentHashMap<>(32);

    private final List<PreDefinedExtraFieldsConfig> preDefinedExtraFieldsConfigs;
    private final PolicyEnforcerProvider policyEnforcerProvider;

    /**
     * Constructs a new enricher for ThingEvents based on the provided configuration and policy enforcer.
     *
     * @param preDefinedExtraFieldsConfigs the list of config entries for pre-defined extraFields enrichment
     * @param policyEnforcerProvider the policy enforcer to use in order to check permissions for enriching extraFields
     */
    public ThingEventEnricher(
            final List<PreDefinedExtraFieldsConfig> preDefinedExtraFieldsConfigs,
            final PolicyEnforcerProvider policyEnforcerProvider
    ) {
        this.preDefinedExtraFieldsConfigs = List.copyOf(preDefinedExtraFieldsConfigs);
        this.policyEnforcerProvider = policyEnforcerProvider;
    }

    /**
     * Enriches the passed in {@code withDittoHeaders} with pre-defined extraFields based on the provided {@code thing}
     * and the global configuration this class holds (based on namespace and optional RQL condition).
     *
     * @param thingId the Thing ID to enrich for
     * @param thing the Thing entity to use for getting extra fields from
     * @param policyId the Policy ID to use for looking up permissions
     * @param withDittoHeaders the object to enrich with pre-defined extraFields (e.g. a Signal)
     * @param <T> the type of the signal to enrich
     * @return an enriched version of the passed in {@code withDittoHeaders} with pre-defined extraFields
     */
    public <T extends DittoHeadersSettable<? extends T>> CompletionStage<T> enrichWithPredefinedExtraFields(
            final ThingId thingId,
            @Nullable final Thing thing,
            @Nullable final PolicyId policyId,
            final T withDittoHeaders
    ) {
        final CompletionStage<T> partialAccessPathsStage =
                enrichWithPartialAccessPathsIfNecessary(withDittoHeaders, thing, policyId);

        if (null != thing && !preDefinedExtraFieldsConfigs.isEmpty()) {
            final List<PreDefinedExtraFieldsConfig> matchingPreDefinedFieldsConfigs =
                    preDefinedExtraFieldsConfigs.stream()
                            .filter(conf -> conf.getNamespace().isEmpty() ||
                                    conf.getNamespace()
                                            .stream()
                                            .anyMatch(pattern ->
                                                    pattern.matcher(thingId.getNamespace()).matches()
                                            )
                            )
                            .filter(applyPredefinedExtraFieldsCondition(thing, withDittoHeaders))
                            .toList();
            final JsonFieldSelector combinedPredefinedExtraFields = matchingPreDefinedFieldsConfigs.stream()
                    .map(PreDefinedExtraFieldsConfig::getExtraFields)
                    .reduce(JsonFactory.newFieldSelector(List.of()), (a, b) -> {
                        final Set<JsonPointer> combinedPointerSet = new LinkedHashSet<>(a.getPointers());
                        combinedPointerSet.addAll(b.getPointers());
                        return JsonFactory.newFieldSelector(combinedPointerSet);
                    });
            return partialAccessPathsStage.thenCompose(enrichedWithPartialAccessPaths ->
                    buildPredefinedExtraFieldsHeaderReadGrantObject(policyId, combinedPredefinedExtraFields, thing)
                            .thenApply(indexedGrants -> {
                                final DittoHeaders currentHeaders = enrichedWithPartialAccessPaths.getDittoHeaders();
                                
                                final String extraFieldsKey = DittoHeaderDefinition.PRE_DEFINED_EXTRA_FIELDS.getKey();
                                final String readGrantKey = DittoHeaderDefinition.PRE_DEFINED_EXTRA_FIELDS_READ_GRANT_OBJECT.getKey();
                                final String extraFieldsObjectKey = DittoHeaderDefinition.PRE_DEFINED_EXTRA_FIELDS_OBJECT.getKey();
                                
                                final DittoHeadersBuilder<?, ?> headersBuilder = currentHeaders.toBuilder()
                                        .putHeader(extraFieldsKey,
                                                buildPredefinedExtraFieldsHeaderList(combinedPredefinedExtraFields))
                                        .putHeader(readGrantKey, indexedGrants.pathsToJson().toString())
                                        .putHeader(extraFieldsObjectKey,
                                                buildPredefinedExtraFieldsHeaderObject(thing,
                                                        combinedPredefinedExtraFields).toString());
                                
                                return enrichedWithPartialAccessPaths.setDittoHeaders(headersBuilder.build());
                            })
            );
        } else {
            return partialAccessPathsStage;
        }
    }

    private <T extends DittoHeadersSettable<? extends T>> CompletionStage<T> enrichWithPartialAccessPathsIfNecessary(
            final T withDittoHeaders,
            @Nullable final Thing thing,
            @Nullable final PolicyId policyId) {

        if (thing == null || !(withDittoHeaders instanceof ThingEvent<?> thingEvent)) {
            return CompletableFuture.completedStage(withDittoHeaders);
        }

        return enrichWithPartialAccessPaths(thingEvent, thing, policyId)
                .thenApply(partialAccessPathsJson -> {
                    if (partialAccessPathsJson.isEmpty()) {
                        return withDittoHeaders;
                    }
                    final DittoHeaders currentHeaders = withDittoHeaders.getDittoHeaders();
                    final String partialAccessPathsKey = DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey();
                    if (currentHeaders.containsKey(partialAccessPathsKey)) {
                        LOGGER.warn("Overwriting existing {} header", partialAccessPathsKey);
                    }
                    final DittoHeaders updatedHeaders = currentHeaders.toBuilder()
                            .putHeader(partialAccessPathsKey, partialAccessPathsJson.toString())
                            .build();
                    return withDittoHeaders.setDittoHeaders(updatedHeaders);
                });
    }

    /**
     * Applies RQL condition from config, using cached parsed criteria for performance.
     * Caches only the Criteria (RQL AST), not the Predicate<Thing>, to allow building
     * fresh predicates with current headers for each event.
     */
    private Predicate<PreDefinedExtraFieldsConfig> applyPredefinedExtraFieldsCondition(
            final Thing thing,
            final WithDittoHeaders withDittoHeaders
    ) {
        return conf -> {
            final Optional<String> optCondition = conf.getCondition();
            if (optCondition.isEmpty()) {
                return true;
            }

            final String rqlCondition = optCondition.get();

            try {
                final Criteria criteria = RQL_CRITERIA_CACHE.computeIfAbsent(rqlCondition, rql -> {
                    try {
                        return QueryFilterCriteriaFactory
                                .modelBased(RqlPredicateParser.getInstance())
                                .filterCriteria(rql, DittoHeaders.empty());
                    } catch (final InvalidRqlExpressionException e) {
                        LOGGER.warn("Invalid RQL condition <{}> - ignoring: {}", rql, e.getMessage());
                        return null;
                    }
                });

                if (criteria == null) {
                    return true;
                }

                final Predicate<Thing> predicate =
                        ThingPredicateVisitor.apply(
                                criteria,
                                PlaceholderFactory.newPlaceholderResolver(TIME_PLACEHOLDER, new Object()),
                                PlaceholderFactory.newPlaceholderResolver(
                                        HEADERS_PLACEHOLDER,
                                        withDittoHeaders.getDittoHeaders()
                                )
                        );

                return predicate.test(thing);

            } catch (final Exception e) {
                LOGGER.warn(
                        "Error evaluating RQL condition <{}> for predefined extra fields - treating as match: {}",
                        rqlCondition, e.getMessage(), e
                );
                return true;
            }
        };
    }

    private static String buildPredefinedExtraFieldsHeaderList(final JsonFieldSelector preDefinedExtraFields) {
        return StreamSupport.stream(preDefinedExtraFields.spliterator(), false)
                .map(JsonPointer::toString)
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray())
                .toString();
    }

    /**
     * Safely gets the policy enforcer stage, handling null cases defensively.
     *
     * @param policyId the policy ID to get the enforcer for
     * @return a non-null CompletionStage, completed with empty Optional if the provider returns null
     */
    private CompletionStage<Optional<PolicyEnforcer>> getPolicyEnforcerSafely(@Nullable final PolicyId policyId) {
        final CompletionStage<Optional<PolicyEnforcer>> stage = policyEnforcerProvider.getPolicyEnforcer(policyId);
        if (stage == null) {
            LOGGER.error("PolicyEnforcerProvider returned null CompletionStage for policyId: {}", policyId);
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return stage;
    }

    /**
     * Builds the read grant object using the new helper classes.
     * Returns indexed format to reduce header size.
     */
    private CompletionStage<IndexedReadGrant> buildPredefinedExtraFieldsHeaderReadGrantObject(
            @Nullable final PolicyId policyId,
            final JsonFieldSelector preDefinedExtraFields,
            final Thing thing
    ) {
        return getPolicyEnforcerSafely(policyId).thenApply(policyEnforcerOpt -> {
            if (policyEnforcerOpt.isEmpty()) {
                LOGGER.warn("No policy enforcer found for policyId: {}, returning empty read grant object", policyId);
                return IndexedReadGrant.empty();
            }

            final PolicyEnforcer policyEnforcer = policyEnforcerOpt.get();

            final ReadGrant readGrant = ReadGrantCollector.collect(
                    preDefinedExtraFields,
                    thing,
                    policyEnforcer
            );

            return ReadGrantIndexer.index(readGrant);
        });
    }

    /**
     * Enriches a ThingEvent with partial access paths header.
     *
     * @param thingEvent the ThingEvent to enrich
     * @param thing the current Thing state
     * @param policyId the Policy ID
     * @return CompletionStage with the partial access paths JSON object
     */
    private CompletionStage<JsonObject> enrichWithPartialAccessPaths(
            final ThingEvent<?> thingEvent,
            final Thing thing,
            @Nullable final PolicyId policyId
    ) {
        LOGGER.debug("Enriching event '{}' (thingId: {}) with partial access paths, policyId: {}",
                thingEvent.getType(), thingEvent.getEntityId(), policyId);
        return getPolicyEnforcerSafely(policyId).thenApply(policyEnforcerOpt -> {
                    if (policyEnforcerOpt.isEmpty()) {
                        LOGGER.warn("No policy enforcer found for policyId: {}, returning empty partial access paths",
                                policyId);
                        return JsonFactory.newObject();
                    }
                    final Map<String, List<JsonPointer>> partialAccessPaths =
                            PartialAccessPathCalculator.calculatePartialAccessPaths(
                                    thingEvent, thing, policyEnforcerOpt.get());
                    final JsonObject result = PartialAccessPathCalculator.toIndexedJsonObject(partialAccessPaths);
                    LOGGER.debug("Calculated partial access paths for event '{}' (thingId: {}): {} subjects with partial access, result: {}",
                            thingEvent.getType(), thingEvent.getEntityId(), partialAccessPaths.size(), result);
                    return result;
                });
    }

    /**
     * Builds JSON object for predefined extra fields, optimized to avoid full thing.toJson() when possible.
     * For large Things, this reduces memory allocation significantly.
     */
    private static JsonObject buildPredefinedExtraFieldsHeaderObject(
            final Thing thing,
            final JsonFieldSelector preDefinedExtraFields
    ) {
        final JsonObjectBuilder builder = JsonObject.newBuilder();
        final JsonObject thingJson = thing.toJson();
        preDefinedExtraFields.getPointers().forEach(pointer ->
                thingJson.getValue(pointer).ifPresent(thingValue -> builder.set(pointer, thingValue))
        );
        return builder.build();
    }
}
