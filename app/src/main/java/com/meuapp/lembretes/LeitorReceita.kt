package com.meuapp.lembretes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

/**
 * Lê uma receita em PDF (no próprio celular, sem internet) e sugere os remédios.
 * 1º tenta o texto do PDF; se o PDF for uma imagem escaneada, usa reconhecimento de texto (OCR).
 */
object LeitorReceita {

    /** Chamar fora da thread principal. */
    fun extrairTexto(ctx: Context, uri: Uri): String {
        val texto = try {
            PDFBoxResourceLoader.init(ctx.applicationContext)
            ctx.contentResolver.openInputStream(uri)?.use { entrada ->
                PDDocument.load(entrada).use { doc -> PDFTextStripper().getText(doc) }
            } ?: ""
        } catch (e: Exception) {
            ""
        }
        if (texto.count { it.isLetter() } >= 20) return texto
        return lerComOcr(ctx, uri)
    }

    private fun lerComOcr(ctx: Context, uri: Uri): String {
        val leitor = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val sb = StringBuilder()
        try {
            ctx.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { pdf ->
                    for (i in 0 until minOf(pdf.pageCount, 5)) {
                        pdf.openPage(i).use { pagina ->
                            val escala = 2
                            val bmp = Bitmap.createBitmap(
                                pagina.width * escala, pagina.height * escala, Bitmap.Config.ARGB_8888
                            )
                            bmp.eraseColor(Color.WHITE)
                            pagina.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val resultado = Tasks.await(leitor.process(InputImage.fromBitmap(bmp, 0)))
                            sb.append(resultado.text).append('\n')
                            bmp.recycle()
                        }
                    }
                }
            }
        } finally {
            leitor.close()
        }
        return sb.toString()
    }

    // ------------------------------------------------------------------
    // Interpretação do texto
    // ------------------------------------------------------------------

    private fun semAcento(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase(Locale.ROOT)

    private const val NUM = "\\d+(?:[.,]\\d+)?"
    private const val UNID = "(?:mg|mcg|µg|ug|g|ml|ui|%)"

    // "Amoxicilina 500 mg", "Dipirona 500mg/ml", "Losartana Potássica 50 mg"
    private val reDose = Regex(
        "($NUM\\s?$UNID(?:\\s?/\\s?(?:$NUM\\s?)?$UNID)?)",
        RegexOption.IGNORE_CASE
    )
    private val reNumerado = Regex("^\\s*\\d{1,2}\\s*[).\\-–]\\s*(.+)$")

    private val inicioPosologia = listOf(
        "tomar", "ingerir", "usar", "uso ", "aplicar", "pingar", "administrar", "via ", "dar ",
        "mastigar", "dissolver", "diluir", "inalar", "fazer", "colocar", "passar"
    )
    private val naoRemedio = listOf(
        "crm", "paciente", "nome", "data", "endereco", "medico", "dr.", "dra.", "receituario",
        "assinatura", "telefone", "cpf", "rua", "av.", "identificacao", "emitente", "comprador",
        "fornecedor", "farmac", "validade", "cid", "idade", "sexo", "peso", "convenio", "hospital",
        "clinica", "carimbo", "rg"
    )

    private val palavrasNumero = mapOf(
        "um" to 1, "uma" to 1, "dois" to 2, "duas" to 2, "tres" to 3, "quatro" to 4,
        "cinco" to 5, "seis" to 6
    )

    private fun pareceRemedio(linha: String): Boolean {
        val miolo = reNumerado.find(linha)?.groupValues?.get(1) ?: linha
        val n = semAcento(miolo).trim()
        if (n.length < 3) return false
        if (!n.first().isLetter()) return false
        if (inicioPosologia.any { n.startsWith(it) }) return false
        if (naoRemedio.any { n.contains(it) }) return false
        if (!linha.trim().first().isLetterOrDigit()) return false
        val temDose = reDose.containsMatchIn(linha)
        val numerado = reNumerado.matches(linha)
        return temDose || numerado
    }

    private fun limparNome(linha: String): Pair<String, String> {
        var l = linha.trim()
        reNumerado.find(l)?.let { l = it.groupValues[1] }
        val m = reDose.find(l)
        val concentracao = m?.value?.replace(Regex("\\s+"), " ")?.trim() ?: ""
        var nome = if (m != null) l.substring(0, m.range.first) else l
        // corta pontilhados e quantidades ("------ 2 caixas")
        nome = nome.replace(Regex("[-_.…]{3,}.*$"), "")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ',', ':', ';', '–')
        if (nome.isBlank()) nome = l.replace(Regex("[-_.…]{3,}.*$"), "").trim()
        // Primeira letra maiúscula, resto como veio (mantém siglas)
        return arrumarMaiusculas(nome) to concentracao
    }

    private fun hora(h: Int) = String.format(Locale.ROOT, "%02d:00", ((h % 24) + 24) % 24)

    private fun horariosPorIntervalo(intervalo: Int): List<String> {
        val passo = intervalo.coerceIn(1, 24)
        val vezes = (24 / passo).coerceAtLeast(1)
        return (0 until vezes).map { hora(8 + it * passo) }.distinct().sorted()
    }

    private fun interpretarPosologia(bloco: String): Triple<String, List<String>, Int?> {
        val t = semAcento(bloco).replace(Regex("\\s+"), " ")

        // quantidade por vez: "tomar 1 comprimido", "tomar meio comprimido", "20 gotas"
        val reQtd = Regex(
            "(?:tomar|ingerir|usar|aplicar|pingar|administrar|dar|mastigar|inalar)?\\s*" +
                "(\\d+(?:[.,]\\d+)?|um|uma|meio|meia|dois|duas|tres|quatro)\\s*" +
                "(comprimidos?|capsulas?|cps?|comp\\.?|gotas?|ml|drageas?|saches?|envelopes?|jatos?|" +
                "puffs?|doses?|colher(?:es)?(?: de (?:sopa|cha|sobremesa))?|ampolas?|supositorios?|unidades?)\\b"
        )
        val dose = reQtd.find(t)?.let { m ->
            val q = m.groupValues[1]
            val u = m.groupValues[2]
            "$q $u".replace("cps", "cápsula").replace("capsula", "cápsula").replace("comp.", "comprimido")
        } ?: ""

        // intervalo em horas
        val intervalo =
            Regex("de\\s*(\\d{1,2})\\s*(?:em|/)\\s*(\\d{1,2})\\s*(?:h|hs|hrs|hora|horas)\\b").find(t)
                ?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("(\\d{1,2})\\s*/\\s*(\\d{1,2})\\s*(?:h|hs|hrs|horas)\\b").find(t)
                    ?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("a cada\\s*(\\d{1,2})\\s*(?:h|hs|hrs|hora|horas)\\b").find(t)
                    ?.groupValues?.get(1)?.toIntOrNull()

        // vezes por dia
        val vezesDia = Regex("(\\d|um|uma|duas|dois|tres|quatro)\\s*(?:x|vez|vezes)\\s*(?:ao|por|/|a)\\s*dia")
            .find(t)?.groupValues?.get(1)?.let { it.toIntOrNull() ?: palavrasNumero[it] }
            ?: if (Regex("\\b(?:ao dia|por dia|diariamente|1x/dia|uma vez)\\b").containsMatchIn(t)) 1 else null

        // períodos do dia
        val periodos = mutableListOf<String>()
        if (Regex("jejum").containsMatchIn(t)) periodos += "07:00"
        if (Regex("\\bmanha\\b|cafe da manha").containsMatchIn(t)) periodos += "08:00"
        if (Regex("almoco").containsMatchIn(t)) periodos += "12:00"
        if (Regex("\\btarde\\b").containsMatchIn(t)) periodos += "15:00"
        if (Regex("jantar").containsMatchIn(t)) periodos += "19:00"
        if (Regex("\\bnoite\\b").containsMatchIn(t)) periodos += "21:00"
        if (Regex("deitar|dormir").containsMatchIn(t)) periodos += "22:00"

        val horarios = when {
            intervalo != null && intervalo in 1..24 -> horariosPorIntervalo(intervalo)
            periodos.isNotEmpty() && (vezesDia == null || vezesDia == periodos.distinct().size) ->
                periodos.distinct().sorted()
            vezesDia != null && vezesDia in 1..6 -> horariosPorIntervalo(24 / vezesDia)
            periodos.isNotEmpty() -> periodos.distinct().sorted()
            else -> listOf("08:00")
        }

        // duração
        val dias = Regex("(?:por|durante)\\s*(\\d{1,3})\\s*dias").find(t)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("(?:por|durante)\\s*(\\d{1,2})\\s*semanas?").find(t)?.groupValues?.get(1)
                ?.toIntOrNull()?.times(7)

        return Triple(dose, horarios, dias)
    }

    private val minusculas = setOf("de", "da", "do", "das", "dos", "e", "com")

    /** "GLIFAGE XR" -> "Glifage XR", "BESILATO DE ANLODIPINO" -> "Besilato de Anlodipino" */
    private fun arrumarMaiusculas(nome: String): String {
        if (nome != nome.uppercase()) return nome
        return nome.lowercase().split(" ").mapIndexed { i, w ->
            when {
                w.isEmpty() -> w
                i > 0 && w in minusculas -> w
                w.length <= 2 -> w.uppercase()
                else -> w.replaceFirstChar { it.uppercase() }
            }
        }.joinToString(" ")
    }

    private fun montar(n: Int, nomeBruto: String, concentracao: String, posologia: String): Remedio {
        val nome = arrumarMaiusculas(nomeBruto.replace(Regex("\\s+"), " ").trim(' ', '-', ',', ':', ';', '.'))
        val (qtd, horarios, dias) = interpretarPosologia(posologia)
        val dose = listOf(qtd, concentracao).filter { it.isNotBlank() }.joinToString(" de ")
        return Remedio(
            id = -(n + 1).toLong(),
            nome = nome,
            dose = dose,
            horarios = horarios,
            fim = dias?.let { LocalDate.now().plusDays((it - 1).toLong()).toString() }
        )
    }

    // Receita no modelo com campos: "Nome do Medicamento:", "Concentração:", "Posologia:" (ex.: CFM, Memed)
    private val reRotuloNome = Regex("(?im)(?:nome\\s+do\\s+medicamento|^\\s*medicamento)\\s*:")
    private val reRotulos = Regex(
        "(?i)concentra[cç][aã]o\\s*:|quantidade\\s*:|posologia\\s*:|forma\\s+farmac[eê]utica\\s*:|via\\s*:|\\n"
    )
    private val reRodape = Regex(
        "(?i)receitu[aá]rio\\s|assinado\\s+digitalmente|receita\\s+simples|receita\\s+de\\s+controle|https?://"
    )

    private fun interpretarComCampos(texto: String): List<Remedio> {
        val marcas = reRotuloNome.findAll(texto).toList()
        if (marcas.isEmpty()) return emptyList()
        return marcas.mapIndexed { n, m ->
            val fimTrecho = marcas.getOrNull(n + 1)?.range?.first ?: texto.length
            var trecho = texto.substring(m.range.last + 1, fimTrecho)
            reRodape.find(trecho)?.let { trecho = trecho.substring(0, it.range.first) }

            val nome = trecho.split(reRotulos).firstOrNull { it.isNotBlank() } ?: ""
            val conc = Regex("(?i)concentra[cç][aã]o\\s*:\\s*([^\\n]*)").find(trecho)
                ?.groupValues?.get(1)?.let { reDose.find(it)?.value?.replace(Regex("\\s+"), " ") } ?: ""
            val posologia = Regex("(?i)posologia\\s*:\\s*([\\s\\S]*)").find(trecho)?.groupValues?.get(1) ?: trecho
            montar(n, nome, conc, posologia)
        }.filter { it.nome.length >= 3 }
            .distinctBy { semAcento(it.nome) }
    }

    /** Transforma o texto da receita em uma lista de remédios para a pessoa conferir. */
    fun interpretar(texto: String): List<Remedio> {
        val comCampos = interpretarComCampos(texto)
        if (comCampos.isNotEmpty()) return comCampos

        val linhas = texto.lines().map { it.replace('\t', ' ').trim() }.filter { it.isNotBlank() }
        val inicios = linhas.indices.filter { pareceRemedio(linhas[it]) }
        val hoje = LocalDate.now()

        return inicios.mapIndexed { n, i ->
            val fimBloco = inicios.getOrNull(n + 1) ?: minOf(linhas.size, i + 6)
            val bloco = linhas.subList(i, fimBloco).joinToString(" ")
            val (nome, concentracao) = limparNome(linhas[i])
            val (qtd, horarios, dias) = interpretarPosologia(bloco)
            val dose = listOf(qtd, concentracao).filter { it.isNotBlank() }.joinToString(" de ")
            Remedio(
                id = -(n + 1).toLong(),
                nome = nome,
                dose = dose,
                horarios = horarios,
                fim = dias?.let { hoje.plusDays((it - 1).toLong()).toString() }
            )
        }.filter { it.nome.length >= 3 }
            .distinctBy { semAcento(it.nome) }
    }
}
