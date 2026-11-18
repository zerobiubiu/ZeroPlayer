// 离线维护工具：node tools/generate-home-file-icons.cjs <解包后的官方扩展目录> [--check]
// Gradle 和 APK 只使用已提交的产物。校验通过前不渲染、不写入，不执行扩展的任何代码。
'use strict';
const assert = require('node:assert/strict');
const { createHash } = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const sharp = require('sharp');

const VERSION = '5.40.0';
const MANIFEST_SHA = '4b43c31b9e54d99ac14afb36027cf1699167dc56fc33f524dcb0ef112fcd65c2';
const LICENSE_SHA = 'cdab3014d4f69b49dde2b85e81792208c72de613aa6aed7f7a9b5c6609b89670';
const SVG_SET_SHA = '4720d27c2654e04ee35daea5cb1d96b86c3feb04992eb4c113ba60c3d0e75d33';
const RENDERER = { sharp: '0.35.5', rsvg: '2.63.2', vips: '8.18.7', webp: '1.6.0' };
const root = path.resolve(__dirname, '..');
const home = path.join(root, 'feature/home/src/main');
const drawableDir = path.join(home, 'res/drawable-xxxhdpi');
const kotlinFile = path.join(home, 'java/com/privateplayer/feature/home/fileicons/MaterialIconAssociations.kt');
const noticeFile = path.join(root, 'app/src/main/assets/third-party-licenses/NOTICE.txt');
const prefix = 'home_mit_';
const pixels = 96; // 24dp × xxxhdpi
const sha = bytes => createHash('sha256').update(bytes).digest();
const resource = id => prefix + id.replaceAll('-', '_');
const quoted = value => JSON.stringify(value).replaceAll('$', '\\$');

function sourceData(dir) {
  const read = member => fs.readFileSync(path.join(dir, member));
  const pkg = JSON.parse(read('package.json'));
  assert.deepEqual([pkg.name, pkg.publisher, pkg.version, pkg.license], ['material-icon-theme', 'PKief', VERSION, 'MIT']);
  assert.equal(pkg.repository.url, 'git+https://github.com/material-extensions/vscode-material-icon-theme.git');
  const raw = read('dist/material-icons.json');
  assert.equal(sha(raw).toString('hex'), MANIFEST_SHA, '需要未定制的官方发行版 manifest');
  const license = read('LICENSE.txt');
  assert.equal(sha(license).toString('hex'), LICENSE_SHA);
  assert.ok(fs.readFileSync(noticeFile).includes(license), 'APK 关于页必须包含完整、原样的上游许可');
  const manifest = JSON.parse(raw);
  const ids = new Set([manifest.file]);
  for (const group of [manifest, manifest.light]) {
    for (const table of [group.fileNames, group.fileExtensions]) {
      for (const id of Object.values(table)) ids.add(id);
    }
  }
  assert.equal(new Set([...ids].map(resource)).size, ids.size, 'Android 资源名冲突');
  const sourceFiles = new Map();
  const svgById = new Map();
  for (const id of [...ids].sort()) {
    assert.match(id, /^[a-z0-9_-]+$/);
    const ref = manifest.iconDefinitions[id].iconPath;
    const match = /^\.\/\.\.\/icons\/([\w.-]+\.svg)$/.exec(ref);
    assert.ok(match, `不接受包外/非 SVG 图形：${ref}`);
    const file = match[1];
    if (!sourceFiles.has(file)) sourceFiles.set(file, read(`icons/${file}`));
    svgById.set(id, sourceFiles.get(file));
  }
  // 按原始文件名排序；NUL 分隔文件名，再接该文件的 32 字节 SHA-256。
  const digest = createHash('sha256');
  for (const file of [...sourceFiles.keys()].sort()) digest.update(file + '\0').update(sha(sourceFiles.get(file)));
  assert.equal(digest.digest('hex'), SVG_SET_SHA, '已安装的图形与官方发行包不一致');
  return { manifest, svgById };
}

function expression(id, lightId = id) {
  const ref = `R.drawable.${resource(id)}`;
  return id === lightId ? ref : `if (light) R.drawable.${resource(lightId)} else ${ref}`;
}

function associations(manifest) {
  const names = new Map();
  const extensions = new Map();
  for (const [key, id] of Object.entries(manifest.fileNames)) {
    const parts = key.toLowerCase().split('/');
    assert.ok(parts.length <= 2, `不支持多层父目录限定：${key}`);
    const leaf = parts.pop();
    const parent = parts[0] || '';
    if (!names.has(leaf)) names.set(leaf, new Map());
    assert.ok(!names.get(leaf).has(parent), `专名冲突：${key}`);
    names.get(leaf).set(parent, expression(id, manifest.light.fileNames[key]));
  }
  for (const [key, id] of Object.entries(manifest.fileExtensions)) {
    const folded = key.toLowerCase();
    assert.ok(!extensions.has(folded), `后缀冲突：${key}`);
    extensions.set(folded, expression(id, manifest.light.fileExtensions[key]));
  }
  // 只填补上游空白；不新增图形，不覆盖默认关联。这些是外观，不改变打开能力。
  for (const [suffix, id] of Object.entries({ aab: 'android', sami: 'subtitles', smi: 'subtitles' })) {
    assert.ok(!extensions.has(suffix) && Object.values(manifest.fileExtensions).includes(id));
    extensions.set(suffix, expression(id));
  }
  const named = new Map();
  for (const [leaf, parents] of names) {
    const fallback = parents.get('') || '0';
    let expr = fallback;
    for (const [parent, ref] of parents) {
      if (parent && ref !== fallback) expr = `if (hasIconParent(path, ${quoted(parent)})) ${ref} else ${expr}`;
    }
    named.set(leaf, expr);
  }
  return { named, extensions };
}

// 与 JVM String.hashCode() 的低四位一致（UTF-16），包括上游的 emoji 后缀。
function bucket(key) {
  let hash = 0;
  for (let i = 0; i < key.length; i++) hash = (31 * hash + key.charCodeAt(i)) & 15;
  return hash;
}

function dispatch(kind, mapping, parent) {
  const buckets = Array.from({ length: 16 }, () => new Map());
  for (const key of [...mapping.keys()].sort()) {
    const expr = mapping.get(key);
    const groups = buckets[bucket(key)];
    if (!groups.has(expr)) groups.set(expr, []);
    groups.get(expr).push(quoted(key));
  }
  const params = parent ? 'key: String, path: String, light: Boolean' : 'key: String, light: Boolean';
  const args = parent ? 'key, path, light' : 'key, light';
  const lines = [`    fun ${kind}(${params}): Int = when (key.hashCode() and 15) {`];
  for (let i = 0; i < 16; i++) lines.push(`        ${i === 15 ? 'else' : i} -> ${kind}${i}(${args})`);
  lines.push('    }');
  for (const [i, groups] of buckets.entries()) {
    lines.push('', `    private fun ${kind}${i}(${params}): Int = when (key) {`);
    for (const [expr, keys] of groups) {
      lines.push('        ' + keys.join(',\n        ') + ' -> ' + expr);
    }
    lines.push('        else -> 0', '    }');
  }
  return lines.join('\n');
}

function kotlin(manifest) {
  const { named, extensions } = associations(manifest);
  const keys = [...extensions.keys()];
  const result = `// 由 tools/generate-home-file-icons.cjs 从 Material Icon Theme ${VERSION} 生成；勿手改。
package com.privateplayer.feature.home.fileicons

import com.privateplayer.feature.home.R

internal object MaterialIconAssociations {
    const val MAX_SUFFIX_PARTS = ${Math.max(...keys.map(k => k.split('.').length))}
    const val MAX_SUFFIX_LENGTH = ${Math.max(...keys.map(k => k.length))}

${dispatch('named', named, true)}

${dispatch('extension', extensions, false)}
}
`;
  return Buffer.from(result);
}

async function main() {
  const [source, flag, ...extra] = process.argv.slice(2);
  assert.ok(source && (!flag || flag === '--check') && !extra.length, '用法：node tools/generate-home-file-icons.cjs <extension 目录> [--check]');
  for (const [name, version] of Object.entries(RENDERER)) assert.equal(sharp.versions[name], version, `需要 ${name} ${version}`);
  const { manifest, svgById } = sourceData(path.resolve(source));
  const table = kotlin(manifest);
  const check = flag === '--check';
  const write = (file, bytes) => {
    if (check) assert.ok(fs.readFileSync(file).equals(bytes), `生成结果不同：${file}`);
    else fs.writeFileSync(file, bytes);
  };
  if (!check) {
    fs.mkdirSync(drawableDir, { recursive: true });
    fs.mkdirSync(path.dirname(kotlinFile), { recursive: true });
  }
  const expected = new Set();
  let total = 0;
  for (const [id, svg] of svgById) {
    const input = svg.toString('utf8');
    assert.ok(input.startsWith('<svg ') && !/\b(?:width|height)=/.test(input.slice(0, input.indexOf('>'))));
    // 从原 viewBox 直接光栅化到目标尺寸，不把 16/24px 位图放大；保留路径、渐变和透明度。
    const sized = Buffer.from(input.replace('<svg ', `<svg width="${pixels}" height="${pixels}" `));
    const { data, info } = await sharp(sized).webp({ lossless: true, effort: 6 }).toBuffer({ resolveWithObject: true });
    assert.deepEqual([info.width, info.height], [pixels, pixels]);
    const file = resource(id) + '.webp';
    expected.add(file);
    write(path.join(drawableDir, file), data);
    total += data.length;
  }
  // 升级时只处理工具专属前缀；绝不触碰该目录的其他资源。
  for (const file of fs.readdirSync(drawableDir)) {
    if (!file.startsWith(prefix) || expected.has(file)) continue;
    assert.ok(!check, `多余图形：${file}`);
    fs.unlinkSync(path.join(drawableDir, file));
  }
  write(kotlinFile, table);
  console.log(`${check ? '核对' : '生成'} ${expected.size} 张图形，${total} 字节；Kotlin 关联表 ${table.length} 字节。`);
}

main().catch(error => { console.error(error); process.exitCode = 1; });
