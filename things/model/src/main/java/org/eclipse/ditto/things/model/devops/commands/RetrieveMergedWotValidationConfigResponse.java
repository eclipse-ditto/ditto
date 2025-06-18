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
package org.eclipse.ditto.things.model.devops.commands;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.base.model.signals.commands.WithEntity;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;

/**
 * Response to a {@link RetrieveMergedWotValidationConfig} command.
 * <p>
 * This response contains a set of WoT validation configurations that have been merged with static
 * configuration. The merged configs represent the effective validation settings that will be applied, following
 * these merging rules:
 * </p>
 * <ul>
 *     <li>Dynamic configuration takes precedence over static configuration in case of conflicts</li>
 *     <li>Top-level settings (enabled, logWarning) are merged with dynamic settings taking precedence</li>
 *     <li>Thing validation settings are merged with dynamic settings taking precedence</li>
 *     <li>Feature validation settings are merged with dynamic settings taking precedence</li>
 *     <li>Dynamic configuration sections are combined from both sources</li>
 * </ul>
 *
 * @since 3.8.0
 */
@Immutable
@JsonParsableCommandResponse(type = RetrieveMergedWotValidationConfigResponse.TYPE)
public final class RetrieveMergedWotValidationConfigResponse
        extends AbstractWotValidationConfigCommandResponse<RetrieveMergedWotValidationConfigResponse>
        implements WithEntity<RetrieveMergedWotValidationConfigResponse> {

    static final String TYPE = TYPE_PREFIX + RetrieveMergedWotValidationConfig.NAME;

    /**
     * JSON field definition for the config object.
     * This field contains the merged validation configuration.
     */
    private static final JsonFieldDefinition<JsonValue> JSON_CONFIG =
            JsonFieldDefinition.ofJsonValue("config", JsonSchemaVersion.V_2);

    /**
     * JSON deserializer for this response.
     * Handles deserialization of the response from JSON format, converting the config object
     * into an {@link WotValidationConfig} object.
     */
    private static final CommandResponseJsonDeserializer<RetrieveMergedWotValidationConfigResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        final WotValidationConfigId configId = WotValidationConfigId.of(jsonObject
                                .getValueOrThrow(WotValidationConfigCommand.JsonFields.CONFIG_ID)
                        );
                        final JsonObject configObj = jsonObject.getValue(JSON_CONFIG)
                                .orElseThrow(() -> new IllegalArgumentException("Missing 'config' field"))
                                .asObject();
                        final WotValidationConfig config = WotValidationConfig.fromJson(configObj);
                        return new RetrieveMergedWotValidationConfigResponse(configId, config,
                                context.getDittoHeaders());
                    });

    private final WotValidationConfig config;

    /**
     * Constructs a new {@code RetrieveMergedWotValidationConfigResponse} object.
     *
     * @param configId the ID of the config to retrieve the merged config for.
     * @param config the merged validation config, where the config represents the effective
     * validation settings after merging dynamic and static configurations.
     * @param dittoHeaders the headers of the command response.
     */
    private RetrieveMergedWotValidationConfigResponse(final WotValidationConfigId configId,
            final WotValidationConfig config,
            final DittoHeaders dittoHeaders) {
        super(TYPE, HttpStatus.OK, configId, dittoHeaders);
        this.config = checkNotNull(config, "config");
    }

    /**
     * Creates a new {@code RetrieveMergedWotValidationConfigResponse} object.
     *
     * @param config the merged validation config, where the config represents the effective
     * validation settings after merging dynamic and static configurations.
     * @param dittoHeaders the headers of the command response.
     * @return the command response.
     */
    public static RetrieveMergedWotValidationConfigResponse of(final WotValidationConfig config,
            final DittoHeaders dittoHeaders) {
        return new RetrieveMergedWotValidationConfigResponse(config.getConfigId(), config, dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveMergedWotValidationConfigResponse} from a JSON object.
     * The JSON object should contain the following field:
     * <ul>
     *     <li>{@code config} (required): The merged validation configuration, where the config
     *         represents the effective validation settings after merging dynamic and static configurations</li>
     * </ul>
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the command response.
     * @return the response.
     */
    public static RetrieveMergedWotValidationConfigResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    /**
     * Returns the merged validation config.
     * The config represents the effective validation settings after merging dynamic and static
     * configurations, following the merging rules defined in the class documentation.
     *
     * @return the merged validation config.
     */
    public WotValidationConfig getConfig() {
        return config;
    }

    @Override
    public RetrieveMergedWotValidationConfigResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(config, dittoHeaders);
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        super.appendPayload(jsonObjectBuilder, schemaVersion, thePredicate);
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_CONFIG, config.toJson(), predicate);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final RetrieveMergedWotValidationConfigResponse that = (RetrieveMergedWotValidationConfigResponse) o;
        return Objects.equals(config, that.config);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveMergedWotValidationConfigResponse;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), config);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", config=" + config +
                "]";
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return config.toJson();
    }

    @Override
    public RetrieveMergedWotValidationConfigResponse setEntity(final JsonValue entity) {
        if (entity.isObject()) {
            final WotValidationConfig newConfig = WotValidationConfig.fromJson(entity.asObject());
            return of(newConfig, getDittoHeaders());
        } else {
            throw new IllegalArgumentException("Expected a JsonObject for config entity");
        }
    }
} 