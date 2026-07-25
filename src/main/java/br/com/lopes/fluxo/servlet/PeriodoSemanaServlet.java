package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.util.SemanaOperacionalUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * API interna consumida pelo n8n — não é usada pelo front-end web.
 * Autenticação por header X-Agro-Api-Key (ver AuthFilter).
 *
 * GET /api/financeiro/periodo-semana
 *
 * Devolve hoje, a semana operacional atual e a próxima semana (sábado a
 * sexta-feira) já calculadas pelo servidor. Pensado pra ser chamado no
 * início do fluxo do n8n (antes do Agent responder) e injetado no system
 * prompt, algo como:
 *
 *   Hoje é {{ $json.hojeBr }}. Semana atual: {{ $json.semanaAtual.inicioBr }}
 *   a {{ $json.semanaAtual.fimBr }}. Próxima semana:
 *   {{ $json.proximaSemana.inicioBr }} a {{ $json.proximaSemana.fimBr }}.
 *
 * Elimina de vez a dependência do agente de IA calcular essas datas
 * sozinho (LLM erra matemática de datas com frequência) — ele só precisa
 * copiar os valores já prontos, seja pra montar a frase da resposta, seja
 * pra preencher dataIniVcto/dataFimVcto de "contas-apagar" diretamente
 * (equivalente a usar periodo=proximaSemana lá).
 */
@WebServlet("/api/financeiro/periodo-semana")
public class PeriodoSemanaServlet extends HttpServlet {

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        PrintWriter out = resp.getWriter();

        LocalDate hoje = LocalDate.now();
        LocalDate[] atual = SemanaOperacionalUtil.semanaAtual();
        LocalDate[] proxima = SemanaOperacionalUtil.proximaSemana();

        JsonObject resultado = new JsonObject();
        resultado.addProperty("ok", true);
        resultado.addProperty("hoje", hoje.toString());
        resultado.addProperty("hojeBr", hoje.format(BR));
        resultado.add("semanaAtual", montarBloco(atual[0], atual[1]));
        resultado.add("proximaSemana", montarBloco(proxima[0], proxima[1]));

        out.print(gson.toJson(resultado));
        out.flush();
    }

    private JsonObject montarBloco(LocalDate inicio, LocalDate fim) {
        JsonObject bloco = new JsonObject();
        bloco.addProperty("inicio", inicio.toString());
        bloco.addProperty("fim", fim.toString());
        bloco.addProperty("inicioBr", inicio.format(BR));
        bloco.addProperty("fimBr", fim.format(BR));
        return bloco;
    }
}
