package de.igslandstuhl.database.permissions.restrictions;

import de.igslandstuhl.database.Registry;

@FunctionalInterface
public interface RestrictionType {
    public static final Registry<String, RestrictionType> typeRegistry = new Registry<>();

    public PostRestriction getRestriction(String[] restrictionArgs, String fieldMissingBehavior);
    
    public default PostRestriction getRestriction(String restriction, String fieldMissingBehavior) {
        return getRestriction(restriction.split("\\."), fieldMissingBehavior);
    }
    public static void register(String name, RestrictionType type) {
        typeRegistry.register(name, type);
    }
    public static RestrictionType get(String name) {
        return typeRegistry.get(name);
    }
}
