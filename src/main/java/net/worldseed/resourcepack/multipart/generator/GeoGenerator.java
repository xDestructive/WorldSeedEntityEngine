package net.worldseed.resourcepack.multipart.generator;

import net.worldseed.resourcepack.PackBuilder;

import javax.json.*;
import java.util.*;

public class GeoGenerator {
    private static List<JsonObject> parseRecursive(JsonObject obj, Map<String, JsonObject> cubeMap, Map<String, JsonObject> locators, Map<String, JsonObject> nullObjects, Map<String, JsonObject> groupUuidToGroup, String parent) {
        List<JsonObject> res = new ArrayList<>();
        float scale = 0.25f;

        // Blockbench format >= 5.0 stores group data (name/origin/rotation) in a top-level
        // "groups" array and leaves outliner nodes as bare {uuid, children} references. Resolve
        // the backing group so origin/rotation aren't read as null (which previously zeroed every
        // bone pivot, making bones rotate about the model origin instead of their own pivot).
        JsonObject group = resolveGroup(obj, groupUuidToGroup);

        String name = getOutlinerName(obj, group);

        JsonArray pivot = obj.getJsonArray("origin");
        if (pivot == null && group != null) pivot = group.getJsonArray("origin");
        if (pivot == null) {
            pivot = Json.createArrayBuilder().add(0).add(0).add(0).build();
        } else {
            pivot = Json.createArrayBuilder()
                    .add(-pivot.getJsonNumber(0).doubleValue() * scale)
                    .add(pivot.getJsonNumber(1).doubleValue() * scale)
                    .add(pivot.getJsonNumber(2).doubleValue() * scale)
                    .build();
        }

        JsonArrayBuilder cubes = Json.createArrayBuilder();

        JsonArray rotation = obj.getJsonArray("rotation");
        if (rotation == null && group != null) rotation = group.getJsonArray("rotation");
        if (rotation == null) {
            rotation = Json.createArrayBuilder().add(0).add(0).add(0).build();
        } else {
            rotation = Json.createArrayBuilder()
                    .add(-rotation.getJsonNumber(0).doubleValue())
                    .add(-rotation.getJsonNumber(1).doubleValue())
                    .add(rotation.getJsonNumber(2).doubleValue())
                    .build();
        }

        JsonArray children = obj.getJsonArray("children");
        if (children == null) children = JsonValue.EMPTY_JSON_ARRAY;

        for (JsonValue child : children) {
            if (child.getValueType() == JsonValue.ValueType.OBJECT) {
                res.addAll(parseRecursive(child.asJsonObject(), cubeMap, locators, nullObjects, groupUuidToGroup, name));
            } else if (child.getValueType() == JsonValue.ValueType.STRING) {
                JsonObject cube = cubeMap.get(child.toString());
                if (cube == null) continue;

                cubes.add(cube);
            }
        }

        JsonObjectBuilder thisEl = Json.createObjectBuilder()
                .add("name", name)
                .add("pivot", pivot)
                .add("rotation", rotation)
                .add("cubes", cubes);

        if (parent != null) {
            thisEl.add("parent", parent);
        }

        res.add(thisEl.build());

        return res;
    }

    /** The group backing an outliner node: its own data if inline (old format), else the
     * group referenced by uuid from the top-level {@code groups} array (format >= 5.0). */
    private static JsonObject resolveGroup(JsonObject obj, Map<String, JsonObject> groupUuidToGroup) {
        if (obj.containsKey("origin") || obj.containsKey("name")) return obj;
        JsonString uuid = obj.getJsonString("uuid");
        if (uuid != null) return groupUuidToGroup.get(uuid.getString());
        return null;
    }

    private static String getOutlinerName(JsonObject obj, JsonObject group) {
        JsonString name = obj.getJsonString("name");
        if (name != null) return name.getString();

        if (group != null) {
            JsonString groupName = group.getJsonString("name");
            if (groupName != null) return groupName.getString();
        }

        JsonString uuid = obj.getJsonString("uuid");
        if (uuid != null) return uuid.getString();

        return "bone_" + Integer.toUnsignedString(obj.hashCode());
    }

    /** Maps Blockbench group UUID strings to their group object (name/origin/rotation) for
     * format >= 5.0 models, where outliner nodes are bare uuid references. */
    static Map<String, JsonObject> collectGroupUuidToGroup(JsonArray groups) {
        Map<String, JsonObject> map = new HashMap<>();
        if (groups == null) return map;
        for (JsonValue v : groups) {
            if (v.getValueType() != JsonValue.ValueType.OBJECT) continue;
            collectGroupUuidToGroupRecursive(v.asJsonObject(), map);
        }
        return map;
    }

    private static void collectGroupUuidToGroupRecursive(JsonObject group, Map<String, JsonObject> out) {
        JsonString uuid = group.getJsonString("uuid");
        if (uuid != null) {
            out.putIfAbsent(uuid.getString(), group);
        }
        JsonArray children = group.getJsonArray("children");
        if (children == null) return;
        for (JsonValue c : children) {
            if (c.getValueType() != JsonValue.ValueType.OBJECT) continue;
            collectGroupUuidToGroupRecursive(c.asJsonObject(), out);
        }
    }

    public static JsonArray generate(JsonArray elements, JsonArray outliner, Map<String, TextureGenerator.TextureData> textures, Map<String, JsonObject> groupUuidToGroup) {
        Map<String, JsonObject> blocks = new HashMap<>();
        Map<String, JsonObject> locators = new HashMap<>();
        Map<String, JsonObject> nullObjects = new HashMap<>();

        for (var element : elements) {
            JsonObject el = element.asJsonObject();
            float scale = 0.25f;

            double inflate = 0;
            if (el.containsKey("inflate")) {
                inflate = el.getJsonNumber("inflate").doubleValue() * scale;
            }

            String elType = el.getString("type", "cube");
            switch (elType) {
                case "cube" -> readCube(el, blocks, scale, inflate, textures);
                case "locator" -> locators.put(el.getString("uuid"), el);
                case "null_object" -> nullObjects.put(el.getString("uuid"), el);
            }
        }

        List<JsonObject> bonesList = new ArrayList<>();
        for (var outline : outliner) {
            if (outline instanceof JsonObject) {
                JsonObject el = outline.asJsonObject();
                bonesList.addAll(parseRecursive(el, blocks, locators, nullObjects, groupUuidToGroup, null));
            }
        }

        JsonArrayBuilder bones = Json.createArrayBuilder();
        for (var bone : bonesList) bones.add(bone);

        return bones.build();
    }

    private static void readCube(JsonObject el, Map<String, JsonObject> blocks, float scale, double inflate, Map<String, TextureGenerator.TextureData> textures) {
        JsonArray origin = el.getJsonArray("origin");
        origin = Json.createArrayBuilder()
                .add(-Math.round(origin.getJsonNumber(0).doubleValue() * 10000 * scale) / 10000.0)
                .add(Math.round(origin.getJsonNumber(1).doubleValue() * 10000 * scale) / 10000.0)
                .add(Math.round(origin.getJsonNumber(2).doubleValue() * 10000 * scale) / 10000.0)
                .build();

        JsonArray from = PackBuilder.applyInflate(el.getJsonArray("from"), -inflate);
        JsonArray to = PackBuilder.applyInflate(el.getJsonArray("to"), inflate);

        to = getJsonValues(scale, to);

        from = getJsonValues(scale, from);

        JsonArray size = buildSize(from, to);

        from = Json.createArrayBuilder()
                .add(-(from.getJsonNumber(0).doubleValue() + size.getJsonNumber(0).doubleValue()))
                .add(from.getJsonNumber(1).doubleValue())
                .add(from.getJsonNumber(2).doubleValue())
                .build();

        JsonArray rotation = el.getJsonArray("rotation");
        if (rotation == null) {
            rotation = Json.createArrayBuilder().add(0).add(0).add(0).build();
        } else {
            JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
            arrayBuilder.add(-Math.round(rotation.getJsonNumber(0).doubleValue() * 100) / 100.0);
            arrayBuilder.add(-Math.round(rotation.getJsonNumber(1).doubleValue() * 100) / 100.0);
            arrayBuilder.add(Math.round(rotation.getJsonNumber(2).doubleValue() * 100) / 100.0);
            rotation = arrayBuilder.build();
        }

        JsonObject faces = parseFaces(el.getJsonObject("faces"), textures);
        JsonObject built = Json.createObjectBuilder()
                .add("origin", from)
                .add("size", size)
                .add("pivot", origin)
                .add("rotation", rotation)
                .add("uv", faces)
                .build();

        blocks.put("\"" + el.getString("uuid") + "\"", built);
    }

    private static JsonArray getJsonValues(float scale, JsonArray from) {
        from = Json.createArrayBuilder()
                .add(Math.round(from.getJsonNumber(0).doubleValue() * 10000 * scale) / 10000.0)
                .add(Math.round(from.getJsonNumber(1).doubleValue() * 10000 * scale) / 10000.0)
                .add(Math.round(from.getJsonNumber(2).doubleValue() * 10000 * scale) / 10000.0)
                .build();
        return from;
    }

    private static JsonObject parseFaces(JsonObject obj, Map<String, TextureGenerator.TextureData> textures) {
        JsonObjectBuilder res = Json.createObjectBuilder();

        for (var entry : obj.entrySet()) {
            boolean invert = (entry.getKey().equals("up") || entry.getKey().equals("down"));

            String face = entry.getKey();
            JsonValue uv = entry.getValue().asJsonObject().get("uv");
            JsonValue rotation = entry.getValue().asJsonObject().get("rotation");

            JsonArray shape = uv.asJsonArray();

            JsonArray size = Json.createArrayBuilder()
                    .add(shape.getJsonNumber(2).doubleValue() - shape.getJsonNumber(0).doubleValue())
                    .add(shape.getJsonNumber(3).doubleValue() - shape.getJsonNumber(1).doubleValue())
                    .build();

            JsonArray from = Json.createArrayBuilder()
                    .add(shape.getJsonNumber(0).doubleValue())
                    .add(shape.getJsonNumber(1).doubleValue())
                    .build();

            if (invert) {
                from = Json.createArrayBuilder()
                        .add(from.getJsonNumber(0).doubleValue() + size.getJsonNumber(0).doubleValue())
                        .add(from.getJsonNumber(1).doubleValue() + size.getJsonNumber(1).doubleValue())
                        .build();

                size = Json.createArrayBuilder()
                        .add(-size.getJsonNumber(0).doubleValue())
                        .add(-size.getJsonNumber(1).doubleValue())
                        .build();
            }

            String texture = "#0";
            if (!textures.isEmpty()) texture = textures.values().toArray(new TextureGenerator.TextureData[0])[0].id();

            var n = entry.getValue().asJsonObject().get("texture");
            if (n != null && n.getValueType() != JsonValue.ValueType.NULL) {
                int nInt = ((JsonNumber) n).intValue();
                if (textures.size() <= nInt) texture = "#" + nInt;
                else texture = textures.values().toArray(new TextureGenerator.TextureData[0])[nInt].id();
            }

            JsonObjectBuilder faceParsed = Json.createObjectBuilder()
                    .add("uv", from)
                    .add("uv_size", size)
                    .add("texture", texture);

            if (rotation instanceof JsonNumber r) {
                faceParsed.add("rotation", r);
            }

            res.add(face, faceParsed.build());
        }

        return res.build();
    }

    private static JsonArray buildSize(JsonArray from, JsonArray to) {
        JsonArrayBuilder builder = Json.createArrayBuilder();

        for (int i = 0; i < from.size(); i++) {
            double from_ = from.getJsonNumber(i).doubleValue();
            double to_ = to.getJsonNumber(i).doubleValue();

            builder.add(Math.round((to_ - from_) * 100000) / 100000.0);
        }

        return builder.build();
    }
}
