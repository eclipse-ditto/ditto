/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocoladapter;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Implementation of an empty {@link TopicPath}.
 */
@Immutable
final class EmptyTopicPath implements TopicPath {

    private EmptyTopicPath() {
        super();
    }

    /**
     * Returns an instance of {@code EmptyTopicPath}.
     *
     * @return the instance.
     */
    static EmptyTopicPath getInstance() {
        return new EmptyTopicPath();
    }

    /**
     * @return always {@code null}.
     */
    @Override
    @Nullable
    public String getNamespace() {
        return null;
    }

    /**
     * @return always {@code null}.
     */
    @Override
    @Nullable
    public String getEntityName() {
        return null;
    }

    /**
     * @return always {@code null}.
     */
    @Override
    @Nullable
    public Group getGroup() {
        return null;
    }

    /**
     * @return always {@code null}.
     */
    @Override
    @Nullable
    public Criterion getCriterion() {
        return null;
    }

    /**
     * @return always {@code null}.
     */
    @Override
    @Nullable
    public Channel getChannel() {
        return null;
    }

    /**
     * @return always an empty Optional.
     */
    @Override
    public Optional<Action> getAction() {
        return Optional.empty();
    }

    /**
     * @return always an empty Optional.
     */
    @Override
    public Optional<SearchAction> getSearchAction() {
        return Optional.empty();
    }

    /**
     * @return always an empty Optional.
     */
    @Override
    public Optional<String> getSubject() {
        return Optional.empty();
    }

    /**
     * @return always an empty String.
     */
    @Override
    public String getPath() {
        return "";
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        return this == o || !(o == null || getClass() != o.getClass());
    }

    @Override
    public int hashCode() {
        return EmptyTopicPath.class.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[]";
    }

}
