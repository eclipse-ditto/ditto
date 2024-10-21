package org.eclipse.ditto.policies.model;

import org.eclipse.ditto.json.JsonObject;

import java.util.List;

/**
 * Represents the permissions associated with a resource.
 */
public interface ResourcePermissions {


    /**
     * Gets the type of the resource.
     *
     * @return the resource type.
     */
    String getResourceType();

    /**
     * Gets the path of the resource.
     *
     * @return the resource path as a JsonPointer.
     */
    String getResourcePath();

    /**
     * Gets the permissions associated with the resource.
     *
     * @return the set of permissions.
     */
    List<String> getPermissions();

    /**
     * Converts the ResourcePermissions instance to a JsonObject.
     *
     * @return the JsonObject representation of the resource permissions.
     */
    JsonObject toJson();
}
