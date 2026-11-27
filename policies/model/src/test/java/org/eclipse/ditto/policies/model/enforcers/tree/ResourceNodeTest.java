/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.model.enforcers.tree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.EffectedPermissions;
import org.junit.Test;

/**
 * Unit test for {@link ResourceNode}.
 */
public final class ResourceNodeTest {

    private static final String KNOWN_NAME = "name";
    private static final JsonPointer KNOWN_POINTER = JsonPointer.of("/thing/name");
    private static final EffectedPermissions KNOWN_PERMISSIONS =
            EffectedPermissions.newInstance(Collections.singleton("READ"), Collections.singleton("WRITE"));
    private static final EffectedPermissions OTHER_PERMISSIONS =
            EffectedPermissions.newInstance(Collections.singleton("WRITE"), Collections.<String>emptySet());

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullName() {
        ResourceNode.of(null, KNOWN_POINTER, KNOWN_PERMISSIONS, Collections.<String, PolicyTreeNode>emptyMap());
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPointer() {
        ResourceNode.of(KNOWN_NAME, null, KNOWN_PERMISSIONS, Collections.<String, PolicyTreeNode>emptyMap());
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPermissions() {
        ResourceNode.of(KNOWN_NAME, KNOWN_POINTER, null, Collections.<String, PolicyTreeNode>emptyMap());
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullChildren() {
        ResourceNode.of(KNOWN_NAME, KNOWN_POINTER, KNOWN_PERMISSIONS, null);
    }

    @Test
    public void equalNodesAreShared() {
        final ResourceNode first = node(KNOWN_PERMISSIONS, leaf("child"));
        final ResourceNode second = node(KNOWN_PERMISSIONS, leaf("child"));

        assertThat(second).isSameAs(first);
        assertThat(second.getChild("child")).containsSame(first.getChild("child").orElse(null));
    }

    @Test
    public void nodesDifferingInPermissionsPositionOrChildrenAreNotEqual() {
        final ResourceNode node = node(KNOWN_PERMISSIONS, leaf("child"));

        assertThat(node(OTHER_PERMISSIONS, leaf("child"))).isNotEqualTo(node);
        assertThat(node(KNOWN_PERMISSIONS, leaf("other"))).isNotEqualTo(node);
        assertThat(node(KNOWN_PERMISSIONS, Collections.<String, PolicyTreeNode>emptyMap())).isNotEqualTo(node);
        assertThat(ResourceNode.of(KNOWN_NAME, JsonPointer.of("/policy/name"), KNOWN_PERMISSIONS,
                leaf("child"))).isNotEqualTo(node);
        assertThat(node(KNOWN_PERMISSIONS, leaf("child")).hashCode()).isEqualTo(node.hashCode());
    }

    @Test
    public void knowsItsPositionAndLevel() {
        final ResourceNode node = node(KNOWN_PERMISSIONS, Collections.<String, PolicyTreeNode>emptyMap());

        assertThat((Object) node.getAbsolutePointer()).isEqualTo(KNOWN_POINTER);
        assertThat(node.getLevel()).isEqualTo(2);
        assertThat(node.getPermissions()).isEqualTo(KNOWN_PERMISSIONS);
    }

    @Test
    public void childrenAreUnmodifiable() {
        final ResourceNode node = node(KNOWN_PERMISSIONS, leaf("child"));

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> node.getChildren().remove("child"));
    }

    private static ResourceNode node(final EffectedPermissions permissions,
            final Map<String, PolicyTreeNode> children) {
        return ResourceNode.of(KNOWN_NAME, KNOWN_POINTER, permissions, children);
    }

    private static Map<String, PolicyTreeNode> leaf(final String name) {
        final Map<String, PolicyTreeNode> children = new LinkedHashMap<>();
        children.put(name, ResourceNode.of(name, KNOWN_POINTER.addLeaf(JsonKey.of(name)),
                KNOWN_PERMISSIONS, Collections.<String, PolicyTreeNode>emptyMap()));
        return children;
    }

}
