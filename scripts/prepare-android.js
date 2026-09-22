/* eslint-disable */
const fs = require('fs');
const path = require('path');

function copyDir(src, dest) {
  if (!fs.existsSync(dest)) {
    fs.mkdirSync(dest, { recursive: true });
  }
  const entries = fs.readdirSync(src, { withFileTypes: true });
  for (const entry of entries) {
    const srcPath = path.join(src, entry.name);
    const destPath = path.join(dest, entry.name);
    if (entry.isDirectory()) {
      copyDir(srcPath, destPath);
    } else {
      fs.copyFileSync(srcPath, destPath);
    }
  }
}

const rootDir = path.resolve(__dirname, '..');
const androidAssets = path.join(rootDir, 'android', 'app', 'src', 'main', 'assets', 'nodejs-project');
const backendSrc = path.join(rootDir, 'android', 'nodejs-assets', 'nodejs-project');
const publicDest = path.join(androidAssets, 'public');

console.log('[Android Prepare] Packaging assets for Android APK...');

// 1. Copy backend server files (server.js, api/, lib/, package.json)
if (fs.existsSync(backendSrc)) {
  copyDir(backendSrc, androidAssets);
  console.log('[Android Prepare] Node backend server files copied.');
}

// 2. Copy public assets (icons, manifest, etc.)
if (fs.existsSync(path.join(rootDir, 'public'))) {
  copyDir(path.join(rootDir, 'public'), publicDest);
  console.log('[Android Prepare] Public web assets copied.');
}

// 3. Copy compiled Next.js HTML if available
const appHtml = path.join(rootDir, '.next', 'server', 'app', 'index.html');
if (fs.existsSync(appHtml)) {
  fs.copyFileSync(appHtml, path.join(publicDest, 'index.html'));
  console.log('[Android Prepare] Next.js compiled index.html copied to public/index.html.');
}

// 4. Copy Next.js static assets (_next/static for CSS, JS chunks)
const nextStatic = path.join(rootDir, '.next', 'static');
const nextStaticDest = path.join(publicDest, '_next', 'static');
if (fs.existsSync(nextStatic)) {
  copyDir(nextStatic, nextStaticDest);
  console.log('[Android Prepare] Next.js static bundles copied to public/_next/static.');
}

console.log('[Android Prepare] Successfully prepared Android APK assets in ' + androidAssets);
