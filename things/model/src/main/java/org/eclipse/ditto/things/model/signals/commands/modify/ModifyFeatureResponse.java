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
package org.eclipse.ditto.things.model.signals.commands.modify;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseHttpStatusValidator;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;

/**
 * Response to a {@link ModifyFeature} command.
 */
@Immutable
@JsonParsableCommandResponse(type = ModifyFeatureResponse.TYPE)
public final class ModifyFeatureResponse extends AbstractCommandResponse<ModifyFeatureResponse>
        implements ThingModifyCommandResponse<ModifyFeatureResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = ThingCommandResponse.TYPE_PREFIX + ModifyFeature.NAME;

    static final JsonFieldDefinition<String> JSON_FEATURE_ID =
            JsonFieldDefinition.ofString("featureId", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonObject> JSON_FEATURE =
            JsonFieldDefinition.ofJsonObject("feature", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final Set<HttpStatus> HTTP_STATUSES;

    static {
        final Set<HttpStatus> httpStatuses = new HashSet<>();
        Collections.addAll(httpStatuses, HttpStatus.CREATED, HttpStatus.NO_CONTENT);
        HTTP_STATUSES = Collections.unmodifiableSet(httpStatuses);
    }

    private static final CommandResponseJsonDeserializer<ModifyFeatureResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();

                        final ThingId thingId =
                                ThingId.of(jsonObject.getValueOrThrow(ThingCommandResponse.JsonFields.JSON_THING_ID));

                        final JsonObject featureJsonObject = jsonObject.getValueOrThrow(JSON_FEATURE);
                        final String extractedFeatureId = jsonObject.getValueOrThrow(JSON_FEATURE_ID);
                        final Feature feature;
                        if (featureJsonObject.isNull()) {
                            feature = null;
                        } else {
                            feature = ThingsModelFactory.newFeatureBuilder(featureJsonObject)
                                    .useId(extractedFeatureId)
                                    .build();
                        }

                        return newInstance(thingId,
                                feature,
                                extractedFeatureId,
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders());
                    });

    private final ThingId thingId;
    private final String featureId;
    @Nullable private final Feature feature;

    private ModifyFeatureResponse(final ThingId thingId,
            @Nullable final Feature feature,
            final String featureId,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, httpStatus, dittoHeaders);
        this.thingId = checkNotNull(thingId, "thingId");
        this.featureId = ConditionChecker.checkArgument(featureId,
                fid -> !featureId.trim().isEmpty(),
                () -> "The featureId must neither be empty nor blank.");
        this.feature = feature;
        if (HttpStatus.NO_CONTENT.equals(httpStatus) && null != feature) {
            throw new IllegalArgumentException(
                    MessageFormat.format("Feature <{0}> is illegal in conjunction with <{1}>.", feature, httpStatus)
            );
        }
    }

    /**
     * Returns a new {@code ModifyFeatureResponse} for a created Feature. This corresponds to the HTTP status
     * {@link org.eclipse.ditto.base.model.common.HttpStatus#CREATED}.
     *
     * @param thingId the Thing ID of the created feature.
     * @param feature the created Feature.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for a created Feature.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyFeatureResponse created(final ThingId thingId,
            final Feature feature,
            final DittoHeaders dittoHeaders) {

        return newInstance(thingId,
                checkNotNull(feature, "feature"),
                feature.getId(),
                HttpStatus.CREATED,
                dittoHeaders);
    }

    /**
     * Returns a new {@code ModifyFeatureResponse} for a modified Feature. This corresponds to the HTTP status
     * {@link org.eclipse.ditto.base.model.common.HttpStatus#NO_CONTENT}.
     *
     * @param thingId the Thing ID of the modified feature.
     * @param featureId the identifier of the modified Feature.
     * @param dittoHeaders the headers of the ThingCommand which caused the new response.
     * @return a command response for a modified Feature.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ModifyFeatureResponse modified(final ThingId thingId,
            final String featureId,
            final DittoHeaders dittoHeaders) {

        return newInstance(thingId, null, featureId, HttpStatus.NO_CONTENT, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code ModifyFeatureResponse} for the specified arguments.
     *
     * @param thingId the ID of the thing the feature belongs to.
     * @param feature the created feature value or {@code null} if an existing feature was modified.
     * @param featureId ID of the modified feature or {@code null} if a new feature was created.
     * @param httpStatus the status of the response.
     * @param dittoHeaders the headers of the response.
     * @return the {@code ModifyFeatureResponse} instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code httpStatus} is not allowed for a {@code ModifyFeatureResponse} or
     * if {@code httpStatus} contradicts {@code feature} or {@code featureId}.
     * @since 2.3.0
     */
    public static ModifyFeatureResponse newInstance(final ThingId thingId,
            @Nullable final Feature feature,
            final String featureId,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        if (null != feature && !Objects.equals(feature.getId(), featureId)) {
            final String pattern = "Provided feature ID <{0}> differs from ID of provided feature <{1}>.";
            throw new IllegalArgumentException(MessageFormat.format(pattern, featureId, feature.getId()));
        }

        return new ModifyFeatureResponse(thingId,
                feature,
                featureId,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        HTTP_STATUSES,
                        ModifyFeatureResponse.class),
                dittoHeaders);
    }

    /**
     * Creates a response to a {@link ModifyFeature} command from a JSON string.
     *
     * @param jsonString the JSON string of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ModifyFeatureResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@link ModifyFeature} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ModifyFeatureResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public ThingId getEntityId() {
        return thingId;
    }

    /**
     * Returns the created {@code Feature}.
     *
     * @return the created Feature.
     */
    public Optional<Feature> getFeatureCreated() {
        final Optional<Feature> result;
        if (null != feature) {
            result = Optional.of(feature);
        } else {
            result = Optional.of(ThingsModelFactory.nullFeature(featureId));
        }
        return result;
    }

    @Override
    public Optional<JsonValue> getEntity(final JsonSchemaVersion schemaVersion) {
        final Optional<JsonValue> result;
        if (null != feature) {
            result = Optional.of(feature.toJson(schemaVersion, FieldType.notHidden()));
        } else {
            result = Optional.of(JsonFactory.nullObject());
        }
        return result;
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/features/" + featureId);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(ThingCommandResponse.JsonFields.JSON_THING_ID, thingId.toString(), predicate);
        jsonObjectBuilder.set(JSON_FEATURE_ID, featureId);
        if (null != feature) {
            jsonObjectBuilder.set(JSON_FEATURE, feature.toJson(schemaVersion, thePredicate), predicate);
        } else {
            jsonObjectBuilder.set(JSON_FEATURE, JsonFactory.nullObject());
        }
    }

    @Override
    public ModifyFeatureResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return newInstance(thingId, feature, featureId, getHttpStatus(), dittoHeaders);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ModifyFeatureResponse that = (ModifyFeatureResponse) o;
        return that.canEqual(this) &&
                Objects.equals(thingId, that.thingId) &&
                Objects.equals(featureId, that.featureId) &&
                Objects.equals(feature, that.feature) &&
                super.equals(that);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thingId, featureId, feature);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", thingId=" + thingId +
                ", featureId=" + featureId +
                ", feature=" + feature +
                "]";
    }

}
