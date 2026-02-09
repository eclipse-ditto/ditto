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
package org.eclipse.ditto.wot.model;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonObject;

/**
 * SecurityDefinitions is a container for named {@link SecurityScheme}s.
 *
 * @since 2.4.0
 */
public interface SecurityDefinitions extends Map<String, SecurityScheme>, Jsonifiable<JsonObject> {

    /**
     * Creates a SecurityDefinitions from the specified JSON object.
     *
     * @param jsonObject the JSON object mapping scheme names to security schemes.
     * @return the SecurityDefinitions.
     */
    static SecurityDefinitions fromJson(final JsonObject jsonObject) {
        return of(jsonObject.stream().collect(Collectors.toMap(
                field -> field.getKey().toString(),
                field -> SecurityScheme.fromJson(field.getKey().toString(), field.getValue().asObject()),
                (u, v) -> {
                    throw WotThingModelInvalidException.newBuilder(String.format("SecuritySchemes: Duplicate key %s", u))
                            .build();
                },
                LinkedHashMap::new)
        ));
    }

    /**
     * Creates a SecurityDefinitions from the specified collection of security schemes.
     *
     * @param securityDefinitions the collection of security schemes.
     * @return the SecurityDefinitions.
     */
    static SecurityDefinitions from(final Collection<SecurityScheme> securityDefinitions) {
        return of(securityDefinitions.stream().collect(Collectors.toMap(
                SecurityScheme::getSecuritySchemeName,
                s -> s,
                (u, v) -> {
                    throw WotThingModelInvalidException.newBuilder(String.format("SecuritySchemes: Duplicate key %s", u))
                            .build();
                },
                LinkedHashMap::new)
        ));
    }

    /**
     * Creates a SecurityDefinitions from the specified map of scheme names to security schemes.
     *
     * @param securityDefinitions the map of scheme names to security schemes.
     * @return the SecurityDefinitions.
     */
    static SecurityDefinitions of(final Map<String, SecurityScheme> securityDefinitions) {
        return new ImmutableSecurityDefinitions(securityDefinitions);
    }

    /**
     * Returns the security definition with the specified name.
     *
     * @param securityDefinitionName the name of the security definition.
     * @return the optional security scheme.
     */
    Optional<SecurityScheme> getSecurityDefinition(CharSequence securityDefinitionName);

}
