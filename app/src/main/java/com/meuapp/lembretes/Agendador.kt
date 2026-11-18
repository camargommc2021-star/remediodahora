package com.meuapp.lembretes

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Marca os alarmes no sistema Android (funcionam com o app fechado). */
object Agendador {
    const val ACAO_REMEDIO = "com.meuapp.lembretes.REMEDIO"
    const val ACAO_COMPROMISSO = "com.meuapp.lembretes.COMPROMISSO"
    const val ACAO_TOMEI = "com.meuapp.lembretes.TOMEI"

    const val EXTRA_ID = "id"
    const val EXTRA_HORA = "hora"
    const val EXTRA_DATA = "data"

    fun codigoRemedio(id: Long, hora: String) = "r$id$hora".hashCode()
    fun codigoCompromisso(id: Long) = "c$id".hashCode()

    fun agendarTudo(ctx: Context) {
        Dados.carregar(ctx)
        Dados.remedios.value.forEach { agendarRemedio(ctx, it) }
        Dados.compromissos.value.forEach { agendarCompromisso(ctx, it) }
    }

    // ---------- Remédios (todo dia, nos horários escolhidos) ----------

    fun agendarRemedio(ctx: Context, r: Remedio) {
        r.horarios.forEach { agendarHorario(ctx, r.id, it) }
    }

    fun agendarHorario(ctx: Context, id: Long, hora: String) {
        val t = LocalTime.parse(hora)
        var quando = LocalDateTime.of(LocalDate.now(), t)
        // margem de 30 s evita repetir o mesmo alarme se ele disparar um pouco adiantado
        if (!quando.isAfter(LocalDateTime.now().plusSeconds(30))) quando = quando.plusDays(1)
        val intent = Intent(ctx, AlarmReceiver::class.java).apply {
            action = ACAO_REMEDIO
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_HORA, hora)
        }
        marcar(ctx, codigoRemedio(id, hora), emMillis(quando), intent)
    }

    fun cancelarRemedio(ctx: Context, r: Remedio) {
        r.horarios.forEach { hora ->
            val intent = Intent(ctx, AlarmReceiver::class.java).apply { action = ACAO_REMEDIO }
            desmarcar(ctx, codigoRemedio(r.id, hora), intent)
        }
    }

    // ---------- Agenda ----------

    fun agendarCompromisso(ctx: Context, c: Compromisso) {
        val evento = LocalDateTime.of(LocalDate.parse(c.data), LocalTime.parse(c.hora))
        val aviso = evento.minusMinutes(c.avisoMin.toLong())
        if (!aviso.isAfter(LocalDateTime.now())) return
        val intent = Intent(ctx, AlarmReceiver::class.java).apply {
            action = ACAO_COMPROMISSO
            putExtra(EXTRA_ID, c.id)
        }
        marcar(ctx, codigoCompromisso(c.id), emMillis(aviso), intent)
    }

    fun cancelarCompromisso(ctx: Context, id: Long) {
        val intent = Intent(ctx, AlarmReceiver::class.java).apply { action = ACAO_COMPROMISSO }
        desmarcar(ctx, codigoCompromisso(id), intent)
    }

    // ---------- Base ----------

    private fun emMillis(t: LocalDateTime) =
        t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun marcar(ctx: Context, codigo: Int, quando: Long, intent: Intent) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, codigo, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val podeExato = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (podeExato) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, pi)
        }
    }

    private fun desmarcar(ctx: Context, codigo: Int, intent: Intent) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, codigo, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        am.cancel(pi)
        pi.cancel()
    }
}
