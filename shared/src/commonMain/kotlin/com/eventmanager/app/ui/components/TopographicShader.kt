package com.eventmanager.app.ui.components

import androidx.compose.ui.graphics.Color

internal data class TopographicConfig(
    val zoom: Float,
    val bands: Float,
    val edgeThreshold: Float,
    val timeScale: Float,
    val animationCycleMillis: Long,
)

internal fun topographicConfig(isDesktop: Boolean): TopographicConfig {
    return if (isDesktop) {
        TopographicConfig(
            zoom = 0.0026f,
            bands = 10f,
            edgeThreshold = 0.16f,
            timeScale = 0.008f,
            animationCycleMillis = 720_000L,
        )
    } else {
        TopographicConfig(
            zoom = 0.0028f,
            bands = 9f,
            edgeThreshold = 0.16f,
            timeScale = 0.009f,
            animationCycleMillis = 720_000L,
        )
    }
}

internal fun topographicAnimationDurationMillis(
    config: TopographicConfig,
    animationMultiplier: Float,
): Int = (config.animationCycleMillis / animationMultiplier.coerceAtLeast(0.25f))
    .toInt()
    .coerceAtLeast(60_000)

internal fun Color.shaderComponents(): FloatArray =
    floatArrayOf(red, green, blue, alpha)

/**
 * SkSL for Skia RuntimeEffect (desktop).
 * Uses float4 uniforms and no integer types — required for Skia shader compilation.
 */
internal val TOPOGRAPHIC_SKSL: String = """
    uniform float iTime;
    uniform float uZoom;
    uniform float uBands;
    uniform float uEdgeThreshold;
    uniform float uTimeScale;
    uniform float4 uColor0;
    uniform float4 uColor1;
    uniform float4 uColor2;
    uniform float4 uColor3;

    float3 mod289(float3 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
    float4 mod289(float4 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
    float4 permute(float4 x) { return mod289(((x * 34.0) + 1.0) * x); }
    float4 taylorInvSqrt(float4 r) { return 1.79284291400159 - 0.85373472095314 * r; }

    float snoise3(float3 v) {
        const float2 C = float2(1.0 / 6.0, 1.0 / 3.0);
        const float4 D = float4(0.0, 0.5, 1.0, 2.0);

        float3 i = floor(v + dot(v, float3(C.y, C.y, C.y)));
        float3 x0 = v - i + dot(i, float3(C.x, C.x, C.x));

        float3 g = step(x0.yzx, x0.xyz);
        float3 l = 1.0 - g;
        float3 i1 = min(g.xyz, l.zxy);
        float3 i2 = max(g.xyz, l.zxy);

        float3 x1 = x0 - i1 + C.x;
        float3 x2 = x0 - i2 + C.y;
        float3 x3 = x0 - D.y;

        i = mod289(i);
        float4 p = permute(permute(permute(
            i.z + float4(0.0, i1.z, i2.z, 1.0))
          + i.y + float4(0.0, i1.y, i2.y, 1.0))
          + i.x + float4(0.0, i1.x, i2.x, 1.0));

        float n_ = 0.142857142857;
        float3 ns = n_ * D.wyz - D.xzx;

        float4 j = p - 49.0 * floor(p * ns.z * ns.z);
        float4 x_ = floor(j * ns.z);
        float4 y_ = floor(j - 7.0 * x_);

        float4 x = x_ * ns.x + ns.yyyy;
        float4 y = y_ * ns.x + ns.yyyy;
        float4 h = 1.0 - abs(x) - abs(y);

        float4 b0 = float4(x.xy, y.xy);
        float4 b1 = float4(x.zw, y.zw);

        float4 s0 = floor(b0) * 2.0 + 1.0;
        float4 s1 = floor(b1) * 2.0 + 1.0;
        float4 sh = -step(h, float4(0.0));

        float4 a0 = b0.xzyw + s0.xzyw * sh.xxyy;
        float4 a1 = b1.xzyw + s1.xzyw * sh.zzww;

        float3 p0 = float3(a0.xy, h.x);
        float3 p1 = float3(a0.zw, h.y);
        float3 p2 = float3(a1.xy, h.z);
        float3 p3 = float3(a1.zw, h.w);

        float4 norm = taylorInvSqrt(float4(dot(p0, p0), dot(p1, p1), dot(p2, p2), dot(p3, p3)));
        p0 *= norm.x;
        p1 *= norm.y;
        p2 *= norm.z;
        p3 *= norm.w;

        float4 m = max(0.6 - float4(dot(x0, x0), dot(x1, x1), dot(x2, x2), dot(x3, x3)), 0.0);
        m = m * m;
        return 42.0 * dot(m * m, float4(dot(p0, x0), dot(p1, x1), dot(p2, x2), dot(p3, x3)));
    }

    half4 main(float2 fragCoord) {
        float3 samplePos = float3(fragCoord * uZoom, iTime * uTimeScale);
        float raw = snoise3(samplePos);
        float normalized = (raw + 1.0) * 0.5;
        float scaled = uBands * normalized;
        float rounded = ceil(scaled);
        float roundingError = rounded - scaled;
        if (roundingError > uEdgeThreshold) {
            return half4(0.0);
        }
        float band = floor(mod(rounded, 4.0));
        if (band < 0.5) { return half4(uColor0); }
        if (band < 1.5) { return half4(uColor1); }
        if (band < 2.5) { return half4(uColor2); }
        return half4(uColor3);
    }
""".trimIndent()

/** AGSL for Android RuntimeShader (API 33+). */
internal val TOPOGRAPHIC_AGSL: String = """
    uniform float iTime;
    uniform float uZoom;
    uniform float uBands;
    uniform float uEdgeThreshold;
    uniform float uTimeScale;
    uniform vec4 uColor0;
    uniform vec4 uColor1;
    uniform vec4 uColor2;
    uniform vec4 uColor3;

    vec3 mod289(vec3 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
    vec4 mod289(vec4 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
    vec4 permute(vec4 x) { return mod289(((x * 34.0) + 1.0) * x); }
    vec4 taylorInvSqrt(vec4 r) { return 1.79284291400159 - 0.85373472095314 * r; }

    float snoise3(vec3 v) {
        const vec2 C = vec2(1.0 / 6.0, 1.0 / 3.0);
        const vec4 D = vec4(0.0, 0.5, 1.0, 2.0);

        vec3 i = floor(v + dot(v, vec3(C.y)));
        vec3 x0 = v - i + dot(i, vec3(C.x));

        vec3 g = step(x0.yzx, x0.xyz);
        vec3 l = 1.0 - g;
        vec3 i1 = min(g.xyz, l.zxy);
        vec3 i2 = max(g.xyz, l.zxy);

        vec3 x1 = x0 - i1 + C.x;
        vec3 x2 = x0 - i2 + C.y;
        vec3 x3 = x0 - D.y;

        i = mod289(i);
        vec4 p = permute(permute(permute(
            i.z + vec4(0.0, i1.z, i2.z, 1.0))
          + i.y + vec4(0.0, i1.y, i2.y, 1.0))
          + i.x + vec4(0.0, i1.x, i2.x, 1.0));

        float n_ = 0.142857142857;
        vec3 ns = n_ * D.wyz - D.xzx;

        vec4 j = p - 49.0 * floor(p * ns.z * ns.z);
        vec4 x_ = floor(j * ns.z);
        vec4 y_ = floor(j - 7.0 * x_);

        vec4 x = x_ * ns.x + ns.yyyy;
        vec4 y = y_ * ns.x + ns.yyyy;
        vec4 h = 1.0 - abs(x) - abs(y);

        vec4 b0 = vec4(x.xy, y.xy);
        vec4 b1 = vec4(x.zw, y.zw);

        vec4 s0 = floor(b0) * 2.0 + 1.0;
        vec4 s1 = floor(b1) * 2.0 + 1.0;
        vec4 sh = -step(h, vec4(0.0));

        vec4 a0 = b0.xzyw + s0.xzyw * sh.xxyy;
        vec4 a1 = b1.xzyw + s1.xzyw * sh.zzww;

        vec3 p0 = vec3(a0.xy, h.x);
        vec3 p1 = vec3(a0.zw, h.y);
        vec3 p2 = vec3(a1.xy, h.z);
        vec3 p3 = vec3(a1.zw, h.w);

        vec4 norm = taylorInvSqrt(vec4(dot(p0, p0), dot(p1, p1), dot(p2, p2), dot(p3, p3)));
        p0 *= norm.x;
        p1 *= norm.y;
        p2 *= norm.z;
        p3 *= norm.w;

        vec4 m = max(0.6 - vec4(dot(x0, x0), dot(x1, x1), dot(x2, x2), dot(x3, x3)), 0.0);
        m = m * m;
        return 42.0 * dot(m * m, vec4(dot(p0, x0), dot(p1, x1), dot(p2, x2), dot(p3, x3)));
    }

    vec4 main(vec2 fragCoord) {
        vec3 samplePos = vec3(fragCoord * uZoom, iTime * uTimeScale);
        float raw = snoise3(samplePos);
        float normalized = (raw + 1.0) * 0.5;
        float scaled = uBands * normalized;
        float rounded = ceil(scaled);
        float roundingError = rounded - scaled;
        if (roundingError > uEdgeThreshold) {
            return vec4(0.0);
        }
        float band = floor(mod(rounded, 4.0));
        if (band < 0.5) { return uColor0; }
        if (band < 1.5) { return uColor1; }
        if (band < 2.5) { return uColor2; }
        return uColor3;
    }
""".trimIndent()

internal fun applyTopographicColorUniforms(
    setUniform4: (name: String, r: Float, g: Float, b: Float, a: Float) -> Unit,
    lineColors: BackgroundLineColors,
) {
    lineColors.primary.shaderComponents().let { setUniform4("uColor0", it[0], it[1], it[2], it[3]) }
    lineColors.secondary.shaderComponents().let { setUniform4("uColor1", it[0], it[1], it[2], it[3]) }
    lineColors.tertiary.shaderComponents().let { setUniform4("uColor2", it[0], it[1], it[2], it[3]) }
    lineColors.surfaceVariant.shaderComponents().let { setUniform4("uColor3", it[0], it[1], it[2], it[3]) }
}

internal fun applyTopographicConfigUniforms(
    setFloat: (name: String, value: Float) -> Unit,
    config: TopographicConfig,
) {
    setFloat("uZoom", config.zoom)
    setFloat("uBands", config.bands)
    setFloat("uEdgeThreshold", config.edgeThreshold)
    setFloat("uTimeScale", config.timeScale)
}
