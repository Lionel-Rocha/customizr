# Identidade visual do Customizr (programa de PC + site)

Uma fonte só das cores e dos ícones, pra o programa de PC (JavaFX) e o site ficarem com a mesma cara.

## O que tem aqui

| Arquivo | O que é |
|---|---|
| `tokens.properties` | **A fonte**: as cores dos 12 temas (Fogueira, Claro, Escuro, Ordem Paranormal, Starfinder, Cyberpunk RED, Apocalypse World, D&D: Livro do Jogador, Old Dragon: Regras Básicas, Tormenta20: Livro Básico, Orgulho, Noir) e as regras dos tons intermediários. É aqui que se muda uma cor. Os temas dos livros usam as cores mais marcantes da capa do livro principal (só as cores). |
| `temas-extra/` | Detalhes de um tema que não são cor (ex.: a faixa arco-íris do Orgulho): `<tema>.fx.css` (PC) e `<tema>.web.css` (site). O gerador cola no fim do tema. |
| `GerarTokens.java` | Gera tudo a partir do `tokens.properties`. Rodar na raiz do repositório: `java design/GerarTokens.java` (só precisa do JDK). |
| `web/tokens.css` | *(gerado)* As cores pro site, como variáveis CSS `--c-*`. Tema pelo atributo `<html data-theme="claro">`. |
| `web/temas.js` | *(gerado)* Lista dos temas (chave e nome) pro seletor. |
| `web/icones.js` | *(gerado)* Os mesmos ícones SVG do programa de PC, com `icone(nome, tamanho)`. |
| `web/img/` | *(gerado)* O logo. |
| `web/customizr.css` | O kit visual do site: os mesmos blocos do programa de PC com os mesmos nomes de classe (`button-primary`, `panel-card`, `nav-rail`, `chip`, `top-bar`, `chat-entry`...). |
| `web/demo.html` | A tela principal do jogo feita só com o kit, com o seletor de tema. Abrir num servidor local (os `import` de módulo não funcionam abrindo o arquivo direto). |

O gerador também reescreve, no programa de PC, o bloco de cores do `style.css` (entre `tokens:inicio` e `tokens:fim`) e os `theme-*.css`.

## No front (Angular ou outro)

1. Copiar (ou apontar o build para) `web/tokens.css`, `web/customizr.css`, `web/temas.js`, `web/icones.js` e `web/img/`.
2. Carregar `tokens.css` e depois `customizr.css` como estilo global.
3. Usar as classes do kit nos componentes. Cores próprias de um componente devem usar as variáveis (`var(--c-accent)`, `var(--c-surface)`...), nunca cor fixa — assim todos os temas funcionam sozinhos.
4. Trocar tema: `document.documentElement.dataset.theme = 'claro'`.
