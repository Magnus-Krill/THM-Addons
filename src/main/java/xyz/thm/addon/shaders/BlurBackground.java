/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */







package xyz.thm.addon.shaders;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.system.THMSystem;

import java.nio.ByteBuffer;
import java.util.Optional;

// "Frosted glass" blur for just the window rectangle (not the whole shader background): copies
// that region of the already-rendered framebuffer into a small offscreen texture, runs a real
// two-pass (horizontal then vertical) box blur over it - the same technique vanilla's own menu
// blur (assets/minecraft/shaders/post/box_blur.fsh) uses, just simplified to a plain N-tap
// average instead of its bilinear-doubled sampling - then blits the result back onto the main
// framebuffer, scissored to the window rect so nothing outside it is touched. No changes needed
// to the 19 shader files, and all shader sources here are supplied inline (no .fsh resource
// files) via GpuDevice#precompilePipeline(pipeline, sourceGetter).
class BlurBackground {
    private static final Identifier VSH_ID = Identifier.fromNamespaceAndPath(THMAddon.MOD_ID, "blur_vsh");
    private static final Identifier BLUR_FSH_ID = Identifier.fromNamespaceAndPath(THMAddon.MOD_ID, "blur_pass_fsh");
    private static final Identifier BLIT_FSH_ID = Identifier.fromNamespaceAndPath(THMAddon.MOD_ID, "blur_blit_fsh");
    private static final Identifier EXTRACT_FSH_ID = Identifier.fromNamespaceAndPath(THMAddon.MOD_ID, "blur_extract_fsh");
    private static final Identifier GLASS_FSH_ID = Identifier.fromNamespaceAndPath(THMAddon.MOD_ID, "glass_fsh");
    private static final float MAX_RADIUS = 20f; // texels, at strength = 100
    // The shader render + both blur passes work at 1/WORK_SCALE resolution - a blurred image
    // already destroys fine detail, so downsampling before blurring and upsampling on the final
    // blit (bilinear, see FilterMode.LINEAR below) is the standard cost/quality tradeoff every
    // engine's blur/bloom uses; cuts the two most expensive passes' pixel count by WORK_SCALE^2.
    private static final int WORK_SCALE = 2;
    private static final int MIN_TEXTURE_SIZE = 4;
    private static final long BLUR_PARAMS_SIZE = 16L; // std140: vec4(stepX, stepY, radius, unused)
    private static final long RECT_BUFFER_SIZE = 32L; // std140: vec4 rectPixels, vec4 fbSize
    private static final long GLASS_RECT_BUFFER_SIZE = 64L;

    private static final String VSH_SRC = """
        #version 330 core
        out vec2 texCoord;
        void main() {
            vec2 uv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
            gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);
            texCoord = uv;
        }
        """;

    private static final String BLUR_FSH_SRC = """
        #version 330 core
        layout(std140) uniform BlurParams {
            vec4 params;
        };
        in vec2 texCoord;
        uniform sampler2D InSampler;
        out vec4 fragColor;
        void main() {
            vec4 sum = texture(InSampler, texCoord);
            float total = 1.0;
            float radius = params.z;
            for (float i = 1.0; i <= radius; i += 1.0) {
                sum += texture(InSampler, texCoord + params.xy * i);
                sum += texture(InSampler, texCoord - params.xy * i);
                total += 2.0;
            }
            fragColor = sum / total;
        }
        """;

    private static final String BLIT_FSH_SRC = """
        #version 330 core
        layout(std140) uniform BlitRect {
            vec4 rectPixels;
            vec4 fbSize;
        };
        uniform sampler2D InSampler;
        out vec4 fragColor;
        void main() {
            vec2 localUV = (gl_FragCoord.xy - rectPixels.xy) / rectPixels.zw;
            fragColor = texture(InSampler, localUV);
        }
        """;

    // Reads the window's region straight out of the main framebuffer's colour texture (bound as
    // InSampler) into the working texture. Sampling it rather than copyTextureToTexture'ing it
    // keeps every coordinate in one convention: a framebuffer texel's v matches the gl_FragCoord.y
    // that wrote it (bottom-left origin), which is the same space the scissor and the blit's
    // rectPixels math use - whereas texture-storage copies address top-down, and reconciling the
    // two by hand is what left the blurred patch vertically offset from the window.
    private static final String EXTRACT_FSH_SRC = """
        #version 330 core
        layout(std140) uniform BlitRect {
            vec4 rectPixels;
            vec4 fbSize;
        };
        in vec2 texCoord;
        uniform sampler2D InSampler;
        out vec4 fragColor;
        void main() {
            vec2 srcPixels = rectPixels.xy + texCoord * rectPixels.zw;
            fragColor = texture(InSampler, srcPixels / fbSize.xy);
        }
        """;

    private static RenderPipeline blurPipeline;
    private static RenderPipeline blitPipeline;
    private static RenderPipeline extractPipeline;
    private static RenderPipeline glassPipeline;
    private static Boolean pipelinesValid;
    private static GpuBuffer blurParamsBuffer;
    private static GpuBuffer rectBuffer;
    private static GpuBuffer glassRectBuffer;

    private static final String GLASS_FSH_SRC = """
        #version 330 core
        layout(std140) uniform BlitRect {
            vec4 rectPixels;
            vec4 frameAndStyle;
            vec4 pointerAndLens;
            vec4 tint;
        };
        uniform sampler2D InSampler;
        out vec4 fragColor;
        void main() {
            vec2 p = gl_FragCoord.xy - rectPixels.xy - rectPixels.zw * 0.5;
            float radius = min(frameAndStyle.z, min(rectPixels.z, rectPixels.w) * 0.5);
            vec2 q = abs(p) - rectPixels.zw * 0.5 + radius;
            vec2 corner = max(q, vec2(0.0));
            float distance = length(corner) + min(max(q.x, q.y), 0.0) - radius;
            float coverage = 1.0 - smoothstep(-0.9, 0.0, distance);
            if (coverage <= 0.0) {
                float shadow = 1.0 - smoothstep(0.0, min(12.0, radius), distance + 1.7 * sign(p.y));
                fragColor = vec4(0.02, 0.03, 0.05, shadow * 0.18 * frameAndStyle.w);
                return;
            }
            vec2 side = q.x > q.y ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
            vec2 normal = sign(p) * (dot(corner, corner) > 0.001 ? normalize(corner) : side);
            float depth = -distance;
            float bevel = 1.0 - smoothstep(0.0, radius * 1.15, depth);
            vec2 bend = normal * bevel * bevel * radius * pointerAndLens.z;
            vec2 uv = (gl_FragCoord.xy - bend) / frameAndStyle.xy;
            vec3 scene = texture(InSampler, uv).rgb;
            if (bevel > 0.0) {
                vec2 dispersion = normal * bevel * 1.35 / frameAndStyle.xy;
                scene.r = texture(InSampler, uv + dispersion).r;
                scene.b = texture(InSampler, uv - dispersion).b;
            }

            vec3 glass = mix(scene, tint.rgb, tint.a) * 0.61 + vec3(0.025, 0.031, 0.044);
            // Compress bright backdrops while keeping their color behind light labels.
            float peak = max(glass.r, max(glass.g, glass.b));
            glass *= 1.0 / (1.0 + peak * 1.25);

            vec2 toPointer = pointerAndLens.xy - gl_FragCoord.xy;
            float proximity = 1.0 - smoothstep(18.0, 240.0, length(toPointer));
            vec2 light = normalize(vec2(-0.68, 0.8) + toPointer / max(length(toPointer), 1.0) * proximity * 0.8);
            float facing = dot(normal, light);
            float glint = exp(-0.47 * abs(depth - 1.5)) * pow(max(facing, 0.0), 2.0);
            float bounce = exp(-0.57 * abs(depth - 1.2)) * max(-facing, 0.0);
            float edgeShadow = exp(-0.43 * abs(depth - 5.0)) * max(-facing, 0.0);
            glass *= 1.0 - edgeShadow * 0.23;
            glass = mix(glass, vec3(0.83, 0.9, 1.0), glint * (0.48 + proximity * 0.2) + bounce * 0.18);
            fragColor = vec4(glass, coverage * frameAndStyle.w);
        }
        """;

    private static GpuTexture textureA, textureB;
    private static GpuTextureView viewA, viewB;
    private static int texWidth = -1, texHeight = -1;

    private static GpuTexture scaledTexture;
    private static GpuTextureView scaledView;
    private static int scaledWidth = -1, scaledHeight = -1;
    private static RenderPipeline scaledPipeline;
    private static long lastShaderDraw;
    private static int lastShaderFps = -1;

    /**
     * Updates the background at its configured resolution and animation rate,
     * then upscales it onto the main framebuffer (bilinear). Reuses the blur pass pipeline with
     * radius = 0, which is just a passthrough sample, i.e. a plain resize.
     *
     * @return true if it drew (caller falls back to a full-resolution draw if not).
     */
    static boolean renderScaled(RenderPipeline shaderPipeline) {
        GpuDevice device = RenderSystem.getDevice();
        if (!ensurePipelines(device)) return false;

        RenderTarget framebuffer = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        GpuTextureView mainView = framebuffer.getColorTextureView();
        if (mainView == null) return false;

        THMSystem system = THMSystem.get();
        int resolution = system == null ? 25 : system.shaderResolution.get();
        int fps = system == null ? 30 : system.shaderFps.get();
        int w = shaderDimension(framebuffer.width, resolution);
        int h = shaderDimension(framebuffer.height, resolution);
        ensureScaledTexture(device, w, h);

        long now = System.nanoTime();
        boolean changed = scaledPipeline != shaderPipeline || lastShaderFps != fps;
        if (shouldUpdateShader(now, lastShaderDraw, fps, changed)) {
            ShaderBackground.drawInto(shaderPipeline, scaledView, w, h);
            lastShaderDraw = now;
            if (changed) THMAddon.LOG.info("[THM/Menu] Background render budget; size={}x{}, animationFps={}", w, h, fps);
            scaledPipeline = shaderPipeline;
            lastShaderFps = fps;
        }

        CommandEncoder encoder = device.createCommandEncoder();
        writeBlurParams(encoder, 0f, 0f, 0f);
        drawFullscreen(encoder, blurPipeline, mainView, "BlurParams", blurParamsBuffer, scaledView);
        return true;
    }

    static int shaderDimension(int dimension, int percentage) {
        return Math.max(MIN_TEXTURE_SIZE, (int) ((long) dimension * Math.clamp(percentage, 25, 100) / 100));
    }

    static boolean shouldUpdateShader(long now, long lastDraw, int fps, boolean changed) {
        return changed || fps <= 0 || now - lastDraw >= 1_000_000_000L / fps;
    }

    /**
     * Blurs whatever is already rendered behind the window - the background shader, or the
     * vanilla panorama when no shader is active. (This used to re-render the active shader into
     * a window-sized texture instead of copying the framebuffer, which drew a *second*, squashed
     * copy of the shader at the window's aspect ratio rather than blurring the one on screen.)
     *
     * @param x1 x2 y1 y2 window bounds in GUI-scaled screen coordinates (same space as
     *           MainMenuFx.windowBounds).
     * @return true if it drew.
     */
    static boolean renderRegion(int strength, int x1, int y1, int x2, int y2) {
        GpuDevice device = RenderSystem.getDevice();
        if (!ensurePipelines(device)) return false;

        Minecraft mc = Minecraft.getInstance();
        RenderTarget framebuffer = mc.gameRenderer.mainRenderTarget();
        GpuTextureView mainView = framebuffer.getColorTextureView();
        if (mainView == null) return false;

        Window window = mc.getWindow();
        double scaleX = framebuffer.width / (double) window.getGuiScaledWidth();
        double scaleY = framebuffer.height / (double) window.getGuiScaledHeight();
        int px1 = Math.clamp((int) Math.round(x1 * scaleX), 0, framebuffer.width);
        int px2 = Math.clamp((int) Math.round(x2 * scaleX), px1, framebuffer.width);
        int py1 = Math.clamp((int) Math.round(framebuffer.height - y2 * scaleY), 0, framebuffer.height);
        int py2 = Math.clamp((int) Math.round(framebuffer.height - y1 * scaleY), py1, framebuffer.height);
        int pw = px2 - px1;
        int ph = py2 - py1;
        if (pw <= 0 || ph <= 0) return false;

        int workW = Math.max(MIN_TEXTURE_SIZE, pw / WORK_SCALE);
        int workH = Math.max(MIN_TEXTURE_SIZE, ph / WORK_SCALE);
        ensureTextures(device, workW, workH);

        float radius = Math.max(1f, strength / 100f * MAX_RADIUS);
        CommandEncoder encoder = device.createCommandEncoder();
        writeRect(encoder, px1, py1, pw, ph, framebuffer.width, framebuffer.height, 0, 0);

        // Pass 1: sample the window's region out of the main framebuffer, downscaled into A.
        drawFullscreen(encoder, extractPipeline, viewA, "BlitRect", rectBuffer, mainView);

        // Pass 2: horizontal blur, A -> B.
        writeBlurParams(encoder, 1f / workW, 0f, radius);
        drawFullscreen(encoder, blurPipeline, viewB, "BlurParams", blurParamsBuffer, viewA);

        // Pass 3: vertical blur, B -> A.
        writeBlurParams(encoder, 0f, 1f / workH, radius);
        drawFullscreen(encoder, blurPipeline, viewA, "BlurParams", blurParamsBuffer, viewB);

        // Pass 4: blit A back onto the main framebuffer at full size, scissored to the window
        // rect - the bilinear sampler does the upscale, which is fine since it's already blurred.
        try (RenderPass pass = encoder.createRenderPass(() -> "THM blur blit", mainView, Optional.empty())) {
            pass.enableScissor(px1, py1, pw, ph);
            pass.setPipeline(blitPipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("BlitRect", rectBuffer);
            pass.bindTexture("InSampler", viewA, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(3, 1, 0, 0);
        }

        return true;
    }

    static boolean renderGlass(ShaderBackground.GlassPane pane, float opacity, boolean captureScene) {
        GpuDevice device = RenderSystem.getDevice();
        if (!ensurePipelines(device)) return false;
        if (glassPipeline == null) {
            RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(THMAddon.MOD_ID, "glass"))
                .withVertexShader(VSH_ID)
                .withFragmentShader(GLASS_FSH_ID)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withBindGroupLayout(BindGroupLayout.builder().withUniform("BlitRect", UniformType.UNIFORM_BUFFER).build())
                .withBindGroupLayout(BindGroupLayout.builder().withSampler("InSampler").build())
                .build();
            if (!device.precompilePipeline(pipeline, BlurBackground::shaderSource).isValid()) {
                throw new IllegalStateException("Glass shader failed to compile");
            }
            glassPipeline = pipeline;
        }

        Minecraft mc = Minecraft.getInstance();
        RenderTarget framebuffer = mc.gameRenderer.mainRenderTarget();
        GpuTextureView mainView = framebuffer.getColorTextureView();
        if (mainView == null) return false;
        Window window = mc.getWindow();
        double sx = framebuffer.width / (double) window.getWidth();
        double sy = framebuffer.height / (double) window.getHeight();
        int x1 = (int) Math.round(pane.x() * sx);
        int x2 = (int) Math.round((pane.x() + pane.width()) * sx);
        int y1 = framebuffer.height - (int) Math.round((pane.y() + pane.height()) * sy);
        int y2 = framebuffer.height - (int) Math.round(pane.y() * sy);
        int left = Math.max(0, x1 - 14);
        int bottom = Math.max(0, y1 - 14);
        int right = Math.min(framebuffer.width, x2 + 14);
        int top = Math.min(framebuffer.height, y2 + 14);
        if (right <= left || top <= bottom) return false;

        CommandEncoder encoder = device.createCommandEncoder();
        if (captureScene) {
            int workW = Math.max(MIN_TEXTURE_SIZE, framebuffer.width / WORK_SCALE);
            int workH = Math.max(MIN_TEXTURE_SIZE, framebuffer.height / WORK_SCALE);
            ensureTextures(device, workW, workH);

            // Capture before Meteor flushes any pane fill or text.
            writeBlurParams(encoder, 1f / workW, 0, 3);
            drawFullscreen(encoder, blurPipeline, viewA, "BlurParams", blurParamsBuffer, mainView);
            writeBlurParams(encoder, 0, 1f / workH, 3);
            drawFullscreen(encoder, blurPipeline, viewB, "BlurParams", blurParamsBuffer, viewA);
        }

        writeGlassRect(encoder, pane, x1, y1, x2 - x1, y2 - y1, framebuffer.width, framebuffer.height,
            (float) Math.min(sx, sy), opacity, (float) (mc.mouseHandler.xpos() * sx),
            framebuffer.height - (float) (mc.mouseHandler.ypos() * sy));
        try (RenderPass pass = encoder.createRenderPass(() -> "THM refractive glass", mainView, Optional.empty())) {
            pass.enableScissor(left, bottom, right - left, top - bottom);
            pass.setPipeline(glassPipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("BlitRect", glassRectBuffer);
            pass.bindTexture("InSampler", viewB, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(3, 1, 0, 0);
        }
        return true;
    }

    private static void writeGlassRect(CommandEncoder encoder, ShaderBackground.GlassPane pane,
                                       int x, int y, int width, int height, int frameWidth, int frameHeight,
                                       float scale, float opacity, float mouseX, float mouseY) {
        if (glassRectBuffer == null) {
            glassRectBuffer = RenderSystem.getDevice().createBuffer(() -> "THM glass material",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, GLASS_RECT_BUFFER_SIZE);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.malloc((int) GLASS_RECT_BUFFER_SIZE);
            data.asFloatBuffer()
                .put(x).put(y).put(width).put(height)
                .put(frameWidth).put(frameHeight).put((float) pane.radius() * scale).put(opacity)
                .put(mouseX).put(mouseY).put(pane.lens()).put(0)
                .put(pane.tint().r / 255f).put(pane.tint().g / 255f).put(pane.tint().b / 255f).put(pane.tint().a / 255f);
            encoder.writeToBuffer(glassRectBuffer.slice(), data);
        }
    }

    private static void drawFullscreen(CommandEncoder encoder, RenderPipeline pipeline, GpuTextureView target,
                                        String uniformName, GpuBuffer uniformBuffer, GpuTextureView source) {
        try (RenderPass pass = encoder.createRenderPass(() -> "THM blur pass", target, Optional.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform(uniformName, uniformBuffer);
            pass.bindTexture("InSampler", source, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(3, 1, 0, 0);
        }
    }

    private static void writeBlurParams(CommandEncoder encoder, float stepX, float stepY, float radius) {
        if (blurParamsBuffer == null) {
            blurParamsBuffer = RenderSystem.getDevice().createBuffer(() -> "THM blur params", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, BLUR_PARAMS_SIZE);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.calloc((int) BLUR_PARAMS_SIZE);
            data.putFloat(0, stepX);
            data.putFloat(4, stepY);
            data.putFloat(8, radius);
            encoder.writeToBuffer(blurParamsBuffer.slice(), data);
        }
    }

    private static void writeRect(CommandEncoder encoder, int px1, int py1, int pw, int ph, int fbWidth, int fbHeight,
                                  float radius, float opacity) {
        if (rectBuffer == null) {
            rectBuffer = RenderSystem.getDevice().createBuffer(() -> "THM blur rect", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, RECT_BUFFER_SIZE);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.calloc((int) RECT_BUFFER_SIZE);
            data.putFloat(0, px1);
            data.putFloat(4, py1);
            data.putFloat(8, pw);
            data.putFloat(12, ph);
            data.putFloat(16, fbWidth);
            data.putFloat(20, fbHeight);
            data.putFloat(24, radius);
            data.putFloat(28, opacity);
            encoder.writeToBuffer(rectBuffer.slice(), data);
        }
    }

    private static boolean ensurePipelines(GpuDevice device) {
        if (pipelinesValid != null) return pipelinesValid;

        blurPipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(THMAddon.MOD_ID, "blur_pass"))
            .withVertexShader(VSH_ID)
            .withFragmentShader(BLUR_FSH_ID)
            .withBindGroupLayout(BindGroupLayout.builder().withUniform("BlurParams", UniformType.UNIFORM_BUFFER).build())
            .withBindGroupLayout(BindGroupLayout.builder().withSampler("InSampler").build())
            .build();

        blitPipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(THMAddon.MOD_ID, "blur_blit"))
            .withVertexShader(VSH_ID)
            .withFragmentShader(BLIT_FSH_ID)
            .withBindGroupLayout(BindGroupLayout.builder().withUniform("BlitRect", UniformType.UNIFORM_BUFFER).build())
            .withBindGroupLayout(BindGroupLayout.builder().withSampler("InSampler").build())
            .build();

        extractPipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(THMAddon.MOD_ID, "blur_extract"))
            .withVertexShader(VSH_ID)
            .withFragmentShader(EXTRACT_FSH_ID)
            .withBindGroupLayout(BindGroupLayout.builder().withUniform("BlitRect", UniformType.UNIFORM_BUFFER).build())
            .withBindGroupLayout(BindGroupLayout.builder().withSampler("InSampler").build())
            .build();

        boolean blurOk = device.precompilePipeline(blurPipeline, BlurBackground::shaderSource).isValid();
        boolean blitOk = device.precompilePipeline(blitPipeline, BlurBackground::shaderSource).isValid();
        boolean extractOk = device.precompilePipeline(extractPipeline, BlurBackground::shaderSource).isValid();
        pipelinesValid = blurOk && blitOk && extractOk;
        if (!pipelinesValid) THMAddon.LOG.warn("[THM] Main-menu window blur shaders failed to compile, disabling blur");
        return pipelinesValid;
    }

    private static String shaderSource(Identifier id, ShaderType type) {
        if (id.equals(VSH_ID)) return VSH_SRC;
        if (id.equals(BLUR_FSH_ID)) return BLUR_FSH_SRC;
        if (id.equals(BLIT_FSH_ID)) return BLIT_FSH_SRC;
        if (id.equals(EXTRACT_FSH_ID)) return EXTRACT_FSH_SRC;
        if (id.equals(GLASS_FSH_ID)) return GLASS_FSH_SRC;
        return null;
    }

    private static void ensureScaledTexture(GpuDevice device, int w, int h) {
        if (scaledTexture != null && scaledWidth == w && scaledHeight == h) return;

        if (scaledView != null) scaledView.close();
        if (scaledTexture != null) scaledTexture.close();

        scaledTexture = device.createTexture(() -> "THM shader scaled",
            GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING, GpuFormat.RGBA8_UNORM, w, h, 1, 1);
        scaledView = device.createTextureView(scaledTexture);
        scaledWidth = w;
        scaledHeight = h;
        scaledPipeline = null;
    }

    private static void ensureTextures(GpuDevice device, int w, int h) {
        if (textureA != null && texWidth == w && texHeight == h) return;

        if (viewA != null) viewA.close();
        if (viewB != null) viewB.close();
        if (textureA != null) textureA.close();
        if (textureB != null) textureB.close();

        int usage = GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING;
        textureA = device.createTexture(() -> "THM blur A", usage, GpuFormat.RGBA8_UNORM, w, h, 1, 1);
        textureB = device.createTexture(() -> "THM blur B", usage, GpuFormat.RGBA8_UNORM, w, h, 1, 1);
        viewA = device.createTextureView(textureA);
        viewB = device.createTextureView(textureB);
        texWidth = w;
        texHeight = h;
    }
}
