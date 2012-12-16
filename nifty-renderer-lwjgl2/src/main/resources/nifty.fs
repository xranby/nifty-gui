#version 150 core

uniform sampler2D uTex;

layout(origin_upper_left) in vec4 gl_FragCoord;

in vec4 vColor;
in vec2 vTexture;
//in vec4 vClipping;

out vec4 fColor;

void main() {
  vec4 frag = gl_FragCoord;

  // x = clipX0
  // y = clipY0
  // z = clipX1
  // w = clipY1
  //if (frag.x < vClipping.x || frag.x > vClipping.z || frag.y < vClipping.y || frag.y > vClipping.w) {
  //  discard;
  //}
  fColor = vColor * texelFetch(uTex, ivec2(vTexture.x * 2048, vTexture.y * 2048), 0);
}
