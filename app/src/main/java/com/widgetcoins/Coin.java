package com.widgetcoins;

/** Uma moeda acompanhada pelo widget (id da CoinGecko + símbolo exibido). */
public final class Coin {
    public final String id;
    public final String symbol;
    public final String name;

    /** Id especial da linha de câmbio real x dólar (não é uma cripto). */
    public static final String FX_BRL_USD = "fx:brl-usd";

    public static Coin fxBrlUsd() {
        return new Coin(FX_BRL_USD, "BRL/USD", "Real em dólar");
    }

    public boolean isFx() {
        return FX_BRL_USD.equals(id);
    }

    public Coin(String id, String symbol, String name) {
        this.id = id;
        this.symbol = symbol.toUpperCase();
        this.name = name;
    }
}
