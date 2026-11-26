package com.widgetcoins;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CoinWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "com.widgetcoins.ACTION_REFRESH";
    private static final long REFRESH_INTERVAL = 15 * 60 * 1000L;

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

    private static void scheduleAlarm(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + REFRESH_INTERVAL, REFRESH_INTERVAL,
                refreshIntent(context, 0));
    }

    private static PendingIntent refreshIntent(Context context, int requestCode) {
        Intent i = new Intent(context, CoinWidgetProvider.class).setAction(ACTION_REFRESH);
        return PendingIntent.getBroadcast(context, requestCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Busca preços de todos os widgets em segundo plano e redesenha. */
    public static void refreshAll(Context ctx, PendingResult pending) {
        final Context context = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                AppWidgetManager manager = AppWidgetManager.getInstance(context);
                int[] ids = manager.getAppWidgetIds(new ComponentName(context, CoinWidgetProvider.class));
                // Agrupa as moedas por moeda de cotação para fazer poucas requisições.
                Map<String, Set<String>> byCurrency = new java.util.HashMap<>();
                for (int id : ids) {
                    WidgetConfig cfg = WidgetConfig.load(context, id);
                    Set<String> set = byCurrency.get(cfg.currency);
                    if (set == null) byCurrency.put(cfg.currency, set = new LinkedHashSet<>());
                    for (Coin c : cfg.coins) set.add(c.id);
                }
                boolean ok = false;
                for (Map.Entry<String, Set<String>> e : byCurrency.entrySet()) {
                    try {
                        Map<String, double[]> prices = PriceApi.fetchPrices(e.getValue(), e.getKey());
                        for (Map.Entry<String, double[]> p : prices.entrySet()) {
                            WidgetConfig.cachePrice(context, e.getKey(), p.getKey(), p.getValue()[0], p.getValue()[1]);
                        }
                        ok = true;
                    } catch (Exception ignored) {
                    }
                }
                if (ok) WidgetConfig.setLastUpdate(context, System.currentTimeMillis());
                for (int id : ids) render(context, manager, id);
            } finally {
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

        float textSp = cfg.textSize == WidgetConfig.TEXT_AUTO
                ? autoTextSize(manager.getAppWidgetOptions(widgetId), cfg)
                : cfg.textSize;

        views.removeAllViews(R.id.rows);
        String cur = currencySymbol(cfg.currency);
        for (Coin coin : cfg.coins) {
            RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_row);
            row.setTextViewText(R.id.symbol, coin.symbol);
            double[] p = WidgetConfig.cachedPrice(context, cfg.currency, coin.id);
            row.setTextViewText(R.id.price, p == null ? "--" : cur + " " + formatPrice(p[0]));
            if (cfg.showChange && p != null) {
                double ch = p[1];
                row.setViewVisibility(R.id.change, View.VISIBLE);
                row.setTextViewText(R.id.change, String.format(Locale.US, "%s%.1f%%", ch >= 0 ? "+" : "", ch));
                row.setTextColor(R.id.change, ch >= 0 ? 0xFF3DDC84 : 0xFFFF5252);
                row.setTextViewTextSize(R.id.change, TypedValue.COMPLEX_UNIT_SP, textSp * 0.75f);
            } else {
                row.setViewVisibility(R.id.change, View.GONE);
            }
            row.setTextViewTextSize(R.id.symbol, TypedValue.COMPLEX_UNIT_SP, textSp);
            row.setTextViewTextSize(R.id.price, TypedValue.COMPLEX_UNIT_SP, textSp);
            views.addView(R.id.rows, row);
        }

        views.setViewVisibility(R.id.footer, cfg.showFooter ? View.VISIBLE : View.GONE);
        long last = WidgetConfig.lastUpdate(context);
        views.setTextViewText(R.id.updated, last == 0 ? "toque para atualizar"
                : "atualizado " + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(last)));

        // Toque nos preços: atualiza agora.
        views.setOnClickPendingIntent(R.id.rows, refreshIntent(context, 1));
        views.setOnClickPendingIntent(R.id.updated, refreshIntent(context, 1));

        // Engrenagem: abre as opções (moedas, tamanho, transparência).
        Intent cfgIntent = new Intent(context, ConfigActivity.class)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        views.setOnClickPendingIntent(R.id.settings, PendingIntent.getActivity(context, 1000 + widgetId,
                cfgIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        manager.updateAppWidget(widgetId, views);
    }

    /** Escolhe um tamanho de fonte que caiba na altura/largura atual do widget. */
    private static float autoTextSize(Bundle opts, WidgetConfig cfg) {
        int w = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180);
        int h = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110);
        if (w <= 0) w = 180;
        if (h <= 0) h = 110;
        int rows = Math.max(1, cfg.coins.size());
        float avail = h - 8 - (cfg.showFooter ? 22 : 0);
        float byHeight = (avail / rows - 4) * 0.62f;
        int chars = 4 + 13 + (cfg.showChange ? 6 : 0);
        float byWidth = (w - 16) / (chars * 0.58f);
        float size = Math.min(byHeight, byWidth);
        return Math.max(8f, Math.min(44f, size));
    }

    static String currencySymbol(String c) {
        switch (c) {
            case "usd": return "$";
            case "brl": return "R$";
            case "eur": return "€";
            case "gbp": return "£";
            case "jpy": return "¥";
            case "btc": return "₿";
            default: return c.toUpperCase();
        }
    }

    static String formatPrice(double v) {
        DecimalFormatSymbols sym = DecimalFormatSymbols.getInstance(Locale.getDefault());
        String pattern;
        if (v >= 1000) pattern = "#,##0.00";
        else if (v >= 1) pattern = "#,##0.00##";
        else if (v >= 0.01) pattern = "0.0000##";
        else pattern = "0.00000000";
        DecimalFormat df = new DecimalFormat(pattern, sym);
        if (v >= 1000) df.setMaximumFractionDigits(2);
        else if (v >= 1) df.setMaximumFractionDigits(3);
        return df.format(v);
    }
}
