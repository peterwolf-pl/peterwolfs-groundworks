package com.piotrek.groundworks.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.piotrek.groundworks.client.camera.CameraZoomManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void groundworks$handleCameraZoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && (mc.gui == null || mc.gui.screen() == null) && !mc.options.getCameraType().isFirstPerson()) {
            if (vertical != 0.0 && CameraZoomManager.shouldZoom(mc)) {
                CameraZoomManager.onScroll(vertical);
                ci.cancel();
            }
        }
    }

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void groundworks$handleCameraZoomReset(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (buttonInfo.button() == InputConstants.MOUSE_BUTTON_MIDDLE && action == InputConstants.PRESS) {
            if (CameraZoomManager.shouldReset(mc)) {
                CameraZoomManager.reset();
                ci.cancel();
            }
        }
    }
}
