/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.DefinitionIdentifier;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureDefinition;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandSizeValidator;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeature;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureResponse;
import org.eclipse.ditto.things.model.signals.events.FeatureCreated;
import org.eclipse.ditto.things.model.signals.events.FeatureModified;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.wot.integration.provider.WotThingDescriptionProvider;

import akka.actor.ActorSystem;

/**
 * This strategy handles the {@link org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeature} command.
 */
@Immutable
final class ModifyFeatureStrategy extends AbstractThingCommandStrategy<ModifyFeature> {

    private final WotThingDescriptionProvider wotThingDescriptionProvider;

    /**
     * Constructs a new {@code ModifyFeatureStrategy} object.
     *
     * @param actorSystem the actor system to use for loading the WoT extension.
     */
    ModifyFeatureStrategy(final ActorSystem actorSystem) {
        super(ModifyFeature.class);
        wotThingDescriptionProvider = WotThingDescriptionProvider.get(actorSystem);
    }

    @Override
    protected Result<ThingEvent<?>> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final ModifyFeature command,
            @Nullable final Metadata metadata) {

        final Thing nonNullThing = getEntityOrThrow(thing);

        final JsonObject thingWithoutFeatureJsonObject = nonNullThing.removeFeature(command.getFeatureId()).toJson();
        final JsonObject featureJsonObject = command.getFeature().toJson();

        ThingCommandSizeValidator.getInstance().ensureValidSize(
                () -> {
                    final long lengthWithOutFeature = thingWithoutFeatureJsonObject.getUpperBoundForStringSize();
                    final long featureLength = featureJsonObject.getUpperBoundForStringSize()
                            + command.getFeatureId().length() + 5L;
                    return lengthWithOutFeature + featureLength;
                },
                () -> {
                    final long lengthWithOutFeature = thingWithoutFeatureJsonObject.toString().length();
                    final long featureLength = featureJsonObject.toString().length()
                            + command.getFeatureId().length() + 5L;
                    return lengthWithOutFeature + featureLength;
                },
                command::getDittoHeaders);

        return extractFeature(command, nonNullThing)
                .map(feature -> getModifyResult(context, nextRevision, command, thing, metadata))
                .orElseGet(() -> getCreateResult(context, nextRevision, command, thing, metadata));
    }

    private Optional<Feature> extractFeature(final ModifyFeature command, @Nullable final Thing thing) {
        return getEntityOrThrow(thing).getFeatures()
                .flatMap(features -> features.getFeature(command.getFeatureId()));
    }

    private Result<ThingEvent<?>> getModifyResult(final Context<ThingId> context, final long nextRevision,
            final ModifyFeature command, @Nullable final Thing thing, @Nullable final Metadata metadata) {

        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final ThingEvent<?> event =
                FeatureModified.of(command.getEntityId(), command.getFeature(), nextRevision, getEventTimestamp(),
                        dittoHeaders, metadata);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                ModifyFeatureResponse.modified(context.getState(), command.getFeatureId(), dittoHeaders),
                thing);

        return ResultFactory.newMutationResult(command, event, response);
    }

    private Result<ThingEvent<?>> getCreateResult(final Context<ThingId> context, final long nextRevision,
            final ModifyFeature command, @Nullable final Thing thing, @Nullable final Metadata metadata) {

        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        Feature feature = command.getFeature();
        final Feature finalNewFeature = feature;
        feature = wotThingDescriptionProvider.provideFeatureSkeletonForCreation(
                        finalNewFeature.getId(),
                        finalNewFeature.getDefinition().orElse(null),
                        dittoHeaders
                )
                .map(wotBasedFeatureSkeleton -> {
                        final Optional<FeatureDefinition> mergedDefinition =
                                wotBasedFeatureSkeleton.getDefinition()
                                        .map(def -> {
                                                final Set<DefinitionIdentifier> identifiers = Stream.concat(
                                                        wotBasedFeatureSkeleton.getDefinition()
                                                                .map(FeatureDefinition::stream)
                                                                .orElse(Stream.empty()),
                                                        finalNewFeature.getDefinition()
                                                                .map(FeatureDefinition::stream)
                                                                .orElse(Stream.empty())
                                                ).collect(Collectors.toCollection(LinkedHashSet::new));
                                                return ThingsModelFactory.newFeatureDefinition(identifiers);
                                        })
                                        .or(finalNewFeature::getDefinition);

                        return mergedDefinition.map(definitionIdentifiers -> JsonFactory.mergeJsonValues(
                                finalNewFeature.setDefinition(definitionIdentifiers).toJson(),
                                wotBasedFeatureSkeleton.toJson()
                        )).orElseGet(() -> JsonFactory.mergeJsonValues(finalNewFeature.toJson(),
                                wotBasedFeatureSkeleton.toJson())
                        );
                })
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .map(ThingsModelFactory::newFeatureBuilder)
                .map(b -> b.useId(finalNewFeature.getId()).build())
                .orElse(finalNewFeature);

        final ThingEvent<?> event =
                FeatureCreated.of(command.getEntityId(), feature, nextRevision, getEventTimestamp(), dittoHeaders,
                        metadata);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                ModifyFeatureResponse.created(context.getState(), feature, dittoHeaders), thing);

        return ResultFactory.newMutationResult(command, event, response);
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final ModifyFeature command, @Nullable final Thing previousEntity) {
        return extractFeature(command, previousEntity).flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ModifyFeature command, @Nullable final Thing newEntity) {
        return Optional.of(command.getFeature()).flatMap(EntityTag::fromEntity);
    }
}
