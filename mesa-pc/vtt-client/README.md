# Customizr

*historias em volta da fogueira*

Esta e a **mesa de jogo do Customizr** (https://github.com/Lionel-Rocha/customizr): o servidor
de la cuida da campanha e do mundo; este programa e onde a sessao acontece. Ver
`../INTEGRACAO-CUSTOMIZR.md` pro plano de ligar os dois.

Customizr (antes "Embertale" e "VTT Caseiro") e uma mesa virtual de RPG (virtual tabletop) caseira, organizado em volta do conceito de
**Mesa** (campanha) -- igual Roll20 ("Game") ou Foundry ("World"): voce
escolhe ou cria uma mesa ao abrir o programa, e dali em diante cenas
(mapas) e fichas de personagem vivem dentro dela, listadas, sem
dialogo de "abrir arquivo" espalhado pelo meio do jogo.

## Como rodar

### Jeito mais facil: o launcher (Customizr.exe)

Dois cliques em **`Criar launcher do Customizr.cmd`** (na pasta `vtt-caseiro-classes`).
Ele compila e gera a pasta **`Customizr`** com o **`Customizr.exe`** (icone da
fogueira, sem janela preta, com o Java embutido: nao precisa de JDK nem Maven pra
abrir) e um atalho **`Customizr`** na mesma pasta. No fim ele pergunta se quer um
atalho na area de trabalho tambem.

- Depois disso, e so abrir pelo atalho. A pasta `Customizr` (~140 MB) pode ser
  copiada pra outro computador Windows ou pra um pendrive.
- Mudou o codigo? Rode o `Criar launcher` de novo (o .exe e uma "foto" do programa).
- Suas mesas e preferencias NAO ficam dentro da pasta `Customizr`; apagar e recriar
  o launcher nao perde nada.
- Criar o launcher precisa de um JDK 25+ (o mesmo do `rodar.cmd`, que tem o `jpackage`).

**Jeito mais facil (Windows): dois cliques em `rodar.cmd`** (dentro de
`vtt-client`). Ele:

1. acha sozinho um JDK 25 ou mais novo (no `JAVA_HOME`, nos JDKs que o
   IntelliJ baixa em `%USERPROFILE%\.jdks`, ou em `C:\Program Files\Java`,
   Adoptium, Microsoft) -- o `java` padrao do Windows costuma ser o Java 8,
   que NAO serve;
2. usa o Maven Wrapper (`mvnw.cmd`), que baixa o Maven 3.9.9 sozinho na
   primeira vez (precisa de internet so nessa vez; depois fica em
   `%USERPROFILE%\.m2\wrapper`);
3. compila e abre a janela de escolha de mesa.

`rodar.cmd test` roda os testes.

No PowerShell, comandos da pasta atual precisam de `.\` na frente:
`.\rodar.cmd` (ou `.\rodar.cmd test`). Tem um `rodar.cmd` tambem na pasta
de cima (`vtt-caseiro-classes`), que so chama o de `vtt-client`.

### A tela

- **Barra de cima** (mestre): a **fila de cenas da sessao**: a cena atual (olho aberto = os jogadores veem; fechado =
  escondida) e as proximas da fila. Clicar numa abre pra todos; botao direito: abrir escondida ou tirar da fila;
  "▶" passa pra proxima e "+" leva pra Mundo > Cenas pra montar a fila. Online, as **bolinhas coloridas** mostram
  quem esta conectado (borda dourada = mestre).
- **Mapa** no centro, com quase a tela toda.
- **Ferramentas do mapa** numa faixa na borda esquerda do mapa (tokens, medir, desenho, pins, objetos, neblina,
  paredes; desfazer e ver tudo). O nome de cada uma aparece ao passar o mouse.
- **Modos do mestre**, no pe dessa faixa: **Jogar** (conduzir a cena), **Preparar** (o que voce colocar, tokens e
  pins, nasce escondido dos jogadores; objetos ficam editaveis) e **Construir** (paredes, portas, luz, neblina).
  Um aviso no alto do mapa mostra quando voce esta preparando ou construindo.
- **Fila de turnos**: durante o combate, cartoes no alto do mapa com retrato, iniciativa, PV (quando voce pode ver)
  e condicoes; o da vez maior. Clicar no cartao mostra o token no mapa; o botao no fim passa a vez (mestre ou
  dono de quem esta na vez).
- **Anel do token**: botao direito no token abre botoes em volta dele: dano, cura, condicoes, mirar, ficha,
  iniciativa e (mestre) esconder/revelar. "..." abre o menu completo. Com varios tokens selecionados, o botao
  direito abre direto o menu com as acoes em grupo.
- **Barra da direita**, uma so, com abas: **Mesa** (chat e rolagens), **Combate**, **Personagens** (fichas e
  grupo), **Mundo** (cenas e diario), **Som** e **Rede**. Clicar de novo na aba aberta (ou em "»") recolhe a
  barra numa faixa de icones; o mapa fica com a tela toda.

### Hospedar e entrar numa mesa

- **Mestre:** aba **Rede** (último ícone da barra da direita) > "Hospedar aqui". Senha da mesa, senha
  de mestre, criptografia (TLS) e ponte ficam em **"▸ Senhas, criptografia e ponte"**, logo abaixo.
- **Jogador:** na tela inicial, **"Entrar numa mesa online"** (nao precisa criar mesa antes: o
  programa guarda o que vier daquela mesa numa pasta escondida, `.online`, dentro da pasta das
  mesas). Ja dentro de uma mesa: painel **Rede** > "Entrar numa mesa online...". Pede o nome, o
  endereco (ou o codigo da ponte) e a senha, se tiver. O programa lembra o endereco, nunca a senha.

### Jogadores pelo navegador (sem instalar nada)

Quando voce hospeda (painel Rede > "Hospedar aqui"), o programa abre tambem a
porta seguinte (55055 -> **55056**) com uma pagina web. O painel Rede mostra o
link (ex.: `http://192.168.0.10:55056`) e um botao "Copiar link". Quem nao tem
o programa abre esse link no navegador -- PC ou celular, na mesma rede ou pela
Tailscale/ZeroTier -- e entra com o nome e a senha da mesa. Pelo navegador da
pra mover os proprios tokens, abrir portas, pingar, conversar, rolar dados,
rolar pela ficha (com condicoes e efeitos), mudar o proprio PV e ler o diario.
Libere a porta 55056 no firewall do Windows se o celular nao conseguir abrir.
A mesma porta serve os sons da mesa (painel Som): quem joga pelo programa tambem
baixa a musica dali, entao ela precisa estar liberada pra todo mundo ouvir.

### Jogar pela internet com criptografia (TLS)

No painel Rede, abra **"▸ Senhas, criptografia e ponte"** e marque **"Criptografar (TLS)"** antes
de "Hospedar aqui". O programa gera um certificado proprio (uma vez so, em `~/.vttcaseiro/tls`) e
mostra o **codigo da mesa** (ex.: `CE90 CE6F 6D58 C11A`). Quem conecta marca
**"Mesa criptografada (TLS)"** na janela "Entrar numa mesa online"; na primeira vez aparece o codigo pra conferir
com o mestre, e o programa lembra dele. Se um dia o codigo mudar, a conexao e
recusada com aviso. Pelo navegador o link vira `https://...`; o aviso de
"certificado nao confiavel" e esperado (certificado proprio) -- e so avancar.

### Jogar pela internet com playit.gg (o mais facil)

O [playit.gg](https://playit.gg) e um programa gratis que roda no PC do mestre e da um endereco
publico pra mesa: ninguem abre porta no roteador, nao precisa de servidor e os jogadores nao instalam
nada alem do Customizr.

1. Mestre: painel **Rede** > **"▸ Jogar pela internet com playit.gg"** > **"Baixar o playit.gg"**.
   Abra o playit e crie a conta quando ele pedir.
2. No site do playit: **Add Tunnel** > tipo **TCP** > porta local **55055** (a de "Hospedar aqui").
3. **"Hospedar aqui"** no Customizr, de preferencia **com senha da mesa** (pelo tunel, qualquer um
   com o endereco chega na tela de senha).
4. Cole o endereco que o playit mostrou (ex.: `algo.ply.gg:12345`) e clique em **"Testar e mostrar
   pros jogadores"**. O programa confere se a mesa responde pelo tunel.
5. Jogadores: **"Entrar numa mesa online"** e colam o endereco inteiro no campo Endereco (a porta
   se separa sozinha).

O playit precisa ficar aberto enquanto voces jogam. Pra jogadores pelo navegador, crie um segundo
tunel TCP pra porta **55056**.

### Ponte: jogar pela internet sem abrir porta

Pra quem nao quer abrir porta no roteador nem usar Tailscale: a **ponte** e um programinha do
Customizr que roda num servidor com porta aberta (um VPS barato ou uma camada gratuita de nuvem).
O mestre e os jogadores conectam NELA (de dentro pra fora, entao nenhum roteador atrapalha) e ela
liga um no outro pelo **codigo da mesa**. A mesa continua rodando no PC do mestre.

1. Dois cliques em **`Criar ponte do Customizr.cmd`** (pasta de cima): gera a pasta
   **`Customizr-Ponte`** com o `customizr-ponte.jar` (16 KB, so precisa de Java 25+) e um guia
   (`LEIA-ME.md`: rodar no Linux, como servico, Docker, liberar a porta 55070).
2. Mestre: painel **Rede** > "Hospedar aqui" > **"▸ Senhas, criptografia e ponte"** > endereco da
   ponte (ex.: `meuservidor.com:55070`) > **Abrir mesa na ponte**. Aparece o codigo da mesa
   (ex.: `K7P-Q2X`, sempre o mesmo pra essa mesa).
3. Jogador: **"Entrar numa mesa online"** (tela inicial, ou painel Rede) > endereco `meuservidor.com`,
   porta `55070` e o **codigo da ponte**; pelo navegador, `http://meuservidor.com:55070/?mesa=K7P-Q2X`.

No servidor Linux, `sudo bash instalar-no-servidor.sh` (vem na pasta da ponte) instala o Java se
faltar, copia pra `/opt/customizr-ponte`, cria o servico (liga sozinho no boot) e libera a porta.

Com TLS ligado, o jogo pelo programa vai criptografado de ponta a ponta (a ponte nao le nada).
Pelo navegador, o trecho ate a ponte e http. Os sons da mesa tambem passam pela ponte, e o
programa confere o hash de cada arquivo baixado (ninguem troca o som no caminho).

### Backup

Engrenagem **⚙** (barra de cima) > **"Exportar mesa inteira (backup)..."** gera um `.zip` com tudo
da mesa. Pra abrir em outro computador: tela de escolher mesa > **"Importar backup..."** (cria uma
mesa nova, nunca sobrescreve).

Na mesma janela ficam os **backups automaticos** (ao abrir a mesa e a cada 10 min, so quando algo
mudou; da pra mudar o intervalo ou desligar), **"Fazer backup agora"** e **"Restaurar backup..."**
(a copia vira uma mesa nova; a atual nao muda). E o **tema** do programa.

### Importar fichas (PDF ou JSON)

Painel **Fichas** > **"Importar..."**. Aceita:

- **PDF preenchivel**: a ficha oficial de 5e, a do D&D Beyond ("Exportar para PDF")
  e fichas parecidas, inclusive traduzidas. Le nome, classe/nivel, atributos, CA,
  PV, deslocamento, pericias e salvaguardas (a proficiencia e deduzida do numero),
  ataques, dinheiro, equipamento e os textos. PDF escaneado ou "impresso como
  imagem" nao tem os dados dentro: use JSON ou crie na mao.
- **PDF preenchivel de Tormenta20 ou Ordem Paranormal**: o Customizr reconhece pelos
  campos (PM/Divindade/Ladinagem = T20; NEX/PE/SAN/AGI/PRE = Ordem) e monta a ficha do
  sistema certo, com atributos, PV/PM/PE/SAN, Defesa, treino das pericias (deduzido do
  valor), ataques, poderes e inventario. Testado com fichas montadas no formato das
  comunitarias; se a sua tiver nomes de campo muito diferentes, avise.
- **JSON** do proprio Customizr ("Exportar ficha aberta (JSON)..."), ator do
  **Foundry VTT** (sistema dnd5e, "Export Data") e personagem do **D&D Beyond**.
  Do Foundry, o PV maximo, a CA e o bonus/dano das armas sao calculados como o
  proprio Foundry faz quando o arquivo nao traz o numero pronto.
- **Pelo navegador**: o jogador tambem importa (aba Ficha > "Importar ficha (PDF ou
  JSON)", ate 5 MB). A ficha chega pro mestre ja como sendo daquele jogador.

Antes de salvar aparece um resumo e o que conferir (ex.: CA que o D&D Beyond nao
manda pronta). A ficha importada vira uma ficha nova, do jogador que importou.

### Importar mapas (Azgaar, Watabou e Dungeondraft)

Mapas do **Dungeondraft** (ou de outro editor) no formato **Universal VTT** (`.dd2vtt`, `.uvtt`,
`.df2vtt`): no Dungeondraft, File > Export > Universal VTT, com imagem em PNG. No Customizr, a mesma
opcao de importar mapa > "Dungeondraft / Universal VTT". Vem a imagem, a grade, as paredes, as portas
(abertas ou fechadas) e as luzes, com a visao ja ligada.

Painel **Mapa** > **"Importar mapa (Azgaar / Watabou)..."** (ou o cartao "Comece aqui"
no mapa vazio):

- **Watabou - One Page Dungeon** (watabou.github.io): botao direito no mapa >
  Export > **JSON**. Vira uma cena pronta: o Customizr desenha a masmorra (piso,
  grade, paredes, portas, porta secreta, escadas, agua, colunas e os numeros das
  notas), cria as **paredes e portas do sistema de visao** e manda o titulo, a
  historia e as notas pro Diario.
- **Watabou - cidades, vilas, cavernas etc.**: exporte como **PNG** e escolha a
  opcao de imagem.
- **Azgaar's Fantasy Map Generator**: Menu > Export > **PNG** (ou JPEG). Se
  quiser, escolha tambem o **JSON "Full"** do mesmo mundo: os reinos e as maiores
  cidades vao pro Diario e as capitais podem ser marcadas no mapa. A grade fica
  escondida e cada celula vale 1 milha na regua.

SVG nao abre (exporte PNG). Imagens grandes sao reduzidas sozinhas pra caber no
limite da mesa (4 MB, que tambem vai pela rede pros jogadores).

### Grade hexagonal e objetos no mapa

- **Tipo de grade** (painel Mapa > Grade da cena atual): **Quadrada**, **Hexagonal** (hexagonos "em pe")
  ou **Sem grade**. No hex os tokens encaixam no centro do hexagono, as setas do teclado andam de
  hexagono em hexagono e a regua conta passos. Sem grade, o token fica onde soltar e a regua mede em
  linha reta. Os jogadores (inclusive no navegador) recebem a grade nova na hora.
- **Objetos** (barra do mapa > **Objetos**, so o mestre): imagens soltas por cima do fundo -- moveis,
  arvores, baus, carrocas. "Do computador..." (pode escolher varias; PNG com fundo transparente fica
  melhor) ou "Da galeria...". Com **Editar objetos** ligado: arraste pra mover (encaixa em meia casa;
  Alt solta livre), **Ctrl+roda** muda o tamanho, **Shift+roda** gira, e o botao direito tem tamanho
  em casas, girar, frente/fundo, esconder dos jogadores, travar, duplicar e apagar. Desligado, os
  objetos viram cenario e os cliques passam pro mapa.
- **Neblina em hexagonos**: com a grade hexagonal, revelar/cobrir e a memoria do "ja explorado"
  funcionam por hexagono (um clique = o hexagono do ponto; arrastar = os hexagonos com o centro
  dentro do retangulo). No navegador tambem.

### Fichas de Tormenta20 e Ordem Paranormal

Ao criar uma ficha ("Nova ficha..."), escolha o sistema: **D&D 5e (2024)**, **Tormenta20** ou
**Ordem Paranormal** (o ultimo escolhido fica marcado). Fichas antigas continuam D&D.

- **Tormenta20**: FOR, DES, CON, INT, SAB, CAR (anote o valor, ex. 14, ou ja o modificador, ex. +2),
  nivel, PV, PM e Defesa. Pericia = 1d20 + metade do nivel + atributo + treino (+2 ate o 6o nivel,
  +4 do 7o ao 14o, +6 do 15o) + outros.
- **Ordem Paranormal**: AGI, FOR, INT, PRE, VIG (0 a 5), NEX, PV, PE, SAN e Defesa. Teste = tantos
  d20 quanto o atributo, fica o maior (atributo 0: 2d20, fica o menor) + treino (treinado +5,
  veterano +10, expert +15). No chat: `3d20kh1+10` (kh = fica com o maior, kl = com o menor).
- Clique em atributo, pericia, ataque, dano ou critico pra rolar. Ataque usa uma pericia como teste
  ("Luta", "Pontaria", "Luta + 2") ou um bonus fixo.
- **Poderes com custo**: cada poder/ritual tem custo e recurso (ex.: 2 PM). **Usar** desconta e
  avisa no chat (e recusa se nao tiver o bastante); **Chat** so mostra. Nos recursos que nao sao PV
  (PM, PE, SAN) o botao **Perder...** aceita numero ou dado (ex.: `1d4` de Sanidade) e anuncia.
- O token ligado mostra o PV, a Defesa vale como CA nos ataques com alvo, e a iniciativa usa a pericia
  Iniciativa (na Ordem, varios d20). Pelo navegador o jogador ve a ficha e rola tudo.

### Chat, giro e regua de movimento

- **Rolagem no meio do texto**: `ataco com [[1d20+5]] e causo [[1d8+3]]` -- cada `[[...]]`
  vira o resultado em destaque (passe o mouse pra ver a conta). Quem rola e o servidor.
- **`/me acao`**: `/me saca a espada` aparece como "Ana saca a espada", em italico.
- **Cartoes de rolagem**: rolagem de dano/cura (sem d20) mostra botoes "Dano nos alvos",
  "Metade" e "Curar" direto no chat.
- **Rolar dano**: o cartao de um ataque (ficha, monstro, T20/Ordem) ganha o botao **Rolar dano**
  com o dano daquela arma. Se o d20 foi critico vira **Rolar dano critico** (dados dobrados).
- **Girar token**: com o token selecionado, **R** gira 45 graus (Shift+R volta); ou botao
  direito > Girar. So a imagem gira; nome e barras ficam retos.
- **Regua de movimento**: ao arrastar um token aparece a distancia (regra de 5e, diagonal
  conta 1). Verde = dentro do deslocamento da ficha/monstro; laranja = precisa de Disparada;
  vermelho = longe demais.

### Campanha: o caderno do mestre

Botao **Campanha** na barra de cima. Abas:

- **Jogadores**: XP e ouro em lancamentos (data, valor, motivo), nivel do D&D 5e pela XP, presencas e
  faltas. "+XP pra todos", "+PO pra todos", "Marcar presenca de todos" e "+ Jogadores conectados".
- **Calendario**: data e hora do mundo (meses, dias da semana, estacoes e lua configuraveis), "+10 min /
  +1 h / +8 h / +1 / +7 / +30 dias", clima do dia sorteado pela estacao (sempre o mesmo pra mesma data).
  **Eventos** marcados em datas avisam quando o tempo passa por eles (os visiveis vao pro chat). **Viagem**:
  distancia em km + ritmo (lento, normal, rapido) + terreno dificil = quanto tempo leva, e "Avancar esse
  tempo". Opcao **"A luz do mapa acompanha a hora"**: dia claro, entardecer e amanhecer em penumbra, noite escura.
- **PdMs**: nome, atitude com o grupo, onde esta, o que os jogadores sabem e os segredos (so o mestre).
- **Aventuras**: numero, estado (ativa, pausada, completa, falhou), datas, objetivo, resultado e notas do mestre.
- **Sessoes**: numero e data automaticos, resumo e link.
- **Anotacoes do mestre**: nunca saem do PC do mestre.
- **Tesouro** (so o mestre): parcelas preparadas antes da sessao (itens, moedas e XP). "Entregar..."
  escolhe pra quem vao os itens (uma ficha ou o inventario do grupo), divide as moedas entre as fichas
  marcadas e a XP entre os jogadores da Campanha, e avisa no chat.
  **Itens nao identificados**: no item, marque "Nao identificado" e escreva o nome que os jogadores
  veem ("espada de lamina avermelhada"). Na ficha deles entra so esse nome, sem valor; o nome verdadeiro
  fica so com o mestre. "Identificar" troca o nome na ficha de quem estiver com o item (mesmo que ele
  tenha passado pro grupo).
- **Encontros** (so o mestre): coloque os monstros no mapa como quer, selecione (Shift+clique ou caixa) e
  "Salvar selecao do mapa como encontro". Na hora, "Colocar no mapa" poe todos no meio da tela com a mesma
  formacao, escondidos (opcional) e com a iniciativa rolada.

Cada item tem **"Visivel aos jogadores"**: so o que estiver marcado vai pra eles (o servidor nem manda
o resto), e eles veem a mesma janela sem poder editar. Fica em `campanha.json` na pasta da mesa.

### Modelos de ficha (PDF editavel)

Qualquer ficha em PDF **preenchivel** (a oficial do seu sistema: Old Dragon, Tormenta, 5e, o que for) vira um
modelo. Ao criar uma ficha ("Nova ficha..." ou "Novo monstro / PdM...") escolha o modelo: os sistemas prontos
(D&D 5e, Tormenta20, Ordem Paranormal; editor de monstro do 5e) ou um modelo de PDF da mesa. A opcao
"Importar modelo de PDF editavel..." aparece na mesma lista; tambem da pra gerenciar em Fichas > Mais >
"Modelos de ficha (PDF editavel)...".

- A ficha mostra **a propria pagina do PDF** com os campos editaveis no mesmo lugar (zoom no alto da janela).
- Na importacao o programa adivinha quais campos sao **nome, PV atuais, PV maximos, CA, iniciativa e
  deslocamento** (da pra conferir e trocar): esses ligam a ficha ao token e ao rastreador. Dano no token
  muda o PV no PDF, e escrever o PV no PDF muda o token.
- Botao direito num campo com numero: **rolar d20 + o valor**.
- **"Exportar PDF preenchido..."** salva o PDF original com tudo que foi escrito (pra imprimir ou mandar).
- As paginas viram imagens da mesa e o modelo vai junto dentro de cada ficha: os jogadores veem a ficha
  igual, mesmo sem ter o PDF. Modulos levam as fichas de modelo com as paginas.

### Auras

Botao direito no token > **"Auras..."**: um efeito (no mesmo formato dos efeitos, ex.: `Aura de Protecao
[salv+3]` ou `Bencao`), o raio em pes e pra quem vale (aliados, inimigos ou todos; "vale pra ele tambem").
Quem entra no raio ganha o efeito sozinho (aparece como "... (aura de Fulano)" e conta nas rolagens); quem
sai perde. O circulo tracejado mostra o alcance. Quem calcula e o programa do mestre.

### Nomes sortidos pros monstros

Colocar o mesmo monstro de novo no mapa da "Goblin Feroz", "Goblin de Olho Vermelho"... em vez de
"Goblin 2". Da pra desligar na engrenagem (Monstros).

### Modulos: levar conteudo pra outra mesa

Engrenagem > **"Exportar modulo..."**: marque cenas, monstros, fichas, o diario e o conteudo do mestre da
Campanha (PdMs, aventuras, tesouro, encontros). Gera um arquivo `.customizr-modulo` com as imagens.
**"Importar modulo..."** junta tudo na mesa aberta sem apagar nada (nome repetido vira "Nome 2"; fichas
chegam sem dono).

### Temas

Engrenagem > tema: Fogueira (padrao), Claro, Escuro, Ordem Paranormal, Starfinder, Cyberpunk RED,
Apocalypse World, **D&D**, **Old Dragon** e **Tormenta 20** (so cores e fonte, nada de logo ou arte).

### Esconder o que o mestre preparou

- **Fichas**: botao direito na lista > "Esconder dos jogadores". So o mestre (e o dono, se tiver) recebe.
  Se o jogador ja tinha recebido, a ficha some da lista dele.
- **Monstros**: nascem escondidos (o jogador nao ve nem que o monstro existe); botao direito > "Mostrar
  a ficha aos jogadores" libera.
- **Cenas**: o jogador nunca recebe a lista de cenas, so a cena aberta. "Abrir escondida (preparar)"
  deixa ele so com o aviso "o mestre esta preparando a cena" ate voce mostrar.

### Pins: pontos de informacao que abrem outros mapas

Barra do mapa > **Pins** > **"Colocar pin"** e clique no mapa (so o mestre cria).
Cada pin tem nome, tipo (cidade, capital, vila, loja, taverna, templo, masmorra,
perigo, tesouro, informacao), um texto e, se quiser, **uma cena ligada**:

- Clicar no pin abre um cartao com o texto. Pin ligado a uma cena mostra
  **"Abrir mapa: ..."**: o mestre clica e a mesa toda vai pra la. Um botao
  **"Voltar para ..."** aparece no canto pra voltar ao mapa anterior (pais ->
  cidade -> loja e volta).
- Na janela do pin, **"Nova cena com imagem..."** cria a cena do lugar e ja liga;
  **"Gerar no Watabou"** abre o gerador de cidade/vila/masmorra com aquele nome
  (exporte o PNG la e use "Nova cena com imagem...").
- **Oculto**: so o mestre ve; o servidor nem manda pros jogadores. "Mostrar aos
  jogadores" no cartao revela.
- Arrastar move (mestre); botao direito edita/apaga. Jogador ve os pins (tambem
  pelo navegador), le o cartao e pode clicar **"Pedir pra ir"** (sussurro pro mestre).
- Importando o Azgaar com o JSON, as capitais e maiores cidades ja viram pins (com
  reino, populacao e o link do gerador de cidade). Na masmorra do Watabou, cada nota
  numerada vira um pin oculto em cima do numero.

**Pelo terminal** (qualquer sistema), com um JDK 25+ no `JAVA_HOME`:

```
cd vtt-client
mvnw.cmd javafx:run      (Windows)
./mvnw javafx:run        (Linux/Mac)
```

ou `mvn javafx:run` se tiver o Maven instalado. `mvnw.cmd test` roda os
testes (~1070 execucoes, contando repeticoes).

**Se nao achar JDK**: instale um JDK 25+ (ex.: adoptium.net) ou, no
IntelliJ, *File > Project Structure > SDK > Download JDK* (versao 25+).

Todos na mesa precisam da MESMA versao do programa (protocolo de rede 3).

Testado nesta maquina: JDK 27 + JavaFX 27.0.1 + Maven 3.9.9 -- `rodar.cmd`
sem `JAVA_HOME` e sem Maven instalado abre o programa e passa os testes.

### Abrindo no IntelliJ

Isto e um projeto Maven padrao (tem `pom.xml` na raiz) -- nao precisa de
nenhum passo especial:

1. **File > Open...** e escolha a pasta raiz do projeto (a que tem o
   `pom.xml`, `src/`, este `README.md` etc. dentro -- a pasta que voce
   extraiu do zip). O IntelliJ detecta o `pom.xml` automaticamente e
   oferece "Open as Project" (ou simplesmente abre direto, dependendo da
   versao).
2. Deixe o IntelliJ importar as dependencias do Maven (ele baixa o
   `javafx-controls`/`javafx-base`/`javafx-graphics` 27.0.1 de verdade do
   Maven Central na primeira vez -- sua maquina tem internet normal,
   diferente deste sandbox). Acompanhe a barra de progresso no canto
   inferior direito ("Importing Maven project...").
3. Confira o **Project SDK**: `File > Project Structure > Project` --
   voce ja confirmou que o JDK 27.0.1 esta selecionado ali, entao esta
   parte ja esta pronta.
4. Pra rodar: abra a aba **Maven** (geralmente uma aba lateral vertical
   escrita "Maven") > `vtt-client` > `Plugins` > `javafx` > de duplo
   clique em `javafx:run`. Isso compila e abre a janela do programa.
   Alternativa: `Run > Edit Configurations... > +  > Maven`, com
   "Command line" = `javafx:run`, e depois rodar essa configuracao
   sempre que quiser (fica salva, nao precisa cavar o menu Maven de novo).

Se der erro de "release version X not supported" ou parecido ao compilar,
confira se o `maven.compiler.release` do `pom.xml` (hoje em `25`) bate com
o JDK que o IntelliJ esta usando pra esse projeto -- isso costuma ser
"Project SDK" errado, nao um problema do codigo.

## Importador de classes do 5etools

Painel **Fichas** > **"Abrir compêndio..."** > "Importar classes (class-\*.json)...". Le o formato
de verdade do 5etools e monta classe + subclasses + features por nivel --
logica portada direto do `ClassService` do projeto `customizr` da sua
noiva (Spring Boot), adaptada pra Gson/arquivo. Ver `HANDOFF.md` pro que
foi testado (a logica de casamento/deteccao, 24 casos) e o que nao foi
(a leitura do JSON em si).

## Inventario, Party e Feats

Inspirado em padroes do projeto `customizr` (Spring Boot) da sua noiva:
sistema de moedas/inventario de verdade (`com.vttcaseiro.inventory`),
compartilhado pela Party (`com.vttcaseiro.party.Party`) e individual por
ficha, e Feats com pre-requisito polimorfico de verdade
(`com.vttcaseiro.character.prerequisite`). Tudo testado de verdade exceto
a peça que ensina o Gson a salvar a lista de pre-requisitos (ver
`HANDOFF.md`). **Agora tem aba "Party"**: nome (editavel), membros
(adicionar a ficha aberta / remover selecionado), inventario
compartilhado (adicionar item, transferir 1 unidade pra ficha aberta) --
sincroniza pela rede com todo mundo conectado. Transferir pra uma ficha
que nao seja a ultima aberta e editar dinheiro da Party ainda nao tem
botao (ver `HANDOFF.md`).

## Backend reestruturado

`Main.java` nao mexe mais direto em `SceneModel`, `GameClient`/
`GameServer` ou `LibraryStorage`. Isso tudo mora em `com.vttcaseiro.service`
(MesaService, SceneService, CharacterService, NetworkService), que falam
com uma interface de armazenamento (`com.vttcaseiro.repository.MesaRepository`)
em vez de uma classe concreta. A rede ganhou heartbeat (detecta queda de
conexao de verdade) e validacao de dono de token/mestre no servidor. Ver
`HANDOFF.md` pra detalhes e pro que foi testado.

## IMPORTANTE: compila de verdade, mas nunca rodou com tela nenhuma

Esse ambiente tem `javac` e um `.jar` do Gson de verdade, mas **nao tem
acesso a internet pra baixar o JavaFX** (nem pelo Maven Central nem por
nenhum espelho -- ja testei um monte, e e bloqueio de rede da sessao,
nao falta de esforco). Pra nao deixar os 9 arquivos que usam JavaFX
(`Main.java`, `ImageCodec.java`, `NetworkService.java`, os 6 de `view/`)
so "revisados a mao" pra sempre, escrevi uma biblioteca de **stubs**
do JavaFX (classes fajutas com os mesmos nomes/metodos da API real, sem
nenhuma implementacao por dentro) e compilei o projeto INTEIRO contra
ela com o `javac` real. Resultado: **os 82 arquivos .java de
`src/main/java` compilam sem erro nenhum, pela primeira vez.** Isso
pega erro de tipo/metodo/assinatura em tudo, incluindo a tela -- mas
**NAO prova que a tela renderiza, que o layout fica bonito ou que um
clique de fato dispara o que deveria**, porque nenhum JavaFX real rodou
aqui ainda. A camada de rede (sockets) e `AppSettings` continuam sendo
a excecao testada de verdade (com testes equivalentes num `java` puro,
sem precisar de tela). Ver `HANDOFF.md`, secao "Verificacao desta
rodada: os 82 arquivos .java do projeto compilam de verdade agora",
pra entender exatamente o que isso prova e o que nao prova.

## Primeira vez abrindo

1. O programa pede a pasta onde ficam suas mesas (ele lembra depois disso,
   nao pergunta de novo nas proximas vezes).
2. Escolha "+ Nova mesa..." e de um nome (ex.: o nome da campanha).
3. Isso abre a janela principal, ja dentro dessa mesa.

## O que tem

- **Mesas**: cada mesa e uma pasta de verdade (com `mapas/` e `fichas/`
  dentro). Trocar de pasta de mesas, criar mesa nova, reabrir uma mesa
  existente -- tudo na tela inicial.
- **Cenas (mapas)**: aba "Mapa" mostra a lista de cenas da mesa atual.
  "+ Nova cena..." pede uma imagem (PNG/JPG/WEBP -- funciona com mapas
  exportados do Azgaar's Fantasy Map Generator ou do Watabou City
  Generator) e um nome, salva na mesa e ja troca pra ela. Clique duplo
  na lista abre outra cena salva. "Salvar cena atual na mesa" grava
  qualquer mudanca (tokens, grade) de volta.
- **Grade**: desenhada por cima do mapa (70px por celula por padrao).
  Campo "Grade" + "Aplicar" muda o tamanho a qualquer momento. Campo
  "Pes por celula" (5 por padrao, a regra padrao do 5e) alimenta a regua
  de medicao e os templates de area abaixo.
- **Regua de medicao**: botao "Medir" na barra flutuante -- clicar-e-
  arrastar no mapa vazio desenha uma linha e mostra a distancia em pes
  (regra de movimento do 5e de verdade: diagonal custa o mesmo que
  ortogonal, nao a hipotenusa de Pitagoras).
- **Rastreador de iniciativa**: aba "Iniciativa", lista os tokens da cena
  ordenados por iniciativa (maior primeiro). "Rolar iniciativa pra todos
  (1d20)", clique duplo pra editar a mao (ex.: somar modificador),
  "Proximo turno"/"Reiniciar rodada" com destaque de quem esta na vez.
  Local desta janela, nao sincroniza pela rede (ver `HANDOFF.md`).
- **Condicoes no token**: botao direito num token abre um seletor com as
  15 condicoes do 5e (Envenenado, Atordoado, Caido...); cada uma marcada
  aparece como um selinho colorido em cima do token. Local, nao
  sincroniza pela rede.
- **Templates de area de efeito**: botao "Area (template)" + seletor de
  forma (Circulo/Cone/Linha/Cubo) na barra flutuante -- clicar-e-arrastar
  desenha a area e mostra o tamanho em pes; "Limpar area" apaga. Local,
  nao sincroniza pela rede.
- **Zoom**: roda do mouse, **centrado no cursor** (o ponto do mapa sob o
  mouse continua sob o mouse depois do zoom, nao "escorrega"). **Navegar**:
  arrastar com o botao direito. **Centralizar**: botao na barra de
  ferramentas flutuante sobre o mapa, reseta zoom/posicao.
- **Tokens**: "+ Token" (na barra flutuante sobre o mapa) pede um nome e
  oferece escolher uma imagem -- cancelar o seletor de imagem cria o
  token mesmo assim, com uma silhueta padrao no lugar. "+ Token (galeria)"
  faz a mesma coisa escolhendo de uma imagem JA usada antes nesta mesa
  (ver "Galeria de imagens" abaixo), sem reabrir o seletor de arquivo.
  Arraste com o botao esquerdo pra mover (encaixa na grade ao soltar);
  passar o mouse por cima destaca o token; duplo-clique edita o PV (e
  aparece uma barrinha colorida de vida acima do token quando o PV esta
  definido).
- **Fichas**: aba "Fichas" mostra a lista de fichas da mesa atual.
  "+ Nova ficha..." pede SO o nome (sem forcar escolher imagem nenhuma --
  a ficha nasce com uma silhueta generica) e abre numa janela propria com
  atributos, PV/CA e uma lista de tracos/feats clicavel (manda pro chat).
  Clicar no retrato troca a imagem depois, se quiser, ou use o botao
  "Galeria" ao lado pra reusar uma imagem ja guardada nesta mesa;
  arrastar o retrato pro mapa cria um token vinculado aquela ficha. A
  janela da ficha tambem tem **slots de magia** (por nivel, com "Usar"/
  "Descanso longo" e um botao pra configurar o maximo de cada nivel) e
  **personalidade** (idade/altura/olhos/pele/cabelo/alinhamento/tracos/
  ideais/vinculos/defeitos/historia, tudo opcional e editavel numa janela
  propria).
- **Chat/Dados unificados**: um campo so, sempre visivel embaixo da tela.
  Mensagem normal, ou `/r 2d6+3` (ou `/roll 1d20+5`) pra rolar. Botoes
  rapidos: d20, d100, Vantagem, Desvantagem (sincronizam pela rede quando
  conectado). Cada linha do chat tem uma cor pelo tipo (rolagem, chat,
  erro, sistema).
- **Rede**: painel "Rede". "Hospedar aqui" sobe um servidor nesta maquina
  (a cena atual vira o ponto de partida compartilhado) e ja conecta nele;
  "Entrar numa mesa online" (tela inicial ou painel Rede) entra no servidor de outra pessoa.
  Conectado, mover token / adicionar token / rolar dado (incluindo
  vantagem/desvantagem) passam a valer pra todo mundo. **Imagens de token
  e de mapa viajam pela rede de verdade agora** (nao so o caminho do
  arquivo -- isso fazia a imagem sumir pra quem nao era o dono do
  arquivo). Mostra a lista de quem esta conectado agora, atualizada
  sozinha. So o mestre (quem hospedou) e quem criou um token controla
  ele; conexao caindo avisa na tela em vez de ficar em silencio. Ver
  `HANDOFF.md` pra saber exatamente o que foi testado de verdade aqui.
- **Testes**: `mvn test` roda os testes de `DiceRoller`, `NetMessage`,
  `NetCodec` e `LibraryStorage` (`src/test/java`).

## Fichas de monstro e CR

`MobSheet` e uma ficha de monstro/NPC, construida em cima do mesmo
vocabulario de `CharacterSheet` (tamanho, tipo de criatura, alinhamento,
resistencias/imunidades a dano, sentidos, idiomas, acoes/acoes
legendarias clicaveis). `ChallengeRatingCalculator` sugere o CR de um
monstro caseiro a partir de PV/CA/dano/ataque, seguindo o algoritmo do
Dungeon Master's Guide. Guardado em `mobs/` dentro da mesa, do mesmo
jeito que uma ficha de jogador fica em `fichas/`. **Importador do
bestiario do 5e.tools pronto**: aba "Fichas", botao "Importar monstros
(bestiary-\*.json)..." -- importa um `bestiary-*.json` inteiro de uma
vez (testado com os 503 monstros do Manual dos Monstros 2024 de
verdade); `bestiario-seed/bestiary-xmm.json` ja vem com esse arquivo
pronto. **Agora tem tela tambem**: clique duplo num monstro da lista
abre a ficha (`MobSheetView`), com os mesmos tracos/acoes/acoes
legendarias clicaveis pro chat que a ficha de jogador tem. Percepcao
passiva e bloco de conjuracao estruturado continuam de fora (ver
`HANDOFF.md`).

## Galeria de imagens: deduplicado por hash + reutilizavel

`ImageAssetStore` guarda cada imagem UMA VEZ por conteudo (hash SHA-256)
em `assets/images/` dentro da mesa -- 10 tokens com o mesmo retrato nao
guardam 10 copias da mesma imagem. `imageAssetId` em `TokenModel`/
`CharacterSheet`/`MobSheet` referencia esse id. **Agora tem tela
tambem** (`ImageGalleryView`): escolher uma imagem num token novo
("+ Token (galeria)") ou no retrato de uma ficha (botao "Galeria")
sem reabrir o seletor de arquivo -- toda imagem escolhida pelo seletor
de arquivo normal tambem entra na galeria automaticamente, pra poder
ser reusada depois. `MobSheetView` ainda nao usa a galeria pro retrato
do monstro (so `TokenNode`/`CharacterSheetView` foram migrados). Sem
"apagar imagem da mesa" ainda. Ver `HANDOFF.md`.

## Compendio e anexos na ficha

Classes, antecedentes, especies, feats, itens e magias agora podem
viver num "compendio" por mesa (`compendio.json`), e uma ficha pode
ANEXAR uma entrada dele (so a referencia -- corrigir a entrada depois
atualiza em toda ficha que a anexou). **Os 5 importadores de
antecedente/especie/feat/item/magia do 5e.tools estao prontos** (texto
convertido de JSON cru pra legivel, antecedente ligado ao feat que
concede, especie ligada a magia que concede) -- ver
`compendio-seed/LEIA-ISTO.md` pra um compendio JA PRONTO com o
conteudo do Jogador e do Mestre 2024 (XPHB/XDMG), pra copiar direto
pra sua mesa. **Agora tem tela tambem**: aba "Fichas" tem um botao de
importar pra cada um dos 5 tipos (abre o arquivo `.json` do 5e.tools),
e uma caixa de busca + "Anexar na ficha aberta" pra ligar uma entrada
do compendio numa ficha sem editar JSON na mao. Na janela da ficha,
os anexos aparecem numa segunda lista (abaixo dos tracos), clicavel
igual aos tracos -- clicar manda o texto atual da entrada pro chat.
Ver `HANDOFF.md` pra detalhes de cada importador.

## Rede: ficha/monstro/party/mapa sincronizam agora

Alem de token/dado/chat, a rede agora sincroniza ficha de personagem,
ficha de monstro, a Party (com o inventario compartilhado) e troca de
mapa/cena entre todos os conectados -- incluindo quem entra DEPOIS de
algo ja ter sido sincronizado. Ver `HANDOFF.md`.

## Rede: imagens, senha de mesa/mestre e reconexao

- **Imagens pela rede**: token/ficha/monstro/mapa agora levam a imagem
  junto quando sincronizados -- quem entra depois recebe as imagens que
  ja estavam em uso, e quem envia so manda cada imagem uma vez por
  conexao (deduplicado pelo mesmo hash do `ImageAssetStore`).
- **Senha de mesa e senha de mestre**: a aba "Rede" tem os campos pra
  digitar as duas, independentes e opcionais -- uma pra entrar na mesa
  (senha errada fecha a conexao), outra pra ser aceito como mestre
  (so habilitada se marcar "Entrar como mestre"; senha errada rebaixa
  pra jogador comum, sem avisar).
- **Reconexao sem perder posse de token**: o id de jogador agora e
  estavel por maquina (salvo em `~/.vttcaseiro/player-id.txt`), nao
  mais um id aleatorio novo a cada conexao -- reabrir o programa e
  reconectar devolve a posse dos proprios tokens em vez de criar um
  "jogador fantasma" novo.

Ver `HANDOFF.md` pra detalhes, o que foi testado e o que ficou de fora
em cada um.

## O que NAO tem ainda (de proposito)

- Validacao de dono de token na rede (`TokenModel.ownerId` existe mas o
  servidor ainda nao confere -- qualquer um move qualquer token).
- Aviso na tela de "perdeu conexao com o servidor".
- Tela de edicao de atributos/PV/CA/tracos de uma ficha ja criada (so da
  pra criar com nome, ou editar o JSON na mao).
- `MobSheetView` ainda nao le o retrato via galeria (so `imagePath`).
- Apagar uma imagem da galeria (`ImageGalleryView` so escolhe, nao
  remove).
- Transferir um item da Party pra uma ficha que nao seja a ultima
  aberta, e editar o dinheiro (`Money`) da Party.
- Fog of war (visao nebulosa por jogador) -- precisa de um conceito novo
  de visibilidade que hoje nao existe no modelo.
- Iniciativa, condicoes do token e templates de area pela rede (hoje sao
  locais desta janela -- ver secao acima e `HANDOFF.md`).
- Exaustao com niveis (hoje e "tem ou nao tem", o 5e 2024 tem 6 niveis).
- Compendio de regras (reaproveitando o pipeline do projeto irmao do
  Firecast).

## Antes de mexer no codigo

Leia o `HANDOFF.md` -- tem as decisoes de arquitetura, os porques delas,
e o que foi testado de verdade vs so revisado, rodada por rodada.

## Estrutura do codigo

```
com.vttcaseiro.model       -> GridSettings, TokenModel, SceneModel (dados
                               puros, sem JavaFX -- o que viaja pela rede
                               e o que fica salvo numa mesa)
com.vttcaseiro.character   -> CharacterSheet, CharacterTrait, Feat (idem,
                               dados puros)
com.vttcaseiro.character.prerequisite -> Prerequisite (interface, padrao
                               polimorfico) + LevelPrerequisite,
                               AbilityScorePrerequisite, FeatPrerequisite,
                               PrerequisiteAdapter (Gson)
com.vttcaseiro.inventory   -> Currency, Money, InventoryItem, Inventory --
                               usado pelo inventario individual da ficha
                               e pelo compartilhado da Party
com.vttcaseiro.party       -> Party (grupo: membros + inventario
                               compartilhado)
com.vttcaseiro.content     -> Source, SourceConfig, Feature, FeatureGrant,
                               Subclass, DndClass (modelo de classe de
                               D&D) + ClassJsonAnalyzer (logica de
                               importacao do 5etools, testada) +
                               ClassImporter (le o JSON de verdade --
                               ver HANDOFF.md pro que foi/nao foi testado)
com.vttcaseiro.dice        -> DiceRoller, RollResult, DiceException
                               (mesma matematica do dice_engine.lua do
                               projeto do Firecast; inclui vantagem/
                               desvantagem, testado de verdade)
com.vttcaseiro.repository  -> MesaRepository: a INTERFACE de armazenamento
                               de uma mesa. Services dependem dela, nunca
                               da implementacao concreta.
com.vttcaseiro.library     -> LibraryStorage: a UNICA implementacao de
                               MesaRepository hoje (pasta com mapas/ e
                               fichas/, Gson)
com.vttcaseiro.persistence -> SceneStorage (usado por LibraryStorage pra
                               salvar/carregar um SceneModel especifico)
com.vttcaseiro.service     -> MesaService, CharacterService, SceneService
                               (com SceneChangeListener), NetworkService --
                               a logica de verdade. Main.java so chama
                               estes, nunca mexe direto em model/net/
                               repository.
com.vttcaseiro.net         -> GameServer, ClientHandler, GameClient,
                               NetMessage, NetCodec, ImageCodec (protocolo
                               de rede, com heartbeat e validacao de
                               dono/mestre -- ver HANDOFF.md pro que foi
                               testado)
com.vttcaseiro.view        -> MapView, TokenNode (o mapa e os tokens),
                               CharacterSheetView (janela da ficha),
                               MobSheetView (janela da ficha de monstro),
                               ImageGalleryView (escolher uma imagem ja
                               guardada na mesa), MesaSelectorView (tela
                               inicial de escolher mesa), ChatListCell
                               (linha colorida do chat), SilhouetteIcon
                               (avatar padrao desenhado com formas, sem
                               imagem nenhuma)
com.vttcaseiro.app         -> Main (orquestrador fino -- monta a tela e
                               liga eventos aos servicos), LogEntry (uma
                               linha do chat, com tipo pra colorir),
                               AppSettings (lembra a pasta de mesas --
                               testado de verdade), Log (log central,
                               java.util.logging)
```

## Visual

Tema escuro via `src/main/resources/style.css` (nao mais estilo inline
espalhado), layout em abas (Mapa/Fichas/Rede) com o chat sempre visivel
ao lado (nunca escondido atras de aba, igual Foundry), paineis
redimensionaveis (`SplitPane`).
