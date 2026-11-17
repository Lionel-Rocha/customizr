package com.vttcaseiro.content;

/** Um livro/fonte de conteudo (nome completo + sigla), ex.: "Player's
 * Handbook" / "PHB". Mesmo formato que o Source do projeto da minha
 * noiva (customizr) -- so sem JPA, porque aqui e tudo arquivo, nao banco. */
public class Source {
    private String name;
    private String code;

    public Source() {
    }

    public Source(String name, String code) {
        this.name = name;
        this.code = code;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
