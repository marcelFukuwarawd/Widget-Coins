package com.widgetcoins;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuração de cada widget, salva em SharedPreferences.
 * O id {@link #DEFAULT_ID} guarda o padrão usado por novos widgets.
 */
public final class WidgetConfig {
    public static final int DEFAULT_ID = -1;
    public static final int TEXT_AUTO = 0;

    private static final String PREFS = "widget_coins";

    public List<Coin> coins = new ArrayList<>();
    public String currency = "usd";
    /** Tamanho do texto em sp; {@link #TEXT_AUTO} ajusta ao tamanho do widget. */
    public int textSize = TEXT_AUTO;
    /** Opacidade do fundo, 0 (totalmente transparente) a 100. */
    public int bgOpacity = 0;
    public boolean showChange = true;
    public boolean showFooter = true;

    public static List<Coin> defaultCoins() {
        List<Coin> list = new ArrayList<>();
        list.add(new Coin("bitcoin", "BTC", "Bitcoin"));
        list.add(new Coin("ethereum", "ETH", "Ethereum"));
        list.add(new Coin("solana", "SOL", "Solana"));
        return list;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static WidgetConfig load(Context c, int widgetId) {
        SharedPreferences p = prefs(c);
        String raw = p.getString("cfg_" + widgetId, null);
        if (raw == null && widgetId != DEFAULT_ID) raw = p.getString("cfg_" + DEFAULT_ID, null);
        WidgetConfig cfg = new WidgetConfig();
        if (raw == null) {
            cfg.coins = defaultCoins();
            return cfg;
        }
        try {
            JSONObject o = new JSONObject(raw);
            cfg.currency = o.optString("currency", "usd");
            cfg.textSize = o.optInt("textSize", TEXT_AUTO);
            cfg.bgOpacity = o.optInt("bgOpacity", 0);
            cfg.showChange = o.optBoolean("showChange", true);
            cfg.showFooter = o.optBoolean("showFooter", true);
            JSONArray arr = o.optJSONArray("coins");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject co = arr.getJSONObject(i);
                    cfg.coins.add(new Coin(co.getString("id"), co.getString("symbol"),
                            co.optString("name", co.getString("symbol"))));
                }
            }
        } catch (Exception e) {
            cfg.coins = defaultCoins();
        }
        return cfg;
    }

    public void save(Context c, int widgetId) {
        try {
            JSONObject o = new JSONObject();
            o.put("currency", currency);
            o.put("textSize", textSize);
            o.put("bgOpacity", bgOpacity);
            o.put("showChange", showChange);
            o.put("showFooter", showFooter);
            JSONArray arr = new JSONArray();
            for (Coin coin : coins) {
                JSONObject co = new JSONObject();
                co.put("id", coin.id);
                co.put("symbol", coin.symbol);
                co.put("name", coin.name);
                arr.put(co);
            }
            o.put("coins", arr);
            prefs(c).edit().putString("cfg_" + widgetId, o.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static void delete(Context c, int widgetId) {
        prefs(c).edit().remove("cfg_" + widgetId).apply();
    }

    // ---- Cache dos últimos preços, para exibir algo mesmo sem internet ----

    public static void cachePrice(Context c, String currency, String coinId, double price, double change) {
        prefs(c).edit()
                .putString("price_" + currency + "_" + coinId, price + ";" + change)
                .apply();
    }

    /** Retorna {preço, variação24h} ou null se nunca foi buscado. */
    public static double[] cachedPrice(Context c, String currency, String coinId) {
        String v = prefs(c).getString("price_" + currency + "_" + coinId, null);
        if (v == null) return null;
        try {
            String[] parts = v.split(";");
            return new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1])};
        } catch (Exception e) {
            return null;
        }
    }

    public static void setLastUpdate(Context c, long time) {
        prefs(c).edit().putLong("last_update", time).apply();
    }

    public static long lastUpdate(Context c) {
        return prefs(c).getLong("last_update", 0);
    }
}
