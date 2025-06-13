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

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.WithEntity;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;

/**
 * Response to a {@link RetrieveAllDynamicConfigSections} command.
 * <p>
 * This response contains an array of dynamic config sections from a WoT validation configuration.
 * Each section contains validation settings that can be overridden for a specific scope.
 * </p>
 *
 * @since 3.8.0
 */
@Immutable
@JsonParsableCommandResponse(type = RetrieveAllDynamicConfigSectionsResponse.TYPE)
public final class RetrieveAllDynamicConfigSectionsResponse
        extends AbstractWotValidationConfigCommandResponse<RetrieveAllDynamicConfigSectionsResponse>
        implements WithEntity<RetrieveAllDynamicConfigSectionsResponse> {

    static final String TYPE = TYPE_PREFIX + RetrieveAllDynamicConfigSections.NAME;

    private final JsonArray dynamicConfigs;

    /**
     * Constructs a new {@code RetrieveAllDynamicConfigSectionsResponse} object.
     *
     * @param configId the ID of the WoT validation config.
     * @param dynamicConfigs the array of dynamic config sections.
     * @param dittoHeaders the headers of the response.
     * @throws NullPointerException if any argument is {@code null}.
     */
    private RetrieveAllDynamicConfigSectionsResponse(final WotValidationConfigId configId,
            final JsonArray dynamicConfigs,
            final DittoHeaders dittoHeaders) {
        super(TYPE, HttpStatus.OK, configId, dittoHeaders);
        this.dynamicConfigs = Objects.requireNonNull(dynamicConfigs, "dynamicConfigs");
    }

    /**
     * Creates a new instance of {@code RetrieveAllDynamicConfigSectionsResponse}.
     *
     * @param configId the ID of the WoT validation config.
     * @param dynamicConfigs the array of dynamic config sections.
     * @param dittoHeaders the headers of the response.
     * @return a new RetrieveAllDynamicConfigSectionsResponse.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveAllDynamicConfigSectionsResponse of(final WotValidationConfigId configId,
            final JsonArray dynamicConfigs,
            final DittoHeaders dittoHeaders) {
        return new RetrieveAllDynamicConfigSectionsResponse(configId, dynamicConfigs, dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveAllDynamicConfigSectionsResponse} from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the response.
     * @return the response.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected format.
     */
    public static RetrieveAllDynamicConfigSectionsResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        final WotValidationConfigId configId = WotValidationConfigId.of(
                jsonObject.getValueOrThrow(WotValidationConfigCommand.JsonFields.CONFIG_ID));
        final JsonArray dynamicConfigs =
                jsonObject.getValueOrThrow(WotValidationConfigCommand.JsonFields.VALIDATION_CONFIG).asArray();
        return of(configId, dynamicConfigs, dittoHeaders);
    }

    /**
     * Returns the array of dynamic config sections.
     *
     * @return the array of dynamic config sections.
     */
    public JsonArray getDynamicConfigs() {
        return dynamicConfigs;
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return dynamicConfigs;
    }

    @Override
    public RetrieveAllDynamicConfigSectionsResponse setEntity(final JsonValue entity) {
        if (entity.isArray()) {
            return of(getConfigId(), entity.asArray(), getDittoHeaders());
        }
        throw new IllegalArgumentException("Expected a JsonArray for dynamic configs entity");
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.of("/dynamicConfigs");
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        super.appendPayload(jsonObjectBuilder, schemaVersion, thePredicate);
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(WotValidationConfigCommand.JsonFields.VALIDATION_CONFIG, dynamicConfigs, predicate);
    }

    @Override
    public RetrieveAllDynamicConfigSectionsResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getConfigId(), dynamicConfigs, dittoHeaders);
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
        final RetrieveAllDynamicConfigSectionsResponse that = (RetrieveAllDynamicConfigSectionsResponse) o;
        return Objects.equals(dynamicConfigs, that.dynamicConfigs);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveAllDynamicConfigSectionsResponse;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), dynamicConfigs);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", dynamicConfigs=" + dynamicConfigs +
                "]";
    }
} 