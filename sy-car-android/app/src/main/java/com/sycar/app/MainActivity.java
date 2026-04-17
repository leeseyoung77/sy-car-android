package com.sycar.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.Manifest;

import android.webkit.JavascriptInterface;
import android.content.pm.ResolveInfo;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> fileUploadCallback;
    private Uri cameraImageUri;
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int LOCATION_PERMISSION_REQUEST = 1002;
    private static final int CAMERA_PERMISSION_REQUEST = 1003;

    // JavaScript → Native 브릿지
    private class AppBridge {
        @JavascriptInterface
        public boolean hasPermission(String perm) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
            switch (perm) {
                case "camera":
                    return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
                case "location":
                    return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                case "storage":
                    if (Build.VERSION.SDK_INT >= 33) return true; // scoped storage
                    return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
                default:
                    return false;
            }
        }

        @JavascriptInterface
        public void requestPermission(String perm) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
            runOnUiThread(() -> {
                switch (perm) {
                    case "camera":
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
                        break;
                    case "location":
                        requestPermissions(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        }, LOCATION_PERMISSION_REQUEST);
                        break;
                    case "storage":
                        requestPermissions(new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        }, 1004);
                        break;
                }
            });
        }

        @JavascriptInterface
        public void openAppSettings() {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }

        @JavascriptInterface
        public String getAppVersion() {
            try {
                return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (Exception e) {
                return "1.0.0";
            }
        }

        @JavascriptInterface
        public boolean canMakeCall() {
            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:0000000000"));
            java.util.List<ResolveInfo> activities = getPackageManager().queryIntentActivities(intent, 0);
            return activities != null && !activities.isEmpty();
        }

        @JavascriptInterface
        public int getVersionCode() {
            try {
                return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            } catch (Exception e) {
                return 1;
            }
        }

        @JavascriptInterface
        public void checkUpdate(String versionUrl) {
            new Thread(() -> {
                try {
                    URL url = new URL(versionUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    conn.setInstanceFollowRedirects(true);
                    InputStream is = conn.getInputStream();
                    byte[] buf = new byte[4096];
                    StringBuilder sb = new StringBuilder();
                    int n;
                    while ((n = is.read(buf)) != -1) {
                        sb.append(new String(buf, 0, n, "UTF-8"));
                    }
                    is.close();
                    conn.disconnect();
                    final String json = sb.toString();
                    runOnUiThread(() -> {
                        webView.evaluateJavascript(
                            "if(window.__onUpdateChecked) window.__onUpdateChecked(" + json + ")", null);
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> {
                        webView.evaluateJavascript(
                            "if(window.__onUpdateChecked) window.__onUpdateChecked(null)", null);
                    });
                }
            }).start();
        }

        @JavascriptInterface
        public void downloadAndInstallApk(String apkUrl) {
            new Thread(() -> {
                try {
                    URL url = new URL(apkUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(60000);
                    conn.setInstanceFollowRedirects(true);
                    int fileLength = conn.getContentLength();

                    File apkFile = new File(getExternalCacheDir(), "update.apk");
                    InputStream is = conn.getInputStream();
                    FileOutputStream fos = new FileOutputStream(apkFile);
                    byte[] buf = new byte[8192];
                    int total = 0;
                    int bytesRead;
                    int lastProgress = 0;
                    while ((bytesRead = is.read(buf)) != -1) {
                        fos.write(buf, 0, bytesRead);
                        total += bytesRead;
                        if (fileLength > 0) {
                            int progress = (int) (total * 100L / fileLength);
                            if (progress != lastProgress) {
                                lastProgress = progress;
                                final int p = progress;
                                runOnUiThread(() -> {
                                    webView.evaluateJavascript(
                                        "if(window.__onDownloadProgress) window.__onDownloadProgress(" + p + ")", null);
                                });
                            }
                        }
                    }
                    fos.close();
                    is.close();
                    conn.disconnect();

                    // APK 설치 시작
                    runOnUiThread(() -> {
                        try {
                            Uri apkUri = FileProvider.getUriForFile(
                                MainActivity.this, getPackageName() + ".fileprovider", apkFile);
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        } catch (Exception e) {
                            e.printStackTrace();
                            webView.evaluateJavascript(
                                "if(window.__onUpdateError) window.__onUpdateError('설치를 시작할 수 없습니다')", null);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> {
                        webView.evaluateJavascript(
                            "if(window.__onUpdateError) window.__onUpdateError('다운로드에 실패했습니다')", null);
                    });
                }
            }).start();
        }
    }

    // CORS 우회가 필요한 외부 도메인
    private boolean needsProxy(String url) {
        return url.startsWith("https://m.search.naver.com/")
            || url.startsWith("https://search.map.kakao.com/")
            || url.startsWith("https://nominatim.openstreetmap.org/");
    }

    private WebResourceResponse proxyRequest(WebResourceRequest request) {
        try {
            URL reqUrl = new URL(request.getUrl().toString());
            HttpURLConnection conn = (HttpURLConnection) reqUrl.openConnection();
            conn.setRequestMethod(request.getMethod());
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(true);
            // 원본 요청 헤더 복사
            Map<String, String> reqHeaders = request.getRequestHeaders();
            if (reqHeaders != null) {
                for (Map.Entry<String, String> h : reqHeaders.entrySet()) {
                    if (!h.getKey().equalsIgnoreCase("Host")) {
                        conn.setRequestProperty(h.getKey(), h.getValue());
                    }
                }
            }
            int code = conn.getResponseCode();
            InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String contentType = conn.getContentType();
            if (contentType == null) contentType = "text/html; charset=utf-8";
            String mime = contentType.contains(";") ? contentType.substring(0, contentType.indexOf(";")).trim() : contentType;
            // CORS 허용 헤더 추가
            Map<String, String> responseHeaders = new HashMap<>();
            responseHeaders.put("Access-Control-Allow-Origin", "*");
            responseHeaders.put("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            responseHeaders.put("Access-Control-Allow-Headers", "*");
            return new WebResourceResponse(mime, "utf-8", code, "OK", responseHeaders, is);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 전체 화면 설정
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        // 상태바 색상 (다크 테마)
        getWindow().setStatusBarColor(0xFF0F1923);

        // WebView 생성
        webView = new WebView(this);
        setContentView(webView);

        // WebView 설정
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowUniversalAccessFromFileURLs(true);  // file:// CORS 우회
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setGeolocationEnabled(true);

        // 위치 + 카메라 권한 런타임 요청 (Android 6.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            java.util.List<String> perms = new java.util.ArrayList<>();
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
                perms.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            }
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.CAMERA);
            }
            if (!perms.isEmpty()) {
                requestPermissions(perms.toArray(new String[0]), LOCATION_PERMISSION_REQUEST);
            }
        }

        // JavaScript 브릿지 등록
        webView.addJavascriptInterface(new AppBridge(), "AppBridge");

        // WebViewClient - CORS 프록시 + 링크 처리
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (needsProxy(url)) {
                    WebResourceResponse response = proxyRequest(request);
                    if (response != null) return response;
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("tel:")) {
                    Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse(url));
                    startActivity(intent);
                    return true;
                }
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    if (!url.contains("cdnjs.cloudflare.com") && !url.contains("api.anthropic.com")
                        && !url.contains("search.map.kakao.com") && !url.contains("car.go.kr")
                        && !url.contains("m.search.naver.com")) {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(intent);
                        return true;
                    }
                }
                return false;
            }
        });

        // WebChromeClient - 파일 업로드, 카메라, 위치 지원
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback,
                                              FileChooserParams fileChooserParams) {
                if (fileUploadCallback != null) {
                    fileUploadCallback.onReceiveValue(null);
                }
                fileUploadCallback = callback;

                // 카메라 촬영 Intent
                Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                cameraImageUri = null;
                if (cameraIntent.resolveActivity(getPackageManager()) != null) {
                    File photoFile = null;
                    try {
                        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                        File storageDir = getExternalCacheDir();
                        photoFile = File.createTempFile("IMG_" + timeStamp + "_", ".jpg", storageDir);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    if (photoFile != null) {
                        cameraImageUri = FileProvider.getUriForFile(
                            MainActivity.this,
                            getPackageName() + ".fileprovider",
                            photoFile
                        );
                        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
                        cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    }
                }

                // 갤러리/파일 선택 Intent
                Intent fileIntent = new Intent(Intent.ACTION_GET_CONTENT);
                fileIntent.addCategory(Intent.CATEGORY_OPENABLE);
                fileIntent.setType("image/*");

                // 카메라 + 갤러리 선택 화면
                Intent chooserIntent = Intent.createChooser(fileIntent, "사진 선택");
                if (cameraImageUri != null) {
                    chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cameraIntent});
                }

                try {
                    startActivityForResult(chooserIntent, FILE_CHOOSER_REQUEST);
                } catch (Exception e) {
                    fileUploadCallback = null;
                    cameraImageUri = null;
                    return false;
                }
                return true;
            }
        });

        // 로컬 HTML 파일 로드
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (fileUploadCallback != null) {
                Uri[] results = null;
                if (resultCode == Activity.RESULT_OK) {
                    if (data != null && data.getDataString() != null) {
                        // 갤러리/파일에서 선택
                        results = new Uri[]{Uri.parse(data.getDataString())};
                    } else if (cameraImageUri != null) {
                        // 카메라로 촬영
                        results = new Uri[]{cameraImageUri};
                    }
                }
                fileUploadCallback.onReceiveValue(results);
                fileUploadCallback = null;
                cameraImageUri = null;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // 권한 변경을 WebView에 알림
        webView.evaluateJavascript("if(window.__onPermissionChanged) window.__onPermissionChanged()", null);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
