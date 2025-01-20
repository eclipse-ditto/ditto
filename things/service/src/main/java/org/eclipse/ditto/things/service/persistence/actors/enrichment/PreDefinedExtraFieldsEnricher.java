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

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import org.apache.pekko.japi.Pair;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.exceptions.InvalidRqlExpressionException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLogger;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.json.JsonArray;
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
import org.eclipse.ditto.things.service.common.config.PreDefinedExtraFieldsConfig;

/**
 * Encapsulates functionality in order to perform a "pre-defined" {@code extraFields} enrichment via DittoHeaders of
 * fields defined per namespace in the Ditto things configuration.
 */
public final class PreDefinedExtraFieldsEnricher {

    private static final DittoLogger LOGGER = DittoLoggerFactory.getLogger(PreDefinedExtraFieldsEnricher.class);

    private static final TimePlaceholder TIME_PLACEHOLDER = TimePlaceholder.getInstance();
    private static final HeadersPlaceholder HEADERS_PLACEHOLDER = PlaceholderFactory.newHeadersPlaceholder();

    private final List<PreDefinedExtraFieldsConfig> preDefinedExtraFieldsConfigs;
    private final PolicyEnforcerProvider policyEnforcerProvider;

    /**
     * Constructs a new enricher of pre-defined extraFields based on the provided configuration and policy enforcer.
     *
     * @param preDefinedExtraFieldsConfigs the list of config entries for pre-defined extraFields enrichment
     * @param policyEnforcerProvider the policy enforcer to use in order to check permissions for enriching extraFields
     */
    public PreDefinedExtraFieldsEnricher(
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
            return buildPredefinedExtraFieldsHeaderReadGrantObject(policyId, combinedPredefinedExtraFields, thing)
                    .thenApply(predefinedExtraFieldsHeaderReadGrantObject ->
                            withDittoHeaders.setDittoHeaders(withDittoHeaders.getDittoHeaders()
                                    .toBuilder()
                                    .putHeader(DittoHeaderDefinition.PRE_DEFINED_EXTRA_FIELDS.getKey(),
                                            buildPredefinedExtraFieldsHeaderList(combinedPredefinedExtraFields)
                                    )
                                    .putHeader(
                                            DittoHeaderDefinition.PRE_DEFINED_EXTRA_FIELDS_READ_GRANT_OBJECT.getKey(),
                                            predefinedExtraFieldsHeaderReadGrantObject
                                    )
                                    .putHeader(DittoHeaderDefinition.PRE_DEFINED_EXTRA_FIELDS_OBJECT.getKey(),
                                            buildPredefinedExtraFieldsHeaderObject(thing,
                                                    combinedPredefinedExtraFields).toString()
                                    )
                                    .build()
                            )
                    );
        } else {
            return CompletableFuture.completedStage(withDittoHeaders);
        }
    }

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
                    final var criteria = QueryFilterCriteriaFactory
                            .modelBased(RqlPredicateParser.getInstance())
                            .filterCriteria(rqlCondition, withDittoHeaders.getDittoHeaders());

                    final var predicate = ThingPredicateVisitor.apply(
                            criteria,
                            PlaceholderFactory.newPlaceholderResolver(TIME_PLACEHOLDER, new Object()),
                            PlaceholderFactory.newPlaceholderResolver(HEADERS_PLACEHOLDER,
                                    withDittoHeaders.getDittoHeaders())
                    );
                    return predicate.test(thing);
                } catch (final InvalidRqlExpressionException e) {
                    LOGGER.warn("Encountered invalid RQL condition <{}> for enriching " +
                            "predefined extra fields: <{}>", rqlCondition, e.getMessage(), e);
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

    private CompletionStage<String> buildPredefinedExtraFieldsHeaderReadGrantObject(
            @Nullable final PolicyId policyId,
            final JsonFieldSelector preDefinedExtraFields,
            final Thing thing
    ) {
        return policyEnforcerProvider.getPolicyEnforcer(policyId)
                .thenApply(policyEnforcerOpt ->
                        policyEnforcerOpt.map(policyEnforcer ->
                                StreamSupport.stream(preDefinedExtraFields.spliterator(), false)
                                        .flatMap(pointer -> {
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

                                            if (!subjectsWithPartialPermission
                                                    .equals(subjectsWithUnrestrictedPermission)
                                            ) {
                                                // we have subjects with only partial permissions, so need to "traverse" down
                                                //  in order to find them out and add them to the read grant object ..
                                                final Set<AuthorizationSubject> partialSubjects =
                                                        new HashSet<>(subjectsWithPartialPermission);
                                                partialSubjects.removeAll(subjectsWithUnrestrictedPermission);
                                                return Stream.concat(simpleReadGrantStream,
                                                        calculatePartialReadFieldsAndSubjects(preDefinedExtraFields,
                                                                thing, policyEnforcer, partialSubjects
                                                        )
                                                );
                                            } else {
                                                return Stream.of(
                                                        JsonField.newInstance(pointer.toString(), unrestrictedReadSubjects)
                                                );
                                            }
                                        })
                                        .collect(mergeFieldsAndArrayValuesWithinFields())
                                        .toString()
                        ).orElse("{}")
                );
    }

    private static Stream<JsonField> calculatePartialReadFieldsAndSubjects(
            final JsonFieldSelector preDefinedExtraFields,
            final Thing thing,
            final PolicyEnforcer policyEnforcer,
            final Set<AuthorizationSubject> partialSubjects
    ) {
        return partialSubjects.stream()
                .map(partialReadSubject ->
                        Pair.create(partialReadSubject, policyEnforcer.getEnforcer()
                                .buildJsonView(
                                        buildPredefinedExtraFieldsHeaderObject(thing, preDefinedExtraFields),
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

    private static Collector<JsonField, JsonObjectBuilder, JsonObject> mergeFieldsAndArrayValuesWithinFields() {
        return Collector.of(JsonFactory::newObjectBuilder, (builder, field) -> {
                    final JsonObject preBuiltObject = builder.build();
                    final JsonField adjustedField;
                    if (field.getValue().isArray() &&
                            preBuiltObject.getValue(field.getKey()).filter(JsonValue::isArray).isPresent()) {
                        final JsonArray existingArray = preBuiltObject.getValue(field.getKey()).orElseThrow().asArray();
                        final JsonArray missingEntriesArray = field.getValue().asArray().stream()
                                .filter(Predicate.not(existingArray::contains))
                                .collect(JsonCollectors.valuesToArray());
                        final JsonArray mergedArray = existingArray.toBuilder()
                                .addAll(missingEntriesArray)
                                .build();
                        adjustedField = JsonField.newInstance(field.getKey(), mergedArray);
                    } else {
                        adjustedField = field;
                    }
                    builder.setAll(JsonFactory.newObject(
                            Stream.of(adjustedField).collect(JsonCollectors.fieldsToObject()),
                            preBuiltObject
                    ));
                },
                JsonObjectBuilder::setAll,
                JsonObjectBuilder::build
        );
    }
}
