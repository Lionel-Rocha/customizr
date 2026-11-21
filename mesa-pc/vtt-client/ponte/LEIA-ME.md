# Ponte do Customizr

A ponte deixa os jogadores entrarem na sua mesa pela internet **sem ninguém abrir porta no
roteador** e sem Tailscale. Ela roda num computador que tem uma porta aberta para a internet
(um servidor/VPS) e só **repassa** os dados: a mesa continua rodando no PC do mestre, com as
fichas, mapas e o salvamento lá.

```
 Jogador ──►  PONTE (servidor com porta aberta)  ◄── Mestre (PC de casa)
             liga um no outro pelo código da mesa
```

Uma ponte serve muitas mesas ao mesmo tempo (cada mesa tem um código próprio), então um grupo
de amigos pode dividir uma só.

## 1. Onde rodar

Qualquer computador sempre ligado com uma porta TCP aberta para a internet:

- **Um VPS barato** (Hetzner, Contabo, DigitalOcean, Vultr...) — o mais simples.
- **Camada gratuita de nuvem** (por exemplo Oracle Cloud "Always Free" ou Google Cloud e2-micro):
  sem custo, mas precisa criar a conta e liberar a porta no painel deles.
- Um PC de um amigo que já tem porta aberta (mas aí ele mesmo poderia hospedar a mesa).

A ponte usa pouquíssima memória (~60 MB) e quase nenhuma CPU.

## 2. Gerar o arquivo da ponte

No seu PC, dois cliques em **`Criar ponte do Customizr.cmd`** (pasta `vtt-caseiro-classes`).
Ele cria a pasta **`Customizr-Ponte`** com o `customizr-ponte.jar` e estes arquivos.
Copie essa pasta para o servidor.

## 3. Rodar no servidor

Precisa de **Java 25 ou mais novo** no servidor (`java -version`).

**Linux, jeito mais fácil (instala tudo de uma vez):**
```
cd Customizr-Ponte
sudo sh instalar-no-servidor.sh        # porta 55070
```
Instala o Java 25 se precisar, copia a ponte pra `/opt/customizr-ponte`, cria o serviço que liga
sozinho e libera a porta no firewall do próprio servidor (inclusive o iptables que vem fechado no
Oracle Cloud). Falta só liberar a porta no painel do provedor, se ele tiver firewall.

**Linux, rápido (pra testar):**
```
cd Customizr-Ponte
./rodar-ponte.sh            # porta 55070
```

**Linux, de vez (liga sozinho quando o servidor reinicia):**
```
sudo mkdir -p /opt/customizr-ponte
sudo cp customizr-ponte.jar /opt/customizr-ponte/
sudo cp customizr-ponte.service /etc/systemd/system/
sudo systemctl enable --now customizr-ponte
sudo systemctl status customizr-ponte
```

**Docker:**
```
docker build -t customizr-ponte .
docker run -d --restart unless-stopped -p 55070:55070 --name ponte customizr-ponte
```

**Windows:** `rodar-ponte.cmd`.

Depois **libere a porta 55070 (TCP)** no firewall do servidor e, se for nuvem, no painel do
provedor ("security list" / "firewall rules").

Teste: abra `http://ENDERECO-DO-SERVIDOR:55070` no navegador — deve aparecer a página do
Customizr pedindo o código da mesa.

## 4. Usar

**Mestre:** painel **Rede** (barra da esquerda, embaixo) > "Hospedar aqui" > abra
**"▸ Senhas, criptografia e ponte"** > no campo da ponte, digite `ENDERECO-DO-SERVIDOR:55070` >
**Abrir mesa na ponte**. Aparece o **código da mesa** (ex.: `K7P-Q2X`), que é sempre o mesmo para
essa mesa.

**Jogador (programa):** na tela inicial, **"Entrar numa mesa online"** (ou, já dentro de uma mesa,
painel **Rede** > "Entrar numa mesa online..."). Endereço: o da ponte; porta: `55070`; e o código
da mesa no campo "Código da ponte".

**Jogador (navegador):** `http://ENDERECO-DO-SERVIDOR:55070/?mesa=K7P-Q2X`.

## Segurança

- A senha da mesa continua valendo (quem confere é o PC do mestre, não a ponte).
- Com **TLS** ligado no mestre (e "Mesa criptografada" no jogador), o jogo vai criptografado
  **de ponta a ponta**: a ponte não consegue ler nada, e o jogador confere o código da mesa do
  mestre como sempre.
- Pelo **navegador**, a passagem pela ponte é sem criptografia (http). Para conversa/senha mais
  protegidas, prefira o programa com TLS.
- Os **sons** passam pela ponte também; o programa confere a impressão digital (SHA-256) de cada
  arquivo, então ninguém no caminho consegue trocar um som.
- Só o mestre que registrou um código consegue usar esse código (cada mesa tem um segredo
  guardado no arquivo `ponte.properties` da mesa).
