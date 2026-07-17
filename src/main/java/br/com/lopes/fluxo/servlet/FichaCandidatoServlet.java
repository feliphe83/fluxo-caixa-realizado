package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.util.OracleConnectionUtil;
import br.com.lopes.fluxo.util.RowMapperUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * GET /api/ficha-candidatos?dataIni=YYYY-MM-DD&dataFim=YYYY-MM-DD&codCargo=N
 *
 * Ficha completa de candidatos (rh.fichacandidato) cadastrados no período,
 * opcionalmente filtrados por cargo desejado (nível 1). codCargo=0 (ou
 * ausente) lista candidatos de todos os cargos.
 *
 * Adaptada de um relatório JasperReports original: os parâmetros $P{DT_INI},
 * $P{DT_FIM} e $P{COD_CARGO} viraram bind params normais. Dois trechos foram
 * removidos por serem tautologias inertes da captura original do relatório
 * (sempre verdadeiras/falsas independente do parâmetro, sem efeito real de
 * filtro): "(FICHACANDIDATO.CPF = 0 or 0 = '0')" e o "or 494 = 0" do filtro
 * de cargo. A subconsulta de "cargo_desejado" não filtra mais por cod_cargo
 * (só por nível 1) para continuar mostrando o cargo desejado do candidato
 * mesmo quando a listagem não está filtrada por um cargo específico.
 */
@WebServlet("/api/ficha-candidatos")
public class FichaCandidatoServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(FichaCandidatoServlet.class.getName());
    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private final Gson gson = new Gson();

    private static final String SQL = """
        SELECT
           FICHACANDIDATO.DATA
         , FICHACANDIDATO.CPF
        , (Select c.descricao from RH.Candidatocargo Candidatocargo, rh.cargo c
                       where CandidatoCargo.Cod_grupoempresa = FichaCandidato.Cod_grupoempresa
                         and CandidatoCargo.CPF              = FichaCandidato.CPF
                        and CandidatoCargo.COD_CARGO        = c.cod_cargo
                         and CandidatoCargo.nivel             = c.nivel
                         and CandidatoCargo.nivel             = '1'
        ) cargo_desejado

         , FICHACANDIDATO.COD_GRUPOEMPRESA
         , FICHACANDIDATO.SALARIO
         , FICHACANDIDATO.ESTUDO_ATUAL
         , FICHACANDIDATO.ESTUDO_HORA
         , FICHACANDIDATO.IDIOMAS
         , FICHACANDIDATO.PAR_NOMES
         , FICHACANDIDATO.PLACA_AVISO
         , FICHACANDIDATO.AGENCIA_EMP
         , FICHACANDIDATO.RECOMENDACAO
         , FICHACANDIDATO.ANUNCIO_JORNAL
         , FICHACANDIDATO.ESPONTANEO
         , FICHACANDIDATO.OUTROS_VAGA
         , FICHACANDIDATO.ATIVIDADE_REMUNERADA
         , FICHACANDIDATO.ASSOCIACAO
         , FICHACANDIDATO.HORA_EXTRA
         , decode(FICHACANDIDATO.HORARIO_NOTURNO,'T','Sim','Não')"HORARIO_NOTURNO"
         , decode(FICHACANDIDATO.EXECUTAR_SERVICO_EXTERNO,'T','Sim','Não')"EXECUTAR_SERVICO_EXTERNO"
         , decode(FICHACANDIDATO.REVEZAMENTO_TURNO,'T','Sim','Não')"REVEZAMENTO_TURNO"
         , FICHACANDIDATO.HORA_EXTRA_OBS
         , FICHACANDIDATO.INICIO_TRABALHO
         , FICHACANDIDATO.ULTIMA_EXP
         , FICHACANDIDATO.PENULTIMA_EXP
         , FICHACANDIDATO.ANTEPENULTIMA_EXP
         , FICHACANDIDATO.EXPERIENCIA
         , FICHACANDIDATO.CURRICULUM
         , FICHACANDIDATO.DEPENDENTES
         , FISICA.COD_ESCOLARIDADE
         , (select e.descricao from rh.escolaridade e
         where e.cod_escolaridade = fisica.COD_ESCOLARIDADE) desc_escolaridade
         , FICHACANDIDATO.NOMEESCOLA
         , FICHACANDIDATO.CANDCONTRATADO
         , FICHACANDIDATO.COD_FORMACAO
         , FICHACANDIDATO.DESCESTUDOATUAL
         , decode(FICHACANDIDATO.EXPERIENCIACARGO,'T','Sim','Não')"EXPERIENCIACARGO"

         , decode(nvl(fichacandidato.possui_filhos,'N'),'S','Sim','Não') possui_filhos
         , fichacandidato.qtde_filhos
         , decode(FICHACANDIDATO.EXPERIENCIASETOR,'T','Sim','Não')"EXPERIENCIASETOR"
         , decode(FICHACANDIDATO.TRABALHOU_EMPRESA,'T','Sim','Não') "TRABALHOU_EMPRESA"
         , PESSOA.COD_PESSOA
         , PESSOA.NOME
         , PESSOA.ENDERECO
         , PESSOA.BAIRRO
         , PESSOA.COD_CIDADE
         , PESSOA.CEP
         , PESSOA.EMAIL
         , CIDADE.DESCRICAO DESC_CIDADE
         , CIDADE.ESTADO UF
         , FISICA.RG
         , FISICA.PIS
         , FISICA.CATEGORIA_HABILITACAO
         , FISICA.CERT_MILITAR
         , FISICA.CART_HABILITACAO
         , FISICA.TITULO
         , FISICA.ZONA
         , FISICA.SECAO
         , FISICA.DATA_NASCIMENTO
         , FISICA.COD_ESTADOCIVIL
         , FISICA.PAI
         , FISICA.MAE
         , FISICA.CTPS
         , FISICA.SERIE
          , (SELECT DESCRICAO FROM RH.CIDADE WHERE CIDADE.COD_CIDADE = FISICA.COD_CIDADE) NATURALIDADE
        , (select substr(trunc(sysdate),1,2)||' '||to_char(TO_DATE(sysdate),'MONTH')||'de '||trim(substr(to_CHAR(sysdate,'DD/MM/YYYY'),7,4))||'.' from DUAL) data_mes_ano
         , fisica.num_calcado
         , decode(fisica.sexo,'M','Masculino','Feminino') sexo
         , (select vinculo.cod_vinculo||' - '||descricao from rh.vinculo where vinculo.cod_vinculo = fichacandidato.cod_vinculo) Desc_vinculo
        , (select descricao from rh.cor where cor.cod_cor = fisica.cod_cor) Desc_cor
         , FISICA.NUM_CARTAO_SUS
         , ESTADOCIVIL.DESCRICAO
         , NACIONALIDADE.DESCRICAO NACIONALIDADE
         , FICHACANDIDATO.COD_TURMA_CAND || ' - ' || TURMA_CANDIDATO.DESCRICAO "TURMADESC"
         , TURMA_CANDIDATO.HORA "HORATURM"
         , TURMA_CANDIDATO.LOCAL "LOCAL_TURM"
         , TURMA_CANDIDATO.DATA_TURMA "DATATURMA"
         , (SELECT CONVOCACAO.COD_OBJETOCUSTO || ' - '|| OBJETOCUSTO.DESCRICAO
            FROM RH.CONVOCACAO,
                 RH.OBJETOCUSTO
            WHERE FICHACANDIDATO.CPF = CONVOCACAO.CPF
            AND   OBJETOCUSTO.COD_OBJETOCUSTO = CONVOCACAO.COD_OBJETOCUSTO
            AND ROWNUM = 1) OBJETOCUSTO

          , (SELECT CONVOCACAO.COD_CARGO || ' - ' || CARGO.DESCRICAO
             FROM  RH.CONVOCACAO,
                   RH.CARGO
             WHERE FICHACANDIDATO.CPF = CONVOCACAO.CPF
             AND   CARGO.COD_CARGO = CONVOCACAO.COD_CARGO
             AND ROWNUM = 1 ) CARGO
         , DECODE(NVL(FICHACANDIDATO.TEM_INDICACAO,'N'),'S','Sim','Não')TEM_INDICACAO
         , FICHACANDIDATO.NOME_QUEM_INDICOU
         ,(SELECT DESCRICAO
           FROM RH.TIPO_DEFICIENCIA
           WHERE 1 = 1
           AND TIPO_DEFICIENCIA.COD_TIPODEFICIENCIA = FISICA.COD_TIPODEFICIENCIA )TIPO_DEFICIENCIA
         ,  fichacandidato.fre numero_ficha
          , (select max(numero)
             from  rh.convocacao
             where 1 = 1
             and  convocacao.cpf              =fichacandidato.cpf
             and  convocacao.cod_grupoempresa =  fichacandidato.cod_grupoempresa
           ) numero_convocacao
        , (select cod_departamento ||' - ' ||descricao
             from   rh.departamento
             where  cod_grupoempresa =   FICHACANDIDATO.COD_GRUPOEMPRESA
             and    cod_departamento =   FICHACANDIDATO.cod_departamento ) depart_func

          , (select cod_setor ||' - '||descricao
            from    rh.setor
            where   cod_setor = FICHACANDIDATO.cod_setor
            ) setor_func
         , FICHACANDIDATO.cod_setor
         , FICHACANDIDATO.cod_departamento
         , FICHACANDIDATO.COD_TURMA_CAND
        FROM
           RH.CIDADE
         , RH.vw_PESSOA pessoa
         , RH.vw_FISICA fisica
         , RH.FICHACANDIDATO FICHACANDIDATO
         , RH.ESTADOCIVIL
         , RH.NACIONALIDADE
         , RH.TURMA_CANDIDATO

        WHERE 1 = 1
          and FICHACANDIDATO.CPF              = FISICA.CPF
          and FISICA.COD_PESSOA               = PESSOA.COD_PESSOA
          and FICHACANDIDATO.COD_GRUPOEMPRESA = 1
          and FICHACANDIDATO.COD_TURMA_CAND   = TURMA_CANDIDATO.COD_TURMA_CAND(+)
          and CIDADE.COD_CIDADE               = PESSOA.COD_CIDADE

          AND FISICA.COD_ESTADOCIVIL          = ESTADOCIVIL.COD_ESTADOCIVIL(+)
          AND FISICA.COD_NACIONALIDADE        = NACIONALIDADE.COD_NACIONALIDADE

          AND (FICHACANDIDATO.DATA  BETWEEN ? AND ?)
          and (? = 0 or Exists (Select 1 from RH.Candidatocargo
                       where CandidatoCargo.Cod_grupoempresa = FichaCandidato.Cod_grupoempresa
                         and CandidatoCargo.CPF              = FichaCandidato.CPF
                         and CandidatoCargo.COD_CARGO        = ?
                         and CandidatoCargo.nivel             = '1'))
        ORDER BY
           PESSOA.NOME ASC
        """;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        PrintWriter out = resp.getWriter();

        LocalDate dataIni = parseDate(req.getParameter("dataIni"));
        LocalDate dataFim = parseDate(req.getParameter("dataFim"));

        if (dataIni == null || dataFim == null) {
            resp.setStatus(400);
            out.print("{\"ok\":false,\"erro\":\"Parâmetros dataIni e dataFim são obrigatórios\"}");
            out.flush();
            return;
        }

        int codCargo = 0;
        String codCargoParam = req.getParameter("codCargo");
        if (codCargoParam != null && !codCargoParam.isBlank()) {
            try {
                codCargo = Integer.parseInt(codCargoParam.trim());
            } catch (NumberFormatException e) {
                resp.setStatus(400);
                out.print("{\"ok\":false,\"erro\":\"Parâmetro codCargo deve ser numérico (0 = todos)\"}");
                out.flush();
                return;
            }
        }

        try (Connection conn = OracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL)) {

            ps.setDate(1, Date.valueOf(dataIni));
            ps.setDate(2, Date.valueOf(dataFim));
            ps.setInt(3, codCargo);
            ps.setInt(4, codCargo);

            List<Map<String, Object>> lista;
            try (ResultSet rs = ps.executeQuery()) {
                lista = RowMapperUtil.toList(rs);
            }

            JsonObject result = new JsonObject();
            result.addProperty("ok", true);
            result.add("data", gson.toJsonTree(lista));
            out.print(gson.toJson(result));

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ficha-candidatos", e);
            String msg = e.getMessage() != null
                    ? e.getMessage().replace("\"", "'").replace("\n", " ")
                    : e.getClass().getName();
            out.print("{\"ok\":false,\"erro\":\"" + msg + "\"}");
        } finally {
            out.flush();
        }
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s.trim(), FMT); }
        catch (DateTimeParseException e) { return null; }
    }
}
