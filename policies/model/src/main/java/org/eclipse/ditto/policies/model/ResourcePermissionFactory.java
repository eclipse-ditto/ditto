package org.eclipse.ditto.policies.model;

import org.eclipse.ditto.json.JsonObject;

import java.util.List;

public class ResourcePermissionFactory {

    /**
     * Creates a {@code ResourcePermission} from the provided JSON object.
     *
     * @param jsonObject the JSON object representing the resource permission.
     * @return a {@code ResourcePermission}.
     */
    public static ResourcePermissions fromJson(final JsonObject jsonObject) {
        return ImmutableResourcePermissions.fromJson(jsonObject);
    }

    public static ResourcePermissions newInstance(String resourceType, String resourcePath, List<String> permissions) {
        return ImmutableResourcePermissions.newInstance(resourceType, resourcePath, permissions);
    }
}