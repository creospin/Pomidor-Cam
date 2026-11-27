package com.example.network

import com.example.data.LanProtocol
import com.example.data.PomidorSettings
import com.example.i18n.AppLocaleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.TimeUnit

data class LanDispatchResult(
    val success: Boolean,
    val message: String,
    val timestampMs: Long = System.currentTimeMillis()
)

class LanNotifier {

    private val client = OkHttpClient.Builder()
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .writeTimeout(3500, TimeUnit.MILLISECONDS)
        .readTimeout(3500, TimeUnit.MILLISECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun sendBreakSignal(
        settings: PomidorSettings,
        focusMinutes: Int = settings.focusDurationMinutes,
        isTest: Boolean = false
    ): LanDispatchResult = withContext(Dispatchers.IO) {
        val isRu = AppLocaleHelper.isRussian(settings.appLanguage)
        val breakMsg = if (isRu) {
            "Помидор (${focusMinutes} мин) закончился! Отдых ${settings.breakDurationMinutes} минут."
        } else {
            "Pomodoro (${focusMinutes} min) completed! Break for ${settings.breakDurationMinutes} minutes."
        }
        val payload = JSONObject().apply {
            put("app", "Pomidor Cam")
            put("event", "pomodoro_completed")
            put("is_test", isTest)
            put("focus_minutes", focusMinutes)
            put("break_minutes", settings.breakDurationMinutes)
            put("timestamp_ms", System.currentTimeMillis())
            put("message", breakMsg)
        }.toString()

        val errors = mutableListOf<String>()
        val successes = mutableListOf<String>()

        if (settings.lanProtocol == LanProtocol.HTTP_AND_UDP || settings.lanProtocol == LanProtocol.HTTP_ONLY) {
            val host = settings.pcHost.trim()
            if (host.isBlank()) {
                errors.add(if (isRu) "IP ПК не указан" else "PC IP not set")
            } else {
                val url = "http://$host:${settings.pcPort}/pomodoro/break"
                val httpRes = postJson(url, payload)
                if (httpRes.isSuccess) {
                    successes.add("HTTP → $host:${settings.pcPort}")
                } else {
                    errors.add("HTTP: ${httpRes.exceptionOrNull()?.message ?: if (isRu) "ошибка" else "error"}")
                }
            }
        }

        if (settings.lanProtocol == LanProtocol.HTTP_AND_UDP ||
            settings.lanProtocol == LanProtocol.UDP_UNICAST ||
            settings.lanProtocol == LanProtocol.UDP_BROADCAST
        ) {
            val targetHost = if (settings.lanProtocol == LanProtocol.UDP_BROADCAST) {
                "255.255.255.255"
            } else {
                settings.pcHost.trim().ifBlank { "255.255.255.255" }
            }
            val udpRes = sendUdp(
                host = targetHost,
                port = settings.pcPort,
                payload = payload,
                broadcast = targetHost == "255.255.255.255"
            )
            if (udpRes.isSuccess) {
                successes.add("UDP → $targetHost:${settings.pcPort}")
            } else {
                errors.add("UDP: ${udpRes.exceptionOrNull()?.message ?: if (isRu) "ошибка" else "error"}")
            }
        }

        if (successes.isNotEmpty()) {
            val joined = successes.joinToString(", ")
            LanDispatchResult(
                success = true,
                message = if (isRu) "Сигнал отправлен ($joined)" else "Signal sent ($joined)"
            )
        } else {
            val fallbackErr = if (isRu) "Не удалось отправить сигнал" else "Failed to send signal"
            LanDispatchResult(
                success = false,
                message = errors.joinToString(" | ").ifBlank { fallbackErr }
            )
        }
    }

    suspend fun pingPc(settings: PomidorSettings): LanDispatchResult = withContext(Dispatchers.IO) {
        val isRu = AppLocaleHelper.isRussian(settings.appLanguage)
        val host = settings.pcHost.trim()
        if (host.isBlank()) {
            return@withContext LanDispatchResult(
                false,
                if (isRu) "Укажите локальный IP компьютера" else "Enter your PC's local IP address"
            )
        }
        val url = "http://$host:${settings.pcPort}/pomodoro/ping"
        val req = Request.Builder().url(url).get().build()
        runCatching {
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    LanDispatchResult(
                        true,
                        if (isRu) "ПК на связи ($host:${settings.pcPort})" else "PC online ($host:${settings.pcPort})"
                    )
                } else {
                    LanDispatchResult(false, "HTTP ${resp.code}: $body")
                }
            }
        }.getOrElse { e ->
            LanDispatchResult(
                false,
                if (isRu) {
                    "Нет ответа от $host:${settings.pcPort} (${e.message})"
                } else {
                    "No response from $host:${settings.pcPort} (${e.message})"
                }
            )
        }
    }

    suspend fun sendWorkdayReport(
        settings: PomidorSettings,
        workdayDate: String,
        reportJson: String
    ): LanDispatchResult = withContext(Dispatchers.IO) {
        val isRu = AppLocaleHelper.isRussian(settings.appLanguage)
        val host = settings.pcHost.trim()
        if (host.isBlank()) {
            return@withContext LanDispatchResult(
                false,
                if (isRu) {
                    "Укажите локальный IP компьютера для выгрузки отчёта"
                } else {
                    "Enter your PC's local IP to export the report"
                }
            )
        }
        val url = "http://$host:${settings.pcPort}/pomodoro/report"
        postJson(url, reportJson).fold(
            onSuccess = { body ->
                val savedNote = runCatching {
                    val obj = JSONObject(body)
                    obj.optString("md_file").ifBlank { obj.optString("json_file") }
                }.getOrDefault("")
                val msg = if (savedNote.isNotBlank()) {
                    if (isRu) {
                        "Отчёт за $workdayDate выгружен на ПК: $savedNote"
                    } else {
                        "Report for $workdayDate exported to PC: $savedNote"
                    }
                } else {
                    if (isRu) {
                        "Отчёт за $workdayDate успешно выгружен на $host:${settings.pcPort}"
                    } else {
                        "Report for $workdayDate exported to $host:${settings.pcPort}"
                    }
                }
                LanDispatchResult(true, msg)
            },
            onFailure = { e ->
                LanDispatchResult(
                    false,
                    if (isRu) {
                        "Ошибка выгрузки на $host:${settings.pcPort}: ${e.message}"
                    } else {
                        "Export failed to $host:${settings.pcPort}: ${e.message}"
                    }
                )
            }
        )
    }

    private fun postJson(url: String, json: String): Result<String> = runCatching {
        val body = json.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val respBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("HTTP ${response.code}")
            }
            respBody
        }
    }

    private fun sendUdp(
        host: String,
        port: Int,
        payload: String,
        broadcast: Boolean
    ): Result<Unit> = runCatching {
        val bytes = payload.toByteArray(Charsets.UTF_8)
        DatagramSocket().use { socket ->
            socket.broadcast = broadcast
            socket.soTimeout = 2000
            val address = InetAddress.getByName(host)
            val packet = DatagramPacket(bytes, bytes.size, address, port)
            socket.send(packet)
        }
    }
}
