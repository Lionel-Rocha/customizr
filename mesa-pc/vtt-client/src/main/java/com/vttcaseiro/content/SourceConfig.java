package com.vttcaseiro.content;

/** Se uma fonte esta habilitada nesta mesa -- mesmo padrao do
 * CampaignSourceConfig dela (campaign+source+enabled), so que aqui a
 * "campaign" e a propria mesa (implicito -- cada mesa tem a sua lista de
 * SourceConfig, nao precisa guardar o id da mesa dentro do proprio
 * objeto). Permite o mestre desligar um livro especifico pra uma
 * campanha sem ter que desinstalar o conteudo inteiro. */
public class SourceConfig {
    private Source source;
    private boolean enabled;

    public SourceConfig() {
    }

    public SourceConfig(Source source, boolean enabled) {
        this.source = source;
        this.enabled = enabled;
    }

    public Source getSource() { return source; }
    public void setSource(Source source) { this.source = source; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
