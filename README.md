# Teclado Mando

Teclado de sistema (IME) para Android con distribución QWERTY al estilo Xbox, pensado para consolas portátiles
Android como las Retroid Pocket y para la tele: se maneja entero con el **mando** y también con el **táctil**.

## Controles

| Botón | Acción |
|---|---|
| Cruceta / stick | Moverse por las teclas |
| A | Pulsar la tecla |
| B / X | Borrar |
| Y | Espacio |
| L | Mayúsculas |
| R | Símbolos |
| Start | Intro |
| Select | Cerrar el teclado |

El teclado solo captura el mando **mientras está visible**; cerrado, los botones vuelven al resto del sistema
(frontends como ES-DE, emuladores y juegos).

## Instalación

1. Instala el APK (en Releases o en `https://sund.es/assets/tecladomando/TecladoMando.apk`).
2. Abre la app y sigue sus dos pasos: activar el teclado en los ajustes de Android y elegirlo como teclado actual.
3. La app comprueba sola si hay versión nueva.

No escribe dentro de Wine/GameNative: ningún teclado de Android puede hacerlo.

## Compilar

Requisitos: JDK 17, Android SDK (compileSdk 34) y Gradle 8.7 (AGP 8.6.0). `minSdk` 24.

```
gradle :app:assembleDebug
```

La firma usa `app/debug.keystore`, que **no** está en el repositorio. Para compilar tu propia versión genera un
keystore propio con el alias y las contraseñas de `app/build.gradle`, o cámbialos.

## Licencia

[MIT](LICENSE).
