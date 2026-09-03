package com.ai10.k12kb;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.ai10.k12kb.prediction.LanguagePacks;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Языковые пакеты: что установлено, какой версии и где взять остальные.
 *
 * Клавиатуре не нужен доступ в сеть: скачивание открывается ссылкой в браузере,
 * а об обновлениях она узнаёт, сравнивая установленную версию пакета с
 * каталогом в своих assets. Каталог обновляется вместе с самой клавиатурой,
 * поэтому «обновление доступно» означает буквально: в сборке клавиатуры знают
 * о более новом пакете, чем стоит на устройстве.
 */
public class ActivityLanguagePacks extends Activity {

    private static final String TAG = "K12Kb-Packs";
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_language_packs);
        list = (LinearLayout) findViewById(R.id.packs_list);
        findViewById(R.id.btn_open_layout_settings).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(ActivityLanguagePacks.this, ActivitySettings.class));
            }
        });
        findViewById(R.id.btn_packs_release_page).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openLink(catalogString("release-page",
                        "https://github.com/tim-ecoder/K12KB/releases"));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Пакет могли поставить или удалить, пока экран был свёрнут.
        LanguagePacks.invalidate();
        Fill();
    }

    /** Показать подсказку про включение раскладки; держится до ухода со страницы. */
    private void ShowEnableLayoutHint() {
        View hint = findViewById(R.id.pill_enable_layout_hint);
        if (hint != null)
            hint.setVisibility(View.VISIBLE);
    }

    private JSONObject catalog() {
        try {
            InputStream is = getAssets().open("language_packs.json");
            BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"), 8192);
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            return new JSONObject(sb.toString());
        } catch (Throwable ex) {
            Log.w(TAG, "Каталог пакетов не прочитан: " + ex);
            return null;
        }
    }

    private String catalogString(String key, String fallback) {
        JSONObject c = catalog();
        return c != null ? c.optString(key, fallback) : fallback;
    }

    private void Fill() {
        list.removeAllViews();
        JSONObject c = catalog();
        JSONArray packs = c != null ? c.optJSONArray("packs") : null;
        if (packs == null) {
            AddRow(getString(R.string.packs_catalog_missing), null, null, null, false);
            return;
        }
        for (int i = 0; i < packs.length(); i++) {
            JSONObject p = packs.optJSONObject(i);
            if (p == null) continue;
            JSONArray items = p.optJSONArray("items");
            if (items == null) continue;
            AddGroupHeader(p.optString("title"));
            for (int j = 0; j < items.length(); j++) {
                JSONObject it = items.optJSONObject(j);
                if (it != null) AddItem(it);
            }
        }
    }

    private void AddGroupHeader(String title) {
        View header = getLayoutInflater().inflate(R.layout.item_language_pack_header, list, false);
        ((TextView) header.findViewById(R.id.pack_group_title)).setText(title);
        list.addView(header);
    }

    /**
     * Строка одного устанавливаемого пакета. Языковой пакет и готовые словари —
     * отдельные APK: словарь на 300k весит вдвое больше самого пакета, и
     * заставлять качать его тех, кому хватит 150k (или сборки индекса на
     * устройстве), было бы неуважением к трафику.
     */
    private void AddItem(JSONObject p) {
        final String url = p.optString("url");
        String pkg = p.optString("package");
        PackageInfo installed = InstalledInfo(pkg);

        String title = p.optString("title") + "  \u00b7  " + p.optString("size-mb") + " MB";
        StringBuilder sub = new StringBuilder(p.optString("contents"));
        sub.append("\n");
        boolean updatable = false;
        if (installed == null) {
            sub.append(getString(R.string.packs_not_installed))
               .append(" \u00b7 ").append(getString(R.string.packs_tap_to_download));
        } else {
            sub.append(getString(R.string.packs_installed))
               .append(" ").append(installed.versionName);
            if (installed.versionCode < p.optInt("version-code", 0)) {
                updatable = true;
                sub.append(" \u00b7 ").append(getString(R.string.packs_update_available))
                   .append(" ").append(p.optString("version-name"));
            } else {
                sub.append(" \u00b7 ").append(getString(R.string.packs_up_to_date));
            }
        }
        AddRow(title, sub.toString(), url, installed != null ? pkg : null, updatable);
    }

    private PackageInfo InstalledInfo(String pkg) {
        if (pkg == null || pkg.isEmpty())
            return null;
        try {
            return getPackageManager().getPackageInfo(pkg, 0);
        } catch (PackageManager.NameNotFoundException ex) {
            return null;
        }
    }

    private void AddRow(String title, String subtitle, final String url,
                        final String installedPackage, boolean updatable) {
        View row = getLayoutInflater().inflate(R.layout.item_language_pack, list, false);
        ((TextView) row.findViewById(R.id.pack_title)).setText(title);
        TextView sub = (TextView) row.findViewById(R.id.pack_subtitle);
        if (subtitle == null) {
            sub.setVisibility(View.GONE);
        } else {
            sub.setText(subtitle);
        }
        // Установлен и актуален — жать некуда: строка приглушена и не нажимается,
        // чтобы не выглядела приглашением скачать то, что уже стоит. Удалить
        // такой пакет можно долгим нажатием.
        boolean actionable = url != null && !url.isEmpty()
                && (installedPackage == null || updatable);
        row.findViewById(R.id.pack_badge).setVisibility(updatable ? View.VISIBLE : View.GONE);

        // Кнопка удаления видна у всего установленного: строка актуального
        // пакета не нажимается, а другого способа снести пакет нет — своего
        // значка в лаунчере у него не бывает.
        View uninstall = row.findViewById(R.id.pack_uninstall);
        if (installedPackage != null) {
            uninstall.setVisibility(View.VISIBLE);
            uninstall.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Uninstall(installedPackage);
                }
            });
        } else {
            uninstall.setVisibility(View.GONE);
        }
        row.findViewById(R.id.pack_chevron).setVisibility(actionable ? View.VISIBLE : View.INVISIBLE);
        float dim = (actionable || installedPackage == null) ? 1f : 0.55f;
        row.findViewById(R.id.pack_title).setAlpha(dim);
        row.findViewById(R.id.pack_subtitle).setAlpha(dim);
        row.setEnabled(actionable);
        if (actionable) {
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (installedPackage != null)
                        AskInstalledAction(installedPackage, url);
                    else
                        openLink(url);
                }
            });
        } else {
            row.setClickable(false);
        }
        if (installedPackage != null) {
            row.setLongClickable(true);
            row.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) {
                    AskInstalledAction(installedPackage, url);
                    return true;
                }
            });
        }
        list.addView(row);
    }

    /** У установленного пакета выбор: обновить по ссылке или удалить. */
    private void AskInstalledAction(final String pkg, final String url) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.packs_title)
                .setItems(new CharSequence[]{
                        getString(R.string.packs_action_reinstall),
                        getString(R.string.packs_action_uninstall)}, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            openLink(url);
                        } else {
                            Uninstall(pkg);
                        }
                    }
                })
                .show();
    }

    /**
     * Удаление пакета. Своего значка в лаунчере у пакета нет, поэтому без этой
     * кнопки его пришлось бы искать в системном списке приложений.
     */
    private void Uninstall(String pkg) {
        try {
            Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg));
            startActivity(i);
        } catch (Throwable ex) {
            Log.w(TAG, "Удаление " + pkg + ": " + ex);
            Toast.makeText(this, pkg, Toast.LENGTH_LONG).show();
        }
    }

    private void openLink(String url) {
        // Скачанный пакет сам себя не включает: раскладки приходят выключенными,
        // и без этой подсказки после установки не понятно, куда идти дальше.
        ShowEnableLayoutHint();
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, url, Toast.LENGTH_LONG).show();
        }
    }
}
