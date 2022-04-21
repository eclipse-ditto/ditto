/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.service.cluster;

import java.util.function.Predicate;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommandResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;

@JsonParsableCommandResponse(type = ModifySplitBrainResolverResponse.TYPE)
public final class ModifySplitBrainResolverResponse
        extends AbstractCommandResponse<ModifySplitBrainResolverResponse> {

    private static final JsonFieldDefinition<Boolean> ENABLED =
            JsonFieldDefinition.ofBoolean("enabled", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final String PREFIX = "ditto.sbr:";
    static final String NAME = "modifyResponse";
    public static final String TYPE = PREFIX + NAME;

    private static final CommandResponseJsonDeserializer<ModifySplitBrainResolverResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final JsonObject jsonObject = context.getJsonObject();
                        final boolean enabled = jsonObject.getValue(ENABLED).orElseThrow();
                        return new ModifySplitBrainResolverResponse(context.getDittoHeaders(), enabled);
                    });

    private final boolean enabled;

    private ModifySplitBrainResolverResponse(final DittoHeaders dittoHeaders, final boolean enabled) {
        super(TYPE, HttpStatus.OK, dittoHeaders);
        this.enabled = enabled;
    }

    static ModifySplitBrainResolverResponse of(final ModifySplitBrainResolver modifySplitBrainResolver) {
        return new ModifySplitBrainResolverResponse(modifySplitBrainResolver.getDittoHeaders(),
                modifySplitBrainResolver.isEnabled());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public static ModifySplitBrainResolverResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {
        jsonObjectBuilder.set(ENABLED, enabled, schemaVersion.and(predicate));
    }

    @Override
    public JsonPointer getResourcePath() {
        return JsonPointer.empty();
    }

    @Override
    public String getResourceType() {
        return "sbr";
    }

    @Override
    public ModifySplitBrainResolverResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new ModifySplitBrainResolverResponse(dittoHeaders, enabled);
    }

}
