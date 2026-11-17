#!/bin/sh
# ============================================================
#  Instala a ponte do Customizr num servidor Linux (Ubuntu/Debian,
#  x86_64 ou ARM -- ex.: VPS barato ou Oracle Cloud "Always Free").
#
#  Uso (dentro da pasta Customizr-Ponte copiada pro servidor):
#     sudo sh instalar-no-servidor.sh            # porta 55070
#     sudo sh instalar-no-servidor.sh 60000      # outra porta
#
#  O que faz:
#   1. instala o Java 25 (pelo apt; se nao tiver, baixa o Temurin da Adoptium)
#   2. copia a ponte pra /opt/customizr-ponte
#   3. cria e liga o servico "customizr-ponte" (volta sozinho se o servidor reiniciar)
#   4. libera a porta no firewall local (ufw/firewalld), se estiver ligado
#  Depois: libere a mesma porta (TCP) no painel do provedor da nuvem, se ele tiver firewall.
# ============================================================
set -e
PORTA="${1:-55070}"
DESTINO=/opt/customizr-ponte
AQUI="$(cd "$(dirname "$0")" && pwd)"

if [ "$(id -u)" != "0" ]; then
  echo "Rode com sudo: sudo sh $0 $PORTA"
  exit 1
fi
if [ ! -f "$AQUI/customizr-ponte.jar" ]; then
  echo "Nao achei customizr-ponte.jar nesta pasta ($AQUI)."
  exit 1
fi

java_ok() {
  command -v "$1" >/dev/null 2>&1 || return 1
  v=$("$1" -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')
  [ "${v:-0}" -ge 25 ] 2>/dev/null
}

JAVA=""
if java_ok java; then JAVA="$(command -v java)"; fi
if [ -z "$JAVA" ] && command -v apt-get >/dev/null 2>&1; then
  echo "[1/4] Instalando Java 25 pelo apt..."
  apt-get update -qq || true
  apt-get install -y -qq openjdk-25-jre-headless >/dev/null 2>&1 || true
  if java_ok java; then JAVA="$(command -v java)"; fi
fi
if [ -z "$JAVA" ]; then
  echo "[1/4] Baixando o Java 25 (Temurin, da Adoptium)..."
  case "$(uname -m)" in
    aarch64|arm64) ARQ=aarch64 ;;
    *) ARQ=x64 ;;
  esac
  mkdir -p "$DESTINO/java"
  URL="https://api.adoptium.net/v3/binary/latest/25/ga/linux/$ARQ/jre/hotspot/normal/eclipse"
  if command -v curl >/dev/null 2>&1; then curl -fsSL "$URL" -o /tmp/jre25.tar.gz; else wget -qO /tmp/jre25.tar.gz "$URL"; fi
  tar -xzf /tmp/jre25.tar.gz -C "$DESTINO/java" --strip-components=1
  rm -f /tmp/jre25.tar.gz
  JAVA="$DESTINO/java/bin/java"
fi
echo "      Java: $JAVA"

echo "[2/4] Copiando a ponte pra $DESTINO..."
mkdir -p "$DESTINO"
cp "$AQUI/customizr-ponte.jar" "$DESTINO/"

echo "[3/4] Criando o servico customizr-ponte..."
cat > /etc/systemd/system/customizr-ponte.service <<EOF
[Unit]
Description=Ponte do Customizr
After=network-online.target
Wants=network-online.target

[Service]
WorkingDirectory=$DESTINO
ExecStart=$JAVA -Xmx128m -jar $DESTINO/customizr-ponte.jar $PORTA
Restart=always
RestartSec=5
DynamicUser=yes

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable --now customizr-ponte
sleep 2
systemctl --no-pager --lines=3 status customizr-ponte || true

echo "[4/4] Firewall local..."
if command -v ufw >/dev/null 2>&1 && ufw status | grep -q "Status: active"; then
  ufw allow "$PORTA"/tcp
elif command -v firewall-cmd >/dev/null 2>&1 && firewall-cmd --state >/dev/null 2>&1; then
  firewall-cmd --permanent --add-port="$PORTA"/tcp && firewall-cmd --reload
else
  echo "      (nenhum firewall local ligado)"
fi
# Oracle Cloud (Ubuntu) vem com iptables que bloqueia tudo menos SSH:
if command -v iptables >/dev/null 2>&1 && iptables -S INPUT 2>/dev/null | grep -q "REJECT"; then
  iptables -I INPUT 1 -p tcp --dport "$PORTA" -j ACCEPT
  command -v netfilter-persistent >/dev/null 2>&1 && netfilter-persistent save || true
  echo "      porta $PORTA liberada no iptables"
fi

echo
echo "Pronto! A ponte esta rodando na porta $PORTA."
echo "Teste num navegador: http://ENDERECO-DESTE-SERVIDOR:$PORTA  (deve pedir o codigo da mesa)."
echo "Se nao abrir: libere a porta $PORTA (TCP) no painel do provedor (security list / firewall rules)."
echo "Ver o que a ponte esta fazendo: journalctl -u customizr-ponte -f"
