package net.worldseed.multipart.animations;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;

public interface FrameProvider {
    // Identity — no axis negated. Animation rotations are ADDED to the bone's rest rotation
    // (ModelBoneImpl.getPropagatedRotation), so they must share its convention. The rest rotation
    // nets to the raw Blockbench value: GeoGenerator emits [-x,-y,z] and GenericModelImpl re-applies
    // .mul(-1,-1,1), cancelling on X and Y — so animations must be raw too, i.e. (1,1,1).
    // The previous (-1,-1,1) inverted every X/Y animation: invisible on symmetric/static models,
    // but the black bear's attack reared its body DOWN into the ground toward the player instead of
    // UP and away. (TranslationMul is a separate pipeline, untouched by this fix.)
    Point RotationMul = new Vec(1, 1, 1);
    Point TranslationMul = new Vec(-1, 1, 1);

    Point getFrame(int tick);
}
