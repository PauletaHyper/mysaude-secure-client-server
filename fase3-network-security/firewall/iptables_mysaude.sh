#!/bin/bash
# ============================================================
#  Firewall MySaude — Trabalho 3, Parte I (iptables)
#  Segurança Informática 2025/26
# ============================================================
#
#  Política implementada:
#    Serviços suportados (INPUT):
#      R1 — SSH apenas da máquina gcc
#      R2 — Ping apenas da sub-rede local (/23)
#      R3 — Servidor mySaude (porta 8080) de qualquer origem
#
#    Serviços utilizados (OUTPUT):
#      R4 — Ping apenas para a máquina gcc
#
#    Tudo o resto é BLOQUEADO (política fechada)
# ============================================================

# ============================================================
#  CONFIGURAÇÃO — PREENCHER ANTES DE USAR
# ============================================================

# IP da máquina gcc (obter com: host gcc  ou  nslookup gcc)
GCC_IP="10.101.X.X"

# Porta do servidor mySaude (TLS)
MYSAUDE_PORT=8080

# ============================================================
#  VALIDAÇÃO — garante que não ficamos locked out por descuido
# ============================================================
if [ "$EUID" -ne 0 ]; then
    echo "ERRO: correr com sudo."
    exit 1
fi

if [[ "$GCC_IP" =~ [A-Za-z] ]]; then
    echo "ERRO: GCC_IP não está preenchido ('$GCC_IP')."
    echo "       Editar este script e preencher o IP real da máquina gcc."
    echo "       Obter com: host gcc"
    exit 1
fi

# ============================================================
#  AUTO-DETEÇÃO DA SUB-REDE LOCAL (/23 = máscara 255.255.254.0)
# ============================================================
CURRENT_IP=$(ip -o -f inet addr show | grep -v "lo" | awk 'NR==1{print $4}' | cut -d'/' -f1)

if [ -z "$CURRENT_IP" ]; then
    echo "ERRO: não foi possível detetar o IP da máquina."
    exit 1
fi

IFS='.' read -r o1 o2 o3 o4 <<< "$CURRENT_IP"
o3_net=$(( o3 & 0xFE ))
LOCAL_SUBNET="${o1}.${o2}.${o3_net}.0/23"

echo "=== Configuração ==="
echo "  IP do servidor  : $CURRENT_IP"
echo "  Sub-rede local  : $LOCAL_SUBNET"
echo "  IP da gcc       : $GCC_IP"
echo "  Porta mySaude   : $MYSAUDE_PORT"
echo ""

# ============================================================
#  RESET — apagar todas as regras existentes
# ============================================================
/sbin/iptables -F
/sbin/iptables -X
/sbin/iptables -t nat    -F 2>/dev/null
/sbin/iptables -t mangle -F 2>/dev/null

# ============================================================
#  POLÍTICAS POR OMISSÃO: DROP (política fechada)
# ============================================================
/sbin/iptables -P INPUT   DROP
/sbin/iptables -P OUTPUT  DROP
/sbin/iptables -P FORWARD DROP

# ============================================================
#  LOOPBACK — não filtrar tráfego local (obs. iii do enunciado)
# ============================================================
/sbin/iptables -A INPUT  -i lo -j ACCEPT
/sbin/iptables -A OUTPUT -o lo -j ACCEPT

# ============================================================
#  LIGAÇÕES JÁ ESTABELECIDAS (obs. iv do enunciado)
#  Permite respostas a ligações já iniciadas (ex: resposta TLS,
#  resposta a pings, respostas SSH)
# ============================================================
/sbin/iptables -A INPUT  -m state --state ESTABLISHED,RELATED -j ACCEPT
/sbin/iptables -A OUTPUT -m state --state ESTABLISHED,RELATED -j ACCEPT

# ============================================================
#  REGRAS INPUT — o que o servidor aceita
# ============================================================

# R1 — SSH (porta 22) apenas da máquina gcc
/sbin/iptables -A INPUT -p tcp --dport 22 -s "$GCC_IP" -j ACCEPT

# R2 — Ping (ICMP echo-request) apenas da sub-rede local (/23)
/sbin/iptables -A INPUT -p icmp --icmp-type echo-request -s "$LOCAL_SUBNET" -j ACCEPT

# R3 — Servidor mySaude (TLS, porta 8080) aceita ligações de qualquer origem
/sbin/iptables -A INPUT -p tcp --dport "$MYSAUDE_PORT" -j ACCEPT

# ============================================================
#  REGRAS OUTPUT — o que o servidor pode enviar
# ============================================================

# R4 — Ping (ICMP echo-request) apenas para a máquina gcc
/sbin/iptables -A OUTPUT -p icmp --icmp-type echo-request -d "$GCC_IP" -j ACCEPT

# ============================================================
#  VISUALIZAR REGRAS APLICADAS
# ============================================================
echo "=== Regras iptables aplicadas ==="
/sbin/iptables -L -v --line-numbers

echo ""
echo "Firewall MySaude ativada com sucesso."
