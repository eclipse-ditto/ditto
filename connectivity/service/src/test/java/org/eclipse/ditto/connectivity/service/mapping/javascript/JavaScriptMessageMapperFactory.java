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
package org.eclipse.ditto.connectivity.service.mapping.javascript;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.connectivity.service.mapping.MessageMapper;
import org.eclipse.ditto.json.JsonValue;
import org.mockito.Mockito;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Factory for creating instances of {@link JavaScriptMessageMapperRhino} and configurations of it.
 */
@Immutable
public final class JavaScriptMessageMapperFactory {

    private JavaScriptMessageMapperFactory() {
        throw new AssertionError();
    }

    /**
     * Creates a MessageMapperConfigurationBuilder for JavaScript.
     *
     * @param id the id of the mapper
     * @param properties the Map of configuration properties to initialize the builder with
     * @return the ConfigurationBuilder
     */
    public static JavaScriptMessageMapperConfiguration.Builder createJavaScriptMessageMapperConfigurationBuilder(
            final String id, final Map<String, String> properties) {

        final Map<String, JsonValue> jsonProperties = properties.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> JsonValue.of(entry.getValue())));
        return new ImmutableJavaScriptMessageMapperConfiguration.Builder(id, jsonProperties, Collections.emptyMap(), Collections.emptyMap());
    }

    /**
     * Creates a new JavaScript MessageMapper based on the Rhino engine.
     *
     * @return the new JavaScript MessageMapper
     */
    public static MessageMapper createJavaScriptMessageMapperRhino() {
        return new JavaScriptMessageMapperRhino(Mockito.mock(ActorSystem.class), Mockito.mock(Config.class));
    }

}
