package net.worldseed.multipart;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.worldseed.multipart.animations.FrameProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins down the world-space direction of an animated bone rotation, using the same pipeline the
 * engine uses to drive a display entity: the raw Blockbench keyframe is multiplied by
 * {@link FrameProvider#RotationMul} and then applied via {@link ModelMath#rotate}.
 *
 * <p>Ground truth comes from the black_bear "attack" animation: the body bone has a {@code +45°}
 * X-rotation keyframe that, in Blockbench, rears the bear UP and BACK — i.e. a point above the
 * bone's pivot tilts toward the model's back (+Z) while staying above the pivot (y &gt; 0). The
 * bear's front is -Z (head pivot z is negative), so "back" is +Z.
 *
 * <p>Animation rotations are added to a bone's rest rotation (which nets to the raw Blockbench
 * value), so {@link FrameProvider#RotationMul} must not flip any axis — it must be {@code (1,1,1)}.
 * The last test guards that invariant on all three axes.
 */
class RotationDirectionTest {

    /** Apply the engine's keyframe→display rotation pipeline to a point. */
    private static Point applyKeyframe(Point rawDegrees, Point point) {
        Point applied = rawDegrees.mul(FrameProvider.RotationMul);
        return ModelMath.rotate(point, applied);
    }

    @Test
    void positiveXKeyframeRearsBoneBackwardAndUp() {
        // A point one unit above the pivot — the "top" of the body bone.
        Point top = new Vec(0, 1, 0);
        Point rotated = applyKeyframe(new Vec(45, 0, 0), top);

        // Rearing up/back: the top must swing toward the model's BACK (+Z) and remain above the
        // pivot (y > 0). A forward/down faceplant (-Z) is the bug the bear exhibits in-game.
        assertEquals(0.0, rotated.x(), 1e-6, "X rotation must not introduce sideways motion");
        org.junit.jupiter.api.Assertions.assertTrue(
                rotated.z() > 0.5,
                "Blockbench +45 X should rear the bone BACKWARD (+Z); top landed at z=" + rotated.z());
        org.junit.jupiter.api.Assertions.assertTrue(
                rotated.y() > 0.0,
                "Top of the bone must stay above the pivot; landed at y=" + rotated.y());
    }

    @Test
    void negativeXKeyframeTiltsBoneForwardAndDown() {
        // The jaw "opens" with a negative-relative motion; verify the opposite sign goes -Z.
        Point top = new Vec(0, 1, 0);
        Point rotated = applyKeyframe(new Vec(-45, 0, 0), top);
        org.junit.jupiter.api.Assertions.assertTrue(
                rotated.z() < -0.5,
                "Blockbench -45 X should tilt the bone FORWARD (-Z); top landed at z=" + rotated.z());
    }

    @Test
    void rotationMultiplierPreservesBlockbenchDirectionOnEveryAxis() {
        // RotationMul must not flip any axis (must be (1,1,1)): multiplying a keyframe then rotating
        // must match rotating by the raw angle. An off-axis probe exposes a flip on any single axis.
        Point probe = new Vec(0.4, 1.0, -0.7);
        for (Point raw : new Point[]{new Vec(40, 0, 0), new Vec(0, 40, 0), new Vec(0, 0, 40)}) {
            Point viaPipeline = ModelMath.rotate(probe, raw.mul(FrameProvider.RotationMul));
            Point viaRaw = ModelMath.rotate(probe, raw);
            assertEquals(viaRaw.x(), viaPipeline.x(), 1e-9, "RotationMul flips an axis for " + raw);
            assertEquals(viaRaw.y(), viaPipeline.y(), 1e-9, "RotationMul flips an axis for " + raw);
            assertEquals(viaRaw.z(), viaPipeline.z(), 1e-9, "RotationMul flips an axis for " + raw);
        }
    }
}
