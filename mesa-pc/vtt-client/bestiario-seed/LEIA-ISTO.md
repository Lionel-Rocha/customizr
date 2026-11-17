# Bestiario pronto (XMM, 2024)

`bestiary-xmm.json` aqui e o arquivo de dados cru do 5e.tools com os 503
monstros do Manual dos Monstros 2024 -- pode importar direto, sem
precisar baixar nada.

## Como usar

Na aba "Fichas", secao "Monstros (bestiario 5e.tools...)", botao
"Importar monstros (bestiary-\*.json)...", escolha este arquivo. Os 503
monstros entram de uma vez, cada um como uma `MobSheet` propria guardada
na mesa (pasta `mobs/`). Importar de novo (ou abrir esse mesmo arquivo
outra vez) nao duplica nada -- um monstro com o MESMO NOME que ja existe
na mesa e pulado.

## O que vem preenchido

Tamanho, tipo, alinhamento, CA, PV (media + formula de dado), as 6
velocidades (andar/voar/nadar/escalar/escavar), os 6 atributos,
bonus de salvaguarda/pericia, vulnerabilidade/resistencia/imunidade a
dano, imunidade a condicao, sentidos, idiomas, CR (com o XP calculado a
partir dele por `ChallengeRatingTable`), e os tracos/acoes/acoes
legendarias (convertidos de JSON pra texto legivel, clicavel pra mandar
no chat quando a tela de ficha de monstro existir).

## O que NAO vem (de proposito, ver `MobImporter`/`HANDOFF.md`)

Percepcao passiva (a ficha nao tem esse campo ainda), o bloco de
conjuracao estruturado (vira so mais uma acao em prosa, igual qualquer
outra), grupo de acao legendaria de covil, e a condicao de uma
velocidade especial (ex.: "(hover)" aparece como so o numero, sem o
aviso "so voando").

## Ainda falta a tela

Os monstros importados ficam guardados na mesa, mas **ainda nao existe
uma tela pra ABRIR e ver uma ficha de monstro** (igual existe pra ficha
de jogador) -- so o contador na aba "Fichas" confirma quantos entraram.
Isso e um passo futuro razoavel, nao foi pedido nesta rodada.
