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
package org.eclipse.ditto.internal.utils.metrics.instruments.tag;

import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;

import scala.jdk.javaapi.CollectionConverters;

/**
 * A utility class to convert between {@link TagSet} and {@link kamon.tag.TagSet}.
 */
@Immutable
public final class KamonTagSetConverter {

    private KamonTagSetConverter() {
        throw new AssertionError();
    }

    /**
     * Converts the specified Ditto {@code TagSet} to a Kamon TagSet.
     *
     * @param dittoTagSet the Ditto TagSet to be converted to a Kamon TagSet.
     * @return the Kamon TagSet.
     * @throws NullPointerException if {@code dittoTagSet} is {@code null}.
     */
    public static kamon.tag.TagSet getKamonTagSet(final TagSet dittoTagSet) {
        ConditionChecker.checkNotNull(dittoTagSet, "dittoTagSet");
        final var builder = kamon.tag.TagSet.builder();
        dittoTagSet.forEach(tag -> builder.add(tag.getKey(), tag.getValue()));
        return builder.build();
    }

    /**
     * Converts the specified Kamon TagSet to a Ditto {@code TagSet}.
     *
     * @param kamonTagSet the Kamon TagSet to be converted to a Ditto TagSet.
     * @return the Ditto TagSet.
     * @throws NullPointerException if {@code kamonTagSet} is {@code null}.
     */
    public static TagSet getDittoTagSet(final kamon.tag.TagSet kamonTagSet) {
        ConditionChecker.checkNotNull(kamonTagSet, "kamonTagSet");
        return TagSet.ofTagCollection(
                CollectionConverters.asJava(kamonTagSet.all())
                        .stream()
                        .map(kamonTag -> Tag.of(kamonTag.key(), String.valueOf(kamon.tag.Tag.unwrapValue(kamonTag))))
                        .collect(Collectors.toList())
        );
    }

}
