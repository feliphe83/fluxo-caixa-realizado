package br.com.lopes.fluxo.dao;

import br.com.lopes.fluxo.util.OracleConnectionUtil;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Tipos de serviço tratados como CORTE DE CANA no Controle de Serviços.
 *
 * A lista decide como cada apontamento entra no relatório, em três lugares da
 * consulta ao ERP:
 *
 * - FATURAMENTO / CORTE DE CANA — os que estão na lista, faturados por
 *   tonelada (qtde/1000);
 * - MÃO DE OBRA / LIDERAÇÃO RURAL — todos os que NÃO estão nela;
 * - FATURAMENTO / EPI, FERRAMENTAS E GELO — cobrado por tonelada cortada,
 *   e portanto só sobre os que estão na lista.
 *
 * Tirar ou pôr um código aqui move dinheiro de um bloco para o outro do
 * relatório. Antes esses sete códigos estavam escritos na consulta, e mudá-los
 * exigia deploy — agora ficam no cadastro, onde quem conhece o serviço mexe.
 */
public class ServicoCorteDAO {

    private static final Logger LOG = Logger.getLogger(ServicoCorteDAO.class.getName());

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/intranet?useSSL=false&serverTimezone=America/Recife&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "lopes_app";
    private static final String DB_PASS = "Lopes@App2024";

    /** Os que estavam na consulta — semeados uma vez, na criação da tabela. */
    private static final int[] INICIAIS = { 5558, 5554, 5532, 5526, 5555, 5531, 5553 };

    private Connection conn() throws SQLException {
        Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        try (Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS fc_servico_corte (
                  cod_tiposervico INT PRIMARY KEY,
                  descricao VARCHAR(150),
                  criado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  criado_por VARCHAR(120)
                )
                """);
            for (int cod : INICIAIS) {
                st.execute("INSERT IGNORE INTO fc_servico_corte (cod_tiposervico, criado_por) VALUES ("
                        + cod + ", 'carga inicial')");
            }
        }
        return c;
    }

    public void garantirEstrutura() throws SQLException {
        conn().close();
    }

    /** Os códigos configurados, em ordem. */
    public List<Integer> codigos() throws SQLException {
        List<Integer> l = new ArrayList<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT cod_tiposervico FROM fc_servico_corte ORDER BY cod_tiposervico");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) l.add(rs.getInt(1));
        }
        return l;
    }

    /**
     * A lista pronta para entrar num IN da consulta ao Oracle.
     *
     * Os valores vêm de uma coluna INT, então já são números — mas montar
     * texto de SQL a partir do banco é o tipo de coisa que um dia deixa de ser
     * verdade. O filtro por dígitos custa nada e fecha a porta.
     *
     * Lista vazia devolve um código impossível em vez de "IN ()", que é erro
     * de sintaxe: sem nenhum serviço marcado, nada é corte de cana, e é isso
     * que o relatório deve mostrar.
     */
    public String listaIn() throws SQLException {
        return juntar(codigos());
    }

    /**
     * Como {@link #listaIn()}, mas não deixa o MySQL derrubar o relatório.
     *
     * Antes do cadastro existir, a lista estava escrita na consulta e o
     * Controle de Serviços não dependia do MySQL para nada. Se o banco local
     * cair, o relatório volta a se comportar como antes em vez de sair com
     * erro — e o log diz que a configuração não foi lida.
     */
    public String listaInOuPadrao() {
        try {
            return listaIn();
        } catch (SQLException e) {
            LOG.warning("Cadastro de serviços de corte indisponível, usando a lista padrão: " + e.getMessage());
            List<Integer> padrao = new ArrayList<>();
            for (int cod : INICIAIS) padrao.add(cod);
            return juntar(padrao);
        }
    }

    private static String juntar(List<Integer> cods) {
        StringBuilder sb = new StringBuilder();
        for (Integer cod : cods) {
            if (cod == null) continue;
            String s = String.valueOf(cod.intValue());
            if (!s.matches("-?\\d+")) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(s);
        }
        return sb.length() == 0 ? "-1" : sb.toString();
    }

    /**
     * Os configurados com sua descrição.
     *
     * A descrição gravada no cadastro vem primeiro; o ERP só é consultado
     * pelos que ainda não têm uma — os sete da carga inicial, que entraram sem
     * passar pela tela.
     */
    public List<Map<String, Object>> configurados() throws SQLException {
        Map<Integer, String> gravadas = new LinkedHashMap<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT cod_tiposervico, descricao FROM fc_servico_corte ORDER BY cod_tiposervico");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) gravadas.put(rs.getInt(1), rs.getString(2));
        }

        List<Integer> semDescricao = new ArrayList<>();
        for (Map.Entry<Integer, String> e : gravadas.entrySet()) {
            if (e.getValue() == null || e.getValue().isBlank()) semDescricao.add(e.getKey());
        }
        Map<Integer, String> doErp = descricoesDoErp(semDescricao);

        List<Map<String, Object>> lista = new ArrayList<>();
        for (Map.Entry<Integer, String> e : gravadas.entrySet()) {
            String desc = (e.getValue() == null || e.getValue().isBlank())
                    ? doErp.get(e.getKey()) : e.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("cod", e.getKey());
            m.put("descricao", desc);
            lista.add(m);
        }
        return lista;
    }

    /**
     * Busca no ERP a descrição dos códigos informados.
     *
     * Falhar aqui não derruba a tela: sem a descrição o cadastro ainda mostra
     * os códigos, e é melhor do que uma tela vazia por causa do Oracle.
     */
    private Map<Integer, String> descricoesDoErp(List<Integer> cods) {
        Map<Integer, String> nomes = new LinkedHashMap<>();
        if (cods.isEmpty()) return nomes;
        StringBuilder in = new StringBuilder();
        for (Integer c : cods) {
            if (in.length() > 0) in.append(',');
            in.append(c.intValue());
        }
        String sql = "select distinct cod_tiposervico, descricao from rh.tiposervico "
                   + "where cod_tiposervico in (" + in + ")";
        try (Connection c = OracleConnectionUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) nomes.put(rs.getInt(1), rs.getString(2));
        } catch (SQLException e) {
            LOG.warning("Não foi possível ler a descrição dos tipos de serviço: " + e.getMessage());
        }
        return nomes;
    }

    /** Todos os tipos de serviço do ERP, para escolher sem digitar código. */
    public List<Map<String, Object>> disponiveis() throws SQLException {
        List<Map<String, Object>> lista = new ArrayList<>();
        // distinct: se o ERP guardar o mesmo tipo de serviço em mais de uma
        // vigência, a tela mostraria o código repetido — e escolher o "outro"
        // não faria diferença nenhuma.
        String sql = "select distinct cod_tiposervico, descricao from rh.tiposervico order by descricao";
        try (Connection c = OracleConnectionUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("cod", rs.getInt(1));
                m.put("descricao", rs.getString(2));
                lista.add(m);
            }
        }
        return lista;
    }

    /**
     * Troca a lista inteira. Apagar e regravar numa transação, e não comparar
     * item a item: a tela manda o conjunto final, e no meio do caminho ninguém
     * pode consultar o relatório com a lista pela metade.
     */
    public void gravar(List<Integer> codigos, String quem) throws SQLException {
        // A descrição é buscada uma vez e guardada junto: assim a tela continua
        // legível mesmo com o ERP fora do ar, que é justamente quando alguém
        // vai querer conferir o que está configurado.
        Map<Integer, String> nomes = descricoesDoErp(codigos == null ? List.of() : codigos);

        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                try (Statement st = c.createStatement()) {
                    st.execute("DELETE FROM fc_servico_corte");
                }
                if (codigos != null && !codigos.isEmpty()) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO fc_servico_corte (cod_tiposervico, descricao, criado_por) VALUES (?,?,?)")) {
                        for (Integer cod : codigos) {
                            if (cod == null) continue;
                            ps.setInt(1, cod);
                            ps.setString(2, nomes.get(cod));
                            ps.setString(3, quem);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                c.commit();
                LOG.info("Serviços de corte de cana alterados por " + quem + ": " + codigos);
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }
}
