/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.signals.commands.base;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFieldSelectorInvalidException;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointerInvalidException;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.signals.base.AbstractErrorRegistry;
import org.eclipse.ditto.signals.base.ErrorRegistry;
import org.eclipse.ditto.signals.base.JsonParsable;

/**
 * A {@link ErrorRegistry} aware of common {@link DittoRuntimeException}s.
 */
@Immutable
public final class CommonErrorRegistry extends AbstractErrorRegistry<DittoRuntimeException> {

    private CommonErrorRegistry(final Map<String, JsonParsable<DittoRuntimeException>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * Returns a new {@code CommonErrorRegistry}.
     *
     * @return the error registry.
     */
    public static CommonErrorRegistry newInstance() {
        final Map<String, JsonParsable<DittoRuntimeException>> parseStrategies = new HashMap<>();

        // exceptions in package
        parseStrategies.put(JsonParseException.ERROR_CODE, (jsonObject, dittoHeaders) -> new DittoJsonException(
                JsonParseException.newBuilder().message(getMessage(jsonObject)).build(), dittoHeaders));

        parseStrategies.put(JsonMissingFieldException.ERROR_CODE, (jsonObject, dittoHeaders) -> new DittoJsonException(
                JsonMissingFieldException.newBuilder().message(getMessage(jsonObject)).build(), dittoHeaders));

        parseStrategies.put(JsonFieldSelectorInvalidException.ERROR_CODE,
                (jsonObject, dittoHeaders) -> new DittoJsonException(
                        JsonFieldSelectorInvalidException.newBuilder().message(getMessage(jsonObject)).build(),
                        dittoHeaders));

        parseStrategies.put(JsonPointerInvalidException.ERROR_CODE,
                (jsonObject, dittoHeaders) -> new DittoJsonException(
                        JsonPointerInvalidException.newBuilder().message(getMessage(jsonObject)).build(),
                        dittoHeaders));

        return new CommonErrorRegistry(parseStrategies);
    }


    private static String getMessage(final JsonObject jsonObject) {
        return jsonObject.getValue(DittoJsonException.JsonFields.MESSAGE)
                .orElseThrow(() -> JsonMissingFieldException.newBuilder()
                        .fieldName(DittoRuntimeException.JsonFields.MESSAGE.getPointer().toString()).build());
    }

}
