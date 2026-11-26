package com.widgetcoins;

/** Uma moeda acompanhada pelo widget (id da CoinGecko + símbolo exibido). */
public final class Coin {
    public final String id;
    public final String symbol;
    public final String name;

    public Coin(String id, String symbol, String name) {
        this.id = id;
        this.symbol = symbol.toUpperCase();
        this.name = name;
    }
}
