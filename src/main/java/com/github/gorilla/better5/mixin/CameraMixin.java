package com.github.gorilla.better5.mixin;

import com.github.gorilla.better5.client.Better5Client;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    private boolean detached;

    // Direct return yerine ModifyVariable kullanarak Minecraft'ın kendi raycast (duvar algılama)
    // algoritmasını çalıştırıyoruz. Böylece duvarların ve blokların içinden geçmiyor!
    @ModifyVariable(method = "getMaxZoom", at = @At("HEAD"), argsOnly = true)
    private float adjustCameraDistance(float cameraDist) {
        if (this.detached) {
            return (float) Better5Client.cameraDistance;
        }
        float startingDistance = 0;
        return startingDistance;
    }
}