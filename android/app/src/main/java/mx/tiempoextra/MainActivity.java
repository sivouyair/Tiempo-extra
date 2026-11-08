package mx.tiempoextra;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Envoltorio mínimo: carga index.html (copiado a assets en la compilación) en un WebView
 * con localStorage habilitado, soporta exportar (descargas data:) e importar (selector de archivo).
 */
public class MainActivity extends Activity {
    private static final int REQ_FILE = 1001;
    private WebView web;
    private ValueCallback<Uri[]> pendingFileChooser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setBuiltInZoomControls(false);
        s.setSupportZoom(false);
        s.setMediaPlaybackRequiresUserGesture(false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("file://")) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) {}
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (pendingFileChooser != null) pendingFileChooser.onReceiveValue(null);
                pendingFileChooser = cb;
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
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

        web.loadUrl("file:///android_asset/index.html");
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
