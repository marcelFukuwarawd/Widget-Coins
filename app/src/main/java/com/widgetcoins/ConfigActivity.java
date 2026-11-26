package com.widgetcoins;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * Tela de opções do widget: adicionar/remover/reordenar moedas, tamanho do texto,
 * transparência e moeda de cotação. Abre pela engrenagem do widget ou pelo
 * "toque e segure" → Configurações/Reconfigurar do launcher.
 */
public class ConfigActivity extends Activity {
    private static final String[] CURRENCIES = {"usd", "brl", "eur", "gbp", "jpy", "btc"};
    private static final Coin[] POPULAR = {
            new Coin("bitcoin", "BTC", "Bitcoin"),
            new Coin("ethereum", "ETH", "Ethereum"),
            new Coin("solana", "SOL", "Solana"),
            new Coin("binancecoin", "BNB", "BNB"),
            new Coin("ripple", "XRP", "XRP"),
            new Coin("cardano", "ADA", "Cardano"),
            new Coin("dogecoin", "DOGE", "Dogecoin"),
            new Coin("tron", "TRX", "TRON"),
            new Coin("polkadot", "DOT", "Polkadot"),
            new Coin("chainlink", "LINK", "Chainlink"),
            new Coin("litecoin", "LTC", "Litecoin"),
            new Coin("avalanche-2", "AVAX", "Avalanche"),
            new Coin("the-open-network", "TON", "Toncoin"),
            new Coin("shiba-inu", "SHIB", "Shiba Inu"),
            new Coin("tether", "USDT", "Tether"),
    };

    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private WidgetConfig cfg;
    private LinearLayout coinList;
    private LinearLayout results;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            widgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        cfg = WidgetConfig.load(this, isWidget() ? widgetId : WidgetConfig.DEFAULT_ID);
        setContentView(buildUi());
    }

    private boolean isWidget() {
        return widgetId != AppWidgetManager.INVALID_APPWIDGET_ID;
    }

    // ------------------------------------------------------------------ UI

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(12));

        TextView title = text(isWidget() ? "Opções do widget" : "Widget Coins", 20, true);
        root.addView(title);
        if (!isWidget()) {
            root.addView(hint("Adicione o widget pela tela inicial: toque e segure um espaço vazio → Widgets → "
                    + "Widget Coins. As opções abaixo valem para todos os widgets."));
        }

        // --- Moedas
        root.addView(section("Moedas no widget"));
        coinList = new LinearLayout(this);
        coinList.setOrientation(LinearLayout.VERTICAL);
        root.addView(coinList);
        renderCoinList();

        // --- Adicionar
        root.addView(section("Adicionar moeda"));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        for (Coin c : POPULAR) {
            Button b = smallButton(c.symbol);
            b.setOnClickListener(v -> addCoin(c));
            chips.addView(b);
        }
        hs.addView(chips);
        root.addView(hs);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        EditText query = new EditText(this);
        query.setHint("Buscar (ex.: pepe, arbitrum)");
        query.setSingleLine(true);
        query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchRow.addView(query, new LinearLayout.LayoutParams(0, -2, 1));
        Button searchBtn = smallButton("Buscar");
        searchRow.addView(searchBtn);
        root.addView(searchRow);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        root.addView(results);
        Runnable doSearch = () -> search(query.getText().toString().trim());
        searchBtn.setOnClickListener(v -> doSearch.run());
        query.setOnEditorActionListener((v, id, ev) -> { doSearch.run(); return true; });

        // --- Cotação
        root.addView(section("Cotação em"));
        Spinner spinner = new Spinner(this);
        String[] labels = new String[CURRENCIES.length];
        int sel = 0;
        for (int i = 0; i < CURRENCIES.length; i++) {
            labels[i] = CURRENCIES[i].toUpperCase();
            if (CURRENCIES[i].equals(cfg.currency)) sel = i;
        }
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        spinner.setSelection(sel);
        root.addView(spinner);

        // --- Tamanho do texto
        root.addView(section("Tamanho do texto"));
        CheckBox auto = new CheckBox(this);
        auto.setText("Automático (acompanha o redimensionamento)");
        auto.setChecked(cfg.textSize == WidgetConfig.TEXT_AUTO);
        root.addView(auto);
        TextView sizeLabel = text("", 14, false);
        root.addView(sizeLabel);
        SeekBar size = new SeekBar(this);
        size.setMax(40 - 8);
        size.setProgress((cfg.textSize == WidgetConfig.TEXT_AUTO ? 16 : cfg.textSize) - 8);
        root.addView(size);
        Runnable updSize = () -> {
            size.setEnabled(!auto.isChecked());
            sizeLabel.setText(auto.isChecked() ? "Ajuste automático" : (size.getProgress() + 8) + " sp");
        };
        updSize.run();
        auto.setOnCheckedChangeListener((b, c) -> updSize.run());
        size.setOnSeekBarChangeListener(new SimpleSeek(updSize));

        // --- Transparência
        root.addView(section("Fundo"));
        TextView bgLabel = text("", 14, false);
        root.addView(bgLabel);
        SeekBar bg = new SeekBar(this);
        bg.setMax(100);
        bg.setProgress(cfg.bgOpacity);
        root.addView(bg);
        Runnable updBg = () -> bgLabel.setText(bg.getProgress() == 0
                ? "Totalmente transparente" : "Opacidade " + bg.getProgress() + "%");
        updBg.run();
        bg.setOnSeekBarChangeListener(new SimpleSeek(updBg));

        CheckBox change = new CheckBox(this);
        change.setText("Mostrar variação 24h");
        change.setChecked(cfg.showChange);
        root.addView(change);
        CheckBox footer = new CheckBox(this);
        footer.setText("Mostrar horário e engrenagem");
        footer.setChecked(cfg.showFooter);
        root.addView(footer);

        root.addView(hint("Dica: toque e segure o widget na tela inicial para redimensioná-lo arrastando as "
                + "bordas, ou escolha \"Configurações\" para voltar a esta tela. Toque nos preços para atualizar."
                + (footer.isChecked() ? "" : " Sem a engrenagem, use o toque e segure para abrir as opções.")));

        // --- Botões
        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.END);
        buttons.setPadding(0, dp(8), 0, 0);
        Button cancel = new Button(this, null, android.R.attr.buttonBarButtonStyle);
        cancel.setText("Cancelar");
        cancel.setOnClickListener(v -> finish());
        Button save = new Button(this, null, android.R.attr.buttonBarButtonStyle);
        save.setText("Salvar");
        save.setOnClickListener(v -> {
            if (cfg.coins.isEmpty()) {
                Toast.makeText(this, "Adicione pelo menos uma moeda", Toast.LENGTH_SHORT).show();
                return;
            }
            cfg.currency = CURRENCIES[spinner.getSelectedItemPosition()];
            cfg.textSize = auto.isChecked() ? WidgetConfig.TEXT_AUTO : size.getProgress() + 8;
            cfg.bgOpacity = bg.getProgress();
            cfg.showChange = change.isChecked();
            cfg.showFooter = footer.isChecked();
            saveAndFinish();
        });
        buttons.addView(cancel);
        buttons.addView(save);
        root.addView(buttons);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        return scroll;
    }

    private void renderCoinList() {
        coinList.removeAllViews();
        List<Coin> coins = cfg.coins;
        for (int i = 0; i < coins.size(); i++) {
            final int idx = i;
            Coin c = coins.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = text(c.symbol + "  ·  " + c.name, 15, false);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            Button up = smallButton("▲");
            up.setEnabled(i > 0);
            up.setOnClickListener(v -> { coins.add(idx - 1, coins.remove(idx)); renderCoinList(); });
            Button down = smallButton("▼");
            down.setEnabled(i < coins.size() - 1);
            down.setOnClickListener(v -> { coins.add(idx + 1, coins.remove(idx)); renderCoinList(); });
            Button del = smallButton("✕");
            del.setOnClickListener(v -> { coins.remove(idx); renderCoinList(); });
            row.addView(up);
            row.addView(down);
            row.addView(del);
            coinList.addView(row);
        }
        if (coins.isEmpty()) coinList.addView(hint("Nenhuma moeda. Adicione abaixo."));
    }

    private void addCoin(Coin c) {
        for (Coin existing : cfg.coins) {
            if (existing.id.equals(c.id)) {
                Toast.makeText(this, c.symbol + " já está no widget", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        cfg.coins.add(c);
        renderCoinList();
        Toast.makeText(this, c.symbol + " adicionada", Toast.LENGTH_SHORT).show();
    }

    private void search(String q) {
        if (q.isEmpty()) return;
        results.removeAllViews();
        results.addView(hint("Buscando…"));
        new Thread(() -> {
            try {
                List<Coin> found = PriceApi.search(q);
                ui.post(() -> {
                    results.removeAllViews();
                    if (found.isEmpty()) results.addView(hint("Nada encontrado."));
                    for (Coin c : found) {
                        Button b = new Button(this, null, android.R.attr.borderlessButtonStyle);
                        b.setAllCaps(false);
                        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                        b.setText("+  " + c.symbol + "  ·  " + c.name);
                        b.setOnClickListener(v -> addCoin(c));
                        results.addView(b);
                    }
                });
            } catch (Exception e) {
                ui.post(() -> {
                    results.removeAllViews();
                    results.addView(hint("Falha na busca: verifique a internet."));
                });
            }
        }).start();
    }

    private void saveAndFinish() {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        if (isWidget()) {
            cfg.save(this, widgetId);
            Intent result = new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
            setResult(RESULT_OK, result);
        } else {
            // Aberto pelo ícone do app: vira o padrão e é aplicado a todos os widgets.
            cfg.save(this, WidgetConfig.DEFAULT_ID);
            for (int id : manager.getAppWidgetIds(new ComponentName(this, CoinWidgetProvider.class))) {
                cfg.save(this, id);
            }
        }
        for (int id : manager.getAppWidgetIds(new ComponentName(this, CoinWidgetProvider.class))) {
            CoinWidgetProvider.render(this, manager, id);
        }
        CoinWidgetProvider.refreshAll(this, null);
        Toast.makeText(this, "Salvo", Toast.LENGTH_SHORT).show();
        finish();
    }

    // ------------------------------------------------------------------ helpers

    private TextView text(String s, float sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView section(String s) {
        TextView t = text(s.toUpperCase(), 12, true);
        t.setTextColor(0xFFF7931A);
        t.setPadding(0, dp(16), 0, dp(4));
        return t;
    }

    private TextView hint(String s) {
        TextView t = text(s, 13, false);
        t.setAlpha(0.7f);
        t.setPadding(0, dp(6), 0, dp(6));
        return t;
    }

    private Button smallButton(String label) {
        Button b = new Button(this, null, android.R.attr.borderlessButtonStyle);
        b.setText(label);
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setMinimumWidth(dp(44));
        b.setPadding(dp(10), 0, dp(10), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), Color.argb(90, 255, 255, 255));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(36));
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        b.setLayoutParams(lp);
        return b;
    }

    private static final class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        private final Runnable r;
        SimpleSeek(Runnable r) { this.r = r; }
        @Override public void onProgressChanged(SeekBar s, int p, boolean u) { r.run(); }
        @Override public void onStartTrackingTouch(SeekBar s) {}
        @Override public void onStopTrackingTouch(SeekBar s) {}
    }
}
