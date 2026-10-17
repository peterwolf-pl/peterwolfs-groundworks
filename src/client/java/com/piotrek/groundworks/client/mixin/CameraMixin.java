package com.piotrek.groundworks.client.mixin;

import com.piotrek.groundworks.client.camera.CameraZoomManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void move(float forwards, float up, float right);

    @ModifyArg(
        method = "alignWithEntity(F)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"
        ),
        index = 0
    )
    private float groundworks$applyCustomCameraZoom(float originalDistance) {
        return CameraZoomManager.modifyDistance(originalDistance);
    }

    @Inject(method = "alignWithEntity(F)V", at = @At("TAIL"))
    private void groundworks$applyCustomCameraOffset(float partialTick, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.options.getCameraType().isFirstPerson()) {
            float offX = CameraZoomManager.getOffsetX();
            float offY = CameraZoomManager.getOffsetY();
            if (Math.abs(offX) > 0.001F || Math.abs(offY) > 0.001F) {
                // In Camera.move(forwards, up, right):
                // up is local Y (vertical)
                // right is local X (lateral)
                this.move(0.0F, offY, offX);
            }
        }
    }
}
