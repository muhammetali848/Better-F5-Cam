package com.github.gorilla.better5.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper; // 26.1 Fabric API için yeni paket
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class Better5Client implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("BetterF5");

    public static double cameraDistance = 4.0;

    private static final double MIN_DISTANCE = 1.0;
    private static final double MAX_DISTANCE = 300.0;
    private static final double ZOOM_SPEED = 0.5;
    private static final double DEFAULT_DISTANCE = 4.0;


    private static double savedDistance = DEFAULT_DISTANCE;
    private static boolean modEnabled = true;
    private static boolean configDirty = false;

    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("BetterF5.properties");


    private static long lastChangeTimeMs = 0;
    private static final long FADE_IN_MS = 150;
    private static final long HOLD_MS = 1000;
    private static final long FADE_OUT_MS = 400;
    private static final long TOTAL_VISIBLE_MS = FADE_IN_MS + HOLD_MS + FADE_OUT_MS;


    private static boolean animating = false;
    private static double animStartValue = DEFAULT_DISTANCE;
    private static double animTargetValue = DEFAULT_DISTANCE;
    private static long animStartTimeMs = 0;
    private static final long ANIM_DURATION_MS = 1000; // Sabit süre kullanılıyor

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("better5", "main"));

    private static KeyMapping toggleKey;
    private static KeyMapping resetKey;

    @Override
    public void onInitializeClient() {
        loadConfig();
        cameraDistance = modEnabled ? savedDistance : DEFAULT_DISTANCE;

        // 26.1 standardına göre yeni KeyMappingHelper arayüzü ile kayıt işlemi yapıldı
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.better5.toggle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                CATEGORY
        ));

        resetKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.better5.reset",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                CATEGORY
        ));

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.HOTBAR,
                Identifier.fromNamespaceAndPath("better5", "distance_hud"),
                Better5Client::renderDistanceHud
        );

        ClientTickEvents.END_CLIENT_TICK.register(Better5Client::onClientTick);


        ClientLifecycleEvents.CLIENT_STOPPING.register(ignored -> saveConfig());
    }

    private static void onClientTick(Minecraft client) {
        if (client == null) return;

        while (toggleKey.consumeClick()) {
            toggleMod();
        }
        while (resetKey.consumeClick()) {
            resetDistance();
        }


        updateAnimation();

        if (configDirty && System.currentTimeMillis() - lastChangeTimeMs > 500) {
            saveConfig();
            configDirty = false;
        }
    }

    private static void resetDistance() {
        savedDistance = DEFAULT_DISTANCE;
        if (modEnabled) {
            startCustomAnimation(DEFAULT_DISTANCE, cameraDistance);
        }
        configDirty = true;
        lastChangeTimeMs = System.currentTimeMillis();
        saveConfig();
    }

    private static void toggleMod() {
        modEnabled = !modEnabled;
        double target = modEnabled ? savedDistance : DEFAULT_DISTANCE;
        startCustomAnimation(target, cameraDistance);
        lastChangeTimeMs = System.currentTimeMillis();
        saveConfig();
    }

    private static void startCustomAnimation(double target, double startValue) {
        animStartValue = startValue;
        animTargetValue = target;
        animStartTimeMs = System.currentTimeMillis();
        animating = true;
    }

    private static void updateAnimation() {
        if (!animating) return;

        long elapsed = System.currentTimeMillis() - animStartTimeMs;
        float t = Math.clamp(elapsed / (float) ANIM_DURATION_MS, 0f, 1f);

        // Drone tarzı Ease-Out Cubic yumuşatması
        float eased = easeOutCubic(t);

        cameraDistance = animStartValue + (animTargetValue - animStartValue) * eased;

        if (t >= 1f) {
            cameraDistance = animTargetValue;
            animating = false;
        }
    }

    private static float easeOutCubic(float t) {
        return 1f - (float) Math.pow(1f - t, 3);
    }

    public static boolean onMouseScroll(double verticalAmount) {
        if (!modEnabled) return false;

        Minecraft client = Minecraft.getInstance();
        boolean isF5Pressed = client.options.keyTogglePerspective.isDown();
        CameraType currentPerspective = client.options.getCameraType();

        if (!currentPerspective.isFirstPerson() && isF5Pressed) {
            animating = false;

            if (verticalAmount > 0) {
                cameraDistance = Math.max(MIN_DISTANCE, cameraDistance - ZOOM_SPEED);
            } else if (verticalAmount < 0) {
                cameraDistance = Math.min(MAX_DISTANCE, cameraDistance + ZOOM_SPEED);
            }

            savedDistance = cameraDistance;
            configDirty = true;
            lastChangeTimeMs = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    private static void renderDistanceHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {

        updateAnimation();

        if (lastChangeTimeMs == 0) return;

        long elapsed = System.currentTimeMillis() - lastChangeTimeMs;
        if (elapsed >= TOTAL_VISIBLE_MS) return;

        float alpha;
        if (elapsed < FADE_IN_MS) {
            alpha = elapsed / (float) FADE_IN_MS;
        } else if (elapsed < FADE_IN_MS + HOLD_MS) {
            alpha = 1.0f;
        } else {
            long fadeOutElapsed = elapsed - FADE_IN_MS - HOLD_MS;
            alpha = 1.0f - (fadeOutElapsed / (float) FADE_OUT_MS);
        }
        alpha = Math.clamp(alpha, 0f, 1f);

        Minecraft client = Minecraft.getInstance();
        String text = modEnabled
                ? String.format("Distance: %.1f", cameraDistance)
                : "BetterF5: OFF";
        int textWidth = client.font.width(text);

        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();

        int x = (screenWidth - textWidth) / 2;
        int y = screenHeight - 60;

        int alphaByte = (int) (alpha * 255f);
        int color = (alphaByte << 24) | 0xFFFFFF;

        // Yeni sürüm haritalandırmasındaki .text() çağrısı korundu
        graphics.text(client.font, text, x, y, color, true);
    }

    private static void loadConfig() {
        if (!Files.exists(CONFIG_PATH)) return;
        Properties props = new Properties();
        try (var in = Files.newInputStream(CONFIG_PATH)) {
            props.load(in);
            savedDistance = parseDouble(props.getProperty("distance"));
            savedDistance = Math.clamp(savedDistance, MIN_DISTANCE, MAX_DISTANCE);
            modEnabled = Boolean.parseBoolean(props.getProperty("enabled", "true"));
        } catch (IOException e) {
            LOGGER.warn("BetterF5: config dosyası okunamadı, varsayılanlar kullanılıyor.", e);
        }
    }

    private static void saveConfig() {
        Properties props = new Properties();
        props.setProperty("distance", String.valueOf(savedDistance));
        props.setProperty("enabled", String.valueOf(modEnabled));
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (var out = Files.newOutputStream(CONFIG_PATH)) {
                props.store(out, "BetterF5 mod ayarlari");
            }
        } catch (IOException e) {
            LOGGER.warn("BetterF5: config dosyası yazılamadı.", e);
        }
    }

    // Fallback parametresi kaldırılmış güncel parse metodu korundu
    private static double parseDouble(String value) {
        if (value == null) return DEFAULT_DISTANCE;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return DEFAULT_DISTANCE;
        }
    }
}
