package com.meuapp.lembretes

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Widget da tela inicial: próxima dose pendente e o botão "Tomei". */
class WidgetDose : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, gerenciador: AppWidgetManager, ids: IntArray) {
        atualizar(ctx)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == ACAO_TOMEI) {
            Dados.carregar(ctx)
            val data = intent.getStringExtra(Agendador.EXTRA_DATA) ?: return
            val hora = intent.getStringExtra(Agendador.EXTRA_HORA) ?: return
            val ids = intent.getLongArrayExtra(EXTRA_IDS) ?: return
            ids.forEach { Dados.marcarTomado(ctx, it, data, hora, true) }
            atualizar(ctx)
        }
    }

    companion object {
        private const val ACAO_TOMEI = "com.meuapp.lembretes.WIDGET_TOMEI"
        private const val EXTRA_IDS = "ids"

        fun atualizar(ctx: Context) {
            val gerenciador = AppWidgetManager.getInstance(ctx) ?: return
            val ids = gerenciador.getAppWidgetIds(ComponentName(ctx, WidgetDose::class.java))
            if (ids.isEmpty()) return
            Dados.carregar(ctx)
            gerenciador.updateAppWidget(ids, montar(ctx))
        }

        private fun montar(ctx: Context): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_dose)
            val abrir = PendingIntent.getActivity(
                ctx, 1,
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            v.setOnClickPendingIntent(R.id.widget_raiz, abrir)

            val hoje = LocalDate.now().toString()
            val agora = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            val tomados = Dados.tomados.value
            val porHora = Dados.remedios.value
                .filter { it.ativoEm(hoje) }
                .flatMap { r -> r.horarios.map { h -> h to r } }
                .groupBy({ it.first }, { it.second })
                .toSortedMap()
            val total = porHora.values.sumOf { it.size }
            val feitas = porHora.entries.sumOf { (h, rs) -> rs.count { Dados.chave(it.id, hoje, h) in tomados } }
            val pendente = porHora.entries.firstOrNull { (h, rs) -> rs.any { Dados.chave(it.id, hoje, h) !in tomados } }

            val corApagado = ContextCompat.getColor(ctx, R.color.widget_apagado)
            val corAmbar = ContextCompat.getColor(ctx, R.color.widget_ambar)
            val corFolha = ContextCompat.getColor(ctx, R.color.widget_folha)
            v.setTextColor(R.id.widget_rotulo, corApagado)
            v.setViewVisibility(R.id.widget_tomei, View.GONE)
            v.setTextViewText(R.id.widget_progresso, if (total > 0) "$feitas de $total hoje" else "")

            when {
                porHora.isEmpty() -> {
                    v.setTextViewText(R.id.widget_rotulo, "Remédio da Hora")
                    v.setTextViewText(R.id.widget_hora, "")
                    v.setTextViewText(R.id.widget_nomes, "Nenhuma dose para hoje.")
                }

                pendente == null -> {
                    v.setTextViewText(R.id.widget_rotulo, "Doses de hoje")
                    v.setTextViewText(R.id.widget_hora, "✓")
                    v.setTextColor(R.id.widget_rotulo, corFolha)
                    val amanha = porHora.firstKey()
                    v.setTextViewText(R.id.widget_nomes, "Tudo tomado. Próxima amanhã às $amanha.")
                }

                else -> {
                    val h = pendente.key
                    val faltam = pendente.value.filter { Dados.chave(it.id, hoje, h) !in tomados }
                    val atrasada = h < agora
                    v.setTextViewText(R.id.widget_rotulo, if (atrasada) "Dose atrasada" else "Próxima dose")
                    if (atrasada) v.setTextColor(R.id.widget_rotulo, corAmbar)
                    v.setTextViewText(R.id.widget_hora, h)
                    v.setTextViewText(
                        R.id.widget_nomes,
                        faltam.joinToString("\n") { r -> if (r.dose.isBlank()) r.nome else "${r.nome}, ${r.dose}" }
                    )
                    val tomei = Intent(ctx, WidgetDose::class.java).apply {
                        action = ACAO_TOMEI
                        putExtra(Agendador.EXTRA_DATA, hoje)
                        putExtra(Agendador.EXTRA_HORA, h)
                        putExtra(EXTRA_IDS, faltam.map { it.id }.toLongArray())
                    }
                    val pi = PendingIntent.getBroadcast(
                        ctx, 2, tomei,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    v.setViewVisibility(R.id.widget_tomei, View.VISIBLE)
                    v.setTextViewText(R.id.widget_tomei, if (faltam.size > 1) "Tomei os ${faltam.size}" else "Tomei")
                    v.setOnClickPendingIntent(R.id.widget_tomei, pi)
                }
            }
            return v
        }
    }
}
