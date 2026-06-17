# Guía de Compilación de Robin App para Producción

Esta guía detalla los pasos recomendados para generar un APK/AAB firmado y optimizado listo para su distribución.

---

## 🛠️ Requisitos Previos

Asegúrate de contar con las siguientes herramientas instaladas en tu máquina de desarrollo:
- **Java Development Kit (JDK) 11 o posterior**
- **Android Studio** (incluye SDK de Android y terminal)
- **Gradle** (gestionado a través del build system nativo)

---

## 1. 🔑 Generar un Almacén de Claves de Producción (Keystore)

Para firmar digitalmente la aplicación, se requiere un archivo de almacén de claves `.jks`. Puedes crearlo utilizando la herramienta `keytool` provista por la instalación de Java.

Abre tu terminal y ejecuta el siguiente comando:

```bash
keytool -genkeypair -v -keystore my-upload-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```

### 📝 Parámetros explicados:
- `-keystore my-upload-key.jks`: El nombre físico del archivo de almacén de claves generado.
- `-alias upload`: Nombre del alias de la clave (utilizado habitualmente para identificar la firma del canal de carga).
- `-validity 10000`: La validez en días del certificado de firma (aproximadamente 27 años).
- `-keysize 2048`: La fuerza de encriptación RSA.

> [!CAUTION]
> **SEGURIDAD CRÍTICA:** Guarda este archivo `my-upload-key.jks` y las contraseñas en un lugar seguro (por ejemplo, un gestor de secretos). Si pierdes este almacén de claves, no podrás publicar actualizaciones de tu aplicación en Google Play Store.

---

## 2. 🎛️ Configurar las Variables de Entorno para Compilación Segura

La configuración del script Gradle de Robin App en `app/build.gradle.kts` lee dinámicamente las credenciales desde variables de entorno para evitar almacenar secretos en el historial de Git.

Configura las siguientes variables en tu terminal de compilación o archivo local `.env`:

```bash
export KEYSTORE_PATH="/ruta/absoluta/a/tu/my-upload-key.jks"
export STORE_PASSWORD="tu_contraseña_del_keystore"
export KEY_ALIAS="upload"
export KEY_PASSWORD="tu_contraseña_de_la_clave"
```

### 🔄 Comportamiento Automático de Seguridad (Fallback)
Si las variables de entorno o el archivo `.jks` no están presentes en el sistema, el script `build.gradle.kts` de Robin App **realizará un fallback automático al almacén de claves de depuración (debug.keystore)**. Esto garantiza que las compilaciones nunca fallen repentinamente, manteniendo la fluidez del desarrollo.

---

## 3. 📦 Ejecutar Comando de Compilación en Producción

Una vez configuradas las variables de entorno, ejecuta el comando de Gradle correspondiente en el directorio raíz de la aplicación para generar los paquetes finales:

### Generar APK Firmado (Producción / Release)
```bash
gradle assembleRelease
```
El archivo de producción generado se ubicará en:
`app/build/outputs/apk/release/app-release.apk`

### Generar Android App Bundle (AAB para Google Play Store)
```bash
gradle bundleRelease
```
El paquete de distribución AAB optimizado se ubicará en:
`app/build/outputs/bundle/release/app-release.aab`

---

## ⚡ Formatos de Modelos Admitidos en Compilaciones Offline

La aplicación Robin App admite la descarga dinámica bajo demanda (On-Demand) de sistemas inteligentes offline. Los siguientes modelos son compatibles con este proceso:
- **Vosk STT Mini:** Procesamiento acústico local optimizado para bajo consumo de batería.
- **Whisper.cpp GGML-Tiny:** Decodificador de transformador local con soporte multilingüe.
- **Piper ONNX TTS:** Red de síntesis de voz neuronal de calidad humana con baja latencia.

Todos los modelos descargados bajo demanda se guardan y cargan localmente desde la memoria privada de la aplicación, garantizando privacidad total (100% offline).
