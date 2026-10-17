package com.piotrek.groundworks.client.camera;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CameraZoomManagerTest {

    @BeforeEach
    void setUp() {
        CameraZoomManager.reset();
        // Step a few lerps to ensure currentDistance and offsets settle to default
        for (int i = 0; i < 30; i++) {
            CameraZoomManager.modifyDistance(CameraZoomManager.DEFAULT_DISTANCE);
            CameraZoomManager.getOffsetX();
            CameraZoomManager.getOffsetY();
        }
    }

    @Test
    void testDefaultDistanceAndOffsets() {
        assertEquals(CameraZoomManager.DEFAULT_DISTANCE, CameraZoomManager.getTargetDistance(), 0.001F);
        assertEquals(0.0F, CameraZoomManager.getTargetOffsetX(), 0.001F);
        assertEquals(0.0F, CameraZoomManager.getTargetOffsetY(), 0.001F);

        float modified = CameraZoomManager.modifyDistance(4.0F);
        assertEquals(4.0F, modified, 0.01F);
    }

    @Test
    void testScrollZoomOut() {
        // Scroll down (negative delta) increases distance
        CameraZoomManager.onScroll(-1.0); // 4.0 + 1.0 = 5.0
        assertEquals(5.0F, CameraZoomManager.getTargetDistance(), 0.001F);

        float current = 4.0F;
        for (int i = 0; i < 30; i++) {
            current = CameraZoomManager.modifyDistance(4.0F);
        }
        assertEquals(5.0F, current, 0.05F);
    }

    @Test
    void testScrollZoomIn() {
        // Scroll up (positive delta) decreases distance
        CameraZoomManager.onScroll(1.0); // 4.0 - 1.0 = 3.0
        assertEquals(3.0F, CameraZoomManager.getTargetDistance(), 0.001F);
    }

    @Test
    void testMinMaxClamping() {
        CameraZoomManager.onScroll(-100.0);
        assertEquals(CameraZoomManager.MAX_DISTANCE, CameraZoomManager.getTargetDistance(), 0.001F);

        CameraZoomManager.onScroll(100.0);
        assertEquals(CameraZoomManager.MIN_DISTANCE, CameraZoomManager.getTargetDistance(), 0.001F);
    }

    @Test
    void testOffsetPanningAndClamping() {
        CameraZoomManager.setOffsets(5.0F, -3.5F);
        assertEquals(5.0F, CameraZoomManager.getTargetOffsetX(), 0.001F);
        assertEquals(-3.5F, CameraZoomManager.getTargetOffsetY(), 0.001F);

        // Clamp beyond limits
        CameraZoomManager.setOffsets(50.0F, -50.0F);
        assertEquals(CameraZoomManager.MAX_OFFSET_X, CameraZoomManager.getTargetOffsetX(), 0.001F);
        assertEquals(-CameraZoomManager.MAX_OFFSET_Y, CameraZoomManager.getTargetOffsetY(), 0.001F);
    }

    @Test
    void testResetRestoresDefaultDistanceAndOffsets() {
        CameraZoomManager.onScroll(-5.0);
        CameraZoomManager.setOffsets(4.0F, 2.0F);

        assertNotEquals(CameraZoomManager.DEFAULT_DISTANCE, CameraZoomManager.getTargetDistance(), 0.001F);
        assertNotEquals(0.0F, CameraZoomManager.getTargetOffsetX(), 0.001F);
        assertNotEquals(0.0F, CameraZoomManager.getTargetOffsetY(), 0.001F);

        CameraZoomManager.reset();
        assertEquals(CameraZoomManager.DEFAULT_DISTANCE, CameraZoomManager.getTargetDistance(), 0.001F);
        assertEquals(0.0F, CameraZoomManager.getTargetOffsetX(), 0.001F);
        assertEquals(0.0F, CameraZoomManager.getTargetOffsetY(), 0.001F);
    }

    @Test
    void testProportionalScalingForCustomBaseDistance() {
        CameraZoomManager.onScroll(-4.0);
        assertTrue(CameraZoomManager.getTargetDistance() > 4.0F);

        for (int i = 0; i < 30; i++) {
            CameraZoomManager.modifyDistance(6.0F);
        }
        float result = CameraZoomManager.modifyDistance(6.0F);
        float expected = 6.0F * (CameraZoomManager.getTargetDistance() / 4.0F);
        assertEquals(expected, result, 0.15F);
    }
}
