package com.harlequinxd.drivehard.gl;

/**
 * PS1-style shaders.
 *  - vertex positions snapped to the low-res pixel grid (the classic jitter)
 *  - affine (non perspective-correct) texture mapping
 *  - nearest filtering, per-vertex lighting, distance fog
 *  - 15-bit colour with 4x4 ordered dithering; alpha is screen-door dithered
 */
public final class Shaders {
    private Shaders() {}

    public static final String WORLD_VS =
            "uniform mat4 uMVP;\n" +
            "uniform vec2 uSnap;\n" +
            "uniform vec3 uCam;\n" +
            "uniform vec2 uFog;\n" +
            "attribute vec3 aPos;\n" +
            "attribute vec2 aUV;\n" +
            "attribute vec3 aCol;\n" +
            "varying vec3 vUVW;\n" +
            "varying vec3 vCol;\n" +
            "varying float vFog;\n" +
            "void main() {\n" +
            "  vec4 p = uMVP * vec4(aPos, 1.0);\n" +
            "  if (p.w > 0.0) {\n" +
            "    vec2 n = p.xy / p.w;\n" +
            "    n = floor(n * uSnap + 0.5) / uSnap;\n" +
            "    p.xy = n * p.w;\n" +
            "  }\n" +
            "  vUVW = vec3(aUV * p.w, p.w);\n" +
            "  vCol = aCol;\n" +
            "  float d = length(aPos - uCam);\n" +
            "  vFog = clamp((d - uFog.x) / (uFog.y - uFog.x), 0.0, 1.0);\n" +
            "  gl_Position = p;\n" +
            "}\n";

    public static final String WORLD_FS =
            "#ifdef GL_FRAGMENT_PRECISION_HIGH\n" +
            "precision highp float;\n" +
            "#else\n" +
            "precision mediump float;\n" +
            "#endif\n" +
            "uniform sampler2D uTex;\n" +
            "uniform sampler2D uDither;\n" +
            "uniform vec3 uFogCol;\n" +
            "uniform vec3 uTint;\n" +
            "varying vec3 vUVW;\n" +
            "varying vec3 vCol;\n" +
            "varying float vFog;\n" +
            "void main() {\n" +
            "  vec2 uv = vUVW.xy / vUVW.z;\n" +
            "  vec4 t = texture2D(uTex, uv);\n" +
            "  float d = texture2D(uDither, gl_FragCoord.xy * 0.25).r;\n" +
            "  if (t.a < d) discard;\n" +
            "  vec3 c = t.rgb * vCol * uTint;\n" +
            "  c = mix(c, uFogCol, vFog);\n" +
            "  c = floor(c * 31.0 + d) / 31.0;\n" +
            "  gl_FragColor = vec4(c, 1.0);\n" +
            "}\n";

    public static final String BLIT_VS =
            "attribute vec2 aPos;\n" +
            "varying vec2 vUV;\n" +
            "void main() {\n" +
            "  vUV = aPos * 0.5 + 0.5;\n" +
            "  gl_Position = vec4(aPos, 0.0, 1.0);\n" +
            "}\n";

    public static final String BLIT_FS =
            "precision mediump float;\n" +
            "uniform sampler2D uTex;\n" +
            "varying vec2 vUV;\n" +
            "void main() { gl_FragColor = texture2D(uTex, vUV); }\n";

    public static final String HUD_VS =
            "uniform vec2 uScreen;\n" +
            "attribute vec2 aPos;\n" +
            "attribute vec2 aUV;\n" +
            "varying vec2 vUV;\n" +
            "void main() {\n" +
            "  vUV = aUV;\n" +
            "  gl_Position = vec4(aPos.x / uScreen.x * 2.0 - 1.0, 1.0 - aPos.y / uScreen.y * 2.0, 0.0, 1.0);\n" +
            "}\n";

    public static final String HUD_FS =
            "precision mediump float;\n" +
            "uniform sampler2D uTex;\n" +
            "uniform vec4 uColor;\n" +
            "varying vec2 vUV;\n" +
            "void main() { gl_FragColor = texture2D(uTex, vUV) * uColor; }\n";
}
