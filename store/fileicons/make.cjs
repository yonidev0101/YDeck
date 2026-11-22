// The file and folder icons on the phone's Files tab: a chosen part of Material Icon Theme (MIT, see LICENSE here),
// the icons VS Code and GitHub show. Run again after picking more:
//
//   npm pack material-icon-theme && tar xzf material-icon-theme-*.tgz     (anywhere outside the repo)
//   npm i svg2vectordrawable@2                                           (next to it)
//   node store/fileicons/make.cjs <that folder>/package <where svg2vectordrawable is>/node_modules
//
// It writes app/src/main/res/drawable/fi_*.xml (one per icon), app/src/main/assets/fileicons.json (extension, file
// name and folder name → icon) and app/src/main/java/com/yonidev/ydeck/ui/FileIconIds.kt (icon → drawable).

const fs = require('fs');
const path = require('path');
const [pkg, modules] = process.argv.slice(2);
if (!pkg || !modules) { console.error('usage: node make.cjs <material-icon-theme package> <node_modules with svg2vectordrawable>'); process.exit(1); }
const s2v = require(path.join(modules, 'svg2vectordrawable'));
const m = require(path.join(pkg, 'dist/material-icons.json'));
const root = path.resolve(__dirname, '../..');

// The icons taken: what projects here hold, and what's common elsewhere.
const FILES = `file document markdown pdf image svg video audio zip exe lock table database console powershell settings
 kotlin java android gradle groovy xml json yaml toml javascript typescript react react_ts vue svelte angular nodejs npm pnpm yarn bun
 deno html css sass less tailwindcss python go rust c cpp csharp php ruby swift dart lua r graphql prisma
 docker git github-actions-workflow readme license changelog contributing tune key certificate font word powerpoint
 vite next nuxt astro eslint prettier babel webpack jest vitest playwright cypress tsconfig jsconfig editorconfig
 firebase vercel netlify wrangler nginx http makefile diff log todo robots favicon
 claude agent copilot cursor vscode visualstudio jupyter`.split(/\s+/).filter(Boolean);
const FOLDERS = `folder src app lib dist test docs images components config scripts public github vscode
 git api utils android ios desktop server client database tools temp store packages core
 views routes layout ui shared plugin video audio i18n content theme claude resource node hook css font`.split(/\s+/).filter(Boolean)
  .map((n) => (n === 'folder' ? 'folder' : 'folder-' + n));

const have = new Set(Object.keys(m.iconDefinitions));
const icons = new Set([...FILES, ...FOLDERS].filter((n) => have.has(n)));
const missing = [...FILES, ...FOLDERS].filter((n) => !have.has(n));
if (missing.length) console.log('not in this version (left out):', missing.join(' '));

// Every mapping that points to an icon taken; the rest fall back to the plain file or folder.
const pick = (o) => Object.fromEntries(Object.entries(o || {}).filter(([, v]) => icons.has(v)).map(([k, v]) => [k.toLowerCase(), v]));
const map = {
  file: 'file', folder: 'folder',
  ext: pick(m.fileExtensions),
  name: pick(m.fileNames),
  dir: pick(m.folderNames),
};

const id = (n) => 'fi_' + n.replace(/[^a-z0-9]/gi, '_').toLowerCase();

(async () => {
  const out = path.join(root, 'app/src/main/res/drawable');
  for (const f of fs.readdirSync(out)) if (f.startsWith('fi_')) fs.unlinkSync(path.join(out, f));
  let bytes = 0;
  for (const n of icons) {
    const file = path.join(pkg, m.iconDefinitions[n].iconPath.replace(/^\.\.?\/*(dist\/)?/, '').replace(/^\.\//, ''));
    const svgPath = fs.existsSync(file) ? file : path.join(pkg, 'icons', path.basename(file));
    const xml = await s2v(fs.readFileSync(svgPath, 'utf8'), { floatPrecision: 2 });
    fs.writeFileSync(path.join(out, id(n) + '.xml'), xml);
    bytes += xml.length;
  }
  fs.mkdirSync(path.join(root, 'app/src/main/assets'), { recursive: true });
  fs.writeFileSync(path.join(root, 'app/src/main/assets/fileicons.json'), JSON.stringify(map));
  const kt = `package com.yonidev.ydeck.ui

import com.yonidev.ydeck.R

// Made by store/fileicons/make.cjs from Material Icon Theme (MIT). Don't edit by hand.

/** Each icon taken (its name in Material Icon Theme) → its drawable. */
internal val FILE_ICON_IDS: Map<String, Int> = mapOf(
${[...icons].sort().map((n) => `    "${n}" to R.drawable.${id(n)},`).join('\n')}
)
`;
  fs.writeFileSync(path.join(root, 'app/src/main/java/com/yonidev/ydeck/ui/FileIconIds.kt'), kt);
  console.log(`${icons.size} icons (${Math.round(bytes / 1024)} KB of XML), ${Object.keys(map.ext).length} extensions, ${Object.keys(map.name).length} file names, ${Object.keys(map.dir).length} folder names`);
})();
