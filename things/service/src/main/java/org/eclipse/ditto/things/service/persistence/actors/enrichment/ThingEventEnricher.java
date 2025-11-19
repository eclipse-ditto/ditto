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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import org.apache.pekko.japi.Pair;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.exceptions.InvalidRqlExpressionException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLogger;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonArrayBuilder;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.placeholders.HeadersPlaceholder;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.placeholders.TimePlaceholder;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcerProvider;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.rql.parser.RqlPredicateParser;
import org.eclipse.ditto.rql.query.filter.QueryFilterCriteriaFactory;
import org.eclipse.ditto.rql.query.things.ThingPredicateVisitor;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.service.common.config.PreDefinedExtraFieldsConfig;
import org.eclipse.ditto.things.service.utils.PartialAccessPathCalculator;

/**
 * Encapsulates functionality in order to enrich ThingEvents with pre-defined {@code extraFields} via DittoHeaders
 * (fields defined per namespace in the Ditto things configuration) and with partial access paths for subjects
 * with restricted READ permissions.
 */
public final class ThingEventEnricher {

    private static final DittoLogger LOGGER = DittoLoggerFactory.getLogger(ThingEventEnricher.class);

    private static final TimePlaceholder TIME_PLACEHOLDER = TimePlaceholder.getInstance();
    private static final HeadersPlaceholder HEADERS_PLACEHOLDER = PlaceholderFactory.newHeadersPlaceholder();

    private static final Map<String, Predicate<Thing>> RQL_PREDICATE_CACHE = new ConcurrentHashMap<>(16);

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
        final CompletionStage<T> partialAccessPathsStage;
        if (null != thing && withDittoHeaders instanceof ThingEvent<?> thingEvent) {
            partialAccessPathsStage = enrichWithPartialAccessPaths(thingEvent, thing, policyId)
                    .thenApply(partialAccessPathsJson -> {
                        final DittoHeaders currentHeaders = withDittoHeaders.getDittoHeaders();
                        final String partialAccessPathsKey = DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey();
                        if (currentHeaders.containsKey(partialAccessPathsKey)) {
                            LOGGER.warn("Overwriting existing {} header", partialAccessPathsKey);
                        }
                        return withDittoHeaders.setDittoHeaders(
                                currentHeaders.toBuilder()
                                        .putHeader(partialAccessPathsKey, partialAccessPathsJson.toString())
                                        .build());
                    });
        } else {
            partialAccessPathsStage = CompletableFuture.completedStage(withDittoHeaders);
        }

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
                            .thenApply(result -> {
                                final DittoHeaders currentHeaders = enrichedWithPartialAccessPaths.getDittoHeaders();
                                
                                final String extraFieldsKey = DittoHeaderDefinition.PRE_DEFINED_EXTRA_FIELDS.getKey();
                                final String readGrantKey = DittoHeaderDefinition.PRE_DEFINED_EXTRA_FIELDS_READ_GRANT_OBJECT.getKey();
                                final String extraFieldsObjectKey = DittoHeaderDefinition.PRE_DEFINED_EXTRA_FIELDS_OBJECT.getKey();
                                
                                return enrichedWithPartialAccessPaths.setDittoHeaders(
                                        currentHeaders.toBuilder()
                                                .putHeader(extraFieldsKey,
                                                        buildPredefinedExtraFieldsHeaderList(combinedPredefinedExtraFields))
                                                .putHeader(readGrantKey, result)
                                                .putHeader(extraFieldsObjectKey,
                                                        buildPredefinedExtraFieldsHeaderObject(thing,
                                                                combinedPredefinedExtraFields).toString())
                                                .build());
                            })
            );
        } else {
            return partialAccessPathsStage;
        }
    }

    /**
     * Applies RQL condition from config, using cached compiled predicates for performance.
     */
    private Predicate<PreDefinedExtraFieldsConfig> applyPredefinedExtraFieldsCondition(
            final Thing thing,
            final WithDittoHeaders withDittoHeaders
    ) {
        return conf -> {
            if (conf.getCondition().isEmpty()) {
                return true;
            } else {
                final String rqlCondition = conf.getCondition().get();
                try {
                    final Predicate<Thing> predicate = RQL_PREDICATE_CACHE.computeIfAbsent(rqlCondition, rql -> {
                        try {
                            final var criteria = QueryFilterCriteriaFactory
                                    .modelBased(RqlPredicateParser.getInstance())
                                    .filterCriteria(rql, withDittoHeaders.getDittoHeaders());

                            return ThingPredicateVisitor.apply(
                                    criteria,
                                    PlaceholderFactory.newPlaceholderResolver(TIME_PLACEHOLDER, new Object()),
                                    PlaceholderFactory.newPlaceholderResolver(HEADERS_PLACEHOLDER,
                                            withDittoHeaders.getDittoHeaders())
                            );
                        } catch (final InvalidRqlExpressionException e) {
                            LOGGER.warn("Encountered invalid RQL condition <{}> for enriching " +
                                    "predefined extra fields: <{}>", rql, e.getMessage(), e);
                            return t -> true;
                        }
                    });
                    return predicate.test(thing);
                } catch (final Exception e) {
                    LOGGER.warn("Error evaluating RQL condition <{}>: {}", rqlCondition, e.getMessage(), e);
                    return true;
                }
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
     * Builds the read grant object with parallelized permission calculations for better performance.
     */
    private CompletionStage<String> buildPredefinedExtraFieldsHeaderReadGrantObject(
            @Nullable final PolicyId policyId,
            final JsonFieldSelector preDefinedExtraFields,
            final Thing thing
    ) {
        final CompletionStage<Optional<PolicyEnforcer>> policyEnforcerStage = policyEnforcerProvider.getPolicyEnforcer(policyId);
        if (policyEnforcerStage == null) {
            LOGGER.warn("PolicyEnforcerProvider.getPolicyEnforcer returned null for policyId: {}, returning empty read grant object",
                    policyId);
            return CompletableFuture.completedFuture(JsonFactory.newObject().toString());
        }
        return policyEnforcerStage
                .thenCompose(policyEnforcerOpt -> {
                    if (policyEnforcerOpt.isEmpty()) {
                        return CompletableFuture.completedFuture("{}");
                    }
                    final PolicyEnforcer policyEnforcer = policyEnforcerOpt.get();

                    final List<JsonPointer> pointers = new ArrayList<>(preDefinedExtraFields.getPointers());
                    
                    if (pointers.isEmpty()) {
                        return CompletableFuture.completedFuture("{}");
                    }
                    
                    final List<CompletableFuture<Stream<JsonField>>> futures = pointers.stream()
                            .map(pointer -> CompletableFuture.supplyAsync(() ->
                                    buildReadGrantFieldsForPointer(pointer, thing, policyEnforcer, preDefinedExtraFields)
                            ))
                            .toList();
                    
                    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                            .thenApply(ignored -> {
                                final Map<JsonPointer, Set<String>> mergedFields = new HashMap<>();
                                
                                futures.stream()
                                        .flatMap(CompletableFuture::join)
                                        .forEach(field -> {
                                            final JsonPointer fieldKey = JsonPointer.of(field.getKey().toString());
                                            if (field.getValue().isArray()) {
                                                mergedFields.computeIfAbsent(fieldKey, k -> new LinkedHashSet<>())
                                                        .addAll(field.getValue().asArray().stream()
                                                                .filter(JsonValue::isString)
                                                                .map(JsonValue::asString)
                                                                .collect(Collectors.toSet()));
                                            }
                                        });
                                
                                final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();
                                mergedFields.forEach((pointer, subjectIds) -> {
                                    final JsonArrayBuilder arrayBuilder = JsonFactory.newArrayBuilder();
                                    subjectIds.forEach(arrayBuilder::add);
                                    builder.set(pointer.toString(), arrayBuilder.build());
                                });
                                
                                return builder.build().toString();
                            });
                });
    }

    /**
     * Extracted helper method to build read grant fields for a single pointer.
     * This reduces complexity of the main flatMap chain.
     */
    private static Stream<JsonField> buildReadGrantFieldsForPointer(
            final JsonPointer pointer,
            final Thing thing,
            final PolicyEnforcer policyEnforcer,
            final JsonFieldSelector preDefinedExtraFields
    ) {
        final Set<AuthorizationSubject> subjectsWithUnrestrictedPermission =
                policyEnforcer.getEnforcer().getSubjectsWithUnrestrictedPermission(
                        PoliciesResourceType.thingResource(pointer),
                        Permissions.newInstance(Permission.READ)
                );
        final Set<AuthorizationSubject> subjectsWithPartialPermission =
                policyEnforcer.getEnforcer().getSubjectsWithPartialPermission(
                        PoliciesResourceType.thingResource(pointer),
                        Permissions.newInstance(Permission.READ)
                );

        final JsonArray unrestrictedReadSubjects =
                subjectsWithUnrestrictedPermission
                        .stream()
                        .map(AuthorizationSubject::getId)
                        .map(JsonValue::of)
                        .collect(JsonCollectors.valuesToArray());
        final Stream<JsonField> simpleReadGrantStream = Stream.of(
                JsonField.newInstance(pointer.toString(), unrestrictedReadSubjects)
        );

        if (!subjectsWithPartialPermission.equals(subjectsWithUnrestrictedPermission)) {
            final Set<AuthorizationSubject> partialSubjects =
                    new HashSet<>(subjectsWithPartialPermission);
            partialSubjects.removeAll(subjectsWithUnrestrictedPermission);
            return Stream.concat(simpleReadGrantStream,
                    calculatePartialReadFieldsAndSubjects(preDefinedExtraFields,
                            thing, policyEnforcer, partialSubjects
                    )
            );
        } else {
            return simpleReadGrantStream;
        }
    }

    private static Stream<JsonField> calculatePartialReadFieldsAndSubjects(
            final JsonFieldSelector preDefinedExtraFields,
            final Thing thing,
            final PolicyEnforcer policyEnforcer,
            final Set<AuthorizationSubject> partialSubjects
    ) {
        final JsonObject predefinedFieldsObject = buildPredefinedExtraFieldsHeaderObject(thing, preDefinedExtraFields);
        
        return partialSubjects.stream()
                .map(partialReadSubject ->
                        Pair.create(partialReadSubject, policyEnforcer.getEnforcer()
                                .buildJsonView(
                                        predefinedFieldsObject,
                                        "thing",
                                        AuthorizationContext.newInstance(
                                                DittoAuthorizationContextType.UNSPECIFIED,
                                                partialReadSubject
                                        ),
                                        Permission.READ
                                )
                        )
                )
                .flatMap(pair -> pair.second().stream()
                        .flatMap(field -> collectFields(pair.first(), field, JsonPointer.empty()))
                );
    }

    private static Stream<JsonField> collectFields(final AuthorizationSubject authorizationSubject,
            final JsonField field,
            final JsonPointer prefix
    ) {
        if (field.getValue().isObject()) {
            return field.getValue().asObject().stream()
                    .flatMap(subField ->
                            collectFields(authorizationSubject, subField, prefix.append(field.getKey().asPointer())) // recurse!
                    );
        } else {
            return Stream.of(
                    JsonField.newInstance(prefix.addLeaf(field.getKey()),
                            JsonArray.newBuilder().add(authorizationSubject.getId()).build()
                    )
            );
        }
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
        final CompletionStage<Optional<PolicyEnforcer>> policyEnforcerStage = policyEnforcerProvider.getPolicyEnforcer(policyId);
        if (policyEnforcerStage == null) {
            LOGGER.error("BUG: PolicyEnforcerProvider returned null CompletionStage for policyId: {}", policyId);
            return CompletableFuture.completedFuture(JsonFactory.newObject());
        }
        return policyEnforcerStage
                .thenApply(policyEnforcerOpt -> {
                    if (policyEnforcerOpt.isEmpty()) {
                        LOGGER.warn("No policy enforcer found for policyId: {}, returning empty partial access paths",
                                policyId);
                        return JsonFactory.newObject();
                    }
                    final Map<String, List<JsonPointer>> partialAccessPaths =
                            PartialAccessPathCalculator.calculatePartialAccessPaths(
                                    thingEvent, thing, policyEnforcerOpt.get());
                    final JsonObject result = PartialAccessPathCalculator.toJsonObject(partialAccessPaths);
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
