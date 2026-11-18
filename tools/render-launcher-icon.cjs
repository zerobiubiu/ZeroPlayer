// 仅将仓库原稿中已用于桌面图标的三条几何路径导出为 Android XML。不加载 SVG、外链或脚本。
// node tools/render-launcher-icon.cjs [输出根目录]；构建只打包已提交的 VectorDrawable。
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const output = path.join(path.resolve(process.argv[2] || root), 'app/src/main/res/drawable');
const art = fs.readFileSync(path.join(root, 'docs/assets/zeroplayer-icon.svg'), 'utf8');

// 这是指定美术原稿的导出器，不是 SVG 转换器。只允许 pathData 命令与数值进入 XML。
function pathData(pattern) {
  const d = art.match(pattern)?.[1];
  if (!d || !/^[MmLlHhVvCcSsQqTtAaZzEe\d.,+\s-]+$/.test(d)) {
    throw new Error(`原稿缺少预期的几何路径：${pattern}`);
  }
  return d.trim().replace(/\s*\n\s*/g, '\n            ');
}
const silhouette = pathData(/<path\b(?=[^>]*\bid="zp-silhouette")[^>]*?\sd="([^"]+)"/);
const upper = pathData(/<path\b(?=[^>]*\bid="upper-ribbon")[^>]*?\sd="([^"]+)"/);
const lower = pathData(/<clipPath\b(?=[^>]*\bid="zp-bottom-clip")[^>]*>\s*<path\b[^>]*?\sd="([^"]+)"/);

// 0028 已安装图标的折面和色标；不是整张原稿的灰阶光照。
const faces = [
  ['M0 0H1254V1254H0Z', [425, 385, 450, 870], [[0, '#9096AC'], [0.5, '#D1D5E2'], [1, '#A3A9BF']]],
  [upper, [430, 280, 865, 600], [[0, '#FFFFFF'], [0.56, '#F6F7FC'], [1, '#D8DCE9']]],
  [lower, [853, 585, 519, 1045], [[0, '#9098B2'], [0.29, '#E5E8F1'], [0.5, '#F4F5FA'], [1, '#B9BFD2']]],
];
const scale = 58 / 869;
function vector(monochrome) {
  return `<?xml version="1.0" encoding="utf-8"?>
<!-- 由 tools/render-launcher-icon.cjs 从原稿路径导出。 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"${monochrome ? '\n    android:tint="#FFFFFFFF"' : ''}
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <!-- 原稿光学锚点 (607, 624) 落在 (54, 54)，869 个原稿单位高映射为 58dp。 -->
    <group android:scaleX="${scale}" android:scaleY="${scale}"
        android:translateX="${54 - 607 * scale}" android:translateY="${54 - 624 * scale}">
${ribbon}
    </group>
</vector>
`;
}
function face([d, [x1, y1, x2, y2], stops]) {
  return `        <path android:pathData="${d}">
            <aapt:attr name="android:fillColor">
                <gradient android:type="linear" android:startX="${x1}" android:startY="${y1}"
                    android:endX="${x2}" android:endY="${y2}">
${stops.map(([offset, color]) => `                    <item android:offset="${offset}" android:color="${color}" />`).join('\n')}
                </gradient>
            </aapt:attr>
        </path>`;
}

// 两层使用同一渐变矢量；单色只在整图根部用 SRC_IN 着白，保留同一份抗锯齿 alpha。
const ribbon = `        <clip-path android:fillType="evenOdd" android:pathData="${silhouette}" />\n${faces.map(face).join('\n')}`;
fs.mkdirSync(output, { recursive: true });
fs.writeFileSync(path.join(output, 'ic_launcher_foreground.xml'), vector(false));
fs.writeFileSync(path.join(output, 'ic_launcher_monochrome.xml'), vector(true));
