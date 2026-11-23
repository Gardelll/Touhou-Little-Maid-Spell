#version 150

uniform sampler2D Sampler0; // 不透明雾气底图
uniform sampler2D Sampler1; // 光束
uniform sampler2D Sampler2; // 雾中光点
uniform sampler2D Sampler3; // 灰度揭幕顺序图
uniform vec4 LayerOpacity;
uniform float Reveal;

in vec2 texCoord0;
out vec4 fragColor;

// 先把雾气各层合成一组，再统一套用揭幕遮罩
vec4 over(vec4 under, vec4 layer, float opacity) {
    float alpha = layer.a * opacity;
    return vec4(layer.rgb * alpha, alpha) + under * (1.0 - alpha);
}

void main() {
    vec4 fog = over(vec4(0.0), vec4(texture(Sampler0, texCoord0).rgb, 1.0), LayerOpacity.x);
    fog = over(fog, texture(Sampler1, texCoord0), LayerOpacity.y);
    fog = over(fog, texture(Sampler2, texCoord0), LayerOpacity.z);

    float radius = mix(0.018, 1.105, Reveal);
    float order = texture(Sampler3, texCoord0).r;
    float band = floor(clamp((radius - order) / 0.105, 0.0, 1.0) * 4.0 + 0.5) / 4.0;
    float hole = band * smoothstep(0.0, 0.20, Reveal);
    fragColor = vec4(fog.rgb / max(fog.a, 0.001), fog.a * (1.0 - hole));
}
