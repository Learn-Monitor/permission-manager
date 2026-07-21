package de.igslandstuhl.database.permissions.restrictions;

import de.igslandstuhl.database.server.webserver.requests.PostRequest;

@FunctionalInterface
public interface PostRestriction {
    public boolean isPostAllowed(PostRequest request);

    public static PostRestriction get(String type, String restriction, String fieldMissingBehavior) {
        return RestrictionType.get(type).getRestriction(restriction, fieldMissingBehavior);
    }
}
