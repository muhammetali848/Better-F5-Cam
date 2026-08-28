package com.github.gorilla.better5.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = KeyboardHandler.class)
public class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void preventF5KeyRepeat(long handle, int action, KeyEvent event, CallbackInfo ci) {
        // action == 2 -> Tuş basılı tutulduğunda OS'un yolladığı sürekli tekrar (repeat) sinyalidir.
        if (action == 2) {
            Minecraft client = Minecraft.getInstance();
            KeyEvent key = null;
            Object scancode = new Object();
            if (client != null && client.options != null && client.options.keyTogglePerspective.matches((KeyEvent) null)) {
                ci.cancel(); // F5 için tekrar sinyalini iptal et, kamera fır fır dönmesin!
            }
        }
    }
}