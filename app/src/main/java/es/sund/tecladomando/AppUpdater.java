package es.sund.tecladomando;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/**
 * Autoactualizacion. Consulta el endpoint de version de sund.es (que calcula
 * versionCode/versionName/sha1 inspeccionando la APK real publicada, nunca un
 * numero a mano), compara contra la version instalada, y si hay una mas nueva
 * la descarga, verifica su SHA1 ANTES de lanzar el instalador del sistema
 * (nunca se instala nada sin verificar el hash) y abre el dialogo de
 * instalacion del SO (no se instala en silencio).
 */
public final class AppUpdater {

    public static final String ENDPOINT = "https://sund.es/APIs/TecladoMandoAndroidVersion";

    private AppUpdater() {
    }

    /**
     * @param silencioso si true, no avisa cuando ya esta al dia ni de errores
     *                   (uso: comprobacion automatica al arrancar). Si false,
     *                   informa al usuario (uso: boton "Buscar actualizacion").
     */
    public static void comprobar(final Activity activity, final boolean silencioso) {
        new Thread(() -> {
            try {
                String json = getTexto(ENDPOINT);
                JSONObject info = new JSONObject(json);
                if (!info.optBoolean("success", false)) {
                    aviso(activity, silencioso, "No se pudo comprobar la actualizacion.");
                    return;
                }
                long versionRemota = info.getLong("versionCode");
                String nombreRemoto = info.optString("versionName", "");
                String sha1Esperado = info.getString("apkSha1");
                String apkUrl = info.getString("apkUrl");

                long versionLocal = versionLocal(activity);
                if (versionRemota <= versionLocal) {
                    aviso(activity, silencioso, "Ya tienes la ultima version (" + versionLocal + ").");
                    return;
                }

                aviso(activity, silencioso, "Descargando version " + nombreRemoto + "...");

                File destino = new File(activity.getCacheDir(), "tecladomando-update.apk");
                descargar(apkUrl, destino);

                String sha1Real = sha1(destino);
                if (!sha1Real.equalsIgnoreCase(sha1Esperado)) {
                    destino.delete();
                    aviso(activity, false, "Actualizacion descartada: el hash no coincide.");
                    return;
                }

                activity.runOnUiThread(() -> lanzarInstalador(activity, destino, apkUrl));
            } catch (Exception e) {
                aviso(activity, silencioso, "Error al comprobar actualizacion.");
            }
        }).start();
    }

    private static void aviso(final Activity activity, boolean silencioso, final String msg) {
        if (silencioso) {
            return;
        }
        activity.runOnUiThread(() -> Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show());
    }

    private static long versionLocal(Activity activity) throws PackageManager.NameNotFoundException {
        PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return info.getLongVersionCode();
        }
        return info.versionCode;
    }

    private static String getTexto(String urlStr) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(urlStr).openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(15000);
        con.setRequestMethod("GET");
        try (InputStream in = con.getInputStream()) {
            byte[] buf = new byte[4096];
            StringBuilder sb = new StringBuilder();
            int n;
            while ((n = in.read(buf)) != -1) {
                sb.append(new String(buf, 0, n, "UTF-8"));
            }
            return sb.toString();
        } finally {
            con.disconnect();
        }
    }

    private static void descargar(String urlStr, File destino) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(urlStr).openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(60000);
        con.setRequestMethod("GET");
        try (InputStream in = con.getInputStream();
             FileOutputStream out = new FileOutputStream(destino)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
        } finally {
            con.disconnect();
        }
    }

    private static String sha1(File fichero) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (FileInputStream in = new FileInputStream(fichero)) {
            byte[] trozo = new byte[8192];
            int leidos;
            while ((leidos = in.read(trozo)) != -1) {
                digest.update(trozo, 0, leidos);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : digest.digest()) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static void lanzarInstalador(Activity activity, File apk, String apkUrl) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(activity,
                    "Activa \"instalar apps desconocidas\" para Teclado Mando y reintenta",
                    Toast.LENGTH_LONG).show();
            try {
                Intent ajustes = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(ajustes);
            } catch (Exception ignored) {
                abrirEnNavegador(activity, apkUrl);
            }
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".fileprovider", apk);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            abrirEnNavegador(activity, apkUrl);
        }
    }

    private static void abrirEnNavegador(Activity activity, String apkUrl) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)));
        } catch (Exception ignored) {
        }
    }
}
