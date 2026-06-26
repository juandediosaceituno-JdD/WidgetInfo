# WeatherWidget 🌤️

Widget Android 4x2 que muestra hora, clima y próximos eventos del calendario.

## Características
- **Hora y fecha** en tiempo real (TextClock, sin código extra)
- **Clima** via Open-Meteo (gratis, sin API key) usando GPS del dispositivo
- **Próximos eventos** de Google Calendar (próximas 24h, hasta 2 eventos)
- Updates confiables via **WorkManager** cada 30 min (funciona en Samsung One UI / Doze)

## Setup del proyecto

### 1. Clonar y abrir
```bash
git clone https://github.com/TU_USUARIO/WeatherWidget.git
```
Abrir en **Android Studio** (la apertura genera automáticamente el Gradle wrapper).

### 2. Generar el Gradle wrapper (si no usas Android Studio)
```bash
gradle wrapper --gradle-version 8.4
git add gradle/ gradlew gradlew.bat
git commit -m "Add gradle wrapper"
git push
```

### 3. GitHub Actions
El workflow en `.github/workflows/build.yml` compila automáticamente al hacer push a `main`.

Para descargar el APK:
1. Ve a **Actions** → último run exitoso
2. Descarga el artifact `WeatherWidget-debug`

## Permisos requeridos
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` — para obtener clima según ubicación
- `READ_CALENDAR` — para leer eventos próximos

> **Importante**: Abrir la app principal al menos una vez para otorgar los permisos antes de agregar el widget.

## Estructura del proyecto
```
app/src/main/
├── java/com/jdd/weatherwidget/
│   ├── WeatherCalendarWidget.kt   # AppWidgetProvider principal
│   ├── WidgetUpdateWorker.kt      # WorkManager (updates confiables)
│   └── MainActivity.kt            # Solicita permisos en runtime
└── res/
    ├── layout/widget_layout.xml
    ├── xml/widget_info.xml
    └── drawable/widget_background.xml
```

## Personalización
- **Colores**: editar `widget_background.xml`
- **Rango de eventos**: cambiar `24 * 60 * 60 * 1000L` en `WeatherCalendarWidget.kt`
- **Frecuencia de update**: cambiar `30, TimeUnit.MINUTES` en `WidgetUpdateWorker.kt`
