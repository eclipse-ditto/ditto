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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.japi.Pair;
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

/**
 * This strategy handles the {@link org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeature} command.
 */
@Immutable
final class ModifyFeatureStrategy extends AbstractThingModifyCommandStrategy<ModifyFeature> {

    /**
     * Constructs a new {@code ModifyFeatureStrategy} object.
     *
     * @param actorSystem the actor system to use for loading the WoT extension.
     */
    ModifyFeatureStrategy(final ActorSystem actorSystem) {
        super(ModifyFeature.class, actorSystem);
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
                .map(feature -> {
                    final Optional<FeatureDefinition> featureDefinition = feature.getDefinition();
                    // validate based on potentially referenced Feature WoT TM
                    final CompletionStage<Void> validatedStage;
                    if (featureDefinition.isPresent()) {
                        validatedStage = wotThingModelValidator.validateFeature(
                                nonNullThing.getDefinition().orElse(null),
                                command.getFeature(),
                                command.getResourcePath(),
                                command.getDittoHeaders()
                        );
                    } else {
                        validatedStage = CompletableFuture.completedStage(null);
                    }
                    return getModifyResult(context, nextRevision, command, thing, metadata, validatedStage);
                })
                .orElseGet(() -> getCreateResult(context, nextRevision, command, thing, metadata));
    }

    @Override
    protected CompletionStage<ModifyFeature> performWotValidation(
            final ModifyFeature command,
            @Nullable final Thing thing
    ) {
        return wotThingModelValidator.validateFeature(
                Optional.ofNullable(thing).flatMap(Thing::getDefinition).orElse(null),
                command.getFeature(),
                command.getResourcePath(),
                command.getDittoHeaders()
        ).thenApply(aVoid -> command);
    }

    private Optional<Feature> extractFeature(final ModifyFeature command, @Nullable final Thing thing) {
        return getEntityOrThrow(thing).getFeatures()
                .flatMap(features -> features.getFeature(command.getFeatureId()));
    }

    private Result<ThingEvent<?>> getModifyResult(final Context<ThingId> context, final long nextRevision,
            final ModifyFeature command, @Nullable final Thing thing, @Nullable final Metadata metadata,
            final CompletionStage<Void> validatedStage) {

        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final CompletionStage<ThingEvent<?>> eventStage =
                validatedStage.thenApply(aVoid ->
                        FeatureModified.of(command.getEntityId(), command.getFeature(), nextRevision,
                                getEventTimestamp(),
                                dittoHeaders, metadata));
        final CompletionStage<WithDittoHeaders> responseStage =
                validatedStage.thenApply(aVoid ->
                        appendETagHeaderIfProvided(command,
                                ModifyFeatureResponse.modified(context.getState(), command.getFeatureId(),
                                        dittoHeaders),
                                thing));

        return ResultFactory.newMutationResult(command, eventStage, responseStage);
    }

    private Result<ThingEvent<?>> getCreateResult(final Context<ThingId> context, final long nextRevision,
            final ModifyFeature command, @Nullable final Thing thing, @Nullable final Metadata metadata) {

        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        final Feature finalNewFeature = command.getFeature();
        final CompletionStage<Feature> featureStage = wotThingSkeletonGenerator.provideFeatureSkeletonForCreation(
                        finalNewFeature.getId(),
                        finalNewFeature.getDefinition().orElse(null),
                        dittoHeaders
                )
                .thenApply(opt -> opt.map(wotBasedFeatureSkeleton -> wotBasedFeatureSkeleton.getDefinition()
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
                                .or(finalNewFeature::getDefinition)
                                .map(definitionIdentifiers -> JsonFactory.mergeJsonValues(
                                        finalNewFeature.setDefinition(definitionIdentifiers).toJson(),
                                        wotBasedFeatureSkeleton.toJson()
                                ))
                                .orElseGet(() -> JsonFactory.mergeJsonValues(finalNewFeature.toJson(),
                                        wotBasedFeatureSkeleton.toJson())
                                ))
                        .filter(JsonValue::isObject)
                        .map(JsonValue::asObject)
                        .map(ThingsModelFactory::newFeatureBuilder)
                        .map(b -> b.useId(finalNewFeature.getId()).build())
                        .orElse(finalNewFeature)
                );

        final CompletionStage<Pair<ModifyFeature, Feature>> validatedStage =
                featureStage.thenCompose(createdFeatureWithImplicits ->
                        buildValidatedStage(
                                ModifyFeature.of(command.getEntityId(), createdFeatureWithImplicits,
                                        command.getDittoHeaders()),
                                thing
                        ).thenApply(modifyFeature -> new Pair<>(modifyFeature, createdFeatureWithImplicits))
                );
        final CompletionStage<ThingEvent<?>> eventStage =
                validatedStage.thenApply(pair ->
                        FeatureCreated.of(command.getEntityId(), pair.second(), nextRevision,
                                getEventTimestamp(), dittoHeaders,
                                metadata)
                );

        final CompletionStage<WithDittoHeaders> response = validatedStage.thenApply(pair ->
                appendETagHeaderIfProvided(pair.first(),
                        ModifyFeatureResponse.created(context.getState(), pair.second(), dittoHeaders), thing)
        );

        return ResultFactory.newMutationResult(command, eventStage, response);
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
