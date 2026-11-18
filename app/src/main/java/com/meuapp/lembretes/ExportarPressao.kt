package com.meuapp.lembretes

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Gera PDF (para o médico) ou planilha CSV com as medições de pressão e abre o "Compartilhar". */
object ExportarPressao {

    private val fmtData = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private fun dataBr(iso: String) = LocalDate.parse(iso).format(fmtData)

    /** dias = null para todas as medições. Devolve false se não houver medições no período. */
    fun compartilhar(ctx: Context, dias: Int?, pdf: Boolean): Boolean {
        val inicio = dias?.let { LocalDate.now().minusDays((it - 1).toLong()).toString() }
        val lista = Dados.medicoes.value
            .filter { inicio == null || it.data >= inicio }
            .sortedBy { it.quando }
        if (lista.isEmpty()) return false

        val pasta = File(ctx.cacheDir, "exportar").apply { mkdirs() }
        pasta.listFiles()?.forEach { it.delete() } // não acumula arquivos antigos
        val sufixo = LocalDate.now().toString()
        val arquivo = if (pdf) {
            File(pasta, "pressao-$sufixo.pdf").also { gerarPdf(ctx, lista, dias, it) }
        } else {
            File(pasta, "pressao-$sufixo.csv").also { gerarCsv(lista, it) }
        }

        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.arquivos", arquivo)
        val envio = Intent(Intent.ACTION_SEND).apply {
            type = if (pdf) "application/pdf" else "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Registro de pressão arterial")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(envio, "Enviar pressão"))
        return true
    }

    // ---------------- CSV (abre no Excel / Planilhas) ----------------

    private fun gerarCsv(lista: List<Medicao>, arquivo: File) {
        fun campo(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        val sb = StringBuilder("﻿") // BOM: o Excel reconhece os acentos
        sb.append("Data;Hora;Sistólica;Diastólica;Pulso;Classificação;Observação\r\n")
        lista.forEach { m ->
            sb.append(dataBr(m.data)).append(';')
                .append(m.hora).append(';')
                .append(m.sistolica).append(';')
                .append(m.diastolica).append(';')
                .append(m.pulso?.toString() ?: "").append(';')
                .append(campo(ClassificacaoPressao.faixas[ClassificacaoPressao.nivel(m.sistolica, m.diastolica)])).append(';')
                .append(campo(m.obs)).append("\r\n")
        }
        arquivo.writeText(sb.toString(), Charsets.UTF_8)
    }

    // ---------------- PDF ----------------

    private const val LARG = 595 // A4 em pontos
    private const val ALT = 842
    private const val MARGEM = 48f

    private val TINTA = Color.rgb(0x18, 0x3A, 0x37)
    private val APAGADO = Color.rgb(0x64, 0x77, 0x6F)
    private val LINHA = Color.rgb(0xDC, 0xE3, 0xE0)
    private val FOLHA = Color.rgb(0x2F, 0x7D, 0x5B)
    private val AMBAR = Color.rgb(0xB8, 0x74, 0x0C)
    private val CORAL = Color.rgb(0xD0, 0x61, 0x4E)
    private val VERMELHO = Color.rgb(0xB3, 0x26, 0x1E)

    private fun corNivel(n: Int) = when (n) {
        0, 1 -> FOLHA
        2 -> AMBAR
        3 -> CORAL
        else -> VERMELHO
    }

    private fun gerarPdf(ctx: Context, lista: List<Medicao>, dias: Int?, arquivo: File) {
        val titulo: Typeface = ResourcesCompat.getFont(ctx, R.font.bitter_bold) ?: Typeface.SERIF
        val normal: Typeface = ResourcesCompat.getFont(ctx, R.font.fira_regular) ?: Typeface.SANS_SERIF
        val medio: Typeface = ResourcesCompat.getFont(ctx, R.font.fira_medium) ?: Typeface.SANS_SERIF

        fun pincel(tf: Typeface, tam: Float, cor: Int = TINTA) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf; textSize = tam; color = cor
        }

        val doc = PdfDocument()
        var numPagina = 0
        lateinit var pagina: PdfDocument.Page
        lateinit var c: Canvas
        var y = 0f

        fun rodape() {
            val p = pincel(normal, 7.5f, APAGADO)
            c.drawText(
                "Gerado pelo app Remédio da Hora em " +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")) + ".",
                MARGEM, ALT - 36f, p
            )
            c.drawText(
                "Classificação conforme a Diretriz Brasileira de Hipertensão (SBC 2020). Não substitui a avaliação médica.",
                MARGEM, ALT - 26f, p
            )
            val num = "Página $numPagina"
            c.drawText(num, LARG - MARGEM - p.measureText(num), ALT - 36f, p)
        }

        fun novaPagina() {
            if (numPagina > 0) {
                rodape()
                doc.finishPage(pagina)
            }
            numPagina++
            pagina = doc.startPage(PdfDocument.PageInfo.Builder(LARG, ALT, numPagina).create())
            c = pagina.canvas
            y = MARGEM
        }

        // ---------- Cabeçalho ----------
        novaPagina()
        y += 22f
        c.drawText("Registro de pressão arterial", MARGEM, y, pincel(titulo, 22f))
        y += 18f
        val periodo = "${dataBr(lista.first().data)} a ${dataBr(lista.last().data)}" +
            (if (dias != null) " (últimos $dias dias)" else "") + ", ${lista.size} medições"
        c.drawText(periodo, MARGEM, y, pincel(normal, 10.5f, APAGADO))
        y += 26f

        // ---------- Resumo ----------
        val ms = lista.map { it.sistolica }.average().roundToInt()
        val md = lista.map { it.diastolica }.average().roundToInt()
        val pulsos = lista.mapNotNull { it.pulso }
        val maior = lista.maxBy { it.sistolica }
        val menor = lista.minBy { it.sistolica }
        val acima = lista.count { it.sistolica >= 140 || it.diastolica >= 90 }

        val blocos = listOf(
            "Média" to "$ms/$md",
            "Maior" to "${maior.sistolica}/${maior.diastolica}",
            "Menor" to "${menor.sistolica}/${menor.diastolica}",
            "Pulso médio" to (if (pulsos.isEmpty()) "—" else "${pulsos.average().roundToInt()} bpm")
        )
        val largBloco = (LARG - 2 * MARGEM) / blocos.size
        blocos.forEachIndexed { i, (rot, valor) ->
            val x = MARGEM + i * largBloco
            c.drawText(rot, x, y, pincel(medio, 9f, APAGADO))
            c.drawText(valor, x, y + 22f, pincel(titulo, 18f))
        }
        y += 40f
        val nivelMedia = ClassificacaoPressao.nivel(ms, md)
        c.drawCircle(MARGEM + 4f, y - 3.5f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = corNivel(nivelMedia) })
        c.drawText(
            "Média classificada como ${ClassificacaoPressao.faixas[nivelMedia].lowercase()}. " +
                "$acima de ${lista.size} medições com 140/90 ou mais.",
            MARGEM + 14f, y, pincel(normal, 10f)
        )
        y += 24f

        // ---------- Gráfico ----------
        if (lista.size >= 2) {
            val gx = MARGEM + 26f
            val gl = LARG - MARGEM - gx
            val ga = 150f
            val topo = y
            val minimo = 50f
            val maximo = maxOf(180, lista.maxOf { it.sistolica } + 10).toFloat()
            fun py(v: Int) = topo + ga * (1f - (v - minimo) / (maximo - minimo))
            fun px(i: Int) = gx + i * gl / (lista.size - 1)

            val grade = Paint().apply { color = LINHA; strokeWidth = 0.6f }
            val rotulo = pincel(normal, 7.5f, APAGADO)
            listOf(60, 80, 100, 120, 140, 160, 180).filter { it <= maximo }.forEach { v ->
                c.drawLine(gx, py(v), gx + gl, py(v), grade)
                c.drawText("$v", MARGEM, py(v) + 2.5f, rotulo)
            }
            val limite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AMBAR; strokeWidth = 1f; style = Paint.Style.STROKE
                pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f)
            }
            listOf(140, 90).forEach { v -> c.drawLine(gx, py(v), gx + gl, py(v), limite) }

            fun serie(valores: List<Int>, cor: Int) {
                val traco = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = cor; strokeWidth = 1.6f; style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
                }
                val p = Path()
                valores.forEachIndexed { i, v -> if (i == 0) p.moveTo(px(i), py(v)) else p.lineTo(px(i), py(v)) }
                c.drawPath(p, traco)
                if (valores.size <= 60) {
                    val ponto = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cor }
                    valores.forEachIndexed { i, v -> c.drawCircle(px(i), py(v), 2.2f, ponto) }
                }
            }
            serie(lista.map { it.sistolica }, TINTA)
            serie(lista.map { it.diastolica }, FOLHA)

            y = topo + ga + 16f
            val leg = pincel(normal, 8.5f, APAGADO)
            var lx = gx
            listOf(TINTA to "Sistólica", FOLHA to "Diastólica", AMBAR to "Limites 140 e 90").forEach { (cor, txt) ->
                c.drawCircle(lx + 3f, y - 3f, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cor })
                c.drawText(txt, lx + 10f, y, leg)
                lx += 18f + leg.measureText(txt) + 14f
            }
            y += 26f
        }

        // ---------- Tabela ----------
        val colunas = floatArrayOf(MARGEM, MARGEM + 72f, MARGEM + 116f, MARGEM + 196f, MARGEM + 238f, MARGEM + 352f)
        val cab = pincel(medio, 8.5f, APAGADO)
        val cel = pincel(normal, 9.5f)
        val celForte = pincel(medio, 9.5f)
        val altLinha = 17f

        fun cabecalhoTabela() {
            listOf("Data", "Hora", "Pressão", "Pulso", "Classificação", "Observação")
                .forEachIndexed { i, t -> c.drawText(t, colunas[i], y, cab) }
            y += 6f
            c.drawLine(MARGEM, y, LARG - MARGEM, y, Paint().apply { color = LINHA; strokeWidth = 0.8f })
            y += 13f
        }

        fun cortar(texto: String, largura: Float, p: Paint): String {
            if (p.measureText(texto) <= largura) return texto
            var t = texto
            while (t.isNotEmpty() && p.measureText("$t…") > largura) t = t.dropLast(1)
            return "$t…"
        }

        cabecalhoTabela()
        lista.reversed().forEach { m ->
            if (y > ALT - 60f) {
                novaPagina()
                y += 10f
                cabecalhoTabela()
            }
            val n = ClassificacaoPressao.nivel(m.sistolica, m.diastolica)
            c.drawText(dataBr(m.data), colunas[0], y, cel)
            c.drawText(m.hora, colunas[1], y, cel)
            c.drawText("${m.sistolica}/${m.diastolica}", colunas[2], y, celForte)
            c.drawText(m.pulso?.toString() ?: "", colunas[3], y, cel)
            c.drawCircle(colunas[4] + 3f, y - 3f, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = corNivel(n) })
            c.drawText(ClassificacaoPressao.faixas[n], colunas[4] + 10f, y, cel)
            c.drawText(cortar(m.obs, LARG - MARGEM - colunas[5], cel), colunas[5], y, cel)
            y += altLinha
        }

        rodape()
        doc.finishPage(pagina)
        arquivo.outputStream().use { doc.writeTo(it) }
        doc.close()
    }
}
