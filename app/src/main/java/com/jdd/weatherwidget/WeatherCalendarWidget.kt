package com.jdd.weatherwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.provider.CalendarContract
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class WeatherCalendarWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onEnabled(context: Context) {
        WidgetUpdateWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        WidgetUpdateWorker.cancel(context)
    }

    companion object {

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int
        ) {
            // Primero mostrar hora/fecha de inmediato (sin esperar red)
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            val now = Date()
            val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            val dateFmt = SimpleDateFormat("EEE d MMM", Locale("es", "CL"))
            views.setTextViewText(R.id.widget_time, timeFmt.format(now))
            views.setTextViewText(R.id.widget_date, dateFmt.format(now))
            appWidgetManager.updateAppWidget(widgetId, views)

            // Luego buscar clima y eventos en background
            CoroutineScope(Dispatchers.IO).launch {
                val weather = fetchWeather(context)
                val events = fetchNextEvents(context)
                val updateTime = timeFmt.format(Date())

                withContext(Dispatchers.Main) {
                    val updatedViews = RemoteViews(context.packageName, R.layout.widget_layout)

                    // Hora y fecha (actualizar de nuevo)
                    updatedViews.setTextViewText(R.id.widget_time, timeFmt.format(Date()))
                    updatedViews.setTextViewText(R.id.widget_date, dateFmt.format(Date()))

                    // Clima
                    if (weather != null) {
                        updatedViews.setTextViewText(R.id.widget_temp, "${weather.temp}°")
                        updatedViews.setTextViewText(R.id.widget_condition, weather.condition)
                    } else {
                        updatedViews.setTextViewText(R.id.widget_temp, "--°")
                        updatedViews.setTextViewText(R.id.widget_condition, "Sin datos")
                    }

                    // Eventos
                    when {
                        events.isEmpty() -> {
                            updatedViews.setTextViewText(R.id.widget_event1, "📅 Sin eventos hoy")
                            updatedViews.setViewVisibility(R.id.widget_event2, View.GONE)
                        }
                        events.size == 1 -> {
                            updatedViews.setTextViewText(R.id.widget_event1, "📅 ${events[0]}")
                            updatedViews.setViewVisibility(R.id.widget_event2, View.GONE)
                        }
                        else -> {
                            updatedViews.setTextViewText(R.id.widget_event1, "📅 ${events[0]}")
                            updatedViews.setTextViewText(R.id.widget_event2, "📅 ${events[1]}")
                            updatedViews.setViewVisibility(R.id.widget_event2, View.VISIBLE)
                        }
                    }

                    updatedViews.setTextViewText(R.id.widget_updated, "Act: $updateTime")
                    appWidgetManager.updateAppWidget(widgetId, updatedViews)
                }
            }
        }

        private suspend fun fetchWeather(context: Context): WeatherData? {
            return try {
                val lm = context.getSystemService(Context.LOCATION_SERVICE)
                        as android.location.LocationManager
                val location =
                    lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                        ?: lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                        ?: return null

                val url = "https://api.open-meteo.com/v1/forecast" +
                        "?latitude=${location.latitude}&longitude=${location.longitude}" +
                        "&current=temperature_2m,weathercode&timezone=auto"

                val json = JSONObject(URL(url).readText())
                val current = json.getJSONObject("current")
                val temp = current.getDouble("temperature_2m").toInt()
                val code = current.getInt("weathercode")
                WeatherData(temp, weatherCodeToString(code))
            } catch (e: Exception) {
                null
            }
        }

        private fun fetchNextEvents(context: Context): List<String> {
            val events = mutableListOf<String>()
            return try {
                val now = System.currentTimeMillis()
                val end = now + (24 * 60 * 60 * 1000L)
                val projection = arrayOf(
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.ALL_DAY
                )
                val selection =
                    "${CalendarContract.Events.DTSTART} >= ? AND " +
                    "${CalendarContract.Events.DTSTART} <= ? AND " +
                    "${CalendarContract.Events.DELETED} = 0"

                context.contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    projection,
                    selection,
                    arrayOf(now.toString(), end.toString()),
                    "${CalendarContract.Events.DTSTART} ASC"
                )?.use { cursor ->
                    while (cursor.moveToNext() && events.size < 2) {
                        val title = cursor.getString(0) ?: return@use
                        val start = cursor.getLong(1)
                        val allDay = cursor.getInt(2) == 1
                        val label = if (allDay) "Todo el día · $title"
                        else "${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(start))} · $title"
                        events.add(label)
                    }
                }
                events
            } catch (e: Exception) {
                events
            }
        }

        private fun weatherCodeToString(code: Int): String = when (code) {
            0 -> "☀️ Despejado"
            1 -> "🌤️ Mayormente despejado"
            2 -> "⛅ Parcial nublado"
            3 -> "☁️ Nublado"
            45, 48 -> "🌫️ Neblina"
            51, 53, 55 -> "🌦️ Llovizna"
            61, 63 -> "🌧️ Lluvia"
            65 -> "🌧️ Lluvia intensa"
            71, 73, 75 -> "❄️ Nieve"
            80, 81, 82 -> "🌦️ Chubascos"
            95 -> "⛈️ Tormenta"
            else -> "🌡️ Variable"
        }
    }

    data class WeatherData(val temp: Int, val condition: String)
}
