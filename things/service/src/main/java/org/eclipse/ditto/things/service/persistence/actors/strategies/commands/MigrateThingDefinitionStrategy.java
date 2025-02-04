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
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.japi.Pair;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.exceptions.InvalidRqlExpressionException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.placeholders.TimePlaceholder;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.rql.model.ParserException;
import org.eclipse.ditto.rql.parser.RqlPredicateParser;
import org.eclipse.ditto.rql.query.filter.QueryFilterCriteriaFactory;
import org.eclipse.ditto.rql.query.things.ThingPredicateVisitor;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandSizeValidator;
import org.eclipse.ditto.things.model.signals.commands.ThingResourceMapper;
import org.eclipse.ditto.things.model.signals.commands.exceptions.SkeletonGenerationFailedException;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThingResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.MigrateThingDefinition;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.model.signals.events.ThingMerged;


/**
 * Strategy to handle the {@link MigrateThingDefinition} command.
 * <p>
 * This strategy processes updates to a Thing's definition, applying necessary data migrations
 * and ensuring that defaults are properly initialized when required.
 *
 * <p>Assumptions:</p>
 * <ul>
 *   <li>The {@link MigrateThingDefinition} command provides a ThingDefinition URL, which is used
 *       to create a skeleton Thing. The command's payload also includes migration data and patch
 *       conditions for fine-grained updates.</li>
 *   <li>Patch conditions are evaluated using RQL-based expressions to determine which migration
 *       payload entries should be applied.</li>
 *   <li>Skeleton generation extracts and merges Thing definitions and default values separately,
 *       ensuring a clear distinction between structural updates and default settings.</li>
 *   <li>After applying skeleton-based modifications and migration payloads, the changes are merged.</li>
 *   <li>Property initialization can be optionally enabled via the command, applying default values
 *       to the updated Thing when set to true.</li>
 *   <li>The resulting Thing undergoes validation to ensure compliance with WoT model constraints
 *       before persisting changes.</li>
 * </ul>
 */
@Immutable
public final class MigrateThingDefinitionStrategy extends AbstractThingModifyCommandStrategy<MigrateThingDefinition> {

    private static final ThingResourceMapper<Thing, Optional<EntityTag>> ENTITY_TAG_MAPPER =
            ThingResourceMapper.from(EntityTagCalculator.getInstance());

    private static final TimePlaceholder TIME_PLACEHOLDER = TimePlaceholder.getInstance();

    /**
     * Constructs a new {@code MigrateThingDefinitionStrategy} object.
     *
     * @param actorSystem the actor system to use for loading the WoT extension.
     */
    MigrateThingDefinitionStrategy(final ActorSystem actorSystem) {
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
        return handleMigrateDefinition(context, existingThing, eventTs, nextRevision, command, metadata);
    }

    private Result<ThingEvent<?>> handleMigrateDefinition(
            final Context<ThingId> context,
            final Thing existingThing,
            final Instant eventTs,
            final long nextRevision,
            final MigrateThingDefinition command,
            @Nullable final Metadata metadata) {

        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final JsonPointer path = JsonPointer.empty();

        // 1. Evaluate Patch Conditions and modify the migrationPayload
        final JsonObject adjustedMigrationPayload = evaluatePatchConditions(
                existingThing,
                command.getMigrationPayload(),
                command.getPatchConditions(),
                dittoHeaders);

        // 2. Generate Skeleton using definition and apply migration
        final CompletionStage<Thing> updatedThingStage = generateSkeleton(command, dittoHeaders)
                .thenApply(skeleton -> mergeSkeletonWithThing(
                        existingThing, skeleton, command.getThingDefinitionUrl(),
                        command.isInitializeMissingPropertiesFromDefaults()))
                .thenApply(mergedThing -> applyMigrationPayload(context,
                        mergedThing, adjustedMigrationPayload, dittoHeaders, nextRevision, eventTs));

        // 3. Validate and build event response
        final CompletionStage<Pair<Thing, MigrateThingDefinition>> validatedStage = updatedThingStage
                .thenCompose(mergedThing -> buildValidatedStage(command, existingThing, mergedThing)
                        .thenApply(migrateThingDefinition -> new Pair<>(mergedThing, migrateThingDefinition)));

        final CompletionStage<ThingEvent<?>> eventStage = validatedStage.thenApply(pair -> ThingMerged.of(
                pair.second().getEntityId(), path, pair.first().toJson(), nextRevision, eventTs, dittoHeaders,
                metadata));

        final CompletionStage<WithDittoHeaders> responseStage = validatedStage.thenApply(pair ->
                appendETagHeaderIfProvided(command, MergeThingResponse.of(command.getEntityId(), path, dittoHeaders),
                        pair.first()));

        return ResultFactory.newMutationResult(command, eventStage, responseStage);
    }

    private JsonObject evaluatePatchConditions(final Thing existingThing,
            final JsonObject migrationPayload,
            final Map<ResourceKey, String> patchConditions,
            final DittoHeaders dittoHeaders) {
        final JsonObjectBuilder adjustedPayloadBuilder = migrationPayload.toBuilder();

        for (final Map.Entry<ResourceKey, String> entry : patchConditions.entrySet()) {
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

    private static boolean doesMigrationPayloadContainResourceKey(final JsonObject migrationPayload,
            final JsonPointer pointer) {
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
        } catch (final ParserException | IllegalArgumentException e) {
            throw InvalidRqlExpressionException.newBuilder()
                    .message(e.getMessage())
                    .cause(e)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    private CompletionStage<Thing> generateSkeleton(
            final MigrateThingDefinition command,
            final DittoHeaders dittoHeaders) {
        return wotThingSkeletonGenerator.provideThingSkeletonForCreation(
                        command.getEntityId(),
                        ThingsModelFactory.newDefinition(command.getThingDefinitionUrl()),
                        dittoHeaders
                )
                .thenApply(optionalSkeleton -> optionalSkeleton.orElseThrow(() ->
                        SkeletonGenerationFailedException.newBuilder(command.getEntityId())
                                .dittoHeaders(command.getDittoHeaders())
                                .build()
                ));
    }


    private Thing extractDefinitions(final Thing thing, final String thingDefinitionUrl) {
        var thingBuilder = ThingsModelFactory.newThingBuilder();
        thingBuilder.setDefinition(ThingsModelFactory.newDefinition(thingDefinitionUrl));
        thing.getFeatures().orElseGet(ThingsModelFactory::emptyFeatures).forEach(feature -> {
            thingBuilder.setFeature(feature.getId(), feature.getDefinition().get(), null);
        });
        return thingBuilder.build();
    }


    private Thing extractDefaultValues(final Thing thing) {
        var thingBuilder = ThingsModelFactory.newThingBuilder();
        thingBuilder.setAttributes(thing.getAttributes().orElse(ThingsModelFactory.emptyAttributes()));
        thing.getFeatures().orElseGet(ThingsModelFactory::emptyFeatures).forEach(feature -> {
            thingBuilder.setFeature(feature.getId(), feature.getDefinition().orElse(null), null);
        });
        return thingBuilder.build();
    }


    private Thing mergeSkeletonWithThing(final Thing existingThing, final Thing skeletonThing,
            final String thingDefinitionUrl, final boolean isInitializeProperties) {

        // Extract definitions and convert to JSON
        final var fullThingDefinitions = extractDefinitions(skeletonThing, thingDefinitionUrl).toJson();

        // Merge the extracted definitions with the existing thing JSON
        final var mergedThingJson = JsonFactory.mergeJsonValues(fullThingDefinitions, existingThing.toJson()).asObject();

        // If not initializing properties, return the merged result
        if (!isInitializeProperties) {
            return ThingsModelFactory.newThing(mergedThingJson);
        }

        // Extract default values and merge them in
        return ThingsModelFactory.newThing(JsonFactory.mergeJsonValues(
                mergedThingJson, extractDefaultValues(skeletonThing).toJson()).asObject());
    }

    private Thing applyMigrationPayload(final Context<ThingId> context, final Thing thing,
            final JsonObject migrationPayload,
            final DittoHeaders dittoHeaders,
            final long nextRevision,
            final Instant eventTs) {
        final JsonObject thingJson = thing.toJson(FieldType.all());
        final JsonObject mergedJson = JsonFactory.newObject(migrationPayload, thingJson);

        context.getLog().debug("Thing updated from migrated JSON: {}", mergedJson);
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
    public Optional<EntityTag> previousEntityTag(final MigrateThingDefinition command,
            @Nullable final Thing previousEntity) {
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
