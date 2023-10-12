/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.headers.conditional;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.Entity;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.IfEqual;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.WithOptionalEntity;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonMergePatch;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;

/**
 * Custom Ditto {@code if-equal} precondition header supporting available strategies defined in {@link org.eclipse.ditto.base.model.headers.IfEqual}.
 * The default is to always {@link IfEqual#UPDATE update} the entity, no matter if the update will lead to the
 * same state. Another option is to {@link IfEqual#SKIP skip} updating an entity when it would be {@code equal}
 * after the change.
 *
 * @param <C> the type of the handled {@link Command}.
 * @since 3.3.0
 */
@Immutable
public final class IfEqualPreconditionHeader<C extends Command<?>> implements PreconditionHeader<Entity<?>> {

    private static final String IF_EQUAL_KEY = DittoHeaderDefinition.IF_EQUAL.getKey();

    private final C command;
    private final IfEqual ifEqual;
    private final ConditionalHeadersValidator.ValidationSettings validationSettings;

    private IfEqualPreconditionHeader(final C command, final IfEqual ifEqual,
            final ConditionalHeadersValidator.ValidationSettings validationSettings) {
        this.command = checkNotNull(command, "command");
        this.ifEqual = checkNotNull(ifEqual, "ifEqual");
        this.validationSettings = checkNotNull(validationSettings, "validationSettings");
    }

    /**
     * Extracts an {@link IfEqualPreconditionHeader} from the given {@code command} if present.
     *
     * @param command The command containing potentially the {@code if-equal} and the value to modify
     * @param validationSettings the settings.
     * @param <C> the type of the handled {@link Command}.
     * @return Optional of {@link IfEqualPreconditionHeader}. Empty if the given {@code command} don't contain an
     * {@code if-equal} or if it is not a modifying command.
     */
    public static <C extends Command<?>> Optional<IfEqualPreconditionHeader<C>> fromDittoHeaders(
            final C command,
            final ConditionalHeadersValidator.ValidationSettings validationSettings) {

        final Command.Category category = command.getCategory();
        if (category == Command.Category.MODIFY || category == Command.Category.MERGE) {
            return command.getDittoHeaders().getIfEqual()
                    .map(ifEqual -> new IfEqualPreconditionHeader<>(command, ifEqual, validationSettings));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public String getKey() {
        return IF_EQUAL_KEY;
    }

    @Override
    public String getValue() {
        return ifEqual.toString();
    }

    /**
     * Indicates whether this {@link IfEqualPreconditionHeader} passes for the given {@code entity}.
     * This means that the {@code command} field which is currently processed would not change the passed {@code entity},
     * so the targeted desired state change indicated by the command is already present.
     *
     * @param entity The entity for which the equality condition should be met.
     * @return True if the equality condition is met. False if not.
     */
    @Override
    public boolean meetsConditionFor(@Nullable final Entity<?> entity) {

        if (entity == null) {
            return false;
        }

        if (ifEqual == IfEqual.SKIP || ifEqual == IfEqual.SKIP_MINIMIZING_MERGE) {
            if (command.getCategory() == Command.Category.MODIFY &&
                    command instanceof WithOptionalEntity<?> withOptionalEntity) {
                return meetsConditionForModifyCommand(entity, withOptionalEntity);
            } else if (command.getCategory() == Command.Category.MERGE &&
                    command instanceof WithOptionalEntity<?> withOptionalEntity) {
                return meetsConditionForMergeCommand(entity, withOptionalEntity);
            } else {
                // other commands to "MODIFY" and "MERGE" do never match the "if-equal" precondition header
                return false;
            }
        } else {
            // for previous default behavior, "if-equal: update", don't match:
            return false;
        }
    }

    private Boolean meetsConditionForModifyCommand(final Entity<?> entity,
            final WithOptionalEntity<?> withOptionalEntity) {

        return withOptionalEntity.getEntity()
                .map(newValue -> {
                    final Predicate<JsonField> fieldPredicate = calculatePredicate(command.getResourcePath());
                    final Optional<JsonValue> previousValue =
                            entity.toJson(JsonSchemaVersion.LATEST, fieldPredicate)
                                    .getValue(command.getResourcePath());
                    final JsonValue adjustedNewValue;
                    if (newValue.isObject()) {
                        adjustedNewValue = newValue.asObject()
                                .filter(calculatePredicateForNew(command.getResourcePath()));
                    } else {
                        adjustedNewValue = newValue;
                    }
                    return previousValue.filter(jsonValue -> jsonValue.equals(adjustedNewValue))
                            .isPresent();
                })
                .orElse(false);
    }

    private Boolean meetsConditionForMergeCommand(final Entity<?> entity,
            final WithOptionalEntity<?> withOptionalEntity) {

        return withOptionalEntity.getEntity()
                .map(newValue -> {
                    final Optional<JsonValue> previousValue = entity.toJson().getValue(command.getResourcePath());
                    if (newValue.isObject()) {
                        final JsonObject newObject;
                        if (command.getResourcePath().isEmpty()) {
                            newObject = newValue.asObject()
                                    .stream()
                                    .filter(calculatePredicateForNew(command.getResourcePath()))
                                    .collect(JsonCollectors.fieldsToObject());
                        } else {
                            newObject = newValue.asObject();
                        }
                        return previousValue.filter(JsonValue::isObject)
                                .map(JsonValue::asObject)
                                .filter(previousObject -> {
                                    final JsonObject patchedAndSortedNewObject =
                                            JsonFactory.mergeJsonValues(newObject, previousObject)
                                                    .asObject().stream()
                                                    .sorted(Comparator.comparing(j -> j.getKey().toString()))
                                                    .collect(JsonCollectors.fieldsToObject());
                                    final JsonObject sortedOldObject = previousObject.stream()
                                            .sorted(Comparator.comparing(j -> j.getKey().toString()))
                                            .collect(JsonCollectors.fieldsToObject());
                                    return patchedAndSortedNewObject.equals(sortedOldObject);
                                }).isPresent();
                    } else {
                        return previousValue.filter(jsonValue -> jsonValue.equals(newValue))
                                .isPresent();
                    }
                })
                .orElse(false);
    }

    /**
     * Handles the {@link #command} field of this class by invoking the passed {@code isCompletelyEqualSupplier} to
     * check whether the affected entity would be completely equal after applying the {@link #command}.
     *
     * @param isCompletelyEqualSupplier a boolean supplier for evaluating lazily whether this command's modifications
     * would lead to the equal entity than it was before.
     * @param entity the previous entity.
     * @return the potentially adjusted Command.
     */
    C handleCommand(final BooleanSupplier isCompletelyEqualSupplier, @Nullable final Entity<?> entity) {

        final C potentiallyAdjustedCommand;
        final Command.Category category = command.getCategory();
        if (ifEqual == IfEqual.UPDATE) {
            // default behavior - no change, just use the complete modify command, not matter what:
            potentiallyAdjustedCommand = command;
        } else if (ifEqual == IfEqual.SKIP_MINIMIZING_MERGE && category == Command.Category.MERGE) {
            // lazily check for equality as this might be expensive to do:
            final boolean completelyEqual = isCompletelyEqualSupplier.getAsBoolean();
            if (completelyEqual) {
                potentiallyAdjustedCommand = respondWithPreconditionFailed();
            } else {
                // not completely equal and a merge command
                potentiallyAdjustedCommand = adjustMergeCommandByOnlyKeepingChanges(command, entity);
            }
        } else if (ifEqual == IfEqual.SKIP) {
            // lazily check for equality as this might be expensive to do:
            final boolean completelyEqual = isCompletelyEqualSupplier.getAsBoolean();
            if (completelyEqual &&
                    (category == Command.Category.MODIFY || category == Command.Category.MERGE)) {
                potentiallyAdjustedCommand = respondWithPreconditionFailed();
            } else {
                potentiallyAdjustedCommand = command;
            }
        } else {
            potentiallyAdjustedCommand = command;
        }
        return potentiallyAdjustedCommand;
    }

    private C adjustMergeCommandByOnlyKeepingChanges(final C command, @Nullable final Entity<?> entity) {
        if (null != entity && command instanceof WithOptionalEntity<?> withOptionalEntity) {
            return adjustMergeCommandByOnlyKeepingChanges(command, entity, withOptionalEntity);
        } else {
            return command;
        }
    }

    private C adjustMergeCommandByOnlyKeepingChanges(final C command,
            final Entity<?> entity,
            final WithOptionalEntity<?> withOptionalEntity) {

        return withOptionalEntity.getEntity()
                .map(newValue -> {
                    final Predicate<JsonField> fieldPredicate = calculatePredicate(command.getResourcePath());
                    final JsonValue oldValue = entity.toJson(JsonSchemaVersion.LATEST, fieldPredicate)
                            .getValue(command.getResourcePath()).orElse(null);
                    if (null == oldValue) {
                        return command;
                    } else if (command instanceof WithOptionalEntity<?> commandWithEntity) {
                        return JsonMergePatch.compute(oldValue, newValue, false)
                                .map(jsonMergePatch -> {
                                    final JsonValue jsonValue = jsonMergePatch.asJsonValue();
                                    return (C) commandWithEntity.setEntity(jsonValue);
                                })
                                .orElse(command);
                    } else {
                        return command;
                    }
                })
                .orElse(command);
    }

    private static Predicate<JsonField> calculatePredicate(final JsonPointer resourcePath) {
        if (resourcePath.isEmpty()) {
            return FieldType.notHidden()
                    .and(Predicate.not(jsonField -> jsonField.getKey().equals(JsonKey.of("thingId"))))
                    .and(Predicate.not(jsonField -> jsonField.getKey().equals(JsonKey.of("policyId"))));
        } else {
            return FieldType.notHidden();
        }
    }

    private static Predicate<JsonField> calculatePredicateForNew(final JsonPointer resourcePath) {
        if (resourcePath.isEmpty()) {
            // filter "special fields" for e.g. on thing level the inline "_policy":
            return jsonField -> !jsonField.getKeyName().startsWith("_");
        } else {
            return jsonField -> true;
        }
    }

    private C respondWithPreconditionFailed() {
        throw validationSettings
                .createPreconditionFailedForEqualityExceptionBuilder()
                .dittoHeaders(command.getDittoHeaders())
                .build();
    }

}
