// phantasia_block.vsh
// Phantasia custom block shader.

#version 150

// ── Standard MC block vertex attributes ─────────────────────────────────────
in vec3  Position;
in vec4  Color;
in vec2  UV0;
in ivec2 UV2;
in vec4  Normal;

// ── Block ID attribute (bound at location 5 by PhantasiaWorldRenderer) ──────
in int BlockId;

// ── Visibility buffer ────────────────────────────────────────────────────────
#ifndef PHANTASIA_LARGE_MACHINE
layout(std140) uniform VisibilityData {
    uint visibilityBits[MAX_BLOCKS_DIV32];
};
#endif

// ── Standard MC uniforms ─────────────────────────────────────────────────────
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec4 ColorModulator;
uniform mat4 TextureMat;
uniform float GameTime;

// ── Outputs ──────────────────────────────────────────────────────────────────
out vec2 texCoord0;
out vec4 vertexColor;
out vec2 texCoord2;
flat out int blockVisible;
flat out int blockId; // Passed out to fragment shader stage

void main() {
    // Forward the block position identity out to the FS interface block
    blockId = BlockId;

    // ── Visibility bitmask lookup ────────────────────────────────────────────
    int wordIdx = BlockId >> 5;
    int bitIdx  = BlockId & 31;

    #ifndef PHANTASIA_LARGE_MACHINE
    blockVisible = int((visibilityBits[wordIdx] >> uint(bitIdx)) & 1u);
    #else
    // SSBO path: declared in the fragment shader via a buffer block;
    // We pass blockVisible=1 here and let the FS do the discard to keep the VS simple.
    blockVisible = 1;
    #endif

    gl_Position  = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0    = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
    texCoord2    = UV2;
    vertexColor  = Color * ColorModulator;
}