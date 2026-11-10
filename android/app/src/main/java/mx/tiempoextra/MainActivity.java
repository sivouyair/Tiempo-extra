package mx.tiempoextra;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Envoltorio mínimo: sirve los archivos de assets bajo un origen https virtual
 * (necesario para que el OCR use Web Workers y WebAssembly), con localStorage,
 * exportar (descargas data:) e importar (selector de archivo).
 *
 * Las versiones anteriores cargaban file:///android_asset/index.html; el localStorage
 * de ese origen se copia una sola vez al origen nuevo para no perder datos.
 */
public class MainActivity extends Activity {
    private static final int REQ_FILE = 1001;
    private static final String HOST = "appassets.tiempoextra.local";
    private static final String BASE = "https://" + HOST + "/";
    private static final String OLD_URL = "file:///android_asset/migrar.html";
    private static final String KEY = "tiempo-extra-v1";
    private static final String PREFS = "app";

    private WebView web;
    private ValueCallback<Uri[]> pendingFileChooser;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setBuiltInZoomControls(false);
        s.setSupportZoom(false);

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (!HOST.equals(u.getHost())) return null;
                String path = u.getPath() == null ? "" : u.getPath();
                if (path.startsWith("/")) path = path.substring(1);
                if (path.isEmpty()) path = "index.html";
                if (path.contains("..")) return notFound();
                try {
                    InputStream in = getAssets().open(path);
                    WebResourceResponse r = new WebResourceResponse(mime(path), isText(path) ? "utf-8" : null, in);
                    Map<String, String> h = new HashMap<>();
                    h.put("Access-Control-Allow-Origin", "*");
                    h.put("Cache-Control", "no-cache");
                    r.setResponseHeaders(h);
                    return r;
                } catch (Exception e) {
                    return notFound();
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (HOST.equals(u.getHost()) || "file".equals(u.getScheme())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (url != null && url.startsWith(BASE)) { view.clearHistory(); restoreMigratedData(); }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (pendingFileChooser != null) pendingFileChooser.onReceiveValue(null);
                pendingFileChooser = cb;
                boolean image = false;
                String[] types = params.getAcceptTypes();
                if (types != null) for (String t : types) if (t != null && t.startsWith("image/")) image = true;
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType(image ? "image/*" : "*/*");
                try {
                    startActivityForResult(i, REQ_FILE);
                } catch (Exception e) {
                    pendingFileChooser = null;
                    return false;
                }
                return true;
            }
        });

        // Exportar JSON: la página genera un enlace data:… con atributo download.
        web.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            if (url.startsWith("data:")) saveDataUrl(url, contentDisposition);
        });

        if (prefs.getBoolean("migrated", false)) web.loadUrl(BASE + "index.html");
        else web.loadUrl(OLD_URL); // lee el localStorage del origen antiguo y continúa en Bridge.migrate()
    }

    /** Recibe los datos guardados por versiones anteriores (origen file://). */
    private class Bridge {
        @JavascriptInterface
        public void migrate(String data) {
            SharedPreferences.Editor e = prefs.edit().putBoolean("migrated", true);
            if (data != null && !data.isEmpty()) e.putString("pending", data);
            e.apply();
            runOnUiThread(() -> web.loadUrl(BASE + "index.html"));
        }
    }

    /** Si hay datos del origen antiguo y el nuevo está vacío, los copia y recarga una vez. */
    private void restoreMigratedData() {
        String pending = prefs.getString("pending", null);
        if (pending == null) return;
        prefs.edit().remove("pending").apply();
        String js = "(function(){try{if(!localStorage.getItem(" + JSONObject.quote(KEY) + ")){localStorage.setItem("
                + JSONObject.quote(KEY) + "," + JSONObject.quote(pending) + ");location.reload();}}catch(e){}})();";
        web.evaluateJavascript(js, null);
    }

    private static WebResourceResponse notFound() {
        return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", new HashMap<>(),
                new java.io.ByteArrayInputStream(new byte[0]));
    }

    private static boolean isText(String p) {
        return p.endsWith(".html") || p.endsWith(".js") || p.endsWith(".css") || p.endsWith(".json") || p.endsWith(".webmanifest");
    }

    private static String mime(String p) {
        if (p.endsWith(".html")) return "text/html";
        if (p.endsWith(".js")) return "application/javascript";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".json")) return "application/json";
        if (p.endsWith(".webmanifest")) return "application/manifest+json";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".wasm")) return "application/wasm";
        return "application/octet-stream";
    }

    private void saveDataUrl(String dataUrl, String contentDisposition) {
        try {
            int comma = dataUrl.indexOf(',');
            String meta = dataUrl.substring(5, comma);
            String payload = dataUrl.substring(comma + 1);
            byte[] bytes = meta.endsWith(";base64")
                    ? Base64.decode(payload, Base64.DEFAULT)
                    : URLDecoder.decode(payload, StandardCharsets.UTF_8.name()).getBytes(StandardCharsets.UTF_8);
            String mime = meta.split(";")[0];
            if (mime.isEmpty()) mime = "application/octet-stream";
            String name = "tiempo-extra-" + System.currentTimeMillis() + ".json";
            if (contentDisposition != null) {
                int i = contentDisposition.indexOf("filename=");
                if (i >= 0) name = contentDisposition.substring(i + 9).replace("\"", "").trim();
            }
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
            cv.put(MediaStore.Downloads.MIME_TYPE, mime);
            cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) throw new IllegalStateException("sin destino");
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                out.write(bytes);
            }
            Toast.makeText(this, "Guardado en Descargas: " + name, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo guardar el archivo", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE && pendingFileChooser != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) result = new Uri[]{data.getData()};
            pendingFileChooser.onReceiveValue(result);
            pendingFileChooser = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
