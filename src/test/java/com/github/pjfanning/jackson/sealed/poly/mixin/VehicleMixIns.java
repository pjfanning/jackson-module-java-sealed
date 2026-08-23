package com.github.pjfanning.jackson.sealed.poly.mixin;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.pjfanning.jackson.sealed.SealedPolymorphismSupport;

/**
 * What the owner of the mapper supplies for a hierarchy they cannot change, in their own package -
 * which is the whole point.
 *
 * <p>Each mix-in carries the marker and nothing else. None of them extends the hierarchy it is mixed
 * into, and none of them could: a sealed type's {@code permits} clause names its subtypes, and
 * someone who cannot change those classes cannot add themselves to it. Jackson never requires a
 * mix-in to be a subtype of what it is mixed into - it only harvests from it, and this module only
 * asks whether the mix-in carries the marker.
 */
public final class VehicleMixIns {

    private VehicleMixIns() {
    }

    public interface OptIn extends SealedPolymorphismSupport {
    }

    /** Carries no marker, so mixing it in opts nothing in. */
    public interface Unrelated {
    }

    /** Opts in and hands the hierarchy straight back to Jackson, which is a contradiction. */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
    public interface OptInThenDefer extends SealedPolymorphismSupport {
    }
}
