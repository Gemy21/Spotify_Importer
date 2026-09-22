package com.spotifyimporter.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    private static final String LOCAL_SERVER_URL = "http://127.0.0.1:3001/";
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private WebView webView;
    private ProgressBar progressBar;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isServerReady = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Dark theme layout programmatically for maximum reliability
        setContentView(createLayout());

        // Check storage permissions if on older Android
        checkPermissions();

        // Start embedded Node.js engine
        startNodeServer();

        // Initialize WebView
        configureWebView();

        // Check when local server is ready, then load
        pollServerAndLoad();
    }

    private View createLayout() {
        android.widget.RelativeLayout layout = new android.widget.RelativeLayout(this);
        layout.setBackgroundColor(0xFF0A0A0A);

        webView = new WebView(this);
        webView.setLayoutParams(new android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT
        ));
        webView.setBackgroundColor(0xFF0A0A0A);
        layout.addView(webView);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        android.widget.RelativeLayout.LayoutParams pbParams = new android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
                8
        );
        pbParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP);
        progressBar.setLayoutParams(pbParams);
        progressBar.setMax(100);
        progressBar.setVisibility(View.GONE);
        layout.addView(progressBar);

        return layout;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString() + " SpotifyImporterAndroid/1.0");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                // Stay inside the local app
                if (url.startsWith(LOCAL_SERVER_URL)) {
                    return false;
                }
                // Open external links in external browser
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                } catch (Exception ignored) {}
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                // If local server still starting, retry shortly
                if (!isServerReady) {
                    handler.postDelayed(() -> webView.loadUrl(LOCAL_SERVER_URL), 1000);
                }
            }
        });

        // Handle MP3/lyrics file downloads
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            handleDownload(url, contentDisposition, mimeType);
        });
    }

    private void handleDownload(String url, String contentDisposition, String mimeType) {
        try {
            if (url.startsWith("blob:") || url.startsWith("data:")) {
                // Convert blob/data via JS to Base64 and write to device
                fetchBlobAndSave(url);
                return;
            }

            String filename = URLUtil.guessFileName(url, contentDisposition, mimeType);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setMimeType(mimeType);
            request.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url));
            request.setDescription("Downloading Spotify song...");
            request.setTitle(filename);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);

            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) {
                dm.enqueue(request);
                Toast.makeText(this, "Downloading " + filename + " to Downloads", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Download error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchBlobAndSave(String blobUrl) {
        String js = "(function() {" +
                "  var xhr = new XMLHttpRequest();" +
                "  xhr.open('GET', '" + blobUrl + "', true);" +
                "  xhr.responseType = 'blob';" +
                "  xhr.onload = function() {" +
                "    var reader = new FileReader();" +
                "    reader.readAsDataURL(xhr.response);" +
                "    reader.onloadend = function() {" +
                "      console.log('BLOB_DATA:' + reader.result);" +
                "    };" +
                "  };" +
                "  xhr.send();" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    private void startNodeServer() {
        try {
            Intent intent = new Intent(this, NodeServerService.class);
            startService(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void pollServerAndLoad() {
        new Thread(() -> {
            int retries = 0;
            while (retries < 30) {
                try {
                    URL u = new URL(LOCAL_SERVER_URL + "health");
                    HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                    conn.setConnectTimeout(1000);
                    conn.setReadTimeout(1000);
                    conn.setRequestMethod("GET");
                    int code = conn.getResponseCode();
                    if (code == 200) {
                        isServerReady = true;
                        handler.post(() -> webView.loadUrl(LOCAL_SERVER_URL));
                        return;
                    }
                } catch (Exception ignored) {}

                retries++;
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {}
            }

            // Fallback load anyway
            handler.post(() -> webView.loadUrl(LOCAL_SERVER_URL));
        }).start();
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_CODE
                );
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webView.destroy();
        }
    }
}
