package com.widgetcoins;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.io.File;
import java.io.FileOutputStream;

/** Logos das moedas: baixados da CoinGecko, recortados em círculo e guardados em cache. */
public final class CoinIcons {
    private static final int SIZE = 72; // px: pequeno para não estourar o limite do RemoteViews

    private CoinIcons() {}

    private static File file(Context c, String coinId) {
        File dir = new File(c.getFilesDir(), "icons");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, coinId.replaceAll("[^a-z0-9-]", "_") + ".png");
    }

    public static boolean has(Context c, String coinId) {
        return file(c, coinId).exists();
    }

    /** Baixa e salva o logo (chamar fora da thread principal). */
    public static void download(Context c, String coinId, String url) {
        if (url == null || url.isEmpty()) return;
        try {
            byte[] data = PriceApi.download(url);
            Bitmap src = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (src == null) return;
            Bitmap out = circle(src);
            try (FileOutputStream fos = new FileOutputStream(file(c, coinId))) {
                out.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
        } catch (Exception ignored) {
        }
    }

    /** Logo salvo ou, se ainda não houver, um círculo colorido com a inicial. */
    public static Bitmap get(Context c, Coin coin) {
        if (coin.isFx()) return fxIcon();
        File f = file(c, coin.id);
        if (f.exists()) {
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (b != null) return b;
        }
        return placeholder(coin);
    }

    private static Bitmap circle(Bitmap src) {
        Bitmap out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawOval(new RectF(0, 0, SIZE, SIZE), p);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(src, new Rect(0, 0, src.getWidth(), src.getHeight()), new Rect(0, 0, SIZE, SIZE), p);
        return out;
    }

    /** Círculo verde com losango amarelo e "R$", inspirado nas cores do Brasil. */
    private static Bitmap fxIcon() {
        Bitmap out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF009C3B);
        canvas.drawOval(new RectF(0, 0, SIZE, SIZE), p);
        android.graphics.Path d = new android.graphics.Path();
        float m = SIZE / 2f;
        d.moveTo(m, SIZE * 0.14f);
        d.lineTo(SIZE * 0.9f, m);
        d.lineTo(m, SIZE * 0.86f);
        d.lineTo(SIZE * 0.1f, m);
        d.close();
        p.setColor(0xFFFFDF00);
        canvas.drawPath(d, p);
        p.setColor(0xFF002776);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(SIZE * 0.32f);
        p.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("R$", m, m - (p.descent() + p.ascent()) / 2f, p);
        return out;
    }

    private static Bitmap placeholder(Coin coin) {
        Bitmap out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float[] hsv = {Math.abs(coin.id.hashCode()) % 360, 0.55f, 0.85f};
        p.setColor(Color.HSVToColor(hsv));
        canvas.drawOval(new RectF(0, 0, SIZE, SIZE), p);
        p.setColor(Color.WHITE);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(SIZE * 0.45f);
        p.setTextAlign(Paint.Align.CENTER);
        String letter = coin.symbol.isEmpty() ? "?" : coin.symbol.substring(0, 1);
        canvas.drawText(letter, SIZE / 2f, SIZE / 2f - (p.descent() + p.ascent()) / 2f, p);
        return out;
    }
}
