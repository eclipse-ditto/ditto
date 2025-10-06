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
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.japi.Pair;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.internal.utils.persistentactors.results.Result;
import org.eclipse.ditto.internal.utils.persistentactors.results.ResultFactory;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.FeatureDefinition;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandSizeValidator;
import org.eclipse.ditto.things.model.signals.commands.ThingResourceMapper;
import org.eclipse.ditto.things.model.signals.commands.modify.MigrateThingDefinition;
import org.eclipse.ditto.things.model.signals.commands.modify.MigrateThingDefinitionResponse;
import org.eclipse.ditto.things.model.signals.events.ThingDefinitionMigrated;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.service.utils.PatchConditionsEvaluator;
import org.eclipse.ditto.wot.model.SkeletonGenerationFailedException;


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
 *
 * @since 3.7.0
 */
@Immutable
public final class MigrateThingDefinitionStrategy extends AbstractThingModifyCommandStrategy<MigrateThingDefinition> {

    private static final ThingResourceMapper<Thing, Optional<EntityTag>> ENTITY_TAG_MAPPER =
            ThingResourceMapper.from(EntityTagCalculator.getInstance());


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
        final boolean isDryRun = dittoHeaders.isExternalDryRun();

        // 1. Evaluate Patch Conditions and modify the migrationPayload
        final JsonObject adjustedMigrationPayload = PatchConditionsEvaluator.evaluatePatchConditions(
                existingThing,
                command.getMigrationPayload(),
                command.getPatchConditions(),
                dittoHeaders);

        // 2. Generate Skeleton using definition and apply migration
        final CompletionStage<Thing> updatedThingStage = generateSkeleton(command, dittoHeaders)
                .thenApply(skeleton -> resolveSkeletonConflicts(
                        existingThing, skeleton,
                        command.isInitializeMissingPropertiesFromDefaults()))
                .thenApply(patchThing -> applyMigrationPayload(context,
                        patchThing, adjustedMigrationPayload, nextRevision, eventTs));

        // 3. Validate and build event response
        final CompletionStage<Pair<Thing, MigrateThingDefinition>> validatedStage = updatedThingStage
                .thenComposeAsync(patchThing -> buildValidatedStage(command, existingThing,
                        buildValidationPreviewThing(existingThing, patchThing, command))
                        .thenApplyAsync(migrateThingDefinition ->
                                new Pair<>(patchThing, migrateThingDefinition), wotValidationExecutor),
                        wotValidationExecutor
                );

        // If Dry Run, return a simulated response without applying changes
        if (isDryRun) {
            return ResultFactory.newQueryResult(
                    command,
                    validatedStage.thenApply(pair ->
                            MigrateThingDefinitionResponse.dryRun(
                                    context.getState(),
                                    pair.first().toJson(),
                                    dittoHeaders))
            );
        }

        // 4. Apply migration and generate event
        final CompletionStage<ThingEvent<?>> eventStage = validatedStage.thenApply(pair -> ThingDefinitionMigrated.of(
                pair.first().toBuilder()
                        .setId(context.getState())
                        .build(), nextRevision, eventTs, dittoHeaders,
                metadata));

        final CompletionStage<WithDittoHeaders> responseStage = validatedStage.thenApply(pair ->
                appendETagHeaderIfProvided(command, MigrateThingDefinitionResponse.applied(context.getState(),
                                pair.first().toJson(), createCommandResponseDittoHeaders(dittoHeaders, nextRevision)),
                        pair.first()));

        return ResultFactory.newMutationResult(command, eventStage, responseStage);
    }

    private Thing buildValidationPreviewThing(final Thing existingThing,
            final Thing patchThing, final MigrateThingDefinition command) {
        final var mergedJson = JsonFactory.mergeJsonValues(patchThing.toJson(), existingThing.toJson()).asObject();

        ThingCommandSizeValidator.getInstance().ensureValidSize(
                mergedJson::getUpperBoundForStringSize,
                () -> mergedJson.toString().length(),
                command::getDittoHeaders);

        return ThingsModelFactory.newThingBuilder(mergedJson)
                .build();
    }


    private CompletionStage<Thing> generateSkeleton(
            final MigrateThingDefinition command,
            final DittoHeaders dittoHeaders) {
        return wotThingSkeletonGenerator.provideThingSkeletonForCreation(
                        command.getEntityId(),
                        ThingsModelFactory.newDefinition(command.getThingDefinitionUrl()),
                        dittoHeaders
                )
                .thenApply(optionalSkeleton -> {
                    final Thing skeleton = optionalSkeleton.orElseThrow(() ->
                            SkeletonGenerationFailedException.newBuilder(command.getThingDefinitionUrl())
                                    .dittoHeaders(command.getDittoHeaders())
                                    .build()
                    );

                    return skeleton.toBuilder()
                            .setDefinition(ThingsModelFactory.newDefinition(command.getThingDefinitionUrl()))
                            .build();
                });
    }


    private Thing extractDefinitions(final Thing thing) {
        var thingBuilder = ThingsModelFactory.newThingBuilder();
        thingBuilder.setDefinition(thing.getDefinition().orElseThrow());
        thing.getFeatures().orElseGet(ThingsModelFactory::emptyFeatures).forEach(feature -> {
            FeatureDefinition featureDefinition = feature.getDefinition().orElse(null);
            thingBuilder.setFeature(feature.getId(), featureDefinition, null);
        });

        return thingBuilder.build();
    }


    /**
     * Resolves conflicts between a skeleton Thing and an existing Thing while optionally initializing properties.
     * If initialization is disabled, only definitions from the skeleton are extracted. Otherwise, conflicting
     * fields are removed, and a new Thing is created with the refined values.
     *
     * @param existingThing The existing Thing to compare against.
     * @param skeletonThing The skeleton Thing containing default values.
     * @param isInitializeProperties A flag indicating whether properties should be initialized.
     * @return A new Thing with conflicts resolved and properties optionally initialized.
     */
    private Thing resolveSkeletonConflicts(final Thing existingThing, final Thing skeletonThing,
            final boolean isInitializeProperties) {

        if (!isInitializeProperties) {
            return extractDefinitions(skeletonThing);
        }

        final var refinedDefaults = removeConflicts(skeletonThing.toJson(), existingThing.toJson().asObject());

        return ThingsModelFactory.newThing(refinedDefaults);
    }


    /**
     * Removes conflicting fields from the default values by recursively comparing them with existing values.
     * Fields containing "definition" are always retained. If a field exists in both JSON objects and is a nested
     * object, the function will recursively filter out conflicting values. If a field does not exist in the
     * existing values, it is retained from the default values.
     *
     * @param defaultValues The JsonObject containing the default values.
     * @param existingValues The JsonObject containing the existing values to compare against.
     * @return A new JsonObject with conflicts removed, preserving necessary fields.
     */
    public static JsonObject removeConflicts(final JsonObject defaultValues, final JsonObject existingValues) {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();

        if (defaultValues.isNull() && existingValues.isNull()) {
            return JsonFactory.nullObject();
        }

        for (JsonField field : defaultValues) {
            final JsonKey key = field.getKey();
            final JsonValue defaultValue = field.getValue();
            final Optional<JsonValue> maybeExistingValue = existingValues.getValue(key);

            if (key.toString().contains("definition")) {
                builder.set(key, defaultValue);
                continue;
            }

            if (maybeExistingValue.isPresent()) {
                Optional<JsonValue> resolvedValue = resolveConflictingValues(defaultValue, maybeExistingValue.get());
                resolvedValue.ifPresent(value -> {
                    if (!isEmptyObject(value)) {
                        builder.set(key, value);
                    }
                });
            } else {
                if (!isEmptyObject(defaultValue)) {
                    builder.set(field);
                }
            }
        }

        return builder.build();
    }

    /**
     * Resolves conflicting JsonValue objects by recursively comparing them.
     * If both values are JsonObjects, it calls {@link #removeConflicts(JsonObject, JsonObject)}
     * to recursively filter out conflicting values. Otherwise, it returns an empty Optional,
     * indicating that the value should not be retained.
     *
     * @param defaultValue The JsonValue from the default values object.
     * @param existingValue The JsonValue from the existing values object.
     * @return An Optional containing a filtered JsonObject if both values are objects; otherwise, an empty Optional.
     */
    private static Optional<JsonValue> resolveConflictingValues(final JsonValue defaultValue,
            final JsonValue existingValue) {
        return (defaultValue.isObject() && existingValue.isObject())
                ? Optional.of(removeConflicts(defaultValue.asObject(), existingValue.asObject()))
                : Optional.empty();
    }

    private static boolean isEmptyObject(final JsonValue value) {
        return value.isObject() && value.asObject().isEmpty();
    }

    private Thing applyMigrationPayload(final Context<ThingId> context, final Thing thing,
            final JsonObject migrationPayload,
            final long nextRevision,
            final Instant eventTs) {
        final JsonObject thingJson = thing.toJson(FieldType.all());
        final JsonObject mergedJson = JsonFactory.newObject(migrationPayload, thingJson);
        context.getLog().debug("Thing updated from migrated JSON: {}", mergedJson);

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
