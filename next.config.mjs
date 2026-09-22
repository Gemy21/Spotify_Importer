/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // Images must be unoptimized in static export mode (no server-side image optimization)
  images: {
    unoptimized: true,
  },
};

// When building for Android (ANDROID_BUILD=1), produce a static export.
// The API routes are excluded from the static export — the page is a 'use client'
// component so all /api/* calls happen at runtime from the browser, not at build time.
if (process.env.ANDROID_BUILD === '1') {
  nextConfig.output = 'export';
  nextConfig.trailingSlash = true;
}

export default nextConfig;
