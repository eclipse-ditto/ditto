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
package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.apache.pekko.actor.ActorSystem;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.placeholders.TimePlaceholder;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.rql.parser.RqlPredicateParser;
import org.eclipse.ditto.rql.query.filter.QueryFilterCriteriaFactory;
import org.eclipse.ditto.rql.query.things.ThingPredicateVisitor;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandSizeValidator;
import org.eclipse.ditto.things.model.signals.commands.ThingResourceMapper;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThing;
import org.eclipse.ditto.things.model.signals.commands.modify.MigrateThingDefinition;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;


/**
 * Strategy to handle the {@link MigrateThingDefinition} command.
 *
 * This strategy processes updates to a Thing's definition, applying necessary data migrations
 * and ensuring that defaults are properly initialized when required.
 *
 * Assumptions:
 * - The {@link MigrateThingDefinition} command provides a ThingDefinition URL, which is used
 *   to create a skeleton Thing. The command's payload also includes migration data and patch
 *   conditions for fine-grained updates.
 * - Patch conditions are evaluated using RQL-based expressions to determine which migration
 *   payload entries should be applied.
 * - Skeleton generation extracts and merges Thing definitions and default values separately,
 *   ensuring a clear distinction between structural updates and default settings.
 * - After applying skeleton-based modifications and migration payloads, the changes are merged
 * - Property initialization can be optionally enabled via the command, applying default values
 *   to the updated Thing when set to true.
 * - The resulting Thing undergoes validation to ensure compliance with WoT model constraints
 *   before persisting changes.
 */
@Immutable
public final class MigrateThingDefinitionStrategy extends AbstractThingModifyCommandStrategy<MigrateThingDefinition> {

    private static final ThingResourceMapper<Thing, Optional<EntityTag>> ENTITY_TAG_MAPPER =
            ThingResourceMapper.from(EntityTagCalculator.getInstance());

    private static final TimePlaceholder TIME_PLACEHOLDER = TimePlaceholder.getInstance();


    public MigrateThingDefinitionStrategy(final ActorSystem actorSystem) {
        super(MigrateThingDefinition.class, actorSystem);
    }

    @Override
    protected Result<ThingEvent<?>> doApply(final Context<ThingId> context,
            @Nullable final Thing thing,
            final long nextRevision,
            final MigrateThingDefinition command,
            @Nullable final Metadata metadata) {

        final Thing existingThing = getEntityOrThrow(thing);
        final Instant eventTs = getEventTimestamp();

        return handleUpdateDefinition(context, existingThing, eventTs, nextRevision, command, metadata)
                .toCompletableFuture()
                .join();
    }

    private CompletionStage<Result<ThingEvent<?>>> handleUpdateDefinition(final Context<ThingId> context,
            final Thing existingThing,
            final Instant eventTs,
            final long nextRevision,
            final MigrateThingDefinition command,
            @Nullable final Metadata metadata) {

        final DittoHeaders dittoHeaders = command.getDittoHeaders();

        // 1. Evaluate Patch Conditions and modify the migrationPayload
        final JsonObject adjustedMigrationPayload = evaluatePatchConditions(
                existingThing,
                command.getMigrationPayload(),
                command.getPatchConditions(),
                dittoHeaders);

        // 2. Generate Skeleton using definition
        final CompletionStage<Thing> skeletonStage = generateSkeleton(context, command, dittoHeaders);

        // 3. Merge Skeleton with Existing Thing and Apply Migration Payload
        final CompletionStage<Thing> updatedThingStage = skeletonStage.thenApply(skeleton -> {
            final Thing mergedThing = mergeSkeletonWithThing(existingThing, skeleton, command.getThingDefinitionUrl(), command.isInitializeMissingPropertiesFromDefaults());
            return applyMigrationPayload(mergedThing, adjustedMigrationPayload, dittoHeaders, nextRevision, eventTs);
        });

        // 4. Validate and Build Result
        return updatedThingStage.thenCompose(updatedThing ->
                buildValidatedStage(command, existingThing, updatedThing)
                        .thenCompose(validatedCommand -> {
                            final MergeThing mergeThingCommand = MergeThing.of(
                                    command.getEntityId(),
                                    JsonPointer.empty(),
                                    updatedThing.toJson(),
                                    dittoHeaders
                            );

                            final MergeThingStrategy mergeStrategy = new MergeThingStrategy(context.getActorSystem());
                            final Result<ThingEvent<?>> mergeResult = mergeStrategy.doApply(
                                    context,
                                    existingThing,
                                    nextRevision,
                                    mergeThingCommand,
                                    metadata
                            );

                            return CompletableFuture.completedFuture(mergeResult);
                        })
        );
    }

    private JsonObject evaluatePatchConditions(final Thing existingThing,
            final JsonObject migrationPayload,
            final Map<ResourceKey, String> patchConditions,
            final DittoHeaders dittoHeaders) {
        final JsonObjectBuilder adjustedPayloadBuilder = migrationPayload.toBuilder();

        for (Map.Entry<ResourceKey, String> entry : patchConditions.entrySet()) {
            final ResourceKey resourceKey = entry.getKey();
            final String conditionExpression = entry.getValue();

            final boolean conditionMatches = evaluateCondition(existingThing, conditionExpression, dittoHeaders);

            final JsonPointer resourcePointer = JsonFactory.newPointer(resourceKey.getResourcePath());
            if (!conditionMatches && doesMigrationPayloadContainResourceKey(migrationPayload, resourcePointer)) {
                adjustedPayloadBuilder.remove(resourcePointer);
            }
        }

        return adjustedPayloadBuilder.build();
    }
    public boolean doesMigrationPayloadContainResourceKey(JsonObject migrationPayload, JsonPointer pointer) {
        return migrationPayload.getValue(pointer).isPresent();
    }
    private boolean evaluateCondition(final Thing existingThing,
            final String conditionExpression,
            final DittoHeaders dittoHeaders) {
        try {
            final var criteria = QueryFilterCriteriaFactory
                    .modelBased(RqlPredicateParser.getInstance())
                    .filterCriteria(conditionExpression, dittoHeaders);

            final var predicate = ThingPredicateVisitor.apply(criteria,
                    PlaceholderFactory.newPlaceholderResolver(TIME_PLACEHOLDER, new Object()));

            return predicate.test(existingThing);
        } catch (Exception e) {
            return false;
        }
    }

    private CompletionStage<Thing> generateSkeleton(final Context<ThingId> context,
            final MigrateThingDefinition command,
            final DittoHeaders dittoHeaders) {
        return wotThingSkeletonGenerator.provideThingSkeletonForCreation(
                        command.getEntityId(),
                        ThingsModelFactory.newDefinition(command.getThingDefinitionUrl()),
                        dittoHeaders
                )
                .thenApply(optionalSkeleton -> optionalSkeleton.orElseThrow());
    }


    private Thing extractDefinitions(final Thing thing, final String thingDefinitionUrl) {
        var thingBuilder = ThingsModelFactory.newThingBuilder();
        thingBuilder.setDefinition(ThingsModelFactory.newDefinition(thingDefinitionUrl));
        thing.getFeatures().orElseGet(null).forEach(feature-> {
            thingBuilder.setFeature(feature.getId(), feature.getDefinition().get(), null);
        });
        return thingBuilder.build();
    }


    private Thing extractDefaultValues(Thing thing) {
        var thingBuilder = ThingsModelFactory.newThingBuilder();
        thingBuilder.setAttributes(thing.getAttributes().get());
        thing.getFeatures().orElseGet(null).forEach(feature-> {
            thingBuilder.setFeature(feature.getId(), feature.getProperties().get());
        });
        return thingBuilder.build();
    }


    private Thing mergeSkeletonWithThing(final Thing existingThing, final Thing skeletonThing,
            final String thingDefinitionUrl, final boolean isInitializeProperties) {

        // Extract definitions and convert to JSON
        var fullThingDefinitions = extractDefinitions(skeletonThing, thingDefinitionUrl).toJson();

        // Merge the extracted definitions with the existing thing JSON
        var mergedThingJson = JsonFactory.mergeJsonValues(fullThingDefinitions, existingThing.toJson()).asObject();

        // If not initializing properties, return the merged result
        if (!isInitializeProperties) {
            return ThingsModelFactory.newThing(mergedThingJson);
        }

        // Extract default values and merge them in
        return ThingsModelFactory.newThing(JsonFactory.mergeJsonValues(
                mergedThingJson, extractDefaultValues(skeletonThing).toJson()).asObject());
    }

    private Thing applyMigrationPayload(final Thing thing,
            final JsonObject migrationPayload,
            final DittoHeaders dittoHeaders,
            final long nextRevision,
            final Instant eventTs) {
        final JsonObject thingJson = thing.toJson(FieldType.all());

        final JsonObject mergePatch = JsonFactory.newObject(JsonPointer.empty(), migrationPayload);
        final JsonObject mergedJson = JsonFactory.mergeJsonValues(mergePatch, thingJson).asObject();

        ThingCommandSizeValidator.getInstance().ensureValidSize(
                mergedJson::getUpperBoundForStringSize,
                () -> mergedJson.toString().length(),
                () -> dittoHeaders);

        return ThingsModelFactory.newThingBuilder(mergedJson)
                .setModified(eventTs)
                .setRevision(nextRevision)
                .build();
    }

    @Override
    public Optional<EntityTag> previousEntityTag(final MigrateThingDefinition command, @Nullable final Thing previousEntity) {
        return ENTITY_TAG_MAPPER.map(JsonPointer.empty(), previousEntity);
    }

    @Override
    public Optional<EntityTag> nextEntityTag(final MigrateThingDefinition command, @Nullable final Thing newEntity) {
        return ENTITY_TAG_MAPPER.map(JsonPointer.empty(), getEntityOrThrow(newEntity));
    }

    @Override
    protected CompletionStage<MigrateThingDefinition> performWotValidation(final MigrateThingDefinition command,
                                                                           @Nullable final Thing previousThing, @Nullable final Thing previewThing) {
        return wotThingModelValidator.validateThing(
                Optional.ofNullable(previewThing).orElseThrow(),
                command.getResourcePath(),
                command.getDittoHeaders()
        ).thenApply(aVoid -> command);
    }
}
