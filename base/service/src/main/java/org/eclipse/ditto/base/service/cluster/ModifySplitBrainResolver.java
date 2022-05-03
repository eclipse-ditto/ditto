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

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.AbstractCommand;
import org.eclipse.ditto.base.model.signals.commands.CommandJsonDeserializer;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;

@JsonParsableCommand(typePrefix = ModifySplitBrainResolver.PREFIX, name = ModifySplitBrainResolver.NAME)
public final class ModifySplitBrainResolver extends AbstractCommand<ModifySplitBrainResolver> {

    public static final JsonFieldDefinition<Boolean> ENABLED_FIELD_KEY =
            JsonFieldDefinition.ofBoolean("enabled", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final String PREFIX = "ditto.sbr:";
    static final String NAME = "modify";
    public static final String TYPE = PREFIX + NAME;

    private final boolean enabled;

    private ModifySplitBrainResolver(final DittoHeaders dittoHeaders, final boolean enabled) {
        super(TYPE, dittoHeaders);
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public static ModifySplitBrainResolver fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<ModifySplitBrainResolver>(TYPE, jsonObject).deserialize(() -> {
            final boolean enabled = jsonObject.getValue(ENABLED_FIELD_KEY).orElseThrow();

            return new ModifySplitBrainResolver(dittoHeaders, enabled);
        });
    }

    static ModifySplitBrainResolver of(final boolean enabled) {
        return new ModifySplitBrainResolver(DittoHeaders.empty(), enabled);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {
        jsonObjectBuilder.set(ENABLED_FIELD_KEY, enabled, schemaVersion.and(predicate));
    }

    @Override
    public String getTypePrefix() {
        return PREFIX;
    }

    @Override
    public Category getCategory() {
        return Category.MODIFY;
    }

    @Override
    public ModifySplitBrainResolver setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new ModifySplitBrainResolver(dittoHeaders, enabled);
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
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ModifySplitBrainResolver;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) return false;
        final ModifySplitBrainResolver that = (ModifySplitBrainResolver) o;
        return enabled == that.enabled;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), enabled);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "enabled=" + enabled +
                "]";
    }

}
