package com.carlauncher.service

import android.content.Context
import android.util.Log
import com.carlauncher.data.models.ParkingAlertConfig
import com.carlauncher.data.secrets.SecretsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Properties
import java.util.concurrent.TimeUnit
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * Sends a parking alert to a single channel. Implementations must be safe to call from
 * any dispatcher; they handle the actual I/O themselves.
 */
interface ParkingNotifier {
    /** Channel name used in logs. */
    val channelName: String

    /**
     * Send the alert. Returns true on success, false otherwise. Implementations must
     * never throw — they should log and return false on any failure.
     */
    suspend fun send(subject: String, body: String): Boolean
}

/**
 * Sends a parking alert to multiple channels. A channel failure does not stop the others.
 */
class CompositeNotifier(
    private val notifiers: List<ParkingNotifier>
) : ParkingNotifier {
    override val channelName: String = "composite"
    override suspend fun send(subject: String, body: String): Boolean {
        if (notifiers.isEmpty()) {
            Log.w(TAG, "No notifiers configured")
            return false
        }
        var anySuccess = false
        notifiers.forEach { n ->
            try {
                val ok = n.send(subject, body)
                if (ok) anySuccess = true
            } catch (e: Exception) {
                Log.e(TAG, "Notifier ${n.channelName} crashed", e)
            }
        }
        return anySuccess
    }

    companion object {
        private const val TAG = "CompositeNotifier"
    }
}

class TelegramNotifier(
    private val botToken: String,
    private val chatId: String
) : ParkingNotifier {

    override val channelName: String = "telegram"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun send(subject: String, body: String): Boolean = withContext(Dispatchers.IO) {
        if (botToken.isBlank() || chatId.isBlank()) {
            Log.w(TAG, "Telegram not configured (token=${botToken.isNotBlank()}, chatId=${chatId.isNotBlank()})")
            return@withContext false
        }
        try {
            val text = buildString {
                if (subject.isNotBlank()) {
                    append("🚨 ").append(subject).append("\n\n")
                }
                append(body)
            }
            val payload = "{\"chat_id\":\"${chatId.escapeJson()}\",\"text\":${text.toJsonString()},\"parse_mode\":\"HTML\"}"
            val request = Request.Builder()
                .url("https://api.telegram.org/bot$botToken/sendMessage")
                .post(payload.toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    Log.e(TAG, "Telegram API HTTP ${resp.code}: ${body.take(300)}")
                    return@withContext false
                }
                Log.i(TAG, "Telegram alert sent (chat=$chatId)")
                return@withContext true
            }
        } catch (e: Exception) {
            val rootCause = generateSequence<Throwable>(e) { it.cause }.last()
            Log.e(
                TAG,
                "Telegram send failed → ${e.javaClass.simpleName}: ${rootCause.message ?: "(no message)"}",
                e
            )
            return@withContext false
        }
    }

    private fun String.escapeJson(): String =
        replace("\\", "\\\\").replace("\"", "\\\"")

    private fun String.toJsonString(): String {
        val sb = StringBuilder("\"")
        forEach { ch ->
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch < ' ') sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
            }
        }
        sb.append("\"")
        return sb.toString()
    }

    companion object {
        private const val TAG = "TelegramNotifier"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}

class EmailNotifier(
    private val smtpHost: String,
    private val smtpPort: Int,
    private val smtpUser: String,
    private val smtpPassword: String,
    private val recipient: String
) : ParkingNotifier {

    override val channelName: String = "email"

    override suspend fun send(subject: String, body: String): Boolean = withContext(Dispatchers.IO) {
        if (smtpHost.isBlank() || smtpUser.isBlank() || smtpPassword.isBlank() || recipient.isBlank()) {
            Log.w(TAG, "SMTP not fully configured (host=${smtpHost.isNotBlank()} user=${smtpUser.isNotBlank()} " +
                "pass=${smtpPassword.isNotBlank()} recipient=${recipient.isNotBlank()})")
            return@withContext false
        }
        try {
            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.starttls.required", "true")
                put("mail.smtp.host", smtpHost)
                put("mail.smtp.port", smtpPort.toString())
                put("mail.smtp.ssl.trust", smtpHost)
                put("mail.smtp.connectiontimeout", "10000")
                put("mail.smtp.timeout", "15000")
                put("mail.smtp.writetimeout", "15000")
            }
            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication() =
                    PasswordAuthentication(smtpUser, smtpPassword)
            })
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(smtpUser))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient))
                setSubject(subject)
                setText(body)
            }
            Transport.send(message)
            Log.i(TAG, "Email alert sent to $recipient via $smtpHost:$smtpPort")
            true
        } catch (e: Exception) {
            val rootCause = generateSequence<Throwable>(e) { it.cause }.last()
            val smtpReply = extractSmtpReply(e)
            val summary = buildString {
                append("SMTP send failed to $smtpHost:$smtpPort as $smtpUser → ")
                append(e.javaClass.simpleName)
                if (smtpReply != null) {
                    append(" [SMTP $smtpReply]")
                } else {
                    append(": ").append(rootCause.message ?: "(no message)")
                }
            }
            Log.e(TAG, summary, e)
            false
        }
    }

    /**
     * Walk the exception chain looking for a Jakarta Mail SMTP reply string
     * like "534-5.7.9 Application-specific password required...". This is the
     * most actionable piece of info for users debugging their config.
     */
    private fun extractSmtpReply(t: Throwable): String? {
        var cur: Throwable? = t
        while (cur != null) {
            val msg = cur.message
            if (msg != null) {
                val match = Regex("""^(\d{3})([ -]\d+\.\d+\.\d+)?\s*(.*)""", RegexOption.MULTILINE)
                    .find(msg)
                if (match != null && match.groupValues[1].toIntOrNull() in 400..599) {
                    return match.value.lineSequence().first().trim()
                }
            }
            cur = cur.cause
        }
        return null
    }

    companion object {
        private const val TAG = "EmailNotifier"
    }
}

/**
 * Build the right [ParkingNotifier] chain for the current [ParkingAlertConfig]. Returns
 * null when the feature is disabled or no channel is selected.
 */
object ParkingNotifierFactory {
    fun build(
        @Suppress("UNUSED_PARAMETER") context: Context,
        config: ParkingAlertConfig,
        secrets: SecretsStore
    ): ParkingNotifier? {
        if (!config.enabled) return null
        val notifiers = mutableListOf<ParkingNotifier>()
        if (config.sendTelegram) {
            val token = secrets.getTelegramBotToken().orEmpty()
            if (token.isNotBlank() && config.telegramChatId.isNotBlank()) {
                notifiers.add(TelegramNotifier(token, config.telegramChatId))
            } else {
                Log.w("ParkingNotifierFactory", "Telegram selected but missing token/chatId")
            }
        }
        if (config.sendEmail) {
            val pass = secrets.getSmtpPassword().orEmpty()
            if (pass.isNotBlank() && config.smtpUser.isNotBlank() && config.smtpHost.isNotBlank()) {
                notifiers.add(
                    EmailNotifier(
                        smtpHost = config.smtpHost,
                        smtpPort = config.smtpPort,
                        smtpUser = config.smtpUser,
                        smtpPassword = pass,
                        recipient = config.smtpRecipient.ifBlank { config.smtpUser }
                    )
                )
            } else {
                Log.w("ParkingNotifierFactory", "Email selected but missing credentials")
            }
        }
        if (notifiers.isEmpty()) return null
        return CompositeNotifier(notifiers)
    }
}
