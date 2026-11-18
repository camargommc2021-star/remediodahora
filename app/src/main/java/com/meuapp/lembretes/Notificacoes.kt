package com.meuapp.lembretes

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notificacoes {
    private const val CANAL_REMEDIOS = "remedios"
    private const val CANAL_AGENDA = "agenda"

    fun criarCanais(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CANAL_REMEDIOS, "Remédios", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Hora de tomar o remédio"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CANAL_AGENDA, "Agenda", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Avisos de compromissos"
            }
        )
    }

    private fun abrirApp(ctx: Context): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun podeNotificar(ctx: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun mostrarRemedio(ctx: Context, r: Remedio, data: String, hora: String) {
        if (!podeNotificar(ctx)) return
        val codigo = Agendador.codigoRemedio(r.id, hora)
        val tomei = Intent(ctx, AlarmReceiver::class.java).apply {
            action = Agendador.ACAO_TOMEI
            putExtra(Agendador.EXTRA_ID, r.id)
            putExtra(Agendador.EXTRA_HORA, hora)
            putExtra(Agendador.EXTRA_DATA, data)
        }
        val piTomei = PendingIntent.getBroadcast(
            ctx, codigo, tomei,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val texto = if (r.dose.isBlank()) "Horário das $hora" else "${r.dose} • $hora"
        val n = NotificationCompat.Builder(ctx, CANAL_REMEDIOS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Hora do remédio: ${r.nome}")
            .setContentText(texto)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(abrirApp(ctx))
            .setAutoCancel(true)
            .addAction(0, "✓ Tomei", piTomei)
            .build()
        NotificationManagerCompat.from(ctx).notify(codigo, n)
    }

    fun mostrarCompromisso(ctx: Context, c: Compromisso) {
        if (!podeNotificar(ctx)) return
        val quando = when (c.avisoMin) {
            0 -> "Agora, às ${c.hora}"
            in 1..59 -> "Em ${c.avisoMin} min, às ${c.hora}"
            in 60..1439 -> "Em ${c.avisoMin / 60} h, às ${c.hora}"
            else -> "Amanhã às ${c.hora}"
        }
        val texto = if (c.obs.isBlank()) quando else "$quando • ${c.obs}"
        val n = NotificationCompat.Builder(ctx, CANAL_AGENDA)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(c.titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setContentIntent(abrirApp(ctx))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(Agendador.codigoCompromisso(c.id), n)
    }

    fun cancelar(ctx: Context, codigo: Int) {
        NotificationManagerCompat.from(ctx).cancel(codigo)
    }
}
