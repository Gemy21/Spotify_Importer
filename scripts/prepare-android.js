/* eslint-disable */
/**
 * Builds the native Android app in `android/` with its Gradle wrapper.
 *
 * The old WebView flow (Next.js static export → android/app/src/main/assets/www)
 * has been retired; the app is now fully native Kotlin + Jetpack Compose.
 *
 * Usage: npm run build:android
 * Requires JDK 17 and Android SDK 34.
 */

const { spawnSync } = require('child_process');
const path = require('path');

const androidDir = path.resolve(__dirname, '..', 'android');
const isWindows = process.platform === 'win32';
const gradlew = path.join(androidDir, isWindows ? 'gradlew.bat' : 'gradlew');

console.log(`[Android Build] Running ${gradlew} assembleDebug ...`);

const result = spawnSync(gradlew, ['assembleDebug'], {
  cwd: androidDir,
  stdio: 'inherit',
  shell: isWindows,
});

if (result.error) {
  console.error('[Android Build] Failed to launch the Gradle wrapper:', result.error.message);
  console.error('[Android Build] Make sure JDK 17 is installed and android/gradle/wrapper/gradle-wrapper.jar exists.');
  process.exit(1);
}

if (result.status !== 0) {
  console.error(`[Android Build] Gradle exited with code ${result.status}.`);
  process.exit(result.status || 1);
}

console.log('\n[Android Build] Done. APK: android/app/build/outputs/apk/debug/app-debug.apk');
