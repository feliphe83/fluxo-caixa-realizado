package br.com.lopes.fluxo.agendamento;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * As duas mensagens da parada de moagem, no formato definido pela indústria.
 *
 * Ficam separadas do envio e do banco para poderem ser conferidas letra a
 * letra — é texto que vai para um grupo de WhatsApp com diretoria dentro, e
 * um rótulo trocado se lê na hora.
 */
public final class MensagemParadaMoagem {

    private MensagemParadaMoagem() {}

    private static final DateTimeFormatter ENTRADA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATA    = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA    = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** Aceita "yyyy-MM-dd HH:mm" e "yyyy-MM-ddTHH:mm" — a tela manda o
     *  segundo formato, o banco devolve o primeiro. Null quando não entende,
     *  e é assim que o servlet valida o que veio do formulário. */
    public static LocalDateTime instante(String v) {
        if (v == null || v.isBlank()) return null;
        String s = v.trim().replace('T', ' ');
        if (s.length() == 16) s = s + ":00";        // "yyyy-MM-dd HH:mm" da tela
        try { return LocalDateTime.parse(s, ENTRADA); } catch (Exception e) { return null; }
    }

    private static String ou(String v, String padrao) {
        return v == null || v.isBlank() ? padrao : v.trim();
    }

    /**
     * "0h 9min" — sempre com as duas unidades, como no modelo. Dizer só
     * "9min" pouparia um caractere e tiraria a comparação imediata entre uma
     * parada de minutos e uma de horas, que é o que se olha primeiro.
     */
    public static String tempoParado(LocalDateTime inicio, LocalDateTime retorno) {
        if (inicio == null || retorno == null) return "—";
        long min = Math.max(0, Duration.between(inicio, retorno).toMinutes());
        return (min / 60) + "h " + (min % 60) + "min";
    }

    public static String parada(Map<String, Object> p) {
        LocalDateTime ini = instante(str(p.get("inicio")));
        StringBuilder sb = new StringBuilder();
        sb.append("🛑🔴 PAROU A MOAGEM 🛑🔴\n\n");
        sb.append("📅 Data: ").append(ini == null ? "—" : DATA.format(ini)).append('\n');
        sb.append("⏰ Hora: ").append(ini == null ? "—" : HORA.format(ini)).append('\n');
        sb.append("📝 Motivo: ").append(ou(str(p.get("motivo")), "Não informado")).append('\n');
        sb.append("⚙️ Parte: ").append(ou(str(p.get("parte")), "Não informada")).append('\n');
        sb.append("⏳ Previsão parada: ").append(ou(str(p.get("previsao")), "Não informada"));
        return sb.toString();
    }

    public static String retorno(Map<String, Object> p) {
        LocalDateTime ini = instante(str(p.get("inicio")));
        LocalDateTime ret = instante(str(p.get("retorno")));
        StringBuilder sb = new StringBuilder();
        sb.append("🟢✅ RETORNO A MOAGEM 🟢✅\n\n");
        sb.append("📅 Data: ").append(ini == null ? "—" : DATA.format(ini)).append('\n');
        sb.append("⏰ Hora início: ").append(ini == null ? "—" : HORA.format(ini)).append('\n');
        sb.append("⏱️ Hora retorno: ").append(ret == null ? "—" : HORA.format(ret)).append('\n');
        sb.append("⏳ Tempo parado: ").append(tempoParado(ini, ret)).append("\n\n");
        sb.append("📝 Motivo: ").append(ou(str(p.get("motivo")), "Não informado")).append('\n');
        sb.append("⚙️ Parte: ").append(ou(str(p.get("parte")), "Não informada"));
        return sb.toString();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
