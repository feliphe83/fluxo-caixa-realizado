package br.com.lopes.fluxo.util;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Transforma a planilha da controladoria no de/para entre conta contábil e
 * linha do demonstrativo (ou grupo do balanço).
 *
 * As regras aqui são as mesmas dos geradores em ferramentas/ — e precisam
 * continuar sendo. Quem importar pela tela e quem rodar o script tem que
 * chegar no mesmo mapa, senão o número da tela passa a depender de por onde
 * o arquivo entrou.
 *
 * PARA DE PROPÓSITO quando não consegue resolver uma conta. Um de/para
 * incompleto não deixa rastro: as contas que sobraram simplesmente somem do
 * demonstrativo, e os totais continuam fechando, só que menores. Melhor
 * recusar a importação inteira e dizer qual conta é.
 */
public final class IndiceContabilParser {

    private IndiceContabilParser() {}

    /** O que a importação produziu, ou o motivo de não ter produzido. */
    public static final class Resultado {
        /** conta -> valores (1 campo no DRE, 4 no balanço). */
        public final Map<String, String[]> mapa = new LinkedHashMap<>();
        /** Contas que não puderam ser resolvidas — se houver, nada é aplicado. */
        public final List<String> problemas = new ArrayList<>();
        /** Só para a tela: quantas contas por destino. */
        public final Map<String, Integer> porDestino = new LinkedHashMap<>();
        public boolean ok() { return problemas.isEmpty() && !mapa.isEmpty(); }
    }

    // ── Demonstrativo do Resultado ────────────────────────────────────────

    /** rótulo como está na planilha -> chave da linha em dre-historico.js. */
    private static final Map<String, String> CHAVE_DRE = new LinkedHashMap<>();
    static {
        CHAVE_DRE.put("(+) RECEITA BRUTA DE VENDAS",              "receita_bruta");
        CHAVE_DRE.put("(-)TRIBUTOS SOBRE VENDAS",                 "tributos");
        CHAVE_DRE.put("(-)CUSTOS DOS PRODUTOS VENDIDOS",          "cpv");
        CHAVE_DRE.put("CUSTO DE OCIOSIDADE",                      "ociosidade");
        CHAVE_DRE.put("DESPESAS COM VENDAS",                      "desp_vendas");
        CHAVE_DRE.put("DESPESAS GERAIS E ADMINISTRATIVAS",        "desp_admin");
        CHAVE_DRE.put("OUTRAS (RECEITAS)/ DESPESAS OPERACIONAIS", "outras_op");
        CHAVE_DRE.put("RESULTADO DE EQUIVALÊNCIA PATRIMONIAL",    "equivalencia");
        CHAVE_DRE.put("DESPESAS/RECEITAS Ñ RECORRENTE",           "nao_recorrente");
        CHAVE_DRE.put("RESULTADO FINANCEIRO LÍQUIDO",             "financeiro");
        // 3.2.3 apropria-se ao custo e se anula. Fica mapeada de propósito,
        // para o servlet poder afirmar que nenhuma conta ficou sem destino.
        CHAVE_DRE.put("CUSTO",                                    "apropriacao");
    }

    public static Resultado lerDre(InputStream entrada) throws java.io.IOException {
        Resultado r = new Resultado();
        List<XlsxUtil.Aba> abas = XlsxUtil.ler(entrada);
        XlsxUtil.Aba idx = XlsxUtil.aba(abas, "indice");
        if (idx == null) {
            r.problemas.add("A planilha não tem uma aba chamada \"indice\".");
            return r;
        }
        Set<String> desconhecidos = new LinkedHashSet<>();
        for (List<String> l : idx.linhas) {
            if (l.size() < 4) continue;
            String conta = l.get(0).trim(), flag = l.get(2).trim(), rotulo = l.get(3).trim();
            // Só contas ANALÍTICAS. A sintética é o somatório das filhas:
            // somar as duas contaria cada real duas vezes.
            if (conta.isEmpty() || flag.isEmpty() || "S".equalsIgnoreCase(flag)) continue;
            String chave = CHAVE_DRE.get(rotulo);
            if (chave == null) { desconhecidos.add(rotulo); continue; }
            r.mapa.put(conta, new String[]{ chave });
            r.porDestino.merge(chave, 1, Integer::sum);
        }
        for (String d : desconhecidos) {
            r.problemas.add("Linha do DRE desconhecida na planilha: \"" + d + "\".");
        }
        if (r.mapa.isEmpty() && r.problemas.isEmpty()) {
            r.problemas.add("A aba \"indice\" não trouxe nenhuma conta analítica.");
        }
        return r;
    }

    // ── Balanço Patrimonial ───────────────────────────────────────────────

    /** prefixo de dois níveis -> o nível a que ele pertence, para desempatar. */
    private static final Map<String, String> NIVEL_DO_PREFIXO = new LinkedHashMap<>();
    static {
        NIVEL_DO_PREFIXO.put("1.1", "Ativo  Circulante");
        NIVEL_DO_PREFIXO.put("1.2", "Realizável a Longo Prazo");
        NIVEL_DO_PREFIXO.put("2.1", "Passivo Circulante");
        NIVEL_DO_PREFIXO.put("2.2", "Passivo Não Circulante");
        NIVEL_DO_PREFIXO.put("2.4", "Patrimônio líquido");
        // 1.3 fica de fora: Investimentos e Imobilizado moram lá, e o rótulo
        // já separa os dois sozinho.
    }

    /**
     * O que nem o rótulo nem o prefixo de dois níveis resolvem.
     *
     * 2.1.2.15 é "CONTRATOS DE MUTUO / EMPRESTIMO" no balancete, e é
     * exatamente a linha "Credores sob contrato" do circulante na planilha.
     * Duas contas ali chamam-se "Raizen" no índice, nome que também existe
     * sob "Empréstimos e financiamentos" — pelo rótulo elas iriam para a
     * linha errada, e o total continuaria fechando.
     */
    private static final Map<String, String[]> FORCADO = new LinkedHashMap<>();
    static {
        FORCADO.put("2.1.2.15",
                new String[]{ "Passivo", "Passivo Circulante", "Passivo Circulante", "Credores sob contrato" });
    }

    private static final String NAO_UTILIZAR = "NÃO UTILIZAR";

    public static Resultado lerBalanco(InputStream entrada) throws java.io.IOException {
        Resultado r = new Resultado();
        List<XlsxUtil.Aba> abas = XlsxUtil.ler(entrada);
        XlsxUtil.Aba idx = XlsxUtil.aba(abas, "indice");
        XlsxUtil.Aba ativo = XlsxUtil.aba(abas, "Ativo");
        XlsxUtil.Aba passivo = XlsxUtil.aba(abas, "passivo");
        if (idx == null || ativo == null || passivo == null) {
            r.problemas.add("A planilha precisa das abas \"Ativo\", \"passivo\" e \"indice\".");
            return r;
        }

        // rótulo (descrição OU nível 2) -> os lugares onde ele aparece
        Map<String, List<String[]>> candidatos = new LinkedHashMap<>();
        for (XlsxUtil.Aba aba : List.of(ativo, passivo)) {
            for (int i = 1; i < aba.linhas.size(); i++) {   // linha 0 é cabeçalho
                List<String> l = aba.linhas.get(i);
                if (l.size() < 10) continue;
                String tipo = l.get(1).trim(), grupo = l.get(3).trim();
                String nivel = l.get(5).trim(), nivel2 = l.get(7).trim(), desc = l.get(9).trim();
                if (nivel2.isEmpty()) continue;
                String[] chave = { tipo, grupo, nivel, nivel2 };
                for (String rot : List.of(desc, nivel2)) {
                    if (rot.isEmpty()) continue;
                    List<String[]> lista = candidatos.computeIfAbsent(rot, k -> new ArrayList<>());
                    boolean repetido = lista.stream().anyMatch(c -> java.util.Arrays.equals(c, chave));
                    if (!repetido) lista.add(chave);
                }
            }
        }

        for (List<String> l : idx.linhas) {
            if (l.size() < 4) continue;
            String conta = l.get(0).trim(), flag = l.get(2).trim(), rotulo = l.get(3).trim();
            if (conta.isEmpty() || flag.isEmpty() || "S".equalsIgnoreCase(flag)) continue;
            if (contarPontos(conta) != 4) continue;            // só analítica
            if (NAO_UTILIZAR.equalsIgnoreCase(rotulo)) continue;

            String[] escolha = null;
            for (Map.Entry<String, String[]> e : FORCADO.entrySet()) {
                if (conta.startsWith(e.getKey() + ".")) { escolha = e.getValue(); break; }
            }
            if (escolha == null) {
                List<String[]> cands = candidatos.get(rotulo);
                if (cands == null || cands.isEmpty()) {
                    r.problemas.add("Conta " + conta + ": o rótulo \"" + rotulo
                            + "\" não existe nas abas Ativo/passivo.");
                    continue;
                }
                if (cands.size() == 1) {
                    escolha = cands.get(0);
                } else {
                    String nivel = NIVEL_DO_PREFIXO.get(prefixo2(conta));
                    List<String[]> filtrados = new ArrayList<>();
                    for (String[] c : cands) if (c[2].equals(nivel)) filtrados.add(c);
                    if (filtrados.size() != 1) {
                        r.problemas.add("Conta " + conta + ": \"" + rotulo + "\" aparece em "
                                + cands.size() + " lugares e o prefixo " + prefixo2(conta)
                                + " não decidiu entre eles.");
                        continue;
                    }
                    escolha = filtrados.get(0);
                }
            }
            r.mapa.put(conta, escolha);
            r.porDestino.merge(escolha[0] + " · " + escolha[3], 1, Integer::sum);
        }

        if (r.mapa.isEmpty() && r.problemas.isEmpty()) {
            r.problemas.add("A aba \"indice\" não trouxe nenhuma conta analítica.");
        }
        return r;
    }

    private static int contarPontos(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '.') n++;
        return n;
    }

    private static String prefixo2(String conta) {
        int p = conta.indexOf('.');
        if (p < 0) return conta;
        int q = conta.indexOf('.', p + 1);
        return q < 0 ? conta : conta.substring(0, q);
    }
}
