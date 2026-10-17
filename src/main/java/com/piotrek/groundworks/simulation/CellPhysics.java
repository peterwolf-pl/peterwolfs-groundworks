package com.piotrek.groundworks.simulation;

import com.piotrek.groundworks.terrain.cell.GranularCell;

/**
 * Calculates physical soil mechanics properties for granular cells.
 *
 * <p>Separates physical simulation parameters (angle of repose, cohesion,
 * density, slide probability) from the core volumetric occupancy model.</p>
 */
public final class CellPhysics {

    private CellPhysics() {}

    /** Weighted angle of repose in degrees based on material composition. */
    public static float effectiveAngleOfRepose(GranularCell cell) {
        return cell.composition().weightedAngleOfRepose();
    }

    /** Weighted cohesion factor in [0, 1]. */
    public static float effectiveCohesion(GranularCell cell) {
        return cell.composition().weightedCohesion();
    }

    /** Weighted density in kg/m^3. */
    public static float effectiveDensity(GranularCell cell) {
        return cell.composition().weightedDensity();
    }

    /** Weighted slide probability in [0, 1]. */
    public static float effectiveSlideProbability(GranularCell cell) {
        return cell.composition().weightedSlideProbability();
    }
}
