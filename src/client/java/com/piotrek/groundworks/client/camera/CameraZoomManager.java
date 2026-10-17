package com.piotrek.groundworks.client.camera;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public final class CameraZoomManager {
    public static final float MIN_DISTANCE = 1.5F;
    public static final float MAX_DISTANCE = 24.0F;
    public static final float DEFAULT_DISTANCE = 4.0F;

    public static final float MAX_OFFSET_X = 12.0F;
    public static final float MAX_OFFSET_Y = 12.0F;

    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("pw_groundworks", "controls"));

    public static KeyMapping KEY_ZOOM_MODIFIER;

    private static float targetDistance = DEFAULT_DISTANCE;
    private static float currentDistance = DEFAULT_DISTANCE;

    private static float targetOffsetX = 0.0F;
    private static float currentOffsetX = 0.0F;

    private static float targetOffsetY = 0.0F;
    private static float currentOffsetY = 0.0F;

    private CameraZoomManager() {}

    public static void init() {
        KEY_ZOOM_MODIFIER = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.pw_groundworks.camera_zoom",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_V,
                CATEGORY
        ));
    }

    private static boolean isKeyDirectlyDown(int keyCode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return false;
        }
        return InputConstants.isKeyDown(mc.getWindow(), keyCode);
    }

    public static boolean isModifierDown() {
        if (KEY_ZOOM_MODIFIER != null && KEY_ZOOM_MODIFIER.isDown()) {
            return true;
        }
        return isKeyDirectlyDown(InputConstants.KEY_V)
                || isKeyDirectlyDown(InputConstants.KEY_LCONTROL)
                || isKeyDirectlyDown(InputConstants.KEY_RCONTROL);
    }

    public static boolean shouldZoom(Minecraft mc) {
        if (mc.player == null || (mc.gui != null && mc.gui.screen() != null) || mc.options.getCameraType().isFirstPerson()) {
            return false;
        }
        // Direct scroll when driving/riding any vehicle in 3rd person!
        if (mc.player.getVehicle() != null) {
            return true;
        }
        return isModifierDown();
    }

    public static boolean shouldReset(Minecraft mc) {
        if (mc.player == null || (mc.gui != null && mc.gui.screen() != null) || mc.options.getCameraType().isFirstPerson()) {
            return false;
        }
        if (mc.player.getVehicle() != null) {
            return true;
        }
        return isModifierDown();
    }

    public static void onScroll(double scrollDelta) {
        // Dynamic step: larger steps at distance, responsive and fast
        float dynamicStep = Math.max(1.0F, targetDistance * 0.20F);
        targetDistance -= (float) (scrollDelta * dynamicStep);
        targetDistance = Mth.clamp(targetDistance, MIN_DISTANCE, MAX_DISTANCE);
    }

    public static void clientTick(Minecraft mc) {
        if (mc.player == null || (mc.gui != null && mc.gui.screen() != null) || mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        if (!isModifierDown()) {
            return;
        }

        // Holding V + Arrow keys pans camera relative to player
        float speed = 0.20F;
        if (isKeyDirectlyDown(InputConstants.KEY_LEFT)) {
            targetOffsetX -= speed;
        }
        if (isKeyDirectlyDown(InputConstants.KEY_RIGHT)) {
            targetOffsetX += speed;
        }
        if (isKeyDirectlyDown(InputConstants.KEY_UP)) {
            targetOffsetY += speed;
        }
        if (isKeyDirectlyDown(InputConstants.KEY_DOWN)) {
            targetOffsetY -= speed;
        }

        targetOffsetX = Mth.clamp(targetOffsetX, -MAX_OFFSET_X, MAX_OFFSET_X);
        targetOffsetY = Mth.clamp(targetOffsetY, -MAX_OFFSET_Y, MAX_OFFSET_Y);
    }

    public static void reset() {
        targetDistance = DEFAULT_DISTANCE;
        targetOffsetX = 0.0F;
        targetOffsetY = 0.0F;
    }

    public static float modifyDistance(float originalDistance) {
        float diff = targetDistance - currentDistance;
        if (Math.abs(diff) < 0.03F) {
            currentDistance = targetDistance;
        } else {
            currentDistance += diff * 0.45F;
        }

        if (Math.abs(currentDistance - DEFAULT_DISTANCE) < 0.001F && Math.abs(targetDistance - DEFAULT_DISTANCE) < 0.001F) {
            return originalDistance;
        }
        return originalDistance * (currentDistance / DEFAULT_DISTANCE);
    }

    public static float getOffsetX() {
        float diff = targetOffsetX - currentOffsetX;
        if (Math.abs(diff) < 0.02F) {
            currentOffsetX = targetOffsetX;
        } else {
            currentOffsetX += diff * 0.45F;
        }
        return currentOffsetX;
    }

    public static float getOffsetY() {
        float diff = targetOffsetY - currentOffsetY;
        if (Math.abs(diff) < 0.02F) {
            currentOffsetY = targetOffsetY;
        } else {
            currentOffsetY += diff * 0.45F;
        }
        return currentOffsetY;
    }

    public static boolean isShiftingCamera() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && !mc.options.getCameraType().isFirstPerson() && isModifierDown();
    }

    public static float getTargetDistance() {
        return targetDistance;
    }

    public static float getCurrentDistance() {
        return currentDistance;
    }

    public static float getTargetOffsetX() {
        return targetOffsetX;
    }

    public static float getTargetOffsetY() {
        return targetOffsetY;
    }

    // Direct test setters
    public static void setOffsets(float targetX, float targetY) {
        targetOffsetX = Mth.clamp(targetX, -MAX_OFFSET_X, MAX_OFFSET_X);
        targetOffsetY = Mth.clamp(targetY, -MAX_OFFSET_Y, MAX_OFFSET_Y);
    }
}
