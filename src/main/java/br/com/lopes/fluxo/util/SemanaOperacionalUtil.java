package br.com.lopes.fluxo.util;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Semana operacional da empresa: vai de sábado a sexta-feira (não domingo a
 * sábado). "Semana atual" é o bloco sábado-sexta que contém hoje.
 *
 * "Próxima semana" vira na SEGUNDA-FEIRA, não no sábado: como os pagamentos
 * só acontecem a partir de segunda, no sábado e no domingo a "próxima semana
 * de pagamento" ainda é o bloco sábado-sexta que acabou de começar. A partir
 * de segunda, é o bloco seguinte.
 *
 * Centraliza esse cálculo pra não depender do agente de IA (LLM) fazer
 * aritmética de datas sozinho — já aconteceu de errar (ex.: devolver um
 * intervalo de 8 ou 11 dias em vez de 7).
 */
public final class SemanaOperacionalUtil {

    private SemanaOperacionalUtil() {
    }

    public static LocalDate[] semanaAtual() {
        return semanaAtual(LocalDate.now());
    }

    public static LocalDate[] semanaAtual(LocalDate hoje) {
        return blocoDe(hoje);
    }

    public static LocalDate[] proximaSemana() {
        return proximaSemana(LocalDate.now());
    }

    public static LocalDate[] proximaSemana(LocalDate hoje) {
        // Hoje menos 2 dias: sábado e domingo ainda contam como a semana
        // anterior, então o "bloco seguinte" deles é a semana que acabou de
        // começar — a virada real acontece na segunda-feira.
        LocalDate[] referencia = blocoDe(hoje.minusDays(2));
        LocalDate inicio = referencia[0].plusDays(7);
        return new LocalDate[]{inicio, inicio.plusDays(6)};
    }

    /** Bloco sábado-sexta que contém a data informada. */
    private static LocalDate[] blocoDe(LocalDate data) {
        int diasDesdeSabado = (data.getDayOfWeek().getValue() - DayOfWeek.SATURDAY.getValue() + 7) % 7;
        LocalDate inicio = data.minusDays(diasDesdeSabado);
        return new LocalDate[]{inicio, inicio.plusDays(6)};
    }
}
