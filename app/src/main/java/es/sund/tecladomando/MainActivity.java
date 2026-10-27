package es.sund.tecladomando;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Pantalla de ayuda y punto de entrada de la autoactualizacion.
 *
 * Android exige dos pasos manuales para usar un teclado nuevo: (1) activarlo
 * en Ajustes y (2) seleccionarlo como teclado actual. Esta pantalla ofrece un
 * boton para cada paso, instrucciones en espanol, la version instalada y un
 * boton de "buscar actualizacion".
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(construirVista());

        // Comprobacion automatica de actualizacion al arrancar (silenciosa).
        AppUpdater.comprobar(this, true);
    }

    private View construirVista() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF101418);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, dp(28), pad, dp(28));
        scroll.addView(root);

        root.addView(titulo("Teclado Mando"));
        root.addView(parrafo(
                "Teclado para toda Android, manejable con el mando (cruceta/stick + botones) "
                        + "y con el dedo, al estilo del teclado en pantalla de Xbox."));

        root.addView(subtitulo("Paso 1 — Activalo en Ajustes"));
        root.addView(parrafo("Marca \"Teclado Mando\" en la lista de teclados del sistema."));
        Button bAjustes = boton("Activa Teclado Mando en ajustes");
        bAjustes.setOnClickListener(v -> abrirAjustesTeclados());
        root.addView(bAjustes);

        root.addView(subtitulo("Paso 2 — Elegirlo como teclado actual"));
        root.addView(parrafo("Selecciona \"Teclado Mando\" en el selector de teclados."));
        Button bPicker = boton("Eligelo como teclado actual");
        bPicker.setOnClickListener(v -> mostrarSelector());
        root.addView(bPicker);

        root.addView(subtitulo("Controles con el mando"));
        root.addView(parrafo(
                "Cruceta/stick: mover la seleccion.\n"
                        + "A: escribir la tecla resaltada.\n"
                        + "B / X: borrar.\n"
                        + "Y: espacio.\n"
                        + "LB: mayusculas.\n"
                        + "RB: numeros y simbolos.\n"
                        + "Start / Select: intro."));

        root.addView(subtitulo("Nota importante"));
        root.addView(parrafo(
                "Funciona en los campos de texto de Android. NO puede escribir dentro del "
                        + "invitado Wine de GameNative ni de otros emuladores que llevan su propio "
                        + "teclado: ahi el texto no pasa por los teclados de Android."));

        // Version + actualizacion.
        root.addView(espaciador(dp(18)));
        TextView ver = parrafo("Version instalada: " + versionName());
        ver.setTextColor(0xFF8A9199);
        root.addView(ver);
        Button bUpd = boton("Buscar actualizacion");
        bUpd.setOnClickListener(v -> AppUpdater.comprobar(this, false));
        root.addView(bUpd);

        return scroll;
    }

    private void abrirAjustesTeclados() {
        try {
            startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
        } catch (Exception ignored) {
        }
    }

    private void mostrarSelector() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showInputMethodPicker();
        }
    }

    private String versionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName + " (" + info.versionCode + ")";
        } catch (Exception e) {
            return "?";
        }
    }

    // ---- Helpers de UI -------------------------------------------------

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    private TextView titulo(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(28);
        tv.setPadding(0, 0, 0, dp(10));
        return tv;
    }

    private TextView subtitulo(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextColor(0xFF13A10E);
        tv.setTextSize(18);
        tv.setPadding(0, dp(18), 0, dp(6));
        return tv;
    }

    private TextView parrafo(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextColor(0xFFD6DBDF);
        tv.setTextSize(15);
        tv.setLineSpacing(dp(3), 1f);
        return tv;
    }

    private Button boton(String t) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(0xFF107C10);
        b.setTextSize(16);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        lp.bottomMargin = dp(4);
        b.setLayoutParams(lp);
        b.setGravity(Gravity.CENTER);
        return b;
    }

    private View espaciador(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, h));
        return v;
    }
}
