package br.com.lopes.fluxo.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Leitor de planilha .xlsx com a biblioteca padrão do Java.
 *
 * Um .xlsx é um zip de XML, então dá para abrir sem POI nem nada — e o
 * projeto ganha a importação sem uma dependência de vinte megabytes que
 * ninguém mais usaria. O que se lê aqui é texto de célula; fórmula entra
 * pelo valor calculado, que é o que o Excel grava junto.
 *
 * Trata o que costuma quebrar leitor caseiro:
 *  - sharedStrings.xml, com o texto formatado quebrado em vários &lt;t&gt;
 *    dentro de &lt;r&gt;, que precisam ser concatenados;
 *  - célula VAZIA, que simplesmente não aparece no XML — a posição vem da
 *    referência ("A1", "BC7"), nunca da ordem em que as células aparecem;
 *  - inlineStr, que guarda o texto na própria célula.
 *
 * Não interpreta formato: data vem como número de série do Excel, e quem
 * precisar converte. Aqui ninguém precisa — as colunas que importam são
 * texto.
 */
public final class XlsxUtil {

    private static final String NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    /** Teto de segurança: planilha de índice tem centenas de linhas, não milhões. */
    private static final int MAX_LINHAS = 200_000;

    private XlsxUtil() {}

    /** Uma aba: o nome e as linhas, cada linha uma lista de células como texto. */
    public static final class Aba {
        public final String nome;
        public final List<List<String>> linhas;
        Aba(String nome, List<List<String>> linhas) { this.nome = nome; this.linhas = linhas; }

        /** A célula, ou "" se a linha for curta — planilha tem buraco o tempo todo. */
        public String celula(int linha, int coluna) {
            if (linha < 0 || linha >= linhas.size()) return "";
            List<String> l = linhas.get(linha);
            return coluna < 0 || coluna >= l.size() ? "" : l.get(coluna);
        }
    }

    /** Todas as abas, na ordem da pasta de trabalho. */
    public static List<Aba> ler(InputStream entrada) throws IOException {
        Map<String, byte[]> partes = descompactar(entrada);

        List<String> compartilhadas = strings(partes.get("xl/sharedStrings.xml"));
        Map<String, String> alvoPorId = relacoes(partes.get("xl/_rels/workbook.xml.rels"));

        List<Aba> abas = new ArrayList<>();
        Document wb = xml(partes.get("xl/workbook.xml"));
        if (wb == null) return abas;

        NodeList sheets = wb.getElementsByTagNameNS(NS, "sheet");
        for (int i = 0; i < sheets.getLength(); i++) {
            Element s = (Element) sheets.item(i);
            String alvo = alvoPorId.get(s.getAttributeNS(NS_REL, "id"));
            if (alvo == null) continue;
            byte[] dados = partes.get(normalizar(alvo));
            if (dados == null) continue;
            abas.add(new Aba(s.getAttribute("name"), linhas(dados, compartilhadas)));
        }
        return abas;
    }

    /** A aba de nome dado, sem diferenciar maiúsculas — "indice" e "Índice". */
    public static Aba aba(List<Aba> abas, String nome) {
        for (Aba a : abas) {
            if (a.nome != null && a.nome.trim().equalsIgnoreCase(nome)) return a;
        }
        return null;
    }

    // ── Leitura ───────────────────────────────────────────────────────────

    private static Map<String, byte[]> descompactar(InputStream entrada) throws IOException {
        Map<String, byte[]> partes = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(entrada)) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String nome = e.getName();
                // Só o que interessa: o resto de um xlsx (temas, estilos,
                // imagens) pode ser bem grande e não é lido por ninguém aqui.
                if (nome.startsWith("xl/worksheets/") || nome.equals("xl/workbook.xml")
                        || nome.equals("xl/sharedStrings.xml") || nome.equals("xl/_rels/workbook.xml.rels")) {
                    partes.put(nome, zip.readAllBytes());
                }
            }
        }
        return partes;
    }

    private static String normalizar(String alvo) {
        if (alvo.startsWith("/xl/")) return alvo.substring(1);
        if (alvo.startsWith("xl/")) return alvo;
        return "xl/" + (alvo.startsWith("/") ? alvo.substring(1) : alvo);
    }

    private static Document xml(byte[] dados) {
        if (dados == null) return null;
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            // Planilha vem de fora: entidade externa em XML é porta de leitura
            // de arquivo do servidor, e não há motivo nenhum para permitir.
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setExpandEntityReferences(false);
            DocumentBuilder b = f.newDocumentBuilder();
            return b.parse(new ByteArrayInputStream(dados));
        } catch (Exception e) {
            throw new IllegalArgumentException("XML da planilha ilegível: " + e.getMessage(), e);
        }
    }

    private static List<String> strings(byte[] dados) {
        List<String> out = new ArrayList<>();
        Document d = xml(dados);
        if (d == null) return out;
        NodeList sis = d.getElementsByTagNameNS(NS, "si");
        for (int i = 0; i < sis.getLength(); i++) {
            out.add(textoDe((Element) sis.item(i)));
        }
        return out;
    }

    private static Map<String, String> relacoes(byte[] dados) {
        Map<String, String> m = new LinkedHashMap<>();
        Document d = xml(dados);
        if (d == null) return m;
        NodeList rs = d.getElementsByTagName("*");
        for (int i = 0; i < rs.getLength(); i++) {
            Node n = rs.item(i);
            if (!"Relationship".equals(n.getLocalName())) continue;
            Element e = (Element) n;
            m.put(e.getAttribute("Id"), e.getAttribute("Target"));
        }
        return m;
    }

    /** Junta todos os &lt;t&gt; do elemento: texto formatado vem em pedaços. */
    private static String textoDe(Element el) {
        StringBuilder sb = new StringBuilder();
        NodeList ts = el.getElementsByTagNameNS(NS, "t");
        for (int i = 0; i < ts.getLength(); i++) sb.append(ts.item(i).getTextContent());
        return sb.toString();
    }

    private static List<List<String>> linhas(byte[] dados, List<String> compartilhadas) {
        List<List<String>> out = new ArrayList<>();
        Document d = xml(dados);
        if (d == null) return out;
        NodeList rows = d.getElementsByTagNameNS(NS, "row");
        for (int i = 0; i < rows.getLength() && out.size() < MAX_LINHAS; i++) {
            Element row = (Element) rows.item(i);
            Map<Integer, String> celulas = new LinkedHashMap<>();
            int maior = -1;

            NodeList cs = row.getElementsByTagNameNS(NS, "c");
            for (int j = 0; j < cs.getLength(); j++) {
                Element c = (Element) cs.item(j);
                String ref = c.getAttribute("r");
                if (ref.isEmpty()) continue;
                int coluna = coluna(ref);
                String tipo = c.getAttribute("t");
                String valor;
                if ("inlineStr".equals(tipo)) {
                    valor = textoDe(c);
                } else {
                    NodeList vs = c.getElementsByTagNameNS(NS, "v");
                    valor = vs.getLength() > 0 ? vs.item(0).getTextContent() : "";
                    if ("s".equals(tipo) && !valor.isEmpty()) {
                        try {
                            int k = Integer.parseInt(valor.trim());
                            valor = k >= 0 && k < compartilhadas.size() ? compartilhadas.get(k) : "";
                        } catch (NumberFormatException ignore) { valor = ""; }
                    }
                }
                celulas.put(coluna, valor == null ? "" : valor.trim());
                if (coluna > maior) maior = coluna;
            }

            List<String> linha = new ArrayList<>();
            for (int k = 0; k <= maior; k++) linha.add(celulas.getOrDefault(k, ""));
            out.add(linha);
        }
        return out;
    }

    /** "BC7" -> 54 (base zero). A posição está na referência, não na ordem. */
    private static int coluna(String ref) {
        int n = 0;
        for (int i = 0; i < ref.length(); i++) {
            char c = Character.toUpperCase(ref.charAt(i));
            if (c < 'A' || c > 'Z') break;
            n = n * 26 + (c - 'A' + 1);
        }
        return n - 1;
    }
}
