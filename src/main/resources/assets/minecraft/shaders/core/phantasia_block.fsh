// phantasia_block.fsh
// Phantasia custom block shader — fragment stage.

#version 150

// ── Samplers ─────────────────────────────────────────────────────────────────
uniform sampler2D Sampler0;  // Block atlas
uniform sampler2D Sampler2;  // Lightmap

// ── Uniforms ──────────────────────────────────────────────────────────────────
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4  FogColor;

// ── SSBO path ─────────────────────────────────────────────────────────────────
#ifdef PHANTASIA_LARGE_MACHINE
// Binding 7 matches PhantasiaVisibilityBuffer.SSBO_BINDING_POINT.
layout(std430, binding = 7) buffer VisibilitySSBO {
    uint visibilityBits[];
};
#endif

// ── Inputs from VS ────────────────────────────────────────────────────────────
in vec2 texCoord0;
in vec4 vertexColor;
in vec2 texCoord2;
flat in int blockVisible;
flat in int blockId; // Linked flat tracking ID input matching VS layout assignment

// ── Output ────────────────────────────────────────────────────────────────────
out vec4 fragColor;

void main() {
    // ── Visibility discard ────────────────────────────────────────────────────
#ifdef PHANTASIA_LARGE_MACHINE
    // SSBO path: resolve the bitmask here in the FS.
    int wordIdx = blockId >> 5;
    int bitIdx  = blockId & 31;
    if (((visibilityBits[wordIdx] >> uint(bitIdx)) & 1u) == 0u) discard;
#else
    // UBO path: blockVisible was resolved in the VS.
    if (blockVisible == 0) discard;
#endif

    // ── Standard block shading ────────────────────────────────────────────────
    vec4 texColor = texture(Sampler0, texCoord0);
    if (texColor.a < 0.1) discard;

    vec4 lightSample = texture(Sampler2, clamp(texCoord2 / 256.0, 0.0, 1.0));
    vec4 color = texColor * vertexColor * lightSample;

    fragColor = color;
}