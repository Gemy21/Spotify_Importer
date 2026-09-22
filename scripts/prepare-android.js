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

// 1. Copy backend server files
if (fs.existsSync(backendSrc)) {
  copyDir(backendSrc, androidAssets);
  console.log('[Android Prepare] Node backend server files copied.');
}

// 2. Copy web assets to public folder
if (fs.existsSync(path.join(rootDir, 'public'))) {
  copyDir(path.join(rootDir, 'public'), publicDest);
  console.log('[Android Prepare] Public web assets copied.');
}

console.log('[Android Prepare] Successfully prepared Android APK assets in ' + androidAssets);
