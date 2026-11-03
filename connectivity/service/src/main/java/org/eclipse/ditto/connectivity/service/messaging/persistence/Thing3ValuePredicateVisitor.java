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
package org.eclipse.ditto.connectivity.service.messaging.persistence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.placeholders.PlaceholderResolver;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.criteria.Predicate;
import org.eclipse.ditto.rql.query.criteria.visitors.CriteriaVisitor;
import org.eclipse.ditto.rql.query.expression.ExistsFieldExpression;
import org.eclipse.ditto.rql.query.expression.FieldExpression;
import org.eclipse.ditto.rql.query.expression.FilterFieldExpression;
import org.eclipse.ditto.rql.query.things.EmptyThingPredicateVisitor;
import org.eclipse.ditto.rql.query.things.ExistsThingPredicateVisitor;
import org.eclipse.ditto.rql.query.things.FilterThingPredicateVisitor;
import org.eclipse.ditto.rql.query.things.ThingJsonPointerVisitor;
import org.eclipse.ditto.rql.query.things.ThingPredicatePredicateVisitor;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingsModelFactory;

/**
 * Visitor to evaluate a criteria against a partial thing with unknown fields.
 */
final class Thing3ValuePredicateVisitor implements CriteriaVisitor<Function<Thing, Trilean>> {

    private static final JsonKey FEATURES_KEY = Thing.JsonFields.FEATURES.getPointer().getRoot().orElseThrow();
    private static final JsonKey METADATA_KEY = Thing.JsonFields.METADATA.getPointer().getRoot().orElseThrow();

    private final Set<JsonPointer> unknownFields;
    private final List<PlaceholderResolver<?>> placeholderResolvers;

    private Thing3ValuePredicateVisitor(final Set<JsonPointer> unknownFields,
            final Collection<PlaceholderResolver<?>> placeholderResolvers) {
        // only internally instantiable, therefore no copying + immutability of fields
        this.unknownFields = unknownFields;
        this.placeholderResolvers = new ArrayList<>(placeholderResolvers);
    }

    /**
     * Evaluate criteria against a partial thing to see whether it could be true.
     *
     * @param criteria the criteria.
     * @param unknownFields the set of unknown fields that shall not falsify the criteria evaluation result.
     * @param partialThing the partial thing.
     * @param placeholderResolvers additional {@code PlaceholderResolver}s to use in order to resolve placeholders in
     * the form {@code prefix:name}.
     * @return whether the criteria may evaluate to true after replacing 'ignoredFields' by unknown values in
     * 'partialThing'.
     */
    static boolean couldBeTrue(final Criteria criteria,
            final Set<JsonPointer> unknownFields,
            final Thing partialThing,
            final PlaceholderResolver<?>... placeholderResolvers) {
        return couldBeTrue(criteria, unknownFields, partialThing, Arrays.asList(placeholderResolvers));
    }

    /**
     * Evaluate criteria against a partial thing to see whether it could be true.
     *
     * @param criteria the criteria.
     * @param unknownFields the set of unknown fields that shall not falsify the criteria evaluation result.
     * @param partialThing the partial thing.
     * @param placeholderResolvers additional {@code PlaceholderResolver}s to use in order to resolve placeholders in
     * the form {@code prefix:name}.
     * @return whether the criteria may evaluate to true after replacing 'ignoredFields' by unknown values in
     * 'partialThing'.
     */
    static boolean couldBeTrue(final Criteria criteria,
            final Set<JsonPointer> unknownFields,
            final Thing partialThing,
            final Collection<PlaceholderResolver<?>> placeholderResolvers) {
        return Trilean.FALSE != criteria.accept(new Thing3ValuePredicateVisitor(unknownFields, placeholderResolvers))
                .apply(partialThing);
    }

    @Override
    public Function<Thing, Trilean> visitAnd(final List<Function<Thing, Trilean>> conjuncts) {
        return thing -> conjuncts.stream().map(f -> f.apply(thing)).reduce(Trilean::and).orElse(Trilean.TRUE);
    }

    @Override
    public Function<Thing, Trilean> visitAny() {
        return thing -> Trilean.TRUE;
    }

    @Override
    public Function<Thing, Trilean> visitExists(final ExistsFieldExpression fieldExpression) {
        return thing -> isUnknownField(fieldExpression)
                ? Trilean.UNKNOWN
                : Trilean.lift(ExistsThingPredicateVisitor.apply(fieldExpression, placeholderResolvers).test(thing));
    }

    @Override
    public Function<Thing, Trilean> visitEmpty(final ExistsFieldExpression fieldExpression) {
        return thing -> isUnknownField(fieldExpression)
                ? Trilean.UNKNOWN
                : Trilean.lift(EmptyThingPredicateVisitor.apply(fieldExpression, placeholderResolvers).test(thing));
    }

    @Override
    public Function<Thing, Trilean> visitField(final FilterFieldExpression fieldExpression, final Predicate predicate) {
        return thing -> {
            if (isUnknownField(fieldExpression)) {
                return Trilean.UNKNOWN;
            } else {
                final ThingPredicatePredicateVisitor ppVisitor = ThingPredicatePredicateVisitor
                        .createInstance(placeholderResolvers);
                return Trilean.lift(
                        FilterThingPredicateVisitor.apply(fieldExpression, predicate.accept(ppVisitor)).test(thing)
                );
            }
        };
    }

    @Override
    public Function<Thing, Trilean> visitNor(final List<Function<Thing, Trilean>> negativeDisjoints) {
        return visitOr(negativeDisjoints).andThen(Trilean::not);
    }

    @Override
    public Function<Thing, Trilean> visitOr(final List<Function<Thing, Trilean>> disjoints) {
        return thing -> disjoints.stream().map(f -> f.apply(thing)).reduce(Trilean::or).orElse(Trilean.FALSE);
    }

    private boolean isUnknownField(final FieldExpression fieldExpression) {
        final JsonPointer fieldExpressionPointer = ThingJsonPointerVisitor.apply(fieldExpression);
        return unknownFields.stream().anyMatch(unknownField -> overlaps(unknownField, fieldExpressionPointer));
    }

    /*
     * Enrichment changes the value of a field when the field is the unknown field itself, a descendant of it or an
     * ancestor of it - so the pointers must be the same up to the shorter one of both. Compares level by level, so
     * that e.g. "/attributes/p" does not overlap with "/attributes/pressure".
     */
    private static boolean overlaps(final JsonPointer unknownField, final JsonPointer pointer) {
        final int commonLevelCount = Math.min(unknownField.getLevelCount(), pointer.getLevelCount());
        for (int level = 0; level < commonLevelCount; level++) {
            if (!isFeatureIdWildcard(unknownField, level) && !unknownField.get(level).equals(pointer.get(level))) {
                return false;
            }
        }
        return true;
    }

    // the feature ID wildcards of extra fields, see ThingsModelFactory#expandFeatureIdWildcard
    private static boolean isFeatureIdWildcard(final JsonPointer unknownField, final int level) {
        if (unknownField.get(level).filter(ThingsModelFactory.FEATURE_ID_WILDCARD::equals).isEmpty()) {
            return false;
        }
        if (level == 1) {
            return unknownField.get(0).filter(FEATURES_KEY::equals).isPresent();
        }
        return level == 2 &&
                unknownField.get(0).filter(METADATA_KEY::equals).isPresent() &&
                unknownField.get(1).filter(FEATURES_KEY::equals).isPresent();
    }

}
