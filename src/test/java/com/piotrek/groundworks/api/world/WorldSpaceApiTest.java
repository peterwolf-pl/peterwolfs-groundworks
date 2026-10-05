package com.piotrek.groundworks.api.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldSpaceApiTest {

    @Test
    void microCoordinateClampsToCell() {
        assertEquals(0, WorldSpaceApi.microCoordinate(-0.1D));
        assertEquals(0, WorldSpaceApi.microCoordinate(0.0D));
        assertEquals(3, WorldSpaceApi.microCoordinate(0.49D));
        assertEquals(7, WorldSpaceApi.microCoordinate(0.999D));
        assertEquals(7, WorldSpaceApi.microCoordinate(1.2D));
    }

    @Test
    void cutGradeUsesFirstLayerAtOrAbovePlane() {
        assertEquals(0, WorldSpaceApi.firstLayerAtOrAbove(-1.0D));
        assertEquals(0, WorldSpaceApi.firstLayerAtOrAbove(0.0D));
        assertEquals(2, WorldSpaceApi.firstLayerAtOrAbove(2.0D));
        assertEquals(3, WorldSpaceApi.firstLayerAtOrAbove(2.01D));
        assertEquals(8, WorldSpaceApi.firstLayerAtOrAbove(8.0D));
    }

    @Test
    void fillGradeUsesOnlyFullyCoveredLayers() {
        assertEquals(0, WorldSpaceApi.fullLayersBelow(-1.0D));
        assertEquals(0, WorldSpaceApi.fullLayersBelow(0.99D));
        assertEquals(1, WorldSpaceApi.fullLayersBelow(1.0D));
        assertEquals(3, WorldSpaceApi.fullLayersBelow(3.99D));
        assertEquals(8, WorldSpaceApi.fullLayersBelow(8.0D));
    }
}
