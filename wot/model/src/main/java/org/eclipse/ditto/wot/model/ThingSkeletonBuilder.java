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

import java.time.Instant;
import java.util.Collection;

import javax.annotation.Nullable;

/**
 * Contains common state/behavior shared by {@link ThingDescription.Builder} and {@link ThingModel.Builder}.
 *
 * @param <B> the type of the ThingSkeletonBuilder.
 * @param <T> the type of the built ThingSkeleton.
 * @since 2.4.0
 */
public interface ThingSkeletonBuilder<B extends ThingSkeletonBuilder<B, T>, T extends ThingSkeleton<T>> extends
        TypedJsonObjectBuilder<B, T> {

    B setAtContext(AtContext atContext);

    B setAtType(@Nullable AtType atType);

    B setId(@Nullable IRI id);

    B setTitle(@Nullable Title title);

    B setTitles(@Nullable Titles titles);

    B setDescription(@Nullable Description description);

    B setDescriptions(@Nullable Descriptions descriptions);

    B setVersion(@Nullable Version version);

    B setBase(@Nullable IRI base);

    B setLinks(@Nullable Links links);

    B setProperties(@Nullable Properties properties);

    B setActions(@Nullable Actions actions);

    B setEvents(@Nullable Events events);

    B setLinks(Collection<BaseLink<?>> links);

    B setForms(Collection<RootFormElement> forms);

    B setForms(@Nullable RootForms forms);

    B setUriVariables(@Nullable UriVariables uriVariables);

    B setSecurityDefinitions(@Nullable SecurityDefinitions securityDefinitions);

    B setSchemaDefinitions(@Nullable SchemaDefinitions schemaDefinitions);

    B setSupport(@Nullable IRI support);

    B setCreated(@Nullable Instant created);

    B setModified(@Nullable Instant modified);

    B setSecurity(@Nullable Security security);

    B setProfile(@Nullable Profile profile);

}
