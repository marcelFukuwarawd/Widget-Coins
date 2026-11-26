package com.widgetcoins;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CoinWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "com.widgetcoins.ACTION_REFRESH";
    /** Atualização automática a cada 5 minutos. */
    private static final long REFRESH_INTERVAL = 5 * 60 * 1000L;

    private static final int GREEN = 0xFF2FD3A1;
    private static final int RED = 0xFFFF5C6C;

    private static volatile boolean refreshing = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_REFRESH.equals(intent.getAction())) {
            refreshAll(context, goAsync());
            return;
        }
        super.onReceive(context, intent);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        scheduleAlarm(context);
        for (int id : ids) render(context, manager, id);   // mostra o cache imediatamente
        refreshAll(context, goAsync());                     // e busca preços novos
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, Bundle newOptions) {
        // Chamado quando o usuário redimensiona o widget: recalcula o tamanho do texto.
        render(context, manager, id);
    }

    @Override
    public void onEnabled(Context context) {
        scheduleAlarm(context);
    }

    @Override
    public void onDisabled(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(refreshIntent(context, 0));
    }

    @Override
    public void onDeleted(Context context, int[] ids) {
        for (int id : ids) WidgetConfig.delete(context, id);
    }

    // ------------------------------------------------------------------

    /**
     * Alarme repetitivo de 5 min. É "não-wakeup": com a tela apagada o Android
     * segura o alarme e o entrega assim que o aparelho acorda, poupando bateria.
     */
    private static void scheduleAlarm(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.setRepeating(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + REFRESH_INTERVAL, REFRESH_INTERVAL,
                refreshIntent(context, 0));
    }

    private static PendingIntent refreshIntent(Context context, int requestCode) {
        Intent i = new Intent(context, CoinWidgetProvider.class).setAction(ACTION_REFRESH);
        return PendingIntent.getBroadcast(context, requestCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static int[] widgetIds(Context context) {
        return AppWidgetManager.getInstance(context)
                .getAppWidgetIds(new ComponentName(context, CoinWidgetProvider.class));
    }

    /** Busca preços (e logos novos) de todos os widgets em segundo plano e redesenha. */
    public static void refreshAll(Context ctx, PendingResult pending) {
        final Context context = ctx.getApplicationContext();
        final AppWidgetManager manager = AppWidgetManager.getInstance(context);
        final int[] ids = widgetIds(context);
        refreshing = true;
        for (int id : ids) render(context, manager, id);   // mostra "Atualizando…"
        new Thread(() -> {
            try {
                // Agrupa as moedas por moeda de cotação para fazer poucas requisições.
                Map<String, Set<String>> byCurrency = new HashMap<>();
                boolean needFx = false;
                for (int id : ids) {
                    WidgetConfig cfg = WidgetConfig.load(context, id);
                    Set<String> set = byCurrency.get(cfg.currency);
                    if (set == null) byCurrency.put(cfg.currency, set = new LinkedHashSet<>());
                    for (Coin c : cfg.coins) {
                        if (c.isFx()) needFx = true;
                        else set.add(c.id);
                    }
                }
                boolean ok = false;
                if (needFx) {
                    try {
                        double[] fx = PriceApi.fetchUsdBrl();
                        if (Double.isNaN(fx[1])) WidgetConfig.cacheMissingChange(context, "fx", Coin.FX_BRL_USD, fx[0]);
                        else WidgetConfig.cachePrice(context, "fx", Coin.FX_BRL_USD, fx[0], fx[1]);
                        ok = true;
                    } catch (Exception ignored) {
                    }
                }
                for (Map.Entry<String, Set<String>> e : byCurrency.entrySet()) {
                    if (e.getValue().isEmpty()) continue;
                    try {
                        Map<String, PriceApi.Quote> prices = PriceApi.fetchPrices(e.getValue(), e.getKey());
                        for (Map.Entry<String, PriceApi.Quote> p : prices.entrySet()) {
                            PriceApi.Quote q = p.getValue();
                            WidgetConfig.cachePrice(context, e.getKey(), p.getKey(), q.price, q.change24h);
                            if (!CoinIcons.has(context, p.getKey())) {
                                CoinIcons.download(context, p.getKey(), q.imageUrl);
                            }
                        }
                        ok = true;
                    } catch (Exception ignored) {
                    }
                }
                if (ok) WidgetConfig.setLastUpdate(context, System.currentTimeMillis());
            } finally {
                refreshing = false;
                for (int id : ids) render(context, manager, id);
                if (pending != null) pending.finish();
            }
        }).start();
    }

    /** Monta o RemoteViews do widget a partir da configuração e do cache de preços. */
    public static void render(Context context, AppWidgetManager manager, int widgetId) {
        WidgetConfig cfg = WidgetConfig.load(context, widgetId);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_coins);

        int alpha = Math.round(cfg.bgOpacity * 255 / 100f);
        views.setInt(R.id.root, "setBackgroundColor", Color.argb(alpha, 0, 0, 0));

        float s = cfg.textSize == WidgetConfig.TEXT_AUTO
                ? autoTextSize(manager.getAppWidgetOptions(widgetId), cfg)
                : cfg.textSize;

        // --- Cabeçalho
        int headerVis = cfg.showHeader ? View.VISIBLE : View.GONE;
        views.setViewVisibility(R.id.header, headerVis);
        views.setViewVisibility(R.id.header_divider, headerVis);
        views.setTextViewText(R.id.title, cfg.title);
        views.setViewVisibility(R.id.title, cfg.title.isEmpty() ? View.GONE : View.VISIBLE);
        views.setTextViewTextSize(R.id.title, TypedValue.COMPLEX_UNIT_SP, Math.max(14, s * 1.05f));
        views.setTextViewText(R.id.updated, updatedLabel(context));
        views.setTextViewTextSize(R.id.updated, TypedValue.COMPLEX_UNIT_SP, Math.max(10, s * 0.72f));
        views.setInt(R.id.refresh, "setImageAlpha", refreshing ? 90 : 230);

        // --- Linhas
        views.removeAllViews(R.id.rows);
        String cur = currencySymbol(cfg.currency);
        List<Coin> coins = cfg.coins;
        for (int i = 0; i < coins.size(); i++) {
            Coin coin = coins.get(i);
            RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_row);
            row.setImageViewBitmap(R.id.icon, CoinIcons.get(context, coin));
            double[] p;
            String priceText;
            if (coin.isFx()) {
                // Cache guarda R$ por US$ 1 e a variação desse par.
                p = WidgetConfig.cachedPrice(context, "fx", coin.id);
                if (p != null && !cfg.fxInverted) {
                    double pct = p[1];
                    p = new double[]{1 / p[0], Double.isNaN(pct) ? pct : (1 / (1 + pct / 100) - 1) * 100};
                }
                row.setTextViewText(R.id.symbol, cfg.fxInverted ? "USD/BRL" : "BRL/USD");
                row.setTextViewText(R.id.name, cfg.fxInverted ? "Dólar em real" : "Real em dólar");
                priceText = p == null ? "--" : (cfg.fxInverted ? "R$ " : "US$ ") + formatFx(p[0]);
            } else {
                p = WidgetConfig.cachedPrice(context, cfg.currency, coin.id);
                row.setTextViewText(R.id.symbol, coin.symbol);
                row.setTextViewText(R.id.name, coin.name);
                priceText = p == null ? "--" : cur + formatPrice(p[0]);
            }
            row.setTextViewText(R.id.price, priceText);
            if (cfg.showChange && p != null && !Double.isNaN(p[1])) {
                double ch = p[1];
                row.setViewVisibility(R.id.change, View.VISIBLE);
                row.setTextViewText(R.id.change, String.format(Locale.getDefault(), "%s %.2f%%",
                        ch >= 0 ? "▲" : "▼", Math.abs(ch)));
                row.setTextColor(R.id.change, ch >= 0 ? GREEN : RED);
            } else {
                row.setViewVisibility(R.id.change, View.GONE);
            }

            row.setTextViewTextSize(R.id.symbol, TypedValue.COMPLEX_UNIT_SP, s);
            row.setTextViewTextSize(R.id.name, TypedValue.COMPLEX_UNIT_SP, s * 0.88f);
            row.setTextViewTextSize(R.id.price, TypedValue.COMPLEX_UNIT_SP, s * 1.25f); // preço maior
            row.setTextViewTextSize(R.id.change, TypedValue.COMPLEX_UNIT_SP, s * 0.9f);
            if (Build.VERSION.SDK_INT >= 31) {
                float icon = s * 2.3f;
                row.setViewLayoutWidth(R.id.icon, icon, TypedValue.COMPLEX_UNIT_DIP);
                row.setViewLayoutHeight(R.id.icon, icon, TypedValue.COMPLEX_UNIT_DIP);
            }
            row.setViewVisibility(R.id.divider, i == coins.size() - 1 ? View.INVISIBLE : View.VISIBLE);
            views.addView(R.id.rows, row);
        }

        // Atualização manual
        views.setOnClickPendingIntent(R.id.refresh, refreshIntent(context, 1));
        views.setOnClickPendingIntent(R.id.updated, refreshIntent(context, 1));

        // Engrenagem: abre as opções (moedas, tamanho, transparência).
        Intent cfgIntent = new Intent(context, ConfigActivity.class)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        views.setOnClickPendingIntent(R.id.settings, PendingIntent.getActivity(context, 1000 + widgetId,
                cfgIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        manager.updateAppWidget(widgetId, views);
    }

    private static String updatedLabel(Context context) {
        if (refreshing) return "Atualizando…";
        long last = WidgetConfig.lastUpdate(context);
        if (last == 0) return "Toque ⟳ para atualizar";
        if (System.currentTimeMillis() - last < 60_000) return "Atualizado agora";
        return "Atualizado " + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(last));
    }

    /** Escolhe um tamanho de fonte (sp) que caiba na altura/largura atual do widget. */
    private static float autoTextSize(Bundle opts, WidgetConfig cfg) {
        int w = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250);
        int h = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 250);
        if (w <= 0) w = 250;
        if (h <= 0) h = 250;
        int rows = Math.max(1, cfg.coins.size());
        float header = cfg.showHeader ? 42 : 0;
        float perRow = (h - 10 - header) / rows;
        float byHeight = (perRow - 19) / 2.6f;
        float byWidth = (w - 40) / 15f;
        float size = Math.min(byHeight, byWidth);
        return Math.max(9f, Math.min(28f, size));
    }

    static String currencySymbol(String c) {
        switch (c) {
            case "usd": return "$";
            case "brl": return "R$ ";
            case "eur": return "€";
            case "gbp": return "£";
            case "jpy": return "¥";
            case "btc": return "₿";
            default: return c.toUpperCase() + " ";
        }
    }

    static String formatFx(double v) {
        return new DecimalFormat("0.0000", DecimalFormatSymbols.getInstance(Locale.getDefault())).format(v);
    }

    static String formatPrice(double v) {
        DecimalFormatSymbols sym = DecimalFormatSymbols.getInstance(Locale.getDefault());
        DecimalFormat df;
        if (v >= 1000) df = new DecimalFormat("#,##0.00", sym);
        else if (v >= 1) { df = new DecimalFormat("#,##0.00#", sym); }
        else if (v >= 0.01) df = new DecimalFormat("0.0000", sym);
        else df = new DecimalFormat("0.00000000", sym);
        return df.format(v);
    }
}
