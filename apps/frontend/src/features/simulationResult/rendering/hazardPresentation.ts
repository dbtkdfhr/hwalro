interface HazardGradientStop {
  offset: number;
  css: string;
  rgba: readonly [red: number, green: number, blue: number, alpha: number];
}

export const HAZARD_GRADIENT_STOPS: readonly HazardGradientStop[] = [
  {
    offset: 0,
    css: 'rgba(177, 32, 32, 0.58)',
    rgba: [177 / 255, 32 / 255, 32 / 255, 0.58],
  },
  {
    offset: 0.5,
    css: 'rgba(225, 75, 75, 0.28)',
    rgba: [225 / 255, 75 / 255, 75 / 255, 0.28],
  },
  {
    offset: 1,
    css: 'rgba(239, 119, 119, 0.08)',
    rgba: [239 / 255, 119 / 255, 119 / 255, 0.08],
  },
];

function glslColor([red, green, blue, alpha]: HazardGradientStop['rgba']) {
  return `vec4(${red.toFixed(6)}, ${green.toFixed(6)}, ${blue.toFixed(6)}, ${alpha.toFixed(6)})`;
}

const [inner, middle, outer] = HAZARD_GRADIENT_STOPS;

export const HAZARD_GRADIENT_VERTEX_SHADER = `
varying vec2 vHazardUv;

void main() {
  vHazardUv = uv;
  gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
}
`;

export const HAZARD_GRADIENT_FRAGMENT_SHADER = `
varying vec2 vHazardUv;

void main() {
  float normalizedRadius = distance(vHazardUv, vec2(0.5)) * 2.0;
  if (normalizedRadius > 1.0) discard;

  vec4 innerColor = ${glslColor(inner.rgba)};
  vec4 middleColor = ${glslColor(middle.rgba)};
  vec4 outerColor = ${glslColor(outer.rgba)};
  vec4 gradientColor = normalizedRadius <= 0.5
    ? mix(innerColor, middleColor, normalizedRadius * 2.0)
    : mix(middleColor, outerColor, (normalizedRadius - 0.5) * 2.0);

  gl_FragColor = gradientColor;
  #include <tonemapping_fragment>
  #include <colorspace_fragment>
}
`;
