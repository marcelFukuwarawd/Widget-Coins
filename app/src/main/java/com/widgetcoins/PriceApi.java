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

    /** Cotação de uma moeda. */
    public static final class Quote {
        public double price;
        public double change24h;
        public String imageUrl;
    }

    /** Busca preço, variação 24h e URL do logo de cada moeda. */
    public static Map<String, Quote> fetchPrices(Collection<String> ids, String currency) throws Exception {
        Map<String, Quote> out = new HashMap<>();
        if (ids.isEmpty()) return out;
        String joined = String.join(",", ids);
        String url = BASE + "/coins/markets?vs_currency=" + currency
                + "&ids=" + URLEncoder.encode(joined, "UTF-8")
                + "&per_page=250&sparkline=false&price_change_percentage=24h";
        JSONArray arr = new JSONArray(get(url));
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            if (o.isNull("current_price")) continue;
            Quote q = new Quote();
            q.price = o.getDouble("current_price");
            q.change24h = o.isNull("price_change_percentage_24h") ? 0 : o.optDouble("price_change_percentage_24h", 0);
            q.imageUrl = o.optString("image", null);
            out.put(o.getString("id"), q);
        }
        return out;
    }

    /** Baixa uma imagem (logo da moeda). */
    public static byte[] download(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(7000);
        conn.setReadTimeout(7000);
        conn.setRequestProperty("User-Agent", "WidgetCoins/1.0 (Android)");
        try {
            if (conn.getResponseCode() != 200) throw new Exception("HTTP " + conn.getResponseCode());
            try (java.io.InputStream in = conn.getInputStream()) {
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                return bos.toByteArray();
            }
        } finally {
            conn.disconnect();
        }
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
