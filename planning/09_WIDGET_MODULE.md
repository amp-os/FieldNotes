# FieldNotes — Widget Module

## Overview

A home screen widget with two buttons — **Field Rec** and **Voice Note** — that immediately launch the recording service without opening the app UI. Built with Jetpack Glance (the modern Compose-based widget API).

## Why Glance?

Jetpack Glance provides a Compose-like API for App Widgets. It handles the RemoteViews complexity and is the recommended approach for API 26+. The Pixel 8 (Android 16) supports it fully.

## Widget design

```
┌──────────────────────────────────────────┐
│  FieldNotes                              │
│                                          │
│  [🎙  FIELD REC]    [📝  VOICE NOTE]    │
└──────────────────────────────────────────┘
```

- Minimum size: 4×1 cells (fills a typical bottom row)
- Resizable: yes (horizontal)
- Dark background matching the app theme
- Amber accent on button text

## `RecordWidget.kt`

```kotlin
class RecordWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            RecordWidgetContent()
        }
    }
}

@Composable
private fun RecordWidgetContent() {
    val context = LocalContext.current
    
    GlanceTheme {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFF0D0D0D))
                .cornerRadius(16)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Field Recording button
                Button(
                    text = "🎙 Field Rec",
                    onClick = actionStartActivity(
                        Intent(context, RecordWidgetActionActivity::class.java).apply {
                            action = RecordingService.ACTION_START_FIELD
                        }
                    ),
                    modifier = GlanceModifier.defaultWeight().padding(end = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = ColorProvider(Color(0xFF1A1A1A)),
                        contentColor = ColorProvider(Color(0xFFE8A838))
                    )
                )
                
                // Voice Note button
                Button(
                    text = "📝 Voice Note",
                    onClick = actionStartActivity(
                        Intent(context, RecordWidgetActionActivity::class.java).apply {
                            action = RecordingService.ACTION_START_VOICE
                        }
                    ),
                    modifier = GlanceModifier.defaultWeight().padding(start = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = ColorProvider(Color(0xFF1A1A1A)),
                        contentColor = ColorProvider(Color(0xFFE8A838))
                    )
                )
            }
        }
    }
}
```

## `RecordWidgetReceiver.kt`

```kotlin
@AndroidEntryPoint
class RecordWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = RecordWidget()
}
```

## `RecordWidgetActionActivity.kt`

A transparent, invisible Activity that starts the RecordingService and immediately finishes. This is needed because Glance widget buttons can't start a Service directly on Android 12+ (background start restrictions), but they can start an Activity that then starts the foreground service.

```kotlin
@AndroidEntryPoint
class RecordWidgetActionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val mode = when (intent.action) {
            RecordingService.ACTION_START_FIELD -> RecordingMode.FIELD
            RecordingService.ACTION_START_VOICE -> RecordingMode.VOICE_NOTE
            else -> null
        }
        
        if (mode != null) {
            val serviceIntent = Intent(this, RecordingService::class.java).apply {
                action = intent.action
            }
            startForegroundService(serviceIntent)
        }
        
        finish() // immediately close — no UI shown
    }
}
```

Note: Set `android:theme="@android:style/Theme.Translucent.NoTitleBar"` on this Activity in the manifest so it is invisible.

## AndroidManifest.xml entries

```xml
<!-- Widget receiver -->
<receiver
    android:name=".widget.RecordWidgetReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE"/>
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/record_widget_info"/>
</receiver>

<!-- Transparent action launcher -->
<activity
    android:name=".widget.RecordWidgetActionActivity"
    android:exported="false"
    android:theme="@android:style/Theme.Translucent.NoTitleBar"
    android:taskAffinity=""
    android:excludeFromRecents="true"/>
```

## `res/xml/record_widget_info.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="250dp"
    android:minHeight="40dp"
    android:targetCellWidth="4"
    android:targetCellHeight="1"
    android:updatePeriodMillis="0"
    android:initialLayout="@layout/glance_default_loading_layout"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen"
    android:description="@string/widget_description"/>
```

## Foreground service notification (while recording)

Once a recording starts from the widget, the user's only UI affordance is the notification. It must have a **Stop** button:

```
┌───────────────────────────────────────┐
│  ● FieldNotes    Field Recording      │
│  00:00:34                             │
│                           [Stop]      │
└───────────────────────────────────────┘
```

Tapping the notification opens the app (RecorderScreen). Tapping Stop ends the recording. For voice notes, the transcription flow opens in the app automatically when Stop is tapped.

## Quick Settings Tile (bonus, optional)

For even faster access, a Quick Settings Tile can be added. The user can drag it into their Quick Settings panel.

```kotlin
class RecordQuickSettingsTile : TileService() {
    override fun onClick() {
        // Start voice note recording (most common quick-capture use case)
        startForegroundService(
            Intent(this, RecordingService::class.java).apply {
                action = RecordingService.ACTION_START_VOICE
            }
        )
    }
    // ... updateTile() to show recording state
}
```

Declare in manifest:
```xml
<service
    android:name=".widget.RecordQuickSettingsTile"
    android:exported="true"
    android:label="@string/qs_tile_label"
    android:icon="@drawable/ic_mic"
    android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
    <intent-filter>
        <action android:name="android.service.quicksettings.action.QS_TILE"/>
    </intent-filter>
</service>
```

This is the **recommended quick-access method** for the user's Pixel 8 — it can be placed in the first row of quick settings and activated with a single pull-down + tap, even from the lock screen.
