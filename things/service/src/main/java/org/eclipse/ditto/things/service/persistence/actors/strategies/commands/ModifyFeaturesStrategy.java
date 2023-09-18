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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandSizeValidator;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatures;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeaturesResponse;
import org.eclipse.ditto.things.model.signals.events.FeaturesCreated;
import org.eclipse.ditto.things.model.signals.events.FeaturesModified;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.wot.integration.provider.WotThingDescriptionProvider;

import org.apache.pekko.actor.ActorSystem;

/**
 * This strategy handles the {@link org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatures} command.
 */
@Immutable
final class ModifyFeaturesStrategy extends AbstractThingCommandStrategy<ModifyFeatures> {

    private final WotThingDescriptionProvider wotThingDescriptionProvider;

    /**
     * Constructs a new {@code ModifyFeaturesStrategy} object.
     *
     * @param actorSystem the actor system to use for loading the WoT extension.
     */
    ModifyFeaturesStrategy(final ActorSystem actorSystem) {
        super(ModifyFeatures.class);
        wotThingDescriptionProvider = WotThingDescriptionProvider.get(actorSystem);
    }

    @Override
    protected Result<ThingEvent<?>> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final ModifyFeatures command,
            @Nullable final Metadata metadata) {

        final Thing nonNullThing = getEntityOrThrow(thing);

        final JsonObject thingWithoutFeaturesJsonObject = nonNullThing.removeFeatures().toJson();
        final JsonObject featuresJsonObject = command.getFeatures().toJson();

        ThingCommandSizeValidator.getInstance().ensureValidSize(
                () -> {
                    final long lengthWithOutFeatures = thingWithoutFeaturesJsonObject.getUpperBoundForStringSize();
                    final long featuresLength = featuresJsonObject.getUpperBoundForStringSize() + "features".length() + 5L;
                    return lengthWithOutFeatures + featuresLength;
                },
                () -> {
                    final long lengthWithOutFeatures = thingWithoutFeaturesJsonObject.toString().length();
                    final long featuresLength = featuresJsonObject.toString().length() + "features".length() + 5L;
                    return lengthWithOutFeatures + featuresLength;
                },
                command::getDittoHeaders);

        return nonNullThing.getFeatures()
                .map(features -> getModifyResult(context, nextRevision, command, thing, metadata))
                .orElseGet(() -> getCreateResult(context, nextRevision, command, thing, metadata));
    }

    private Result<ThingEvent<?>> getModifyResult(final Context<ThingId> context, final long nextRevision,
            final ModifyFeatures command, @Nullable final Thing thing, @Nullable final Metadata metadata) {

        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final ThingEvent<?> event =
                FeaturesModified.of(command.getEntityId(), command.getFeatures(), nextRevision,
                        getEventTimestamp(), dittoHeaders, metadata);
        final WithDittoHeaders response = appendETagHeaderIfProvided(command,
                ModifyFeaturesResponse.modified(context.getState(), dittoHeaders), thing);

        return ResultFactory.newMutationResult(command, event, response);
    }

    private Result<ThingEvent<?>> getCreateResult(final Context<ThingId> context, final long nextRevision,
            final ModifyFeatures command, @Nullable final Thing thing, @Nullable final Metadata metadata) {

        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final List<CompletionStage<Feature>> featureStages = command.getFeatures()
                .stream()
                .map(feature -> wotThingDescriptionProvider.provideFeatureSkeletonForCreation(
                                        feature.getId(),
                                        feature.getDefinition().orElse(null),
                                        dittoHeaders
                                )
                                .thenApply(opt -> opt.map(wotBasedFSkel -> wotBasedFSkel.getDefinition()
                                                .map(def -> {
                                                    final Set<DefinitionIdentifier> identifiers = Stream.concat(
                                                            wotBasedFSkel.getDefinition()
                                                                    .map(FeatureDefinition::stream)
                                                                    .orElse(Stream.empty()),
                                                            feature.getDefinition()
                                                                    .map(FeatureDefinition::stream)
                                                                    .orElse(Stream.empty())
                                                    ).collect(Collectors.toCollection(LinkedHashSet::new));
                                                    return ThingsModelFactory.newFeatureDefinition(identifiers);
                                                })
                                                .or(feature::getDefinition).map(definitionIdentifiers -> JsonFactory.mergeJsonValues(
                                                        feature.setDefinition(definitionIdentifiers).toJson(),
                                                        wotBasedFSkel.toJson()
                                                )).orElseGet(() -> JsonFactory.mergeJsonValues(feature.toJson(),
                                                        wotBasedFSkel.toJson())
                                                ))
                                        .filter(JsonValue::isObject)
                                        .map(JsonValue::asObject)
                                        .map(ThingsModelFactory::newFeatureBuilder)
                                        .map(b -> b.useId(feature.getId()).build())
                                        .orElse(feature)
                                )
                )
                .toList();

        final CompletableFuture<Features> featuresStage =
                CompletableFuture.allOf(featureStages.toArray(new CompletableFuture[0]))
                        .thenApply(aVoid ->
                                ThingsModelFactory.newFeatures(
                                        featureStages.stream()
                                                .map(CompletionStage::toCompletableFuture)
                                                .map(CompletableFuture::join)
                                                .filter(Objects::nonNull)
                                                .toList()
                                )
                        );

        final CompletableFuture<ThingEvent<?>> eventStage = featuresStage.thenApply(features ->
                FeaturesCreated.of(command.getEntityId(), features, nextRevision, getEventTimestamp(),
                        dittoHeaders, metadata
                )
        );

        final CompletableFuture<WithDittoHeaders> responseStage =
                featuresStage.thenApply(features -> appendETagHeaderIfProvided(command,
                        ModifyFeaturesResponse.created(context.getState(), features, dittoHeaders), thing)
                );

        return ResultFactory.newMutationResult(command, eventStage, responseStage);
    }


    @Override
    public Optional<EntityTag> previousEntityTag(final ModifyFeatures command, @Nullable final Thing previousEntity) {
        return Optional.ofNullable(previousEntity).flatMap(Thing::getFeatures).flatMap(EntityTag::fromEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final ModifyFeatures command, @Nullable final Thing newEntity) {
        return Optional.of(command.getFeatures()).flatMap(EntityTag::fromEntity);
    }
}
