/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.Features;

/**
 * Abstract base class for {@link org.eclipse.ditto.things.model.signals.commands.ThingCommand} strategies.
 *
 * @param <C> the type of the handled command - of type {@code Command} as also
 * {@link org.eclipse.ditto.things.api.commands.sudo.SudoCommand} are handled which are no ThingCommands.
 */
@Immutable
abstract class AbstractRetrieveThingCommandStrategy<C extends Command<C>> extends AbstractThingCommandStrategy<C> {

    private static final JsonPointer FEATURE_ID_WILDCARD = JsonPointer.of("*");

    protected AbstractRetrieveThingCommandStrategy(final Class<C> theMatchingClass) {
        super(theMatchingClass);
    }

    static JsonFieldSelector expandFeatureIdWildcard(final JsonFieldSelector fieldSelector,
            final JsonPointer prefixPointer, final Features features) {

        if (features.isEmpty()) {
            return fieldSelector;
        } else {
            final List<String> featureIds = features.stream().map(Feature::getId).collect(Collectors.toList());
            final JsonPointer prefixWithWildcard = prefixPointer.append(FEATURE_ID_WILDCARD);
            final List<JsonPointer> expanded = fieldSelector.getPointers().stream().flatMap(p -> {
                final boolean isWildcardSelector = p.getPrefixPointer(prefixWithWildcard.getLevelCount())
                        .stream()
                        .anyMatch(pp -> pp.equals(prefixWithWildcard));
                if (isWildcardSelector) {
                    return featureIds.stream()
                            .map(fid -> replaceLevel(p, prefixWithWildcard.getLevelCount() - 1, JsonPointer.of(fid)));
                } else {
                    return Stream.of(p);
                }
            }).collect(Collectors.toList());

            return JsonFactory.newFieldSelector(expanded);
        }

    }

    static JsonPointer replaceLevel(final JsonPointer source, final int level, final JsonPointer replacement) {

        if (source.getLevelCount() <= level || level < 0) {
            return source;
        } else {
            if (level == 0) {
                return replacement.append(source.nextLevel());
            } else if (level == source.getLevelCount() - 1) {
                return source.cutLeaf().append(replacement);
            } else {
                return source.getPrefixPointer(level)
                        .map(p -> p.append(replacement))
                        .map(p -> p.append(source.getSubPointer(level + 1).orElse(JsonPointer.empty())))
                        .orElse(JsonPointer.empty());
            }
        }
    }

}
