# Fluxo de Caixa — Realizado

Módulo Java/Jakarta EE para o **Tomcat 10** no VPS Ubuntu 24.04.

## Estrutura do Projeto

```
fluxo-caixa-realizado/
├── pom.xml
├── deploy.sh
└── src/main/
    ├── java/br/com/lopes/fluxo/
    │   ├── dao/
    │   │   └── FluxoRealizadoDAO.java      ← executa a query Oracle
    │   ├── model/
    │   │   └── FluxoRealizadoItem.java     ← DTO de uma linha
    │   ├── servlet/
    │   │   ├── FluxoRealizadoServlet.java  ← GET /api/fluxo-realizado
    │   │   └── EncodingFilter.java
    │   └── util/
    │       └── OracleConnectionUtil.java   ← config JDBC
    └── webapp/
        ├── WEB-INF/web.xml
        └── index.html                      ← tela principal
```

## Pré-requisitos no Servidor

| Software | Versão |
|----------|--------|
| Java     | 17+    |
| Maven    | 3.8+   |
| Tomcat   | 10.x   |
| Oracle Driver | ojdbc8.jar |

### Copiar o driver Oracle

```bash
# No servidor — copie para o lib do Tomcat (não vai no WAR)
sudo cp ojdbc8.jar /opt/tomcat/lib/
sudo systemctl restart tomcat
```

## Configuração do Banco

Edite **`OracleConnectionUtil.java`** antes de buildar:

```java
private static final String DB_URL  = "jdbc:oracle:thin:@SEU_HOST:1521:SEU_SID";
private static final String DB_USER = "financeiro";
private static final String DB_PASS = "sua_senha";
```

> **Oracle 11g**: se a autenticação falhar com o driver thin, adicione no
> `$CATALINA_HOME/bin/setenv.sh`:
> ```bash
> export JAVA_OPTS="$JAVA_OPTS -Doracle.jdbc.thinForceTNSNames=false"
> ```

## Build & Deploy

### Deploy remoto (VPS)
```bash
chmod +x deploy.sh
./deploy.sh root@app.lopesconsultores.com.br
```

### Deploy local (testar antes)
```bash
./deploy.sh
# ou manualmente:
mvn clean package -DskipTests
sudo cp target/fluxo-caixa.war /opt/tomcat/webapps/
```

## API

```
GET /fluxo-caixa/api/fluxo-realizado?dataIni=YYYY-MM-DD&dataFim=YYYY-MM-DD
```

**Resposta de sucesso:**
```json
{
  "ok": true,
  "data": [
    {
      "codContaFluxo": 2107,
      "descricaoConta": "FOLHA DE PESSOAL",
      "dataPgto": "2026-06-05",
      "realizado": 1179102.23,
      ...
    }
  ]
}
```

**Resposta de erro:**
```json
{ "ok": false, "erro": "mensagem do erro" }
```

## Tela

Acessível em:  
`http://SEU_SERVIDOR:8080/fluxo-caixa/`

- **Filtros**: Data Início, Data Fim, Agrupamento (Conta / Fornecedor / Empenho / Tipo), Periodicidade (Diário / Semanal / Mensal)
- **Tabela pivô**: colunas por período, expandível por linha com detalhe de cada lançamento
- **Cards de resumo**: Total Realizado, Período, Nº de Registros
- **Export**: Excel (TSV) e Impressão/PDF

## Próximos Passos

- [ ] Adicionar aba "A Realizar" (query de parcelas em aberto)
- [ ] Autenticação: integrar ao sistema de login existente (App Lopes)
- [ ] Pool de conexões com DBCP2 (substituir `DriverManager`)
- [ ] Nginx proxy reverso: `/fluxo-caixa/` → Tomcat
# fluxo-caixa-realizado
