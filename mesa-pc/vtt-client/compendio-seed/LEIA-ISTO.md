# Compendio pronto (XPHB/XDMG, 2024)

`compendio.json` aqui e o resultado de rodar os 5 importadores novos
(`com.vttcaseiro.compendium.*Importer`, ver HANDOFF.md) sobre os dados do
Jogador 2024 (XPHB) e do Mestre 2024 (XDMG) do 5e.tools -- pronto pra usar,
sem precisar importar nada na mao.

## Como usar

Copie este arquivo pra dentro da pasta da sua mesa, substituindo o
`compendio.json` vazio que ja esta la:

```
cp compendio-seed/compendio.json <pasta-da-sua-mesa>/compendio.json
```

(O `CompendiumService` le esse arquivo ao abrir a mesa -- so precisa
estar no lugar certo, nao precisa de nenhum botao na tela.)

## O que tem dentro

| Tipo | Quantidade | Fonte |
|---|---|---|
| Antecedentes (BACKGROUND) | 16 | XPHB |
| Especies (SPECIES) | 10 | XPHB |
| Feats (FEAT) | 77 | XPHB |
| Itens (ITEM) | 678 (94 mundanos + 584 magicos) | XPHB (mundanos) / XDMG (magicos) |
| Magias (SPELL) | 391 | XPHB |

Todos os 16 antecedentes ja estao ligados (`relatedEntryIds`) ao feat que
concedem. 4 das 10 especies (as que de fato concedem magia -- as outras 6
simplesmente nao concedem nenhuma) estao ligadas a magia que concedem.

## Sobre o XMM (Manual dos Monstros 2024)

O XMM NAO contribuiu nada pra este compendio -- ele e o Manual dos
Monstros, e nenhum dos 5 tipos de compendio (antecedente/especie/feat/
item/magia) vem dele. O conteudo util do XMM sao os BLOCOS DE MONSTRO
(`bestiary-xmm.json` no 5e.tools), que viram `MobSheet`, nao
`CompendiumEntry` -- isso seria um importador SEPARADO (de monstro pra
`MobSheet`), que nao foi pedido nesta rodada e nao foi feito.

## Os arquivos .json em `5etools-json/`

Sao os dados crus do 5e.tools, JA FILTRADOS so pras entradas de XPHB/XDMG
(o arquivo original de magias, por exemplo, tem o PHB inteiro MAIS todo
suplemento desde entao -- filtrei pra so sobrar o que pedimos). Servem pra
reimportar do zero (ver `CompendiumImportService`) se um dia alguem quiser
regenerar o `compendio.json` com uma mudanca no importador, ou pra
importar OUTRO livro (edite o filtro de fonte e repita).
