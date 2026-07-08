package br.com.lopes.fluxo.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Representa uma linha retornada pela query de Fluxo de Caixa Realizado.
 * Campos mapeados diretamente da SELECT externa da query SQL.
 */
public class FluxoRealizadoItem {

    private Integer    codContaFluxo;
    private String     descricaoConta;
    private String     codEmpenho;
    private String     descEmpenho;
    private Integer    codFornecedor;
    private String     nome;
    private Integer    codTipoContasPagar;
    private String     descricaoTipoDeContasPagar;
    private LocalDate  dataEntrada;
    private LocalDate  dataPgto;
    private LocalDate  dataVctoOrig;
    private LocalDate  dataVcto;
    private LocalDate  dataCriacao;
    private String     usuario;
    private String     pagarReceber;
    private String     documento;
    private String     parcela;
    private BigDecimal realizado;

    // ─── Getters / Setters ────────────────────────────────────────────────

    public Integer getCodContaFluxo()                  { return codContaFluxo; }
    public void    setCodContaFluxo(Integer v)         { this.codContaFluxo = v; }

    public String  getDescricaoConta()                 { return descricaoConta; }
    public void    setDescricaoConta(String v)         { this.descricaoConta = v; }

    public String  getCodEmpenho()                     { return codEmpenho; }
    public void    setCodEmpenho(String v)             { this.codEmpenho = v; }

    public String  getDescEmpenho()                    { return descEmpenho; }
    public void    setDescEmpenho(String v)            { this.descEmpenho = v; }

    public Integer getCodFornecedor()                  { return codFornecedor; }
    public void    setCodFornecedor(Integer v)         { this.codFornecedor = v; }

    public String  getNome()                           { return nome; }
    public void    setNome(String v)                   { this.nome = v; }

    public Integer getCodTipoContasPagar()             { return codTipoContasPagar; }
    public void    setCodTipoContasPagar(Integer v)    { this.codTipoContasPagar = v; }

    public String  getDescricaoTipoDeContasPagar()     { return descricaoTipoDeContasPagar; }
    public void    setDescricaoTipoDeContasPagar(String v) { this.descricaoTipoDeContasPagar = v; }

    public LocalDate getDataEntrada()                  { return dataEntrada; }
    public void      setDataEntrada(LocalDate v)       { this.dataEntrada = v; }

    public LocalDate getDataPgto()                     { return dataPgto; }
    public void      setDataPgto(LocalDate v)          { this.dataPgto = v; }

    public LocalDate getDataVctoOrig()                 { return dataVctoOrig; }
    public void      setDataVctoOrig(LocalDate v)      { this.dataVctoOrig = v; }

    public LocalDate getDataVcto()                     { return dataVcto; }
    public void      setDataVcto(LocalDate v)          { this.dataVcto = v; }

    public LocalDate getDataCriacao()                  { return dataCriacao; }
    public void      setDataCriacao(LocalDate v)       { this.dataCriacao = v; }

    public String  getUsuario()                        { return usuario; }
    public String  getDocumento()                      { return documento; }
    public String  getParcela()                        { return parcela; }
    public void    setUsuario(String v)                { this.usuario = v; }
    public void    setDocumento(String v)              { this.documento = v; }
    public void    setParcela(String v)                { this.parcela = v; }

    public String  getPagarReceber()                   { return pagarReceber; }
    public void    setPagarReceber(String v)           { this.pagarReceber = v; }

    public BigDecimal getRealizado()                   { return realizado; }
    public void       setRealizado(BigDecimal v)       { this.realizado = v; }
}
