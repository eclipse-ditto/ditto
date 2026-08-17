/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.timeseries.model;

import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

/**
 * Parsed representation of a {@code ditto:timeseries} extension annotation declared on a WoT
 * property in a ThingModel.
 * <p>
 * Tag values are stored verbatim as declared in the WoT model — placeholder expressions like
 * {@code "{{ thing-json:attributes/building }}"} are not resolved here; that happens at ingestion
 * time, on the Things-service side, against the live Thing JSON.
 *
 * @since 4.0.0
 */
public interface WotTimeseriesAnnotation extends Jsonifiable<JsonObject> {

    /**
     * The local (un-prefixed) name of the WoT extension term under which this annotation lives in
     * a property schema. The actual key is {@code <prefix>:timeseries}, where {@code <prefix>} is
     * whatever the ThingModel's {@code @context} binds the Ditto WoT extension IRI to.
     */
    String EXTENSION_LOCAL_NAME = "timeseries";

    /**
     * The WoT extension key under the conventional {@code ditto} prefix. Use
     * {@link #findInProperty(org.eclipse.ditto.json.JsonObject, CharSequence)} with a prefix
     * resolved from the model's {@code @context} when the prefix may differ.
     */
    String EXTENSION_KEY = "ditto:" + EXTENSION_LOCAL_NAME;

    /**
     * Returns a new {@code WotTimeseriesAnnotation}.
     *
     * @param ingest the ingestion mode.
     * @param tags the declared tag map (keys to placeholder or constant string values). Must not
     * be {@code null}; may be empty.
     * @return the new annotation.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static WotTimeseriesAnnotation of(final Ingest ingest, final Map<String, String> tags) {
        return ImmutableWotTimeseriesAnnotation.of(ingest, tags);
    }

    /**
     * Parses a {@code WotTimeseriesAnnotation} from the JSON object value of a {@code
     * ditto:timeseries} extension entry.
     *
     * @param jsonObject the JSON object that is the value of {@code ditto:timeseries}.
     * @return the parsed annotation.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code ingest} is missing.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code ingest} has an unknown value or
     * a tag value is not a JSON string.
     */
    static WotTimeseriesAnnotation fromJson(final JsonObject jsonObject) {
        return ImmutableWotTimeseriesAnnotation.fromJson(jsonObject);
    }

    /**
     * Convenience: extract the {@code ditto:timeseries} annotation from the JSON of a WoT property
     * (i.e. the value at {@code properties.<name>}). Returns empty when the property has no
     * {@code ditto:timeseries} declaration.
     *
     * @param propertySchema the WoT property schema JSON.
     * @return the parsed annotation, or empty.
     * @throws NullPointerException if {@code propertySchema} is {@code null}.
     */
    static Optional<WotTimeseriesAnnotation> findInProperty(final JsonObject propertySchema) {
        return ImmutableWotTimeseriesAnnotation.findInProperty(propertySchema, EXTENSION_KEY);
    }

    /**
     * Like {@link #findInProperty(JsonObject)}, but reads the annotation under an explicit
     * extension key. Use this with a key built from the prefix the ThingModel's {@code @context}
     * binds the Ditto WoT extension to (e.g. {@code "ditto:timeseries"}), so models that alias the
     * extension to a non-default prefix still resolve.
     *
     * @param propertySchema the WoT property schema JSON.
     * @param extensionKey the fully-qualified extension key to look up.
     * @return the parsed annotation, or empty.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static Optional<WotTimeseriesAnnotation> findInProperty(final JsonObject propertySchema,
            final CharSequence extensionKey) {
        return ImmutableWotTimeseriesAnnotation.findInProperty(propertySchema, extensionKey);
    }

    /**
     * @return the ingestion mode declared by this annotation.
     */
    Ingest getIngest();

    /**
     * @return the declared tag map, keyed by tag name. Always non-null; may be empty. The returned
     * map is unmodifiable.
     */
    Map<String, String> getTags();

    /**
     * @return {@code true} when {@link #getIngest()} is {@link Ingest#ALL}; {@code false}
     * otherwise (i.e. {@link Ingest#NONE}).
     */
    default boolean isIngestEnabled() {
        return getIngest() == Ingest.ALL;
    }

    @Override
    default JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[] {JsonSchemaVersion.V_2};
    }

    /**
     * JSON field definitions for {@link WotTimeseriesAnnotation}.
     */
    final class JsonFields {

        /**
         * The ingest-mode token (e.g. {@code "ALL"}).
         */
        public static final JsonFieldDefinition<String> INGEST =
                JsonFactory.newStringFieldDefinition("ingest", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * The tag map. Optional; absent or empty means no declared tags.
         */
        public static final JsonFieldDefinition<JsonObject> TAGS =
                JsonFactory.newJsonObjectFieldDefinition("tags", FieldType.REGULAR, JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }
    }
}
