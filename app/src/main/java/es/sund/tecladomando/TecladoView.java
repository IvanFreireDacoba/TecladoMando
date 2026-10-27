package es.sund.tecladomando;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Vista del teclado dibujada con Canvas. Mantiene su propia rejilla de teclas,
 * una seleccion resaltada (estilo Xbox) que se mueve con el mando, y traduce
 * tanto los toques como las pulsaciones del mando (via el Service) en acciones
 * de teclado. No habla con el InputConnection directamente: delega en el
 * listener (el Service), que es quien tiene acceso al campo de texto.
 */
public class TecladoView extends View {

    // Tipos de accion de una tecla.
    public static final int A_CHAR = 0;
    public static final int A_BACKSPACE = 1;
    public static final int A_SPACE = 2;
    public static final int A_SHIFT = 3;
    public static final int A_SYMBOLS = 4; // alterna a capa de simbolos
    public static final int A_ABC = 5;     // vuelve a capa de letras
    public static final int A_ENTER = 6;

    public interface Listener {
        void onAction(int action, String text);
    }

    public static final class Key {
        String label;   // lo que se dibuja
        String text;    // lo que se escribe (solo A_CHAR)
        int action;
        int row;
        final RectF rect = new RectF();

        Key(String label, String text, int action) {
            this.label = label;
            this.text = text;
            this.action = action;
        }
    }

    private Listener listener;

    // Teclas de la capa actualmente visible, ya posicionadas.
    private final List<Key> keys = new ArrayList<>();
    private int selected = 0;

    private boolean upper = false;   // mayusculas (shift)
    private boolean symbols = false; // capa de simbolos

    private final float density;

    // Pinturas.
    private final Paint pBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pKey = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pKeySpecial = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSelBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTextSel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pHint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Colores (tema oscuro, legible en movil y en TV).
    private static final int C_BG = 0xFF1E1E1E;
    private static final int C_KEY = 0xFF3A3A3A;
    private static final int C_KEY_SPECIAL = 0xFF2B2B2B;
    private static final int C_SEL = 0xFF107C10;        // verde Xbox
    private static final int C_SEL_BORDER = 0xFF13A10E;
    private static final int C_TEXT = 0xFFF2F2F2;
    private static final int C_TEXT_SEL = 0xFFFFFFFF;
    private static final int C_HINT = 0xFF9BA1A6;

    // Dimensiones (en px, calculadas desde densidad).
    private float rowHeight;
    private float hintHeight;
    private float gap;
    private float padTop;
    private float corner;

    public TecladoView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        this.density = dm.density;

        rowHeight = dp(52);
        hintHeight = dp(26);
        gap = dp(4);
        padTop = dp(6);
        corner = dp(7);

        pBg.setColor(C_BG);
        pKey.setColor(C_KEY);
        pKeySpecial.setColor(C_KEY_SPECIAL);
        pSel.setColor(C_SEL);
        pSelBorder.setColor(C_SEL_BORDER);
        pSelBorder.setStyle(Paint.Style.STROKE);
        pSelBorder.setStrokeWidth(dp(2.5f));

        pText.setColor(C_TEXT);
        pText.setTextAlign(Paint.Align.CENTER);
        pText.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));

        pTextSel.setColor(C_TEXT_SEL);
        pTextSel.setTextAlign(Paint.Align.CENTER);
        pTextSel.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));

        pHint.setColor(C_HINT);
        pHint.setTextAlign(Paint.Align.CENTER);
        pHint.setTextSize(dp(12));

        setFocusable(true);
        setFocusableInTouchMode(true);

        construirRejilla();
    }

    private float dp(float v) {
        return v * density;
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    // ---- Construccion de la capa actual --------------------------------

    /** Define las filas de la capa activa (letras o simbolos). */
    private String[][] currentRows() {
        if (symbols) {
            return new String[][]{
                    {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
                    {".", ",", ":", ";", "@", "#", "/", "?", "!", "'"},
                    {"\"", "(", ")", "-", "_", "+", "=", "*", "&", "⌫"},
                    {"ABC", "espacio", "↵"}
            };
        }
        return new String[][]{
                {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
                {"a", "s", "d", "f", "g", "h", "j", "k", "l"},
                {"z", "x", "c", "v", "b", "n", "m", "⌫"},
                {"⇧", "?123", "espacio", "↵"}
        };
    }

    private void construirRejilla() {
        keys.clear();
        String[][] rows = currentRows();
        for (int r = 0; r < rows.length; r++) {
            for (String cell : rows[r]) {
                Key k = keyFor(cell);
                k.row = r;
                keys.add(k);
            }
        }
        if (selected >= keys.size()) {
            selected = keys.size() - 1;
        }
        requestLayout();
        if (getWidth() > 0) {
            positionKeys(getWidth());
        }
        invalidate();
    }

    private Key keyFor(String cell) {
        switch (cell) {
            case "⌫":
                return new Key("⌫", null, A_BACKSPACE);
            case "espacio":
                return new Key("espacio", " ", A_SPACE);
            case "⇧":
                return new Key("⇧", null, A_SHIFT);
            case "?123":
                return new Key("?123", null, A_SYMBOLS);
            case "ABC":
                return new Key("ABC", null, A_ABC);
            case "↵":
                return new Key("↵", null, A_ENTER);
            default: {
                String shown = upper ? cell.toUpperCase() : cell;
                return new Key(shown, shown, A_CHAR);
            }
        }
    }

    private float weightOf(Key k) {
        switch (k.action) {
            case A_SPACE:
                return 4.0f;
            case A_SHIFT:
            case A_SYMBOLS:
            case A_ABC:
            case A_ENTER:
            case A_BACKSPACE:
                return 1.4f;
            default:
                return 1.0f;
        }
    }

    // ---- Medida y posicionado ------------------------------------------

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int numRows = currentRows().length;
        int height = (int) (padTop + numRows * rowHeight + (numRows + 1) * gap + hintHeight + dp(6));
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        positionKeys(w);
    }

    private void positionKeys(int viewWidth) {
        String[][] rows = currentRows();
        float sidePad = gap;
        // Agrupar por fila.
        int idx = 0;
        float y = padTop + gap;
        for (int r = 0; r < rows.length; r++) {
            int count = rows[r].length;
            // Suma de pesos de la fila.
            float totalWeight = 0f;
            for (int c = 0; c < count; c++) {
                totalWeight += weightOf(keys.get(idx + c));
            }
            float usable = viewWidth - 2 * sidePad - (count - 1) * gap;
            float x = sidePad;
            for (int c = 0; c < count; c++) {
                Key k = keys.get(idx + c);
                float kw = usable * (weightOf(k) / totalWeight);
                k.rect.set(x, y, x + kw, y + rowHeight);
                x += kw + gap;
            }
            idx += count;
            y += rowHeight + gap;
        }
        // Tamanos de texto en funcion de la altura de tecla.
        pText.setTextSize(rowHeight * 0.42f);
        pTextSel.setTextSize(rowHeight * 0.44f);
    }

    // ---- Dibujo ---------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), pBg);

        for (int i = 0; i < keys.size(); i++) {
            Key k = keys.get(i);
            boolean sel = (i == selected);
            Paint bg = sel ? pSel : (k.action == A_CHAR || k.action == A_SPACE ? pKey : pKeySpecial);
            canvas.drawRoundRect(k.rect, corner, corner, bg);
            if (sel) {
                canvas.drawRoundRect(k.rect, corner, corner, pSelBorder);
            }
            Paint tp = sel ? pTextSel : pText;
            // Resaltar shift activo / capa activa.
            if (!sel && ((k.action == A_SHIFT && upper) || (k.action == A_SYMBOLS && symbols))) {
                tp = pTextSel;
            }
            String label = k.label;
            float cx = k.rect.centerX();
            float cy = k.rect.centerY() - (tp.ascent() + tp.descent()) / 2f;
            canvas.drawText(label, cx, cy, tp);
        }

        // Barra de pistas de botones.
        String hint = "A escribir  ·  B borrar  ·  Y espacio  ·  LB mayus  ·  RB simbolos  ·  Start intro";
        float hy = getHeight() - hintHeight / 2f - (pHint.ascent() + pHint.descent()) / 2f;
        canvas.drawText(hint, getWidth() / 2f, hy, pHint);
    }

    // ---- Navegacion del mando ------------------------------------------

    public static final int DIR_UP = 0;
    public static final int DIR_DOWN = 1;
    public static final int DIR_LEFT = 2;
    public static final int DIR_RIGHT = 3;

    /** Mueve la seleccion a la tecla vecina mas cercana en la direccion dada. */
    public void moveSelection(int dir) {
        if (keys.isEmpty()) {
            return;
        }
        Key cur = keys.get(selected);
        float curX = cur.rect.centerX();
        float curY = cur.rect.centerY();

        int best = -1;
        float bestScore = Float.MAX_VALUE;
        for (int i = 0; i < keys.size(); i++) {
            if (i == selected) {
                continue;
            }
            Key k = keys.get(i);
            float dx = k.rect.centerX() - curX;
            float dy = k.rect.centerY() - curY;
            boolean ok;
            float primary, perp;
            switch (dir) {
                case DIR_LEFT:
                    ok = dx < -1;
                    primary = -dx;
                    perp = Math.abs(dy);
                    break;
                case DIR_RIGHT:
                    ok = dx > 1;
                    primary = dx;
                    perp = Math.abs(dy);
                    break;
                case DIR_UP:
                    ok = dy < -1;
                    primary = -dy;
                    perp = Math.abs(dx);
                    break;
                default: // DIR_DOWN
                    ok = dy > 1;
                    primary = dy;
                    perp = Math.abs(dx);
                    break;
            }
            if (!ok) {
                continue;
            }
            // Penalizar fuerte la desviacion perpendicular para no "saltar"
            // a teclas de otra columna/fila lejana.
            float score = primary + perp * 2.2f;
            if (score < bestScore) {
                bestScore = score;
                best = i;
            }
        }
        if (best >= 0) {
            selected = best;
            invalidate();
        }
    }

    /** Activa (escribe) la tecla seleccionada ahora mismo. */
    public void activateSelected() {
        if (selected >= 0 && selected < keys.size()) {
            fire(keys.get(selected));
        }
    }

    private void fire(Key k) {
        // Las teclas de cambio de capa las gestiona la propia vista; el resto
        // se delegan al listener (Service) para tocar el InputConnection.
        switch (k.action) {
            case A_SHIFT:
                toggleShift();
                break;
            case A_SYMBOLS:
            case A_ABC:
                toggleSymbols();
                break;
            default:
                if (listener != null) {
                    listener.onAction(k.action, k.text);
                }
                break;
        }
    }

    public void toggleShift() {
        upper = !upper;
        // Re-generar etiquetas de letras.
        rebuildLabels();
        invalidate();
    }

    public void toggleSymbols() {
        symbols = !symbols;
        selected = 0;
        construirRejilla();
    }

    private void rebuildLabels() {
        // Solo cambian las etiquetas de las teclas de caracter de letras.
        if (symbols) {
            return;
        }
        construirRejilla();
    }

    // ---- Toque ----------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            int hit = keyAt(event.getX(), event.getY());
            if (hit >= 0) {
                selected = hit;
                invalidate();
            }
            return true;
        } else if (action == MotionEvent.ACTION_UP) {
            int hit = keyAt(event.getX(), event.getY());
            if (hit >= 0) {
                selected = hit;
                fire(keys.get(hit));
                invalidate();
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    private int keyAt(float x, float y) {
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).rect.contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    // Permite que el Service entregue eventos de movimiento (hat/stick) a la
    // vista cuando el foco los dirige aqui.
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (listener instanceof TecladoMandoService) {
            return ((TecladoMandoService) listener).handleMotion(event);
        }
        return super.onGenericMotionEvent(event);
    }
}
