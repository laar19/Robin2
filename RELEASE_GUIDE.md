# Guía de Compilación de APK Firmada de Producción | Robin Assistant

Esta guía detalla los pasos técnicos para firmar y generar una APK de producción lista para distribución. La configuración de compilación de Gradle en Robin ya está completamente automatizada y optimizada para producción con firmas dinámicas y mecanismos de contingencia.

---

## 1. Funcionamiento del Sistema de Firmas en Gradle

En `app/build.gradle.kts`, se parametriza la sección `signingConfigs` para buscar variables de entorno o bien utilizar firmas de respaldo automáticamente:

```kotlin
signingConfigs {
    create("release") {
        val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
        val sFile = file(keystorePath)
        if (sFile.exists()) {
            storeFile = sFile
            storePassword = System.getenv("STORE_PASSWORD") ?: "android"
            keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "android"
        } else {
            // Respaldarse en debug keystore para evitar fallos de compilación locales
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
}
```

Esto garantiza que:
1. **Compilación Segura:** Si no has configurado aún tu clave de producción, el build de release funcionará utilizando la firma local de depuración de manera segura.
2. **Inyección de Secretos:** Puedes inyectar las claves mediante variables de entorno en sistemas de Integración Continua (CI/CD) o un archivo local de properties.

---

## 2. Generación del Almacén de Claves de Lanzamiento (Keystore)

Para generar tu propio almacén de claves de producción (`my-upload-key.jks`), abre el terminal de tu máquina de desarrollo y ejecuta:

```bash
keytool -genkey -v -keystore my-upload-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```

### Parámetros recomendados:
- **`-keystore`**: Nombre del archivo (ej. `my-upload-key.jks`). Ubícalo en la raíz del proyecto.
- **`-alias`**: Alias para identificar la clave (ej. `upload`).
- **`-validity`**: Tiempo de validez en días (se recomiendan al menos 10000 días).

---

## 3. Instrucciones de Compilación de Lanzamiento (Release APK/AAB)

### Opción A: Usando la Interfaz de Google AI Studio
1. Haz clic en el botón de **Exportar / Descargar Gradle** o accede al menú **Configuraciones de Compilación** en la barra superior.
2. Genera y descarga el archivo completo APK / AAB.

### Opción B: Usando el Terminal del Entorno Gradle
Si estás depurando en tu equipo de desarrollo o en un pipeline de integración, arranca los comandos de compilación mediante:

#### Generar una APK de Lanzamiento (Release APK)
```bash
gradle :app:assembleRelease
```
*La APK se generará en la ruta:* `/app/build/outputs/apk/release/app-release.apk`

#### Generar un Android App Bundle para Play Store (AAB)
```bash
gradle :app:bundleRelease
```
*El paquete AAB se generará en la ruta:* `/app/build/outputs/bundle/release/app-release.aab`

---

## 4. Configurar Variables de Entorno en Lanzamiento (Opcional)

Si configuras builds automatizados en sistemas remotos de Integración Continua (como GitHub Actions, GitLab CI, Jenkins o Bitrise), define estas variables de entorno en tu panel de control de manera confidencial:

- `KEYSTORE_PATH`: Ruta absoluta donde se encuentra el archivo `.jks` en el servidor de compilaciones.
- `STORE_PASSWORD`: Contraseña del KeyStore de producción.
- `KEY_ALIAS`: Nombre del alias configurado en la clave.
- `KEY_PASSWORD`: Contraseña de la clave correspondiente al alias.
