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
package org.eclipse.ditto.things.service.persistence.actors.enrichment;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.InvalidRqlExpressionException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.pekko.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
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
import org.eclipse.ditto.things.model.signals.events.ThingDefinitionMigrated;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.model.signals.events.ThingMerged;
import org.eclipse.ditto.things.service.common.config.PreDefinedExtraFieldsConfig;
import org.eclipse.ditto.things.service.utils.AccessRestrictions;
import org.eclipse.ditto.things.service.utils.AccessRestrictionsMemo;
import org.eclipse.ditto.things.service.utils.PartialAccessPathCalculator;
import org.eclipse.ditto.things.service.utils.ReadGrant;
import org.eclipse.ditto.things.service.utils.ReadGrantCollector;

/**
 * Encapsulates functionality in order to enrich ThingEvents with pre-defined {@code extraFields} via DittoHeaders
 * (fields defined per namespace in the Ditto things configuration), with partial access paths for subjects
 * with restricted READ permissions and with the paths whose READ permission is revoked per subject.
 */
public final class ThingEventEnricher {

    private static final ThreadSafeDittoLogger LOGGER = DittoLoggerFactory.getThreadSafeLogger(ThingEventEnricher.class);

    private static final TimePlaceholder TIME_PLACEHOLDER = TimePlaceholder.getInstance();
    private static final HeadersPlaceholder HEADERS_PLACEHOLDER = PlaceholderFactory.newHeadersPlaceholder();

    private final PolicyEnforcerProvider policyEnforcerProvider;
    private final boolean partialAccessEventsEnabled;
    private final boolean partialAccessPathsCacheEnabled;
    // one entry each for events and messages, so that they do not evict each other
    private final AccessRestrictionsMemo accessRestrictionsMemo = new AccessRestrictionsMemo(2);

    /**
     * Constructs a new enricher for ThingEvents based on the provided policy enforcer.
     *
     * @param policyEnforcerProvider the policy enforcer to use in order to check permissions for enriching extraFields
     * @param partialAccessEventsEnabled whether partial access events should be emitted
     * @param partialAccessPathsCacheEnabled whether the per-thing partial-access-paths cache is enabled
     */
    public ThingEventEnricher(
            final PolicyEnforcerProvider policyEnforcerProvider,
            final boolean partialAccessEventsEnabled,
            final boolean partialAccessPathsCacheEnabled
    ) {
        this.policyEnforcerProvider = policyEnforcerProvider;
        this.partialAccessEventsEnabled = partialAccessEventsEnabled;
        this.partialAccessPathsCacheEnabled = partialAccessPathsCacheEnabled;
    }

    /**
     * Enriches the passed in {@code withDittoHeaders} with pre-defined extraFields based on the provided {@code thing}
     * and the global configuration (based on namespace and optional RQL condition) and with the READ restrictions of
     * the subjects of the policy.
     *
     * @param preDefinedExtraFieldsConfigs the list of config entries for pre-defined extraFields enrichment
     * @param thingId the Thing ID to enrich for
     * @param previousThing the Thing entity before the event, which contains what the event deleted
     * @param thing the Thing entity to use for getting extra fields from
     * @param policyId the Policy ID to use for looking up permissions
     * @param withDittoHeaders the object to enrich with pre-defined extraFields (e.g. a Signal)
     * @param <T> the type of the signal to enrich
     * @return an enriched version of the passed in {@code withDittoHeaders}, failed if a Thing event or message cannot
     * be enriched with the READ restrictions
     */
    public <T extends DittoHeadersSettable<? extends T>> CompletionStage<T> enrichWithPredefinedExtraFields(
            final List<PreDefinedExtraFieldsConfig> preDefinedExtraFieldsConfigs,
            final ThingId thingId,
            @Nullable final Thing previousThing,
            @Nullable final Thing thing,
            @Nullable final PolicyId policyId,
            final T withDittoHeaders
    ) {
        if (null == thing) {
            return CompletableFuture.completedStage(withDittoHeaders);
        }
        final JsonObject thingJson;
        final JsonFieldSelector predefinedExtraFields;
        try {
            thingJson = thing.toJson();
            predefinedExtraFields =
                    getPredefinedExtraFields(preDefinedExtraFieldsConfigs, thingId, thing, withDittoHeaders);
        } catch (final RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        @Nullable final ThingEvent<?> thingEvent = withDittoHeaders instanceof ThingEvent<?> event ? event : null;
        final boolean withAccessRestrictions = null != thingEvent || withDittoHeaders instanceof MessageCommand<?, ?>;
        if (!withAccessRestrictions && predefinedExtraFields.isEmpty()) {
            return CompletableFuture.completedStage(withDittoHeaders);
        }

        // one lookup for all headers, so that they are calculated from the same policy
        return policyEnforcerProvider.getPolicyEnforcer(policyId).thenApply(policyEnforcerOpt -> {
            @Nullable final PolicyEnforcer policyEnforcer = policyEnforcerOpt
                    .map(enforcer -> enforcer.forNamespace(thingId.getNamespace()))
                    .orElse(null);
            T result = withDittoHeaders;
            if (withAccessRestrictions) {
                if (null == policyEnforcer) {
                    throw new IllegalStateException("No policy enforcer found for policyId: " + policyId);
                }
                result = getAccessRestrictions(thingEvent, policyEnforcer, thingJson, previousThing).applyTo(result);
            }
            if (!predefinedExtraFields.isEmpty()) {
                result = addPredefinedExtraFields(result, predefinedExtraFields, thingJson, policyEnforcer, policyId);
            }
            return result;
        });
    }

    private JsonFieldSelector getPredefinedExtraFields(
            final List<PreDefinedExtraFieldsConfig> preDefinedExtraFieldsConfigs,
            final ThingId thingId,
            final Thing thing,
            final WithDittoHeaders withDittoHeaders) {

        return preDefinedExtraFieldsConfigs.stream()
                .filter(conf -> conf.getNamespace().isEmpty() ||
                        conf.getNamespace()
                                .stream()
                                .anyMatch(pattern -> pattern.matcher(thingId.getNamespace()).matches())
                )
                .filter(applyPredefinedExtraFieldsCondition(thing, withDittoHeaders))
                .map(PreDefinedExtraFieldsConfig::getExtraFields)
                .reduce(JsonFactory.newFieldSelector(List.of()), (a, b) -> {
                    final Set<JsonPointer> combinedPointerSet = new LinkedHashSet<>(a.getPointers());
                    combinedPointerSet.addAll(b.getPointers());
                    return JsonFactory.newFieldSelector(combinedPointerSet);
                });
    }

    private <T extends DittoHeadersSettable<? extends T>> T addPredefinedExtraFields(
            final T withDittoHeaders,
            final JsonFieldSelector predefinedExtraFields,
            final JsonObject thingJson,
            @Nullable final PolicyEnforcer policyEnforcer,
            @Nullable final PolicyId policyId) {

        final DittoHeaders dittoHeaders = withDittoHeaders.getDittoHeaders();
        final ReadGrant readGrant;
        if (null == policyEnforcer) {
            LOGGER.withCorrelationId(dittoHeaders)
                    .warn("No policy enforcer found for policyId: {}, returning empty read grant object", policyId);
            readGrant = ReadGrant.empty();
        } else {
            readGrant = ReadGrantCollector.collect(predefinedExtraFields, thingJson, policyEnforcer);
        }
        final DittoHeadersBuilder<?, ?> headersBuilder = dittoHeaders.toBuilder()
                .putJsonHeader(DittoHeaderDefinition.PRE_DEFINED_EXTRA_FIELDS.getKey(),
                        buildPredefinedExtraFieldsHeaderList(predefinedExtraFields))
                .putJsonHeader(DittoHeaderDefinition.PRE_DEFINED_EXTRA_FIELDS_READ_GRANT_OBJECT.getKey(),
                        readGrant.toJson())
                .putJsonHeader(DittoHeaderDefinition.PRE_DEFINED_EXTRA_FIELDS_OBJECT.getKey(),
                        buildPredefinedExtraFieldsHeaderObject(thingJson, predefinedExtraFields));
        return withDittoHeaders.setDittoHeaders(headersBuilder.build());
    }

    private AccessRestrictions getAccessRestrictions(@Nullable final ThingEvent<?> thingEvent,
            final PolicyEnforcer policyEnforcer,
            final JsonObject thingJson,
            @Nullable final Thing previousThing) {

        final boolean withPartialAccessPaths = partialAccessEventsEnabled && null != thingEvent;
        if (!AccessRestrictions.mayBeRestricted(policyEnforcer, withPartialAccessPaths)) {
            return AccessRestrictions.NONE;
        }
        // the paths must also cover what the event deleted, which the thing no longer contains
        final JsonObject structureJson = null != thingEvent
                ? withEventPayload(thingEvent, thingJson, previousThing)
                : thingJson;
        if (!partialAccessPathsCacheEnabled) {
            return AccessRestrictions.calculate(policyEnforcer, structureJson, withPartialAccessPaths, true);
        }
        return accessRestrictionsMemo.get(policyEnforcer, PartialAccessPathCalculator.structureHash(structureJson),
                withPartialAccessPaths,
                () -> AccessRestrictions.calculate(policyEnforcer, structureJson, withPartialAccessPaths, true));
    }

    private static JsonObject withEventPayload(final ThingEvent<?> thingEvent, final JsonObject thingJson,
            @Nullable final Thing previousThing) {

        final JsonPointer resourcePath = thingEvent.getResourcePath();
        if (thingEvent instanceof ThingMerged || thingEvent instanceof ThingDefinitionMigrated) {
            return thingEvent.getEntity()
                    .map(value -> resourcePath.isEmpty()
                            ? (value.isObject() && !value.isNull() ? value.asObject() : null)
                            : JsonObject.empty().setValue(resourcePath, value))
                    .map(eventJson -> overlay(thingJson, eventJson,
                            containsNull(eventJson) ? previousJson(previousThing, resourcePath) : null))
                    .orElse(thingJson);
        }
        if (resourcePath.isEmpty() || thingJson.contains(resourcePath)) {
            return thingJson;
        }
        @Nullable final JsonObject previousJson = previousJson(previousThing, resourcePath);
        final JsonValue deleted = null != previousJson ? previousJson.getValue(resourcePath).orElse(null) : null;
        return thingJson.setValue(resourcePath, null != deleted ? deleted : JsonValue.nullLiteral());
    }

    // only the part of the previous Thing the event can have deleted is needed
    @Nullable
    private static JsonObject previousJson(@Nullable final Thing previousThing, final JsonPointer resourcePath) {
        if (null == previousThing) {
            return null;
        }
        return resourcePath.isEmpty()
                ? previousThing.toJson()
                : previousThing.toJson(JsonFactory.newFieldSelector(resourcePath));
    }

    private static boolean containsNull(final JsonObject json) {
        for (final JsonField field : json) {
            final JsonValue value = field.getValue();
            if (value.isNull() || isObject(value) && containsNull(value.asObject())) {
                return true;
            }
        }
        return false;
    }

    // unlike a JSON merge patch, keeps null values, as they are fields of the event, but replaces a null value
    // deleting an object by the deleted object, whose fields the event deleted
    private static JsonObject overlay(final JsonObject base, final JsonObject overlay,
            @Nullable final JsonObject previous) {

        final JsonObjectBuilder builder = base.toBuilder();
        for (final JsonField field : overlay) {
            final JsonValue overlayValue = field.getValue();
            @Nullable final JsonValue baseValue = base.getValue(field.getKey()).orElse(null);
            @Nullable final JsonValue previousValue =
                    null != previous ? previous.getValue(field.getKey()).orElse(null) : null;
            if (isObject(baseValue) && isObject(overlayValue)) {
                builder.set(field.getKey(), overlay(baseValue.asObject(), overlayValue.asObject(),
                        isObject(previousValue) ? previousValue.asObject() : null));
            } else if (overlayValue.isNull() && null == baseValue && isObject(previousValue) &&
                    !previousValue.asObject().isEmpty()) {
                builder.set(field.getKey(), previousValue);
            } else {
                builder.set(field.getKey(), overlayValue);
            }
        }
        return builder.build();
    }

    private static boolean isObject(@Nullable final JsonValue value) {
        return null != value && value.isObject() && !value.isNull();
    }

    /**
     * Applies RQL condition from config.
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
                final Criteria criteria = QueryFilterCriteriaFactory
                        .modelBased(RqlPredicateParser.getInstance())
                        .filterCriteria(rqlCondition, withDittoHeaders.getDittoHeaders());

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

            } catch (final InvalidRqlExpressionException e) {
                LOGGER.withCorrelationId(withDittoHeaders.getDittoHeaders())
                        .warn("Invalid RQL condition <{}> - ignoring: {}", rqlCondition, e.getMessage());
                return true;
            } catch (final Exception e) {
                LOGGER.withCorrelationId(withDittoHeaders.getDittoHeaders())
                        .warn(
                                "Error evaluating RQL condition <{}> for predefined extra fields - treating as match: {}",
                                rqlCondition, e.getMessage(), e
                        );
                return true;
            }
        };
    }

    private static JsonArray buildPredefinedExtraFieldsHeaderList(final JsonFieldSelector preDefinedExtraFields) {
        return StreamSupport.stream(preDefinedExtraFields.spliterator(), false)
                .map(JsonPointer::toString)
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray());
    }

    /**
     * Builds JSON object for predefined extra fields from the pre-computed thing JSON.
     */
    private static JsonObject buildPredefinedExtraFieldsHeaderObject(
            final JsonObject thingJson,
            final JsonFieldSelector preDefinedExtraFields
    ) {
        final JsonObjectBuilder builder = JsonObject.newBuilder();
        preDefinedExtraFields.getPointers().forEach(pointer ->
                thingJson.getValue(pointer).ifPresent(thingValue -> builder.set(pointer, thingValue))
        );
        return builder.build();
    }

}
