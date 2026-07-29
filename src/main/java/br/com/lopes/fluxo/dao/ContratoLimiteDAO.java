package br.com.lopes.fluxo.dao;

import br.com.lopes.fluxo.util.OracleConnectionUtil;
import br.com.lopes.fluxo.util.RowMapperUtil;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Contratos em andamento que já consumiram boa parte do valor contratado.
 *
 * São duas metades unidas, com regras diferentes conforme o tipo de contrato:
 * a primeira soma o que foi efetivamente PAGO (valorpago) e cobre os tipos
 * fora do 12; a segunda soma o valor das PARCELAS (valorparcela) e cobre só o
 * tipo 12, olhando contratos iniciados desde 2018. Os contratos excluídos por
 * número em cada metade vieram da consulta original e foram mantidos.
 *
 * PERCENTUAL_RESTANTE, apesar do nome herdado, é o percentual JÁ UTILIZADO do
 * contrato: a fórmula devolve pago/total × 100.
 *
 * Como o alerta de divergência de nota, esta consulta não filtra por
 * aprovador — todo destinatário do agendamento recebe a mesma lista.
 *
 * Colunas: numerocontrato, nome (fornecedor), negocio, datainicio,
 * datatermino, descricaoresumida, total_pago, valor_total, diferenca,
 * percentual_restante.
 */
public class ContratoLimiteDAO {

    private static final Logger LOG = Logger.getLogger(ContratoLimiteDAO.class.getName());

    /**
     * Binds, nesta ordem: data mínima de vencimento das parcelas (uma vez em
     * cada metade da união) e percentual mínimo já utilizado.
     */
    private static final String SQL = """
        select * from (
            SELECT contrato.numerocontrato,
                   material.fn_buscanomefornec(contrato.cod_fornecedor,sysdate) nome,
                   (select max(ob.negocio)
                      from financeiro.historicocontrato h, rh.objetocusto ob
                     where h.numerocontrato = contrato.numerocontrato
                       and h.cod_objetocusto = ob.cod_objetocusto) negocio,
                   g.DATAINICIO,
                   g.DATATERMINO,
                   contrato.DESCRICAORESUMIDA,
                   SUM(parcelascontaspagar.valorpago) AS TOTAL_PAGO,
                   MAX(g.valortotal) AS VALOR_TOTAL,
                   MAX(g.valortotal) - SUM(parcelascontaspagar.valorpago) AS DIFERENCA,
                   ROUND(
                       (
                           (((MAX(g.valortotal) - SUM(parcelascontaspagar.valorpago))
                           / MAX(g.valortotal)) * 100) - 100
                       ) * -1
                   , 2) AS PERCENTUAL_RESTANTE
            FROM   FINANCEIRO.PARCELASCONTASPAGAR parcelascontaspagar,
                   Financeiro.Contrato contrato,
                   financeiro.historicocontrato g
            WHERE  contrato.numerocontrato = g.numerocontrato
              AND  parcelascontaspagar.Provisao = 'N'
              AND  parcelascontaspagar.COD_GRUPOEMPRESA = contrato.Cod_GrupoEmpresa
              AND  parcelascontaspagar.COD_EMPRESA = contrato.Cod_Empresa
              AND  parcelascontaspagar.COD_Filial = contrato.Cod_Filial
              AND  parcelascontaspagar.Documento = contrato.NumeroContrato
              AND  parcelascontaspagar.PagarReceber = contrato.PagarReceber
              AND  contrato.COD_FORNECEDOR = parcelascontaspagar.Cod_Fornecedor
              AND  contrato.Cod_GrupoEmpresa = 1
              AND  contrato.Cod_Empresa = 1
              AND  contrato.Cod_Filial = 1
              AND  g.valortotal IS NOT NULL
              AND  contrato.finalizado = 'F'
              AND  g.DATAINICIO >= DATE '2026-03-01'
              AND  parcelascontaspagar.Datavcto >= ?
              AND  g.datatermino >= SYSDATE
              and  g.numerocontrato not in (3411,3380,2669,3469,3439)
              and  contrato.cod_tipocontrato not in (12)
              and  contrato.numerocontrato not in (2603)
            GROUP BY contrato.numerocontrato,
                     contrato.cod_fornecedor,
                     parcelascontaspagar.documento,
                     contrato.numerocontrato, G.DATAINICIO, G.DATATERMINO, CONTRATO.DESCRICAORESUMIDA

            union all

            SELECT contrato.numerocontrato,
                   material.fn_buscanomefornec(contrato.cod_fornecedor,sysdate) nome,
                   (select max(ob.negocio)
                      from financeiro.historicocontrato h, rh.objetocusto ob
                     where h.numerocontrato = contrato.numerocontrato
                       and h.cod_objetocusto = ob.cod_objetocusto) negocio,
                   g.DATAINICIO,
                   g.DATATERMINO,
                   contrato.DESCRICAORESUMIDA,
                   SUM(parcelascontaspagar.valorparcela) AS TOTAL_PAGO,
                   MAX(g.valortotal) AS VALOR_TOTAL,
                   MAX(g.valortotal) - SUM(parcelascontaspagar.valorparcela) AS DIFERENCA,
                   ROUND(
                       (
                           (((MAX(g.valortotal) - SUM(parcelascontaspagar.valorparcela))
                           / MAX(g.valortotal)) * 100) - 100
                       ) * -1
                   , 2) AS PERCENTUAL_RESTANTE
            FROM   FINANCEIRO.PARCELASCONTASPAGAR parcelascontaspagar,
                   Financeiro.Contrato contrato,
                   financeiro.historicocontrato g
            WHERE  contrato.numerocontrato = g.numerocontrato
              AND  parcelascontaspagar.Provisao = 'N'
              AND  parcelascontaspagar.COD_GRUPOEMPRESA = contrato.Cod_GrupoEmpresa
              AND  parcelascontaspagar.COD_EMPRESA = contrato.Cod_Empresa
              AND  parcelascontaspagar.COD_Filial = contrato.Cod_Filial
              AND  parcelascontaspagar.Documento = contrato.NumeroContrato
              AND  parcelascontaspagar.PagarReceber = contrato.PagarReceber
              AND  contrato.COD_FORNECEDOR = parcelascontaspagar.Cod_Fornecedor
              AND  contrato.Cod_GrupoEmpresa = 1
              AND  contrato.Cod_Empresa = 1
              AND  contrato.Cod_Filial = 1
              AND  g.valortotal IS NOT NULL
              AND  contrato.finalizado = 'F'
              AND  g.DATAINICIO >= DATE '2018-01-01'
              AND  parcelascontaspagar.Datavcto >= ?
              AND  g.datatermino >= SYSDATE
              and  g.numerocontrato not in (3411,3380)
              and  contrato.cod_tipocontrato in (12)
              and  contrato.numerocontrato not in (2603)
            GROUP BY contrato.numerocontrato,
                     contrato.cod_fornecedor,
                     parcelascontaspagar.documento,
                     contrato.numerocontrato, G.DATAINICIO, G.DATATERMINO, CONTRATO.DESCRICAORESUMIDA
        )
        where percentual_restante >= ?
        order by percentual_restante desc
        """;

    /**
     * Contratos que já usaram pelo menos {@code percentualMinimo} do valor
     * contratado.
     *
     * @param dataVctoMinima   só considera parcelas vencendo a partir daqui —
     *                         é o recorte de período do alerta
     * @param percentualMinimo percentual já utilizado a partir do qual o
     *                         contrato entra no alerta (ex.: 70)
     */
    public List<Map<String, Object>> buscarNoLimite(LocalDate dataVctoMinima, double percentualMinimo) {
        try (Connection conn = OracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL)) {

            Date data = Date.valueOf(dataVctoMinima);
            ps.setDate(1, data);
            ps.setDate(2, data);
            ps.setDouble(3, percentualMinimo);

            try (ResultSet rs = ps.executeQuery()) {
                return RowMapperUtil.toList(rs);
            }

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao buscar contratos no limite (dataVcto=" + dataVctoMinima
                    + ", percentual=" + percentualMinimo + "): " + e.getMessage(), e);
            throw new RuntimeException("Falha na consulta de contratos no limite: " + e.getMessage(), e);
        }
    }
}
