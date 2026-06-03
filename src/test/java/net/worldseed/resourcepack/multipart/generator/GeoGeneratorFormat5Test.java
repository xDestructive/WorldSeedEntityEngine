package net.worldseed.resourcepack.multipart.generator;

import org.junit.jupiter.api.Test;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Regression coverage for Blockbench {@code format_version >= 5.0} models, where group data
 * (name / origin / rotation) lives in a flat top-level {@code groups} array and the outliner
 * holds bare {@code {uuid, isOpen, children}} references into it.
 *
 * <p>Before the fix, {@link GeoGenerator#parseRecursive} read {@code origin}/{@code rotation}
 * straight off the outliner node. For format-5.0 models those keys are absent, so every bone
 * pivot fell back to {@code [0,0,0]} and all bones rotated about the model origin instead of
 * their own pivot — collapsing animated parts (e.g. an opening jaw flew off-model). These tests
 * mirror the real {@code black_bear.bbmodel} layout: flat {@code groups}, nested {@code outliner}.
 */
class GeoGeneratorFormat5Test {
    // Scale Blockbench → geometry units (GeoGenerator uses 0.25) with X negated, Y/Z kept.
    private static final double S = 0.25;

    private static JsonObject group(String uuid, String name, double ox, double oy, double oz, double rx, double ry, double rz) {
        return Json.createObjectBuilder()
                .add("uuid", uuid)
                .add("name", name)
                .add("origin", arr(ox, oy, oz))
                .add("rotation", arr(rx, ry, rz))
                .build();
    }

    /** A format-5.0 outliner node: a bare uuid reference with no inline origin/name. */
    private static JsonObjectBuilder node(String uuid) {
        return Json.createObjectBuilder().add("uuid", uuid).add("isOpen", true);
    }

    private static JsonArray arr(double... v) {
        JsonArrayBuilder b = Json.createArrayBuilder();
        for (double d : v) b.add(d);
        return b.build();
    }

    private static JsonObject boneNamed(JsonArray bones, String name) {
        for (var v : bones) {
            JsonObject o = v.asJsonObject();
            if (name.equals(o.getString("name"))) return o;
        }
        return null;
    }

    private static void assertPivot(JsonObject bone, double x, double y, double z) {
        JsonArray p = bone.getJsonArray("pivot");
        assertEquals(x, p.getJsonNumber(0).doubleValue(), 1e-6, "pivot.x");
        assertEquals(y, p.getJsonNumber(1).doubleValue(), 1e-6, "pivot.y");
        assertEquals(z, p.getJsonNumber(2).doubleValue(), 1e-6, "pivot.z");
    }

    /** Builds the black_bear-shaped fixture: flat groups, nested outliner (bone → body, head → lower_jaw). */
    private JsonArray generateBearLike() {
        JsonArray groups = Json.createArrayBuilder()
                .add(group("g-bone", "bone", 0, 0, 4, 0, 0, 0))
                .add(group("g-body", "body", 0, 0, 4, 0, 0, 0))
                .add(group("g-head", "head", 0, 15, -7, 15, 0, 0))
                .add(group("g-jaw", "lower_jaw", 0, 10, -14, 0, 0, 0))
                .build();

        JsonArray outliner = Json.createArrayBuilder()
                .add(node("g-bone").add("children", Json.createArrayBuilder()
                        .add(node("g-body").add("children", Json.createArrayBuilder()))
                        .add(node("g-head").add("children", Json.createArrayBuilder()
                                .add(node("g-jaw").add("children", Json.createArrayBuilder()))))))
                .build();

        var groupMap = GeoGenerator.collectGroupUuidToGroup(groups);
        return GeoGenerator.generate(JsonArray.EMPTY_JSON_ARRAY, outliner, Map.of(), groupMap);
    }

    @Test
    void format5GroupOriginsResolveToBonePivots() {
        JsonArray bones = generateBearLike();
        assertEquals(4, bones.size());

        // Origins scaled by 0.25 with X negated. head [0,15,-7] -> [0, 3.75, -1.75].
        assertPivot(requireBone(bones, "bone"), 0, 0, 1.0);
        assertPivot(requireBone(bones, "body"), 0, 0, 1.0);
        assertPivot(requireBone(bones, "head"), 0, 3.75, -1.75);
        assertPivot(requireBone(bones, "lower_jaw"), 0, 2.5, -3.5);
    }

    @Test
    void format5OutlinerNestingDrivesBoneParenting() {
        JsonArray bones = generateBearLike();

        JsonObject bone = requireBone(bones, "bone");
        assertFalse(bone.containsKey("parent"), "root bone has no parent");
        assertEquals("bone", boneNamed(bones, "body").getString("parent"));
        assertEquals("bone", boneNamed(bones, "head").getString("parent"));
        assertEquals("head", boneNamed(bones, "lower_jaw").getString("parent"));
    }

    @Test
    void format5GroupRotationIsResolvedAndAxisCorrected() {
        JsonArray bones = generateBearLike();
        JsonArray rot = requireBone(bones, "head").getJsonArray("rotation");
        // GeoGenerator negates X and Y, keeps Z. Group rotation [15,0,0] -> [-15,0,0].
        assertEquals(-15.0, rot.getJsonNumber(0).doubleValue(), 1e-6);
        assertEquals(0.0, rot.getJsonNumber(1).doubleValue(), 1e-6);
        assertEquals(0.0, rot.getJsonNumber(2).doubleValue(), 1e-6);
    }

    /**
     * Locks in the cause of the original bug: without a resolvable group (an empty map, which is
     * what the old generator effectively had for format-5.0 models), every pivot collapses to
     * {@code [0,0,0]}. Guards against a future regression that drops group resolution.
     */
    @Test
    void withoutGroupResolutionAllPivotsCollapseToZero() {
        JsonArray outliner = Json.createArrayBuilder()
                .add(node("g-head").add("children", Json.createArrayBuilder()))
                .build();

        JsonArray bones = GeoGenerator.generate(JsonArray.EMPTY_JSON_ARRAY, outliner, Map.of(), Map.of());
        assertEquals(1, bones.size());
        // No group resolved -> name falls back to the uuid, pivot is zeroed (the broken behaviour).
        assertPivot(bones.get(0).asJsonObject(), 0, 0, 0);
    }

    /**
     * Backward compatibility: pre-5.0 models inline {@code origin}/{@code name} on the outliner
     * node itself, with no top-level {@code groups}. Resolution must still read them directly.
     */
    @Test
    void inlinePre5OutlinerNodesStillResolve() {
        JsonArray outliner = Json.createArrayBuilder()
                .add(Json.createObjectBuilder()
                        .add("uuid", "x")
                        .add("name", "body")
                        .add("origin", arr(0, 0, 4))
                        .add("rotation", arr(0, 0, 0))
                        .add("children", Json.createArrayBuilder()))
                .build();

        JsonArray bones = GeoGenerator.generate(JsonArray.EMPTY_JSON_ARRAY, outliner, Map.of(), Map.of());
        assertEquals(1, bones.size());
        JsonObject body = bones.get(0).asJsonObject();
        assertEquals("body", body.getString("name"));
        assertPivot(body, 0, 0, 1.0);
    }

    private static JsonObject requireBone(JsonArray bones, String name) {
        JsonObject bone = boneNamed(bones, name);
        assertNotNull(bone, "expected bone '" + name + "' present");
        return bone;
    }
}
