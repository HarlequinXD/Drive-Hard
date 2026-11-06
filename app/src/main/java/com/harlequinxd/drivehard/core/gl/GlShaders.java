package com.harlequinxd.drivehard.core.gl;

/**
 * GLSL ES 3.00 sources of the GPU renderer. Each is a line-for-line port of the CPU renderer's
 * per-pixel code ({@link com.harlequinxd.drivehard.core.render.SoftRenderer}): the same sky, ground
 * colours, sea and shading, reduced to 15-bit colour with the same 4x4 ordered dither.
 */
final class GlShaders {
    private GlShaders() { }

    static final String HEAD = "#version 300 es\n"
            + "precision highp float;\nprecision highp int;\n"
            + "precision highp usampler2D;\nprecision highp sampler2DArray;\n";

    /** The camera: eye space is (right, up, forward), the horizon is a vertical shift (no pitch). */
    static final String CAMERA = ""
            + "uniform vec3 uEye;\n"
            + "uniform vec4 uBasis;\n"            // right.x, right.z, fwd.x, fwd.z
            + "uniform vec4 uProj;\n"             // 2f/W, 2f/H, 1-2hy/H, snap (1 = PS1 vertex snapping)
            + "uniform vec2 uDepth;\n"            // A, B: z_clip = A*vz + B*s
            + "uniform vec2 uRes;\n"
            + "vec4 project(vec3 p, float s, out float vz) {\n"
            + "  vec3 d = p - uEye;\n"
            + "  float vx = d.x * uBasis.x + d.z * uBasis.y;\n"
            + "  vz = d.x * uBasis.z + d.z * uBasis.w;\n"
            + "  vec4 c = vec4(uProj.x * vx, uProj.y * d.y + uProj.z * vz, uDepth.x * vz + uDepth.y * s, vz);\n"
            + "  if (uProj.w > 0.5 && vz > 0.05) {\n"
            + "    vec2 px = floor((c.xy / c.w * 0.5 + 0.5) * uRes + 0.5);\n"
            + "    c.xy = (px / uRes * 2.0 - 1.0) * c.w;\n"
            + "  }\n"
            + "  return c;\n"
            + "}\n";

    /** 15-bit colour with the CPU renderer's 4x4 ordered dither. */
    static final String DITHER = ""
            + "const int BAYER[16] = int[16](0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5);\n"
            + "uniform float uRows;\n"
            + "int bayer() {\n"
            + "  int x = int(gl_FragCoord.x), y = int(uRows) - 1 - int(gl_FragCoord.y);\n"
            + "  return BAYER[((y & 3) << 2) | (x & 3)];\n"
            + "}\n"
            + "vec3 quant(vec3 c8, float d) {\n"
            + "  vec3 v = min(floor((max(c8, 0.0) + d) / 8.0), vec3(31.0));\n"
            + "  return (v * 8.0 + floor(v / 4.0)) / 255.0;\n"
            + "}\n";

    // ------------------------------------------------------------------ polygons and sprites

    static final String POLY_VS = HEAD + CAMERA
            + "layout(location = 0) in vec3 aPos;\n"
            + "layout(location = 1) in vec2 aUv;\n"
            + "layout(location = 2) in vec4 aLit;\n"      // light/4, coverage, depth scale/4
            + "layout(location = 3) in vec4 aNrm;\n"
            + "layout(location = 4) in uvec2 aTex;\n"     // texture id, flags
            + "uniform vec3 uOffset;\n"
            + "uniform float uScroll;\n"
            + "uniform usampler2D uInfo;\n"
            + "out vec2 vUv;\n"
            + "out vec2 vUvW;\n"                         // uv * w: screen-linear uv after / vW
            + "out float vW;\n"
            + "uniform int uCorners;\n"                   // 1: polygon corners in uCornerTex (the ordering table)
            + "uniform float uNear;\n"
            + "uniform highp sampler2D uCornerTex;\n"
            // the polygon's outline on screen, clipped to the near plane: up to 5 corners (x, y pixels, u, v)
            + "flat out vec4 vP0, vP1, vP2, vP3, vP4;\n"
            + "flat out int vON;\n"
            + "vec2 toPixel(vec3 p) { float z; vec4 c = project(p, 1.0, z); return (c.xy / c.w * 0.5 + 0.5) * uRes; }\n"
            + "float viewZ(vec3 p) { vec3 d = p - uEye; return d.x * uBasis.z + d.z * uBasis.w; }\n"
            + "out vec2 vLit;\n"
            + "out float vDepth;\n"
            + "out float vDist;\n"
            + "flat out uvec4 vRect;\n"
            + "flat out uint vFlags;\n"
            + "void main() {\n"
            + "  vec3 p = aPos + uOffset;\n"
            + "  vec3 n = aNrm.xyz;\n"
            + "  vFlags = aTex.y;\n"
            + "  vRect = texelFetch(uInfo, ivec2(int(aTex.x & 63u), int(aTex.x >> 6)), 0);\n"
            + "  vUv = aUv;\n"
            + "  vLit = vec2(aLit.x * 4.0, aLit.y);\n"
            + "  vDist = length((p - uEye).xz);\n"
            + "  if (dot(n, n) > 0.01 && dot(n, uEye - p) <= 0.0) { vDepth = 0.0; gl_Position = vec4(2.0, 2.0, 2.0, 1.0); return; }\n"
            + "  if ((aTex.y & 8u) != 0u) vUv.x += uScroll;\n"
            + "  float vz;\n"
            + "  gl_Position = project(p, aLit.z * 4.0, vz);\n"
            + "  vDepth = vz;\n"
            + "  int k = gl_VertexID & 3;\n"
            + "  vW = gl_Position.w;\n"
            + "  vUvW = vUv * vW;\n"
            // the provoking corners (2 and 3) carry the whole polygon's outline to the fragments
            + "  vON = 0; vP0 = vP1 = vP2 = vP3 = vP4 = vec4(0.0);\n"
            + "  if (uCorners != 0 && k >= 2) {\n"
            + "    int pi = gl_VertexID >> 2;\n"
            + "    ivec2 t = ivec2((pi & 255) * 5, pi >> 8);\n"
            + "    vec4 a = texelFetch(uCornerTex, t, 0), b = texelFetch(uCornerTex, t + ivec2(1, 0), 0), c = texelFetch(uCornerTex, t + ivec2(2, 0), 0);\n"
            + "    vec4 d = texelFetch(uCornerTex, t + ivec2(3, 0), 0), e = texelFetch(uCornerTex, t + ivec2(4, 0), 0);\n"
            + "    vec3 P[4] = vec3[4](a.xyz + uOffset, vec3(a.w, b.xy) + uOffset, vec3(b.zw, c.x) + uOffset, c.yzw + uOffset);\n"
            + "    vec2 sc = vec2((aTex.y & 8u) != 0u ? uScroll : 0.0, 0.0);\n"
            + "    vec2 T[4] = vec2[4](d.xy + sc, d.zw + sc, e.xy + sc, e.zw + sc);\n"
            + "    vec4 S[5]; int n = 0;\n"
            + "    for (int i = 0; i < 4; i++) {\n"
            + "      int j = (i + 1) & 3;\n"
            + "      float za = viewZ(P[i]), zb = viewZ(P[j]);\n"
            + "      bool ia = za >= uNear, ib = zb >= uNear;\n"
            + "      if (ia && n < 5) S[n++] = vec4(toPixel(P[i]), T[i]);\n"
            + "      if (ia != ib && n < 5) { float f = (uNear - za) / (zb - za); S[n++] = vec4(toPixel(mix(P[i], P[j], f)), mix(T[i], T[j], f)); }\n"
            + "    }\n"
            + "    for (int i = n; i < 5; i++) S[i] = S[0];\n"
            + "    vP0 = S[0]; vP1 = S[1]; vP2 = S[2]; vP3 = S[3]; vP4 = S[4]; vON = n;\n"
            + "  }\n"
            + "}\n";

    /**
     * Flags: 1 alpha cut-out, 2 stippled blend, 4 small detail (culled beyond the detail distance),
     * 8 scrolling, 16 wrap vertically, 32 blob shadow (darkens what is beneath).
     */
    static final String POLY_FS = HEAD + DITHER
            + "uniform sampler2DArray uAtlas;\n"
            + "uniform sampler2D uTex;\n"
            + "uniform int uSingle;\n"
            + "uniform vec3 uTint;\n"
            + "uniform vec3 uFog;\n"
            + "uniform vec2 uFogRange;\n"
            + "uniform float uDetail;\n"
            + "uniform int uTexMap;\n"
            + "uniform int uCorners;\n"
            + "uniform vec2 uRes;\n"
            + "flat in vec4 vP0, vP1, vP2, vP3, vP4;\n"
            + "flat in int vON;\n"                    // 0 perspective, 1 GBA scanline, 2 PS1 affine
            + "in vec2 vUv;\n"
            + "in vec2 vUvW;\n"
            + "in float vW;\n"
            + "in vec2 vLit;\n"
            + "in float vDepth;\n"
            + "in float vDist;\n"
            + "flat in uvec4 vRect;\n"
            + "flat in uint vFlags;\n"
            + "out vec4 outColor;\n"
            // GBA: this row's span of the triangle runs between the two edges it crosses; the texture
            // is exact there (uv/w and 1/w are linear on screen) and stepped linearly in between
            // GBA: no divides. The polygon is walked whole: u, v step linearly down its edges (on screen)
            // and linearly across each row between them, so it warps as one piece, floors included
            + "vec2 mapUv() {\n"
            + "  if (uTexMap == 0) return vUv;\n"
            + "  if (uTexMap == 2 || uCorners == 0 || vON < 3) return vUvW / vW;\n"
            + "  vec4 S[5] = vec4[5](vP0, vP1, vP2, vP3, vP4);\n"
            + "  float y = gl_FragCoord.y, xl = 1e9, xr = -1e9;\n"
            + "  vec2 tl = vec2(0.0), tr = vec2(0.0);\n"
            + "  for (int i = 0; i < 5; i++) {\n"
            + "    if (i >= vON) break;\n"
            + "    vec4 A = S[i], B = S[(i + 1) % vON];\n"
            + "    if ((A.y <= y && B.y > y) || (B.y <= y && A.y > y)) {\n"
            + "      float f = (y - A.y) / (B.y - A.y);\n"
            + "      vec4 C = mix(A, B, f);\n"
            + "      if (C.x < xl) { xl = C.x; tl = C.zw; }\n"
            + "      if (C.x > xr) { xr = C.x; tr = C.zw; }\n"
            + "    }\n"
            + "  }\n"
            + "  if (xr - xl < 0.01) return vUvW / vW;\n"
            + "  return mix(tl, tr, (gl_FragCoord.x - xl) / (xr - xl));\n"
            + "}\n"
            + "void main() {\n"
            + "  if ((vFlags & 4u) != 0u && vDist > uDetail) discard;\n"
            + "  ivec2 size = uSingle != 0 ? textureSize(uTex, 0) : ivec2(int(vRect.z), int(vRect.w));\n"
            + "  vec2 tc = floor(mapUv() * vec2(size));\n"
            + "  float tu = mod(tc.x, float(size.x));\n"
            + "  float tv = (vFlags & 16u) != 0u ? mod(tc.y, float(size.y)) : clamp(tc.y, 0.0, float(size.y - 1));\n"
            + "  vec4 c = uSingle != 0 ? texelFetch(uTex, ivec2(int(tu), int(tv)), 0).bgra\n"
            + "      : texelFetch(uAtlas, ivec3(int(vRect.x & 2047u) + int(tu), int(vRect.y) + int(tv), int(vRect.x >> 11)), 0).bgra;\n"
            + "  int b = bayer();\n"
            + "  if ((vFlags & 32u) != 0u) {\n"
            + "    if (c.a * 255.0 > float(b * 16 + 8)) { outColor = vec4(0.5625, 0.5625, 0.625, 1.0); return; }\n"
            + "    discard;\n"
            + "  }\n"
            + "  if ((vFlags & 2u) != 0u && floor(vLit.y * 16.0) <= float(b)) discard;\n"
            + "  if ((vFlags & 1u) != 0u && c.a < 0.5) discard;\n"
            + "  vec3 c8 = min(floor(c.rgb * 255.0 + 0.5) * vLit.x * uTint, vec3(255.0));\n"
            + "  float f = clamp((vDepth - uFogRange.x) * uFogRange.y, 0.0, 1.0);\n"
            + "  c8 += (uFog - c8) * f;\n"
            + "  outColor = vec4(quant(c8, float(b >> 1)), 1.0);\n"
            + "}\n";

    // ------------------------------------------------------------------ the ground map and the sea

    static final String GROUND_VS = HEAD + CAMERA
            + "layout(location = 0) in vec3 aPos;\n"
            + "layout(location = 1) in vec4 aColor;\n"
            + "out vec3 vWorld;\n"
            + "out float vDepth;\n"
            + "out vec3 vColor;\n"
            + "uniform float uGroundS;\n"
            + "void main() {\n"
            + "  vWorld = aPos;\n"
            + "  vColor = aColor.rgb;\n"
            + "  float vz;\n"
            // the ground's depth is written ~6% farther: things standing on it always show
            + "  gl_Position = project(aPos, uGroundS, vz);\n"
            + "  vDepth = vz;\n"
            + "}\n";

    static final String GROUND_FS = HEAD + DITHER
            + "uniform sampler2D uMap;\n"           // the sand tile under the shallows (repeating)
            + "uniform float uSandM;\n"            // metres per sand tile
            + "uniform sampler2D uWater;\n"         // sea-floor / sand height, 65 x 65 (linear)
            + "uniform sampler2D uWaves;\n"         // this frame's wave field: brightness / 2, highlight
            + "uniform sampler2D uAlong;\n"         // along-shore phase, 64 x 64
            + "uniform sampler2D uFoam;\n"          // foam lace, 128 x 128
            + "uniform int uFlat;\n"                // 1: no tile loaded (flat colours from the vertices)
            + "uniform int uHasWater;\n"
            + "uniform vec2 uTile;\n"               // the tile's corner (x0, z0)
            + "uniform vec2 uMip;\n"                // base distance of mip 1 / 2, mip count
            + "uniform vec4 uTintDry;\n"            // tint r g b (/256), dryness
            + "uniform vec3 uFog;\n"
            + "uniform vec3 uFogRange;\n"           // start, 1 / (end - start), far
            + "uniform vec4 uSea;\n"                // sea sky colour, chop
            + "uniform vec2 uTimeEye;\n"            // time, eye height
            + "in vec3 vWorld;\n"
            + "in float vDepth;\n"
            + "in vec3 vColor;\n"
            + "out vec4 outColor;\n"
            + "float wrapf(float v, float m) { return mod(v, m); }\n"
            + "float along(float x, float z) {\n"
            + "  float gx = x * (1.0 / 37.0), gz = z * (1.0 / 37.0);\n"
            + "  float ix = floor(gx), iz = floor(gz);\n"
            + "  float tx = gx - ix, tz = gz - iz;\n"
            + "  tx = tx * tx * (3.0 - 2.0 * tx); tz = tz * tz * (3.0 - 2.0 * tz);\n"
            + "  float a = texelFetch(uAlong, ivec2(int(wrapf(ix, 64.0)), int(wrapf(iz, 64.0))), 0).r;\n"
            + "  float b = texelFetch(uAlong, ivec2(int(wrapf(ix + 1.0, 64.0)), int(wrapf(iz, 64.0))), 0).r;\n"
            + "  float c = texelFetch(uAlong, ivec2(int(wrapf(ix, 64.0)), int(wrapf(iz + 1.0, 64.0))), 0).r;\n"
            + "  float d = texelFetch(uAlong, ivec2(int(wrapf(ix + 1.0, 64.0)), int(wrapf(iz + 1.0, 64.0))), 0).r;\n"
            + "  float top = a + (b - a) * tx, bot = c + (d - c) * tx;\n"
            + "  return top + (bot - top) * tz;\n"
            + "}\n"
            + "float swash(float phase) {\n"
            + "  float p = phase - floor(phase);\n"
            + "  float r = p < 0.15 ? p / 0.15 : max(0.0, 1.0 - (p - 0.15) / 0.55);\n"
            + "  return -0.03 + 0.24 * r * r * (3.0 - 2.0 * r);\n"
            + "}\n"
            + "vec3 sea(vec3 c, float x, float z, float dist, float mip, int b, out bool quantised) {\n"
            + "  quantised = false;\n"
            + "  float lx = clamp(x - uTile.x, 0.0, 64.0), lz = clamp(z - uTile.y, 0.0, 64.0);\n"
            + "  float e = texture(uWater, vec2((lx + 0.5) / 65.0, (lz + 0.5) / 65.0)).r * 255.0;\n"
            + "  float h = 0.8 - e * 0.05;\n"
            + "  if (h > 0.5) return c;\n"
            + "  float t = uTimeEye.x;\n"
            + "  float phase0 = t * 0.11 + along(x, z) * 2.3;\n"
            + "  float level = swash(phase0);\n"
            + "  ivec2 wi = ivec2(int(wrapf(floor(x * 8.0), 128.0)), int(wrapf(floor(z * 8.0), 128.0)));\n"
            + "  vec2 wave = texelFetch(uWaves, wi, 0).rg;\n"
            + "  float mod_ = floor(wave.r * 255.0 + 0.5) * 2.0, spec = floor(wave.g * 255.0 + 0.5);\n"
            + "  if (h >= level) {\n"
            + "    if (h < 0.24) {\n"
            + "      float wet = 1.0 - h / 0.24;\n"
            + "      float k = 256.0 - floor(wet * 95.0);\n"
            + "      vec3 s = floor(c * k / 256.0);\n"
            + "      if (h < level + 0.05) s += 28.0 + floor(spec / 4.0);\n"
            + "      return min(s, vec3(255.0));\n"
            + "    }\n"
            + "    return c;\n"
            + "  }\n"
            + "  float depth = level - h;\n"
            + "  vec3 w;\n"
            + "  if (depth < 1.5) w = mix(vec3(64.0, 196.0, 190.0), vec3(30.0, 150.0, 178.0), depth / 1.5);\n"
            + "  else if (depth < 6.0) w = mix(vec3(30.0, 150.0, 178.0), vec3(20.0, 96.0, 150.0), (depth - 1.5) / 4.5);\n"
            + "  else w = mix(vec3(20.0, 96.0, 150.0), vec3(14.0, 64.0, 112.0), min(1.0, (depth - 6.0) / 6.0));\n"
            + "  w = floor(w);\n"
            + "  if (mip >= 1.0) { mod_ = 256.0 + floor((mod_ - 256.0) / exp2(mip)); spec = floor(spec / exp2(mip)); }\n"
            + "  w = floor(floor(w * uTintDry.rgb) * mod_ / 256.0);\n"
            + "  float graze = (uTimeEye.y + 2.0) / max(1.0, dist);\n"
            + "  float refl = max(0.0, min(0.62, 0.7 - graze * 2.2));\n"
            + "  w += floor((uSea.rgb - w) * refl);\n"
            + "  float clear = 1.0 - min(1.0, depth / 0.7);\n"
            + "  if (clear > 0.0) w += floor((c - w) * clear * 0.75);\n"
            + "  float foam = 0.0;\n"
            + "  if (depth < 4.0) {\n"
            + "    float ph = phase0 + depth * 0.32;\n"
            + "    float f = ph - floor(ph);\n"
            + "    float crest = f < 0.1 ? 1.0 - f / 0.1 : (f > 0.96 ? (f - 0.96) / 0.04 : 0.0);\n"
            + "    float wash = f < 0.35 ? (0.35 - f) / 0.35 * 0.35 : 0.0;\n"
            + "    foam = max(crest, wash) * (1.0 - depth / 4.0) * (0.55 + 0.45 * uSea.w);\n"
            + "  }\n"
            + "  if (depth < 0.08) foam = max(foam, 0.85 * (1.0 - depth / 0.08));\n"
            + "  if (foam > 0.0) {\n"
            + "    ivec2 fi = ivec2(int(wrapf(floor(x * 6.0), 128.0)), int(wrapf(floor(z * 6.0 + t * 1.3), 128.0)));\n"
            + "    float lace = texelFetch(uFoam, fi, 0).r;\n"
            + "    float fa = clamp((foam - (1.0 - lace) * 0.75) * 2.2, 0.0, 1.0);\n"
            + "    if (mip >= 3.0) fa *= 0.6;\n"
            + "    vec3 fc = floor(vec3(236.0, 242.0, 240.0) * uTintDry.rgb);\n"
            + "    w += floor((fc - w) * fa);\n"
            + "    spec = floor(spec / 2.0);\n"
            + "  }\n"
            + "  w += vec3(spec, spec, floor(spec * 15.0 / 16.0));\n"
            + "  quantised = true;\n"
            + "  return quant(clamp(w, 0.0, 255.0), float(b >> 1)) * 255.0;\n"
            + "}\n"
            + "void main() {\n"
            + "  float dr = vDepth;\n"
            + "  if (dr > uFogRange.z) discard;\n"
            + "  int b = bayer();\n"
            + "  vec3 c;\n"
            + "  float mip = 0.0;\n"
            + "  if (uFlat != 0) {\n"
            + "    c = floor(vColor * 255.0 / 8.0);\n"
            + "    c = c * 8.0 + floor(c / 4.0);\n"
            + "  } else {\n"
            // dry land belongs to the terrain's polygons
            + "    float lx = clamp(vWorld.x - uTile.x, 0.0, 64.0), lz = clamp(vWorld.z - uTile.y, 0.0, 64.0);\n"
            + "    if (uHasWater == 0 || 0.8 - texture(uWater, vec2((lx + 0.5) / 65.0, (lz + 0.5) / 65.0)).r * 255.0 * 0.05 > 0.5) discard;\n"
            + "    if (dr > uMip.x * 2.0) mip = clamp(ceil(log2(dr / uMip.x)) - 1.0, 0.0, uMip.y - 1.0);\n"
            + "    ivec2 ts = textureSize(uMap, 0);\n"
            + "    float st = exp2(min(mip, 3.0));\n"
            + "    ivec2 ti = ivec2(mod(floor(vWorld.xz / uSandM * vec2(ts) / st) * st, vec2(ts)));\n"
            + "    vec3 m = texelFetch(uMap, ti, 0).bgr;\n"
            // the 15-bit colour as 8-bit, as the CPU renderer expands it
            + "    vec3 q5 = floor(vec3(m.r * 31.0, m.g * 63.0, m.b * 31.0) + 0.5);\n"
            + "    q5.g = floor(q5.g / 2.0);\n"
            + "    vec3 C = q5 * 8.0 + floor(q5 / 4.0);\n"
            // season: dry grass turns straw coloured; then the scene light, kept 15-bit
            + "    float dk = uTintDry.w * 0.6;\n"
            + "    if (dk > 0.0 && C.g > C.r && C.g > C.b) {\n"
            + "      float k = min(1.0, (C.g - max(C.r, C.b)) / 40.0) * dk;\n"
            + "      C.r += (C.g * 0.95 - C.r) * k;\n"
            + "      C.g -= C.g * 0.1 * k;\n"
            + "      C.b -= C.b * 0.35 * k;\n"
            + "    }\n"
            + "    vec3 L = min(floor(C * uTintDry.rgb), vec3(255.0));\n"
            + "    L = floor(L / 8.0);\n"
            + "    c = L * 8.0 + floor(L / 4.0);\n"
            + "    if (uHasWater != 0) { bool q; c = sea(c, vWorld.x, vWorld.z, dr, mip, b, q); }\n"
            + "  }\n"
            + "  if (dr <= uFogRange.x) { outColor = vec4(c / 255.0, 1.0); return; }\n"
            + "  float fv = min(256.0, floor((dr - uFogRange.x) * uFogRange.y * 256.0));\n"
            + "  c += floor((uFog - c) * fv / 256.0);\n"
            + "  outColor = vec4(quant(c, float(b >> 1)), 1.0);\n"
            + "}\n";

    // ------------------------------------------------------------------ sky

    static final String FULLSCREEN_VS = HEAD
            + "out vec2 vUv;\n"
            + "uniform vec4 uRect;\n"               // x0 y0 x1 y1 in NDC
            + "uniform int uFlipV;\n"
            + "void main() {\n"
            + "  vec2 q = vec2(float(gl_VertexID & 1), float(gl_VertexID >> 1));\n"
            + "  vUv = vec2(q.x, uFlipV != 0 ? 1.0 - q.y : q.y);\n"
            + "  gl_Position = vec4(mix(uRect.x, uRect.z, q.x), mix(uRect.y, uRect.w, q.y), 0.0, 1.0);\n"
            + "}\n";

    static final String SKY_FS = HEAD + DITHER
            + "uniform sampler2D uClouds;\n"
            + "uniform vec4 uView;\n"               // cx, horizon row, focal, env (1 = full sky)
            + "uniform vec4 uBasis;\n"              // right.x, right.z, fwd.x, fwd.z
            + "uniform vec3 uEye;\n"
            + "uniform vec3 uFog;\n"
            + "uniform vec3 uZen;\n"
            + "uniform vec4 uSun;\n"
            + "uniform vec4 uMoon;\n"
            + "uniform vec4 uSkyFx;\n"              // stars, rainbow, cover, haze
            + "uniform vec3 uCloudLit;\n"
            + "uniform vec3 uCloudShade;\n"
            + "uniform vec2 uCloudOff;\n"
            + "out vec4 outColor;\n"
            + "uint hashu(int x, int y, int seed) {\n"
            + "  uint h = uint(x) * 374761393u + uint(y) * 668265263u + uint(seed) * 1442695041u;\n"
            + "  h = (h ^ (h >> 13)) * 1274126177u;\n"
            + "  return h ^ (h >> 16);\n"
            + "}\n"
            + "void main() {\n"
            + "  int yi = int(uRows) - 1 - int(gl_FragCoord.y);\n"
            + "  float yc = float(yi) + 0.5;\n"
            + "  float focal = uView.z, hy = uView.y;\n"
            + "  float elev = atan((hy - yc) / focal);\n"
            + "  vec3 col = elev >= 0.0 ? floor(uFog + (uZen - uFog) * sqrt(min(1.0, elev / 0.9))) : uFog;\n"
            + "  if (uView.w > 0.5) {\n"
            + "    float xs = (gl_FragCoord.x - uView.x) / focal;\n"
            + "    float dx = uBasis.z + uBasis.x * xs, dz = uBasis.w + uBasis.y * xs;\n"
            + "    float hl2 = dx * dx + dz * dz, az = atan(dx, dz);\n"
            + "    float s = (hy - yc) / focal;\n"
            + "    if (s > 0.0) {\n"
            + "      float inv = 1.0 / sqrt(hl2 + s * s);\n"
            + "      vec3 v = vec3(dx * inv, s * inv, dz * inv);\n"
            + "      if (uSkyFx.x > 0.0 && v.y > 0.06) {\n"
            + "        uint hs = hashu(int(az * 300.0), int(v.y * 300.0), 77);\n"
            + "        if ((hs & 1023u) < 4u) {\n"
            + "          float k = uSkyFx.x * float(120u + ((hs >> 24) & 127u));\n"
            + "          col += vec3(k, k, k * 0.9);\n"
            + "        }\n"
            + "      }\n"
            + "      if (uMoon.w > 0.0) {\n"
            + "        float d = dot(v, uMoon.xyz);\n"
            + "        if (d > 0.99935) col += (vec3(230.0, 232.0, 220.0) - col) * uMoon.w;\n"
            + "        else if (d > 0.995) col += (d - 0.995) / 0.00435 * 40.0 * uMoon.w;\n"
            + "      }\n"
            + "      if (uSun.w > 0.0) {\n"
            + "        float d = dot(v, uSun.xyz);\n"
            + "        if (d > 0.9991) col += (vec3(255.0, 250.0, 228.0) - col) * uSun.w;\n"
            + "        else if (d > 0.96) { float k = (d - 0.96) / 0.0391; k = k * k * 130.0 * uSun.w; col += vec3(k, k * 0.85, k * 0.55); }\n"
            + "      }\n"
            + "      if (uSkyFx.y > 0.0) {\n"
            + "        float d = -dot(v, uSun.xyz);\n"
            + "        if (d > 0.733 && d < 0.778) {\n"
            + "          float f = (d - 0.733) / 0.045;\n"
            + "          float k = uSkyFx.y * 0.32 * (1.0 - abs(f - 0.5) * 1.2);\n"
            + "          vec3 rc = f < 0.2 ? vec3(255.0, 40.0, 30.0) : f < 0.35 ? vec3(255.0, 150.0, 20.0) : f < 0.5 ? vec3(250.0, 240.0, 40.0)\n"
            + "              : f < 0.66 ? vec3(40.0, 220.0, 60.0) : f < 0.82 ? vec3(40.0, 90.0, 255.0) : vec3(150.0, 50.0, 220.0);\n"
            + "          col += (rc - col) * k;\n"
            + "        }\n"
            + "      }\n"
            + "      float cover = uSkyFx.z;\n"
            + "      if (cover > 0.02 && s > 0.012) {\n"
            + "        float t = (900.0 - uEye.y) / s;\n"
            + "        float wx = uEye.x + dx * t + uCloudOff.x, wz = uEye.z + dz * t + uCloudOff.y;\n"
            + "        float n = texelFetch(uClouds, ivec2(int(mod(float(int(wx / 24.0)), 256.0)), int(mod(float(int(wz / 24.0)), 256.0))), 0).r;\n"
            + "        float th = 1.0 - cover;\n"
            + "        float a = (n - th * 0.85 - 0.08) / 0.2;\n"
            + "        if (a > 0.0) {\n"
            + "          a = min(a, 1.0) * min(1.0, 1.5 - t / 14000.0);\n"
            + "          if (cover > 0.9) a = max(a, (cover - 0.9) * 10.0);\n"
            + "          if (a > 0.0) {\n"
            + "            float sh = min(1.0, (n - th * 0.85) * 2.2);\n"
            + "            col += (uCloudLit + (uCloudShade - uCloudLit) * sh - col) * a;\n"
            + "          }\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "    if (uSkyFx.w > 0.0) {\n"
            + "      float k = uSkyFx.w * (s > 0.0 ? max(0.35, 1.0 - s * 0.9) : 1.0);\n"
            + "      col += (uFog - col) * k;\n"
            + "    }\n"
            + "  }\n"
            + "  outColor = vec4(quant(min(floor(col), vec3(255.0)), float(bayer() >> 1)), 1.0);\n"
            + "}\n";

    // ------------------------------------------------------------------ screen-space overlays

    /** Rain streaks and the lightning bolt: coloured quads in frame pixels (top-down). */
    static final String FLAT_VS = HEAD
            + "layout(location = 0) in vec2 aPx;\n"
            + "layout(location = 1) in vec4 aColor;\n"
            + "uniform vec2 uRes;\n"
            + "out vec4 vColor;\n"
            + "void main() {\n"
            + "  vColor = aColor;\n"
            + "  gl_Position = vec4(aPx.x / uRes.x * 2.0 - 1.0, 1.0 - aPx.y / uRes.y * 2.0, 0.0, 1.0);\n"
            + "}\n";

    static final String FLAT_FS = HEAD
            + "in vec4 vColor;\n"
            + "out vec4 outColor;\n"
            + "void main() { outColor = vColor; }\n";

    /** Shows the finished frame (or the HUD / a loading screen) on the screen, unfiltered. */
    static final String BLIT_FS = HEAD
            + "uniform sampler2D uTex;\n"
            + "uniform int uSwizzle;\n"              // 1: ARGB ints uploaded as bytes (b g r a)
            + "in vec2 vUv;\n"
            + "out vec4 outColor;\n"
            + "void main() {\n"
            + "  vec4 c = texture(uTex, vUv);\n"
            + "  outColor = uSwizzle != 0 ? c.bgra : vec4(c.rgb, 1.0);\n"
            + "}\n";
}
