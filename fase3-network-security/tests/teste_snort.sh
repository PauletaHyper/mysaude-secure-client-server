#!/bin/bash
# ============================================================
#  TESTE SNORT — Correr no CLIENTE
#  Trabalho 3, Parte II — Segurança Informática 2025/26
#
#  Uso: ./tests/teste_snort.sh <IP_SERVIDOR>
#
#  Pré-requisito: no SERVIDOR ter o snort a correr:
#    cd ids/
#    sudo snort -i <interface> -A console -q -c mysaude_snort.conf
#
#  Também testa R1, R2, R3 do iptables (Parte I).
# ============================================================

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

IP_SERVIDOR="$1"
MYSAUDE_PORT=8080

if [ -z "$IP_SERVIDOR" ]; then
    echo "Uso: $0 <IP_SERVIDOR>"
    echo "Exemplo: $0 10.101.85.200"
    exit 1
fi

pass() { echo -e "  ${GREEN}[PASS]${NC} $1"; }
fail() { echo -e "  ${RED}[FAIL]${NC} $1"; }
info() { echo -e "  ${YELLOW}[INFO]${NC} $1"; }
header() { echo -e "\n${BOLD}${CYAN}$1${NC}"; echo "$(printf '=%.0s' {1..60})"; }
wait_msg() { echo -e "  ${YELLOW}[WAIT]${NC} $1"; sleep "$2"; }

echo -e "${BOLD}"
echo "╔══════════════════════════════════════════════════════════╗"
echo "║   TESTE SNORT + IPTABLES MYSAUDE — Trabalho 3           ║"
echo "╠══════════════════════════════════════════════════════════╣"
echo "║   Servidor: $IP_SERVIDOR$(printf '%*s' $((41 - ${#IP_SERVIDOR})) '')║"
echo "╚══════════════════════════════════════════════════════════╝"
echo -e "${NC}"

# ============================================================
#  TESTES IPTABLES (INPUT) — Parte I
#  Verificar R1, R2, R3 do lado do cliente
# ============================================================
header "IPTABLES — PARTE I (testes de INPUT no servidor)"

echo -e "\n${YELLOW}R3 — Servidor mySaude (porta 8080) aceita ligações de qualquer origem:${NC}"
nc -z -w 3 "$IP_SERVIDOR" "$MYSAUDE_PORT" &>/dev/null \
    && pass "R3: Porta $MYSAUDE_PORT acessível de qualquer origem" \
    || info "R3: Porta $MYSAUDE_PORT inacessível (servidor mySaude está a correr?)"

echo -e "\n${YELLOW}R1 — SSH (porta 22): testar acesso (este PC não é gcc → devia ser bloqueado):${NC}"
nc -z -w 3 "$IP_SERVIDOR" 22 &>/dev/null \
    && fail "R1: SSH acessível — devia estar bloqueado (este PC não é gcc)" \
    || pass "R1: SSH bloqueado para este PC (não é gcc)"

echo -e "\n${YELLOW}Outras portas devem estar bloqueadas (default DROP):${NC}"
nc -z -w 2 "$IP_SERVIDOR" 9999 &>/dev/null \
    && fail "Porta 9999 acessível (devia estar bloqueada)" \
    || pass "Porta 9999 bloqueada (default DROP ativo)"

echo -e "\n${YELLOW}R2 — Ping para o servidor (máscara /23 — funciona se cliente na mesma sub-rede):${NC}"
ping -c 2 -W 2 "$IP_SERVIDOR" &>/dev/null \
    && pass "R2: Ping aceite (este PC está na sub-rede local)" \
    || info "R2: Ping bloqueado (este PC pode estar fora da sub-rede /23 — comportamento correto)"

# ============================================================
#  TESTES SNORT — Parte II
#  NOTA: o snort deve estar a correr no servidor antes destes testes
# ============================================================
header "SNORT — PARTE II"
info "Certificar que o snort está a correr no servidor antes de continuar."
info "Comando no servidor: sudo snort -i <iface> -A console -q -c ids/mysaude_snort.conf"
echo ""
read -p "  Pressionar ENTER para continuar quando o snort estiver ativo..."

# ============================================================
#  S1 — Varrimento de portos (port scan)
#  Espera: alerta a cada 6 ligações TCP para portos < 2048
# ============================================================
header "S1 — Varrimento de Portos (port scan)"

echo -e "\n${YELLOW}Teste positivo: 6 ligações TCP SYN para portos < 2048 → deve gerar alerta S1${NC}"
info "A enviar 6 SYN para portos 21, 22, 23, 25, 53, 80..."
for port in 21 22 23 25 53 80; do
    echo -e "    SYN → porta $port"
    nc -z -w 1 "$IP_SERVIDOR" "$port" &>/dev/null || true
    sleep 0.3
done
pass "6 ligações enviadas → verificar alerta [S1] na consola do snort"

echo ""
echo -e "${YELLOW}Teste negativo (sem falso positivo): ligações para porta 8080 (>= 2048) NÃO devem gerar S1${NC}"
info "A enviar 6 ligações para porta 8080..."
for i in $(seq 1 6); do
    nc -z -w 1 "$IP_SERVIDOR" "$MYSAUDE_PORT" &>/dev/null || true
    sleep 0.2
done
info "Verificar que NÃO apareceu alerta S1 na consola do snort (porta 8080 >= 2048)"

echo ""
echo -e "${YELLOW}Teste: 12 ligações para portos < 2048 → deve gerar 2 alertas S1 (a cada 6)${NC}"
info "A enviar mais 6 SYN (portos 110, 143, 993, 995, 587, 465 — todos < 2048)..."
for port in 110 143 993 995 587 465; do
    echo -e "    SYN → porta $port"
    nc -z -w 1 "$IP_SERVIDOR" "$port" &>/dev/null || true
    sleep 0.3
done
pass "12 ligações enviadas → verificar 2 alertas [S1] na consola do snort"

# ============================================================
#  S2 — Brute-force ao servidor mySaude
#  Espera: 1 alerta único após 7 ligações do mesmo IP em 30s
# ============================================================
header "S2 — Brute-Force mySaude (porto 8080)"

echo -e "${YELLOW}Aguardar fim da janela de 30s antes de iniciar S2...${NC}"
wait_msg "A aguardar 32 segundos para limpar estado do snort..." 32

echo -e "\n${YELLOW}Teste positivo: 7 ligações para porta 8080 → deve gerar 1 alerta S2${NC}"
for i in $(seq 1 7); do
    echo -e "    Ligação $i → porta $MYSAUDE_PORT"
    nc -z -w 1 "$IP_SERVIDOR" "$MYSAUDE_PORT" &>/dev/null || true
    sleep 0.5
done
pass "7 ligações enviadas → verificar alerta [S2] na consola do snort"

echo ""
echo -e "${YELLOW}Teste negativo (sem falso positivo): 3 ligações adicionais NÃO devem gerar novo alerta${NC}"
for i in $(seq 8 10); do
    echo -e "    Ligação $i → porta $MYSAUDE_PORT (deve ser ignorada pelo snort)"
    nc -z -w 1 "$IP_SERVIDOR" "$MYSAUDE_PORT" &>/dev/null || true
    sleep 0.5
done
info "Verificar que NÃO apareceu segundo alerta S2 (type both = 1 alerta por janela de 30s)"

echo ""
echo -e "${YELLOW}Teste negativo: 6 ligações de outro IP não devem gerar S2 (track by_src)${NC}"
info "Para testar este caso, correr este script de outra máquina cliente."

echo ""
echo -e "${YELLOW}Teste: após 30s, novo ciclo → 7 ligações devem gerar S2 de novo${NC}"
wait_msg "A aguardar 32 segundos para nova janela de 30s..." 32
for i in $(seq 1 7); do
    nc -z -w 1 "$IP_SERVIDOR" "$MYSAUDE_PORT" &>/dev/null || true
    sleep 0.4
done
pass "Nova janela de 30s — verificar novo alerta [S2] na consola do snort"

# ============================================================
#  S3 — Flood ICMP echo-request
#  Espera: alertas para os primeiros 5, os restantes ignorados
# ============================================================
header "S3 — Flood ICMP (ping)"

echo -e "${YELLOW}Aguardar fim da janela de 45s antes de iniciar S3...${NC}"
wait_msg "A aguardar 47 segundos para limpar estado do snort..." 47

echo -e "\n${YELLOW}Teste positivo: 10 pings → deve gerar 5 alertas S3 (restantes ignorados)${NC}"
info "A enviar 10 pings..."
ping -c 10 -i 0.3 "$IP_SERVIDOR"
pass "10 pings enviados → verificar exactamente 5 alertas [S3] na consola do snort"

echo ""
echo -e "${YELLOW}Teste negativo: dentro dos 45s os pings adicionais NÃO devem gerar alerta${NC}"
info "A enviar mais 5 pings dentro da mesma janela de 45s..."
ping -c 5 -i 0.5 "$IP_SERVIDOR"
info "Verificar que NÃO apareceram novos alertas S3 (limite de 5 atingido)"

echo ""
echo -e "${YELLOW}Teste: após 45s, nova janela → 5 alertas S3 de novo${NC}"
wait_msg "A aguardar 47 segundos para nova janela de 45s..." 47
ping -c 5 -i 0.5 "$IP_SERVIDOR"
pass "Nova janela — verificar 5 alertas [S3] na consola do snort"

# ============================================================
#  RESUMO
# ============================================================
header "RESUMO DOS TESTES"

echo -e "${YELLOW}Verificar na consola do snort (servidor):${NC}"
echo "  [S1] deve ter aparecido a cada 6 ligações TCP para portos < 2048"
echo "  [S2] deve ter aparecido 1x após a 7.ª ligação do mesmo IP à porta 8080"
echo "  [S3] deve ter aparecido 5x por janela de 45s, restantes ignorados"
echo ""
echo -e "${YELLOW}Falsos positivos — NÃO devem ter aparecido:${NC}"
echo "  S1 para ligações à porta 8080 (>= 2048)"
echo "  S2 para menos de 7 ligações à porta 8080"
echo "  S3 para pings após os primeiros 5 na mesma janela de 45s"
echo ""
