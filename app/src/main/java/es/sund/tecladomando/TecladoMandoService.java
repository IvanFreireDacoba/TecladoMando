package es.sund.tecladomando;

import android.inputmethodservice.InputMethodService;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

/**
 * IME "Teclado Mando": teclado en pantalla estilo Xbox (QWERTY) navegable
 * tanto con mando (D-pad/stick + botones) como con el dedo.
 *
 * Los eventos de tecla hardware llegan primero al IME (onKeyDown/onKeyUp):
 * devolvemos true para consumir los que manejamos. El D-pad de muchos mandos
 * (incluido el Retroid) llega como eje HAT en vez de como tecla, asi que
 * tambien tratamos onGenericMotionEvent.
 */
public class TecladoMandoService extends InputMethodService implements TecladoView.Listener {

    private TecladoView view;

    // Debounce de ejes: solo un paso por cruce de umbral.
    private int lastHatX = 0;
    private int lastHatY = 0;
    private static final float AXIS_THRESHOLD = 0.5f;

    @Override
    public View onCreateInputView() {
        view = new TecladoView(this, this);
        return view;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (view != null) {
            // Para recibir eventos de movimiento (ejes) cuando el foco venga
            // dirigido a la vista del IME.
            view.setFocusableInTouchMode(true);
            view.requestFocus();
        }
        lastHatX = 0;
        lastHatY = 0;
    }

    // ---- Salida de texto ----------------------------------------------

    @Override
    public void onAction(int action, String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return;
        }
        switch (action) {
            case TecladoView.A_CHAR:
                if (text != null) {
                    ic.commitText(text, 1);
                }
                break;
            case TecladoView.A_SPACE:
                ic.commitText(" ", 1);
                break;
            case TecladoView.A_BACKSPACE:
                // Si hay seleccion, borrarla; si no, un caracter hacia atras.
                CharSequence sel = ic.getSelectedText(0);
                if (sel != null && sel.length() > 0) {
                    ic.commitText("", 1);
                } else {
                    ic.deleteSurroundingText(1, 0);
                }
                break;
            case TecladoView.A_ENTER:
                enviarIntro(ic);
                break;
            default:
                break;
        }
    }

    private void enviarIntro(InputConnection ic) {
        EditorInfo editor = getCurrentInputEditorInfo();
        if (editor != null) {
            int imeAction = editor.imeOptions & EditorInfo.IME_MASK_ACTION;
            boolean noEnterAction = (editor.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;
            if (!noEnterAction && imeAction != EditorInfo.IME_ACTION_NONE
                    && imeAction != EditorInfo.IME_ACTION_UNSPECIFIED) {
                ic.performEditorAction(imeAction);
                return;
            }
        }
        // Campo multilinea o sin accion definida: un Enter normal.
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
    }

    // ---- Teclas hardware del mando ------------------------------------

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // SOLO capturar el mando cuando el teclado esta VISIBLE. `view` se crea una
        // vez y queda no-null para siempre aunque el teclado este oculto, asi que
        // guardar por `view == null` hacia que el IME se tragara los botones del
        // mando en TODO el sistema (p.ej. navegar ES-DE dejaba de funcionar).
        if (view == null || !isInputViewShown()) {
            return super.onKeyDown(keyCode, event);
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                view.moveSelection(TecladoView.DIR_UP);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                view.moveSelection(TecladoView.DIR_DOWN);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                view.moveSelection(TecladoView.DIR_LEFT);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                view.moveSelection(TecladoView.DIR_RIGHT);
                return true;

            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER: // por si el mando mapea A a Enter
                view.activateSelected();
                return true;

            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BUTTON_X:
                onAction(TecladoView.A_BACKSPACE, null);
                return true;

            case KeyEvent.KEYCODE_BUTTON_Y:
                onAction(TecladoView.A_SPACE, null);
                return true;

            case KeyEvent.KEYCODE_BUTTON_L1:
                view.toggleShift();
                return true;

            case KeyEvent.KEYCODE_BUTTON_R1:
                view.toggleSymbols();
                return true;

            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_MENU:
                onAction(TecladoView.A_ENTER, null);
                return true;

            case KeyEvent.KEYCODE_BUTTON_SELECT:
                // Select cierra el teclado (peticion del usuario): forma comoda de
                // ocultarlo con el mando sin tocar la pantalla.
                requestHideSelf(0);
                return true;

            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // Solo con el teclado visible (ver onKeyDown): si no, dejar pasar el mando
        // a la app de debajo (ES-DE, juegos, etc.).
        if (!isInputViewShown()) {
            return super.onKeyUp(keyCode, event);
        }
        // Consumir el up de las mismas teclas que consumimos en down para que
        // no se filtren a la app de debajo.
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BUTTON_X:
            case KeyEvent.KEYCODE_BUTTON_Y:
            case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_BUTTON_R1:
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_BUTTON_SELECT:
            case KeyEvent.KEYCODE_MENU:
                return true;
            default:
                return super.onKeyUp(keyCode, event);
        }
    }

    // ---- D-pad como eje HAT / stick izquierdo -------------------------

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (handleMotion(event)) {
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    /** Lee HAT_X/Y (y el stick izquierdo) y mueve la seleccion con debounce. */
    public boolean handleMotion(MotionEvent event) {
        // Solo con el teclado visible (ver onKeyDown): si no, no tocar el mando.
        if (view == null || !isInputViewShown()) {
            return false;
        }
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK
                && (event.getSource() & InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD
                && (event.getSource() & InputDevice.SOURCE_DPAD) != InputDevice.SOURCE_DPAD) {
            return false;
        }

        float hx = event.getAxisValue(MotionEvent.AXIS_HAT_X);
        float hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
        // Si no hay hat, usar el stick izquierdo.
        if (hx == 0 && hy == 0) {
            hx = event.getAxisValue(MotionEvent.AXIS_X);
            hy = event.getAxisValue(MotionEvent.AXIS_Y);
        }

        int dirX = (hx <= -AXIS_THRESHOLD) ? -1 : (hx >= AXIS_THRESHOLD ? 1 : 0);
        int dirY = (hy <= -AXIS_THRESHOLD) ? -1 : (hy >= AXIS_THRESHOLD ? 1 : 0);

        boolean moved = false;
        // Un paso por cruce de umbral: solo actuar cuando cambia respecto al
        // ultimo estado (y no estaba ya en esa direccion).
        if (dirX != lastHatX) {
            if (dirX == -1) {
                view.moveSelection(TecladoView.DIR_LEFT);
                moved = true;
            } else if (dirX == 1) {
                view.moveSelection(TecladoView.DIR_RIGHT);
                moved = true;
            }
            lastHatX = dirX;
        }
        if (dirY != lastHatY) {
            if (dirY == -1) {
                view.moveSelection(TecladoView.DIR_UP);
                moved = true;
            } else if (dirY == 1) {
                view.moveSelection(TecladoView.DIR_DOWN);
                moved = true;
            }
            lastHatY = dirY;
        }
        return moved || dirX != 0 || dirY != 0;
    }
}
