package br.com.lopes.fluxo.util;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Semana operacional da empresa: vai de sábado a sexta-feira (não domingo a
 * sábado). "Semana atual" é o bloco sábado-sexta que contém hoje; "próxima
 * semana" é o bloco seguinte a esse.
 *
 * Centraliza esse cálculo pra não depender do agente de IA (LLM) fazer
 * aritmética de datas sozinho — já aconteceu de errar (ex.: devolver um
 * intervalo de 8 ou 11 dias em vez de 7).
 */
public final class SemanaOperacionalUtil {

    private SemanaOperacionalUtil() {
    }

    public static LocalDate[] semanaAtual() {
        LocalDate hoje = LocalDate.now();
        int diasDesdeSabado = (hoje.getDayOfWeek().getValue() - DayOfWeek.SATURDAY.getValue() + 7) % 7;
        LocalDate inicio = hoje.minusDays(diasDesdeSabado);
        LocalDate fim = inicio.plusDays(6);
        return new LocalDate[]{inicio, fim};
    }

    public static LocalDate[] proximaSemana() {
        LocalDate[] atual = semanaAtual();
        LocalDate inicio = atual[0].plusDays(7);
        LocalDate fim = inicio.plusDays(6);
        return new LocalDate[]{inicio, fim};
    }
}
