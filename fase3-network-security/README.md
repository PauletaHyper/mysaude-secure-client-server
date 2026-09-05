# Segurança de Rede — iptables + snort

Fase 3 do projeto MySaude: proteção do servidor ao nível de rede, complementando a segurança aplicacional (TLS, autenticação, criptografia) das fases anteriores.

- **`firewall/`** — Regras `iptables` que restringem o acesso ao servidor mySaude apenas às origens autorizadas
- **`ids/`** — Deteção de intrusão com `snort`: 3 regras próprias (`mysaude.rules`) para detetar varrimento de portos, força bruta contra o servidor e flood de ICMP
- **`tests/`** — Scripts para gerar tráfego e validar que as regras de firewall e IDS disparam corretamente

================================================================================
 TRABALHO 3 — Segurança Informática 2025/26
 Parte I: iptables  |  Parte II: snort
================================================================================


================================================================================
 ESTRUTURA DE FICHEIROS
================================================================================

 Terceira fase/
 ├── setup_servidor.sh          Faz o setup completo (compilar, keystores,
 │                              utilizadores). Correr UMA VEZ antes de tudo.
 │
 ├── firewall/
 │   ├── iptables_mysaude.sh    Aplica as regras de firewall no servidor.
 │   │                          EDITAR: preencher GCC_IP antes de correr.
 │   └── remover_regras.sh      Apaga todas as regras e repõe políticas
 │                              ACCEPT. Usar após os testes.
 │
 ├── ids/
 │   ├── mysaude.rules          As 3 regras snort (S1, S2, S3).
 │   └── mysaude_snort.conf     Configuração mínima do snort.
 │                              EDITAR: ajustar HOME_NET antes de correr.
 │
 └── tests/
     ├── teste_firewall.sh      Testa as regras iptables (correr no SERVIDOR).
     └── teste_snort.sh         Gera tráfego para testar o snort
                                (correr no CLIENTE, recebe IP do servidor).


================================================================================
 CONFIGURAÇÃO OBRIGATÓRIA (antes de correr qualquer coisa)
================================================================================

 1. Preencher o IP da máquina gcc em firewall/iptables_mysaude.sh:

      GCC_IP="10.101.X.X"    <- obter com: host gcc

 2. Ajustar a sub-rede do servidor em ids/mysaude_snort.conf:

      var HOME_NET 10.101.84.0/23   <- obter IP com: ip addr
                                       (servidor em 10.101.85.X → 10.101.84.0/23)
                                       (servidor em 10.121.53.X → 10.121.52.0/23)


================================================================================
 PASSO A PASSO — COMO RODAR O PROJETO COMPLETO
================================================================================

 ONDE CORRE CADA COISA:
   Servidor  — mySaude (Fase 2) + iptables (Fase 3) + snort (Fase 3)
   Cliente   — cliente mySaude (Fase 2) + teste_snort.sh (Fase 3)


--------------------------------------------------------------------------------
 PASSO 1 — SETUP INICIAL (só na primeira vez, no SERVIDOR)
--------------------------------------------------------------------------------

    cd "Terceira fase/"
    ./setup_servidor.sh

    O script faz automaticamente:
      - Compila todo o código Java (Fase 2)
      - Cria keystores RSA-2048 para usera, userb, userc, userd
      - Exporta e importa certificados entre keystores
      - Cria os utilizadores: usera (medico), userb (medico),
                              userc (medico), bob (utente)
      - Mostra os comandos exatos para os passos seguintes


--------------------------------------------------------------------------------
 PASSO 2 — COPIAR FICHEIROS PARA O CLIENTE
--------------------------------------------------------------------------------

    Copiar (pen drive ou rede) para a máquina cliente:
      - keystore.usera
      - keystore.userb    (ou keystore.userc, conforme o utilizador do cliente)
      - pasta client/ (com os .class compilados)


--------------------------------------------------------------------------------
 PASSO 3 — ARRANCAR O SERVIDOR mySaude — TERMINAL 1 (no SERVIDOR)
--------------------------------------------------------------------------------

    cd "Segunda Fase/"
    export _JAVA_OPTIONS="-Djavax.net.ssl.trustStore=keystore.usera \
                          -Djavax.net.ssl.trustStorePassword=123456"
    echo macpassword123 | java server.MySaudeServer 8080 keystore.usera 123456

    Resultado esperado:
      MAC do ficheiro 'users' verificado com sucesso.
      Servidor MySaude (TLS) a correr no porto 8080

    NOTA: este terminal fica bloqueado. Abrir novos terminais para os passos seguintes.


--------------------------------------------------------------------------------
 PASSO 4 — ATIVAR FIREWALL — TERMINAL 2 (no SERVIDOR, após passo 3)
--------------------------------------------------------------------------------

    sudo "Terceira fase/firewall/iptables_mysaude.sh"

    O que faz:
      - Define política fechada (INPUT/OUTPUT/FORWARD DROP por omissão)
      - R1: aceita SSH (porta 22) apenas da máquina gcc
      - R2: aceita ping apenas da sub-rede local (/23)
      - R3: aceita ligações ao mySaude (porta 8080) de qualquer origem
      - R4: permite ping de saída apenas para gcc
      - Loopback e ligações estabelecidas sempre aceites

    Para remover as regras após os testes:
      sudo "Terceira fase/firewall/remover_regras.sh"


--------------------------------------------------------------------------------
 PASSO 5 — ARRANCAR SNORT — TERMINAL 3 (no SERVIDOR, após passo 3)
--------------------------------------------------------------------------------

    Obter a interface de rede:
      ip link show   (ex: eth0, ens3)

    cd "Terceira fase/ids/"
    sudo snort -i <interface> -A console -q -c mysaude_snort.conf

    Exemplos:
      sudo snort -i eth0  -A console -q -c mysaude_snort.conf
      sudo snort -i ens3  -A console -q -c mysaude_snort.conf

    O snort fica à escuta e imprime alertas na consola quando deteta:
      [S1] Varrimento de portos (6 TCP SYN para portos < 2048 em 2 min)
      [S2] Brute-force ao mySaude (7 ligações do mesmo IP em 30s)
      [S3] Flood ICMP (primeiros 5 pings em 45s)


--------------------------------------------------------------------------------
 PASSO 6 — TESTAR TUDO — no CLIENTE
--------------------------------------------------------------------------------

    --- Testar Fase 3 (firewall + snort) ---

    export _JAVA_OPTIONS="-Djavax.net.ssl.trustStore=keystore.usera \
                          -Djavax.net.ssl.trustStorePassword=123456"
    "Terceira fase/tests/teste_snort.sh" <IP_SERVIDOR>

    O script testa automaticamente:
      - R3: porta 8080 acessível de qualquer origem
      - R1: SSH bloqueado para máquinas que não são gcc
      - R2: ping aceite/bloqueado conforme sub-rede
      - S1: envia 6 SYN para portos < 2048 (verificar alerta no snort)
      - S2: envia 7 ligações à porta 8080 (verificar 1 alerta no snort)
      - S3: envia 10 pings (verificar 5 alertas no snort)

    --- Testar Fase 2 (mySaude com firewall ativa) ---

    Confirmar que o mySaude continua a funcionar normalmente:

      java client.MySaude -s <IP>:8080 -u userb -p 123456 -t userc -e ficheiro.txt
      java client.MySaude -s <IP>:8080 -u userc -p 123456 -r ficheiro.txt
      java client.MySaude -s <IP>:8080 -u userb -p 123456 -t userc -ce ficheiro.txt
      java client.MySaude -s <IP>:8080 -u userc -p 123456 -rd ficheiro.txt
      java client.MySaude -s <IP>:8080 -u userb -p 123456 -t userc -ace ficheiro.txt
      java client.MySaude -s <IP>:8080 -u userc -p 123456 -t userb -rdv ficheiro.txt


--------------------------------------------------------------------------------
 PASSO 7 — TESTAR FIREWALL DO SERVIDOR — TERMINAL 2 (no SERVIDOR)
--------------------------------------------------------------------------------

    sudo "Terceira fase/tests/teste_firewall.sh"

    Testa automaticamente:
      - Antes das regras: confirma que tudo é permitido
      - Ativa as regras iptables
      - Verifica restrições: ping bloqueado para não-gcc (R4)
      - Verifica casos permitidos: ping para gcc funciona (R4),
        porta 8080 ainda acessível (R3)


================================================================================
 POLÍTICA DE FIREWALL (Parte I)
================================================================================

  R1: SSH (porta 22)           — INPUT, apenas da máquina gcc
  R2: Ping entrada             — INPUT, apenas da sub-rede local (/23)
  R3: mySaude (porta 8080)     — INPUT, qualquer origem
  R4: Ping saída               — OUTPUT, apenas para gcc
  Loopback                     — sempre aceite (INPUT + OUTPUT)
  ESTABLISHED/RELATED          — sempre aceite (INPUT + OUTPUT)
  Tudo o resto                 — DROP (política fechada)


================================================================================
 REGRAS SNORT (Parte II)
================================================================================

  S1: 6 ligações TCP para portos < 2048 em 2 minutos
      → alerta a cada 6 ligações (qualquer origem — track by_dst)

  S2: 7 ligações do mesmo IP para porta 8080 em 30 segundos
      → 1 alerta único por janela de 30s (track by_src)

  S3: Primeiros 5 echo_request em 45 segundos
      → 5 alertas máximo, os restantes ignorados (track by_dst)


================================================================================
 ATENÇÃO — MÁQUINAS IMPORTANTES DO LABORATÓRIO
================================================================================

  Não bloquear ao testar (necessárias para o funcionamento do lab):
    Gateway : 10.101.148.1
    Lynx    : 10.101.85.165
    Electra : 10.101.85.162
    Outros  : 10.121.53.17-19, 10.121.52.17-19


================================================================================
 ENTREGA
================================================================================

  Prazo: 24 de Maio de 2026, até às 23:55
  Entregar na área de grupo da disciplina:
    iptables.pdf   — regras + método de teste com evidências (screenshots)
    snort.pdf      — regras + invocação + método de teste com evidências
================================================================================
