package com.jdd.weatherwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
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
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            CoroutineScope(Dispatchers.IO).launch {
                val weather = fetchWeather(context)
                val events = fetchNextEvents(context)
                val updateTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

                withContext(Dispatchers.Main) {
                    // Clima
                    if (weather != null) {
                        views.setTextViewText(R.id.widget_temp, "${weather.temp}°")
                        views.setTextViewText(R.id.widget_condition, weather.condition)
                    } else {
                        views.setTextViewText(R.id.widget_temp, "--°")
                        views.setTextViewText(R.id.widget_condition, "Sin datos")
                    }

                    // Eventos
                    when {
                        events.isEmpty() -> {
                            views.setTextViewText(R.id.widget_event1, "📅 Sin eventos hoy")
                            views.setViewVisibility(R.id.widget_event2, View.GONE)
                        }
                        events.size == 1 -> {
                            views.setTextViewText(R.id.widget_event1, "📅 ${events[0]}")
                            views.setViewVisibility(R.id.widget_event2, View.GONE)
                        }
                        else -> {
                            views.setTextViewText(R.id.widget_event1, "📅 ${events[0]}")
                            views.setTextViewText(R.id.widget_event2, "📅 ${events[1]}")
                            views.setViewVisibility(R.id.widget_event2, View.VISIBLE)
                        }
                    }

                    // Última actualización
                    views.setTextViewText(R.id.widget_updated, "Actualizado: $updateTime")

                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            }
        }

        private suspend fun fetchWeather(context: Context): WeatherData? {
            return try {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE)
                        as android.location.LocationManager

                val location =
                    locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                        ?: locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                        ?: return null

                val lat = location.latitude
                val lon = location.longitude

                val urlStr = "https://api.open-meteo.com/v1/forecast" +
                        "?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,weathercode" +
                        "&timezone=auto"

                val json = JSONObject(URL(urlStr).readText())
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
                // Próximas 24 horas
                val end = now + (24 * 60 * 60 * 1000L)

                val uri = CalendarContract.Events.CONTENT_URI
                val projection = arrayOf(
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.ALL_DAY
                )
                val selection =
                    "${CalendarContract.Events.DTSTART} >= ? AND " +
                    "${CalendarContract.Events.DTSTART} <= ? AND " +
                    "${CalendarContract.Events.DELETED} = 0"
                val selArgs = arrayOf(now.toString(), end.toString())
                val sortOrder = "${CalendarContract.Events.DTSTART} ASC"

                context.contentResolver.query(uri, projection, selection, selArgs, sortOrder)
                    ?.use { cursor ->
                        while (cursor.moveToNext() && events.size < 2) {
                            val title = cursor.getString(0) ?: return@use
                            val start = cursor.getLong(1)
                            val allDay = cursor.getInt(2) == 1

                            val label = if (allDay) {
                                "Todo el día · $title"
                            } else {
                                val time = SimpleDateFormat("HH:mm", Locale.getDefault())
                                    .format(Date(start))
                                "$time · $title"
                            }
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
            2 -> "⛅ Parcialmente nublado"
            3 -> "☁️ Nublado"
            45, 48 -> "🌫️ Neblina"
            51, 53, 55 -> "🌦️ Llovizna"
            61, 63 -> "🌧️ Lluvia moderada"
            65 -> "🌧️ Lluvia intensa"
            71, 73, 75 -> "❄️ Nieve"
            77 -> "🌨️ Nieve granulada"
            80, 81 -> "🌦️ Chubascos"
            82 -> "⛈️ Chubascos intensos"
            85, 86 -> "🌨️ Chubascos de nieve"
            95 -> "⛈️ Tormenta"
            96, 99 -> "⛈️ Tormenta con granizo"
            else -> "🌡️ Variable"
        }
    }

    data class WeatherData(val temp: Int, val condition: String)
}
