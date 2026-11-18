# Remédio da Hora

App Android para lembrar dos remédios e servir de agenda.

## O que faz
- **Remédios**: doses de hoje com o botão "Tomei", nome, dose, horários diários e duração do tratamento.
  Na hora chega uma notificação com o botão "✓ Tomei".
- **Importar receita em PDF**: toque no botão ou compartilhe o PDF (WhatsApp, e-mail) com o app.
  Ele lê os remédios, monta horários ("de 8 em 8 horas" → 08:00, 16:00, 00:00) e duração ("por 7 dias");
  você confere e cadastra.
- **Agenda**: calendário do mês, compromissos com data, hora, observação e aviso
  (na hora, 15 min, 1 h ou 1 dia antes).
- Os alarmes funcionam com o app fechado e são refeitos depois que o celular reinicia.
- Tudo fica salvo só no celular.

## Como baixar e instalar (pelo celular)
1. Abra **Releases** nesta página do GitHub (ou `/releases/latest`).
2. Toque em **RemedioDaHora.apk** para baixar.
3. Abra o arquivo baixado. Se o Android pedir, permita **instalar apps desta fonte**.
4. Abra o app e **permita as notificações**.

Cada vez que o código muda, o GitHub gera um APK novo sozinho (aba **Actions**).
Ele instala por cima do anterior sem apagar seus dados.

## Dica importante
Alguns celulares (Samsung, Xiaomi, Motorola) economizam bateria fechando apps.
Se os lembretes atrasarem: **Configurações → Apps → Remédio da Hora → Bateria → Sem restrições**.
