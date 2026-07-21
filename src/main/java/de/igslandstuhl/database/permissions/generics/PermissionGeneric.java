package de.igslandstuhl.database.permissions.generics;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import de.igslandstuhl.database.Registry;

public abstract class PermissionGeneric {

    private static final Registry<String, PermissionGeneric> registry = new Registry<>();

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$[A-Za-z0-9_]+");
    private static final Pattern DOTTED_PLACEHOLDER = Pattern.compile("\\.\\$[A-Za-z0-9_]+");

    private final String name;

    public PermissionGeneric(String name) {
        this.name = name;
    }

    public abstract List<String> getAllReplacements();

    public String getName() {
        return name;
    }
    public void register() {
        registry.register(name, this);
    }

    public static PermissionGeneric get(String name) {
        return registry.get(name);
    }
    public static List<PermissionGeneric> getAll() {
        return registry.keyStream().map(PermissionGeneric::get).toList();
    }
    public static Stream<Map<String, ?>> applyAll(Map<String, ?> genericPermission) {
        Stream<Map<String, ?>> stream = Stream.of(genericPermission);
        for (PermissionGeneric g : getAll()) {
            stream = stream.flatMap(g::apply);
        }
        return stream;
    }

    // ------------------------------------------------------------------
    // apply()
    // ------------------------------------------------------------------

    public Stream<Map<String, ?>> apply(Map<String, ?> genericPermission) {
        // 1. Alle im JSON vorkommenden Platzhalter einsammeln
        Set<String> placeholderNames = new LinkedHashSet<>();
        collectPlaceholders(genericPermission, placeholderNames);

        List<PermissionGeneric> generics = placeholderNames.stream()
                .map(PermissionGeneric::get)
                .filter(Objects::nonNull)
                .toList();

        // 2. Konkrete Varianten (kartesisches Produkt aller Replacement-Werte)
        Stream<Map<String, ?>> concreteStream;
        if (generics.isEmpty()) {
            concreteStream = Stream.of(genericPermission);
        } else {
            concreteStream = cartesianProduct(generics)
                    .map(replacements -> castToMap(substituteValues(genericPermission, replacements)));
        }

        // 3. Optional: generische Variante ohne konkreten Wert
        boolean generateGeneric = Boolean.TRUE.equals(genericPermission.get("generate_generic"));
        if (generateGeneric && !generics.isEmpty()) {
            Map<String, ?> genericVersion = castToMap(stripValue(genericPermission));
            concreteStream = Stream.concat(concreteStream, Stream.of(genericVersion));
        }

        return concreteStream;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ?> castToMap(Object o) {
        return (Map<String, ?>) o;
    }

    // ------------------------------------------------------------------
    // Platzhalter einsammeln
    // ------------------------------------------------------------------

    private static void collectPlaceholders(Object value, Set<String> found) {
        if (value instanceof String s) {
            Matcher m = PLACEHOLDER_PATTERN.matcher(s);
            while (m.find()) {
                found.add(m.group());
            }
        } else if (value instanceof Map<?, ?> map) {
            for (Object v : map.values()) {
                collectPlaceholders(v, found);
            }
        } else if (value instanceof List<?> list) {
            for (Object v : list) {
                collectPlaceholders(v, found);
            }
        }
    }

    // ------------------------------------------------------------------
    // Kartesisches Produkt aller Replacement-Werte
    // ------------------------------------------------------------------

    private static Stream<Map<String, String>> cartesianProduct(List<PermissionGeneric> generics) {
        Stream<Map<String, String>> result = Stream.of(Map.of());
        for (PermissionGeneric generic : generics) {
            List<String> replacements = generic.getAllReplacements();
            result = result.flatMap(partial ->
                    replacements.stream().map(value -> {
                        Map<String, String> next = new LinkedHashMap<>(partial);
                        next.put(generic.getName(), value);
                        return next;
                    })
            );
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Konkretes Ersetzen ($platzhalter -> Wert)
    // ------------------------------------------------------------------

    private static Object substituteValues(Object value, Map<String, String> replacements) {
        if (value instanceof String s) {
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                s = s.replace(entry.getKey(), entry.getValue());
            }
            return s;
        } else if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put((String) entry.getKey(), substituteValues(entry.getValue(), replacements));
            }
            return result;
        } else if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (Object v : list) {
                result.add(substituteValues(v, replacements));
            }
            return result;
        }
        return value;
    }

    // ------------------------------------------------------------------
    // Generische Variante: Platzhalter entfernen, betroffene
    // post_restrictions löschen
    // ------------------------------------------------------------------

    private static Object stripValue(Object value) {
        if (value instanceof String s) {
            return cleanupPlaceholderString(s);
        } else if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = (String) entry.getKey();
                Object val = entry.getValue();

                if ("post_restrictions".equals(key) && val instanceof List<?> restrictions) {
                    List<Object> filtered = restrictions.stream()
                            .filter(r -> !containsPlaceholder(r))
                            .map(PermissionGeneric::stripValue)
                            .toList();
                    result.put(key, filtered);
                } else {
                    result.put(key, stripValue(val));
                }
            }
            return result;
        } else if (value instanceof List<?> list) {
            return list.stream().map(PermissionGeneric::stripValue).toList();
        }
        return value;
    }

    private static boolean containsPlaceholder(Object value) {
        if (value instanceof String s) {
            return PLACEHOLDER_PATTERN.matcher(s).find();
        } else if (value instanceof Map<?, ?> map) {
            return map.values().stream().anyMatch(PermissionGeneric::containsPlaceholder);
        } else if (value instanceof List<?> list) {
            return list.stream().anyMatch(PermissionGeneric::containsPlaceholder);
        }
        return false;
    }

    private static String cleanupPlaceholderString(String s) {
        // ".$platzhalter" komplett entfernen (inkl. führendem Punkt)
        String result = DOTTED_PLACEHOLDER.matcher(s).replaceAll("");
        // verbleibende Platzhalter ohne führenden Punkt entfernen
        result = PLACEHOLDER_PATTERN.matcher(result).replaceAll("");
        // Aufräumen falls doppelte/führende/abschließende Punkte entstehen
        result = result.replaceAll("\\.{2,}", ".");
        result = result.replaceAll("^\\.|\\.$", "");
        return result;
    }
}