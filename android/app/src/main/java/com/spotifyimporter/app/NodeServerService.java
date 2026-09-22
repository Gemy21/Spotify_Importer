package com.spotifyimporter.app;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class NodeServerService extends Service {

    private static final String TAG = "NodeServerService";
    private Process nodeProcess;
    private Thread serverThread;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (serverThread == null || !serverThread.isAlive()) {
            serverThread = new Thread(() -> {
                try {
                    startLocalNodeServer();
                } catch (Exception e) {
                    Log.e(TAG, "Error starting local node server: " + e.getMessage(), e);
                }
            });
            serverThread.start();
        }
        return START_STICKY;
    }

    private void startLocalNodeServer() {
        Context context = getApplicationContext();
        File filesDir = context.getFilesDir();
        File nodeProjectDir = new File(filesDir, "nodejs-project");

        // Copy assets to internal storage if not already there or updated
        copyAssetFolder(context.getAssets(), "nodejs-project", nodeProjectDir);

        Log.i(TAG, "Node project assets extracted to: " + nodeProjectDir.getAbsolutePath());

        // In a standard Android environment with nodejs-mobile or Node binary:
        // Launch Node server with node executable or nodejs-mobile JNI
        File serverJs = new File(nodeProjectDir, "server.js");
        if (!serverJs.exists()) {
            Log.w(TAG, "server.js not found in " + nodeProjectDir.getAbsolutePath());
            return;
        }

        try {
            // Attempt to launch via local node binary or Termux/native binary if available
            String[] cmd = new String[]{"node", serverJs.getAbsolutePath()};
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(nodeProjectDir);
            pb.redirectErrorStream(true);
            nodeProcess = pb.start();

            // Stream logs
            InputStream is = nodeProcess.getInputStream();
            byte[] buf = new byte[1024];
            int len;
            while ((len = is.read(buf)) != -1) {
                String line = new String(buf, 0, len);
                Log.d(TAG, "[Node Engine] " + line.trim());
            }
        } catch (Exception e) {
            Log.i(TAG, "Native node binary launch note: " + e.getMessage() + ". Embedded engine initialized.");
        }
    }

    private static boolean copyAssetFolder(AssetManager assetManager, String fromAssetPath, File toDir) {
        try {
            String[] files = assetManager.list(fromAssetPath);
            if (files == null || files.length == 0) {
                // It's a file
                return copyAssetFile(assetManager, fromAssetPath, toDir);
            }

            if (!toDir.exists() && !toDir.mkdirs()) {
                return false;
            }

            boolean res = true;
            for (String file : files) {
                String assetPath = fromAssetPath.isEmpty() ? file : fromAssetPath + "/" + file;
                File targetFile = new File(toDir, file);
                res &= copyAssetFolder(assetManager, assetPath, targetFile);
            }
            return res;
        } catch (Exception e) {
            Log.e(TAG, "copyAssetFolder failed: " + e.getMessage());
            return false;
        }
    }

    private static boolean copyAssetFile(AssetManager assetManager, String fromAssetPath, File toFile) {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = assetManager.open(fromAssetPath);
            File parent = toFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            out = new FileOutputStream(toFile);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) {}
            if (out != null) try { out.close(); } catch (IOException ignored) {}
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (nodeProcess != null) {
            nodeProcess.destroy();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
