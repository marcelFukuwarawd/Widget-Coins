package com.widgetcoins;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Cliente simples da API pública da CoinGecko (não precisa de chave). */
public final class PriceApi {
    private static final String BASE = "https://api.coingecko.com/api/v3";

    private PriceApi() {}

    /** Retorna mapa coinId -> {preço, variação 24h em %}. */
    public static Map<String, double[]> fetchPrices(Collection<String> ids, String currency) throws Exception {
        Map<String, double[]> out = new HashMap<>();
        if (ids.isEmpty()) return out;
        String joined = String.join(",", ids);
        String url = BASE + "/simple/price?ids=" + URLEncoder.encode(joined, "UTF-8")
                + "&vs_currencies=" + currency + "&include_24hr_change=true";
        JSONObject root = new JSONObject(get(url));
        for (String id : ids) {
            JSONObject o = root.optJSONObject(id);
            if (o == null || !o.has(currency)) continue;
            out.put(id, new double[]{o.getDouble(currency), o.optDouble(currency + "_24h_change", 0)});
        }
        return out;
    }

    /** Pesquisa moedas pelo nome ou símbolo. */
    public static List<Coin> search(String query) throws Exception {
        String url = BASE + "/search?query=" + URLEncoder.encode(query, "UTF-8");
        JSONArray arr = new JSONObject(get(url)).optJSONArray("coins");
        List<Coin> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length() && i < 15; i++) {
            JSONObject o = arr.getJSONObject(i);
            list.add(new Coin(o.getString("id"), o.getString("symbol"), o.optString("name")));
        }
        return list;
    }

    private static String get(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(7000);
        conn.setReadTimeout(7000);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "WidgetCoins/1.0 (Android)");
        try {
            int code = conn.getResponseCode();
            if (code != 200) throw new Exception("HTTP " + code);
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }
}
