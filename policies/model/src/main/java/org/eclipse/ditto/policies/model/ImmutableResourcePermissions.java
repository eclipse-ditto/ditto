package org.eclipse.ditto.policies.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

/**
 * An immutable implementation of {@link ResourcePermissions}.
 */
@Immutable
final class ImmutableResourcePermissions implements ResourcePermissions {

    private final String resourceType;
    private final String resourcePath;
    private final List<String> permissions;

    private ImmutableResourcePermissions(final String resourceType, final String resourcePath, final List<String> permissions) {
        this.resourceType = resourceType;
        this.resourcePath = resourcePath;
        this.permissions = Collections.unmodifiableList(permissions);
    }

    /**
     * Returns a new instance of {@code ImmutableResourcePermission} based on the provided {@code resourceType},
     * {@code resourcePath}, and {@code permissions}.
     *
     * @param resourceType the type of the resource.
     * @param resourcePath the path of the resource (as a String).
     * @param permissions the permissions associated with this ResourcePermission.
     * @return a new ResourcePermission.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code resourceType} or {@code resourcePath} is empty.
     */
    public static ImmutableResourcePermissions newInstance(final String resourceType, final String resourcePath,
            final List<String> permissions) {
        return new ImmutableResourcePermissions(
                argumentNotEmpty(resourceType, "resourceType"),
                argumentNotEmpty(resourcePath, "resourcePath"),
                checkNotNull(permissions, "permissions")
        );
    }

    /**
     * Creates an {@code ImmutableResourcePermission} instance from the given JSON object without creating a {@code ResourceKey}.
     * The resourceKey will be created later by the actor using the provided attributes.
     *
     * @param jsonObject the JSON object containing the resource permission data.
     * @return an {@code ImmutableResourcePermission} instance.
     * @throws DittoJsonException if the JSON object is not valid or missing required fields.
     */
    public static ImmutableResourcePermissions fromJson(final JsonObject jsonObject) {
        final String resourceType = jsonObject.getValueOrThrow(JsonFieldDefinition.ofString("resourceType"));

        final String resourcePath = jsonObject.getValueOrThrow(JsonFieldDefinition.ofString("resourcePath"));

        final List<String> permissions = jsonObject.getValueOrThrow(JsonFactory.newJsonArrayFieldDefinition("hasPermissions"))
                .stream().map(JsonValue::asString)
                .collect(Collectors.toList());

        return newInstance(resourceType, resourcePath, permissions);
    }

    @Override
    public String getResourceType() {
        return resourceType;
    }

    @Override
    public String getResourcePath() {
        return resourcePath;
    }

    @Override
    public List<String> getPermissions() {
        return permissions;
    }

    @Override
    public JsonObject toJson() {
        JsonObjectBuilder jsonBuilder = JsonFactory.newObjectBuilder();
        jsonBuilder.set("resourceType", resourceType);
        jsonBuilder.set("resourcePath", resourcePath);
        jsonBuilder.set("hasPermissions", JsonArray.of(permissions));
        return jsonBuilder.build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableResourcePermissions that = (ImmutableResourcePermissions) o;
        return Objects.equals(resourceType, that.resourceType) &&
                Objects.equals(resourcePath, that.resourcePath) &&
                Objects.equals(permissions, that.permissions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceType, resourcePath, permissions);
    }

    @Override
    @Nonnull
    public String toString() {
        return "ResourcePermission [resourceType=" + resourceType + ", resourcePath=" + resourcePath + ", permissions=" + permissions + "]";
    }
}