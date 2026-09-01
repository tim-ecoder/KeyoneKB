package com.ai10.k12kb.prediction;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Словари из отдельно установленных APK-пакетов.
 *
 * Словари предсказаний и перевода весят десятки мегабайт, и класть их все в
 * клавиатуру нельзя. Пакет — обычное приложение без кода, у которого внутри
 * только assets; клавиатура читает их через createPackageContext, то есть
 * локально, без сети и без доступа к общей памяти. Новых разрешений это не
 * требует: в манифесте объявлена только видимость пакетов (<queries>).
 *
 * Доверяем лишь пакетам, подписанным тем же ключом, что и сама клавиатура —
 * иначе подсунуть словарь смогло бы любое приложение.
 */
public final class LanguagePacks {

    private static final String TAG = "K12Kb-Packs";
    public static final String PACK_ACTION = "com.ai10.k12kb.LANGUAGE_PACK";
    private static final String META_LANGUAGES = "com.ai10.k12kb.languages";

    /** Язык, который всегда есть в самой клавиатуре. */
    public static final String BUILTIN_LANGUAGE = "en";

    private static List<String> cachedPackages;
    private static final List<String> declaredLanguages = new ArrayList<>();

    private LanguagePacks() {}

    /** Забыть найденные пакеты — например, после установки нового. */
    public static synchronized void invalidate() {
        cachedPackages = null;
        declaredLanguages.clear();
    }

    /** Пакеты словарей, установленные на устройстве и подписанные нашим ключом. */
    public static synchronized List<String> packages(Context context) {
        if (cachedPackages != null)
            return cachedPackages;
        List<String> found = new ArrayList<>();
        declaredLanguages.clear();
        try {
            PackageManager pm = context.getPackageManager();
            List<ResolveInfo> services = pm.queryIntentServices(new Intent(PACK_ACTION),
                    PackageManager.GET_META_DATA);
            for (int i = 0; i < services.size(); i++) {
                ResolveInfo ri = services.get(i);
                if (ri.serviceInfo == null || ri.serviceInfo.packageName == null)
                    continue;
                String pkg = ri.serviceInfo.packageName;
                if (!SignedLikeUs(context, pkg)) {
                    Log.w(TAG, "Пакет " + pkg + " подписан другим ключом, пропускаем");
                    continue;
                }
                found.add(pkg);
                String declared = null;
                if (ri.serviceInfo.metaData != null)
                    declared = ri.serviceInfo.metaData.getString(META_LANGUAGES);
                if (declared != null) {
                    String[] codes = declared.split(",");
                    for (int c = 0; c < codes.length; c++) {
                        String code = codes[c].trim();
                        if (!code.isEmpty() && !declaredLanguages.contains(code))
                            declaredLanguages.add(code);
                    }
                }
                Log.i(TAG, "Найден пакет словарей: " + pkg + " языки: " + declared);
            }
        } catch (Throwable ex) {
            Log.w(TAG, "Поиск пакетов не удался: " + ex);
        }
        cachedPackages = found;
        return found;
    }

    private static boolean SignedLikeUs(Context context, String pkg) {
        try {
            PackageManager pm = context.getPackageManager();
            Signature[] ours = SignaturesOf(pm, context.getPackageName());
            Signature[] theirs = SignaturesOf(pm, pkg);
            if (ours == null || theirs == null)
                return false;
            for (Signature our : ours) {
                for (Signature their : theirs) {
                    if (our.equals(their))
                        return true;
                }
            }
        } catch (Throwable ex) {
            Log.w(TAG, "Проверка подписи " + pkg + ": " + ex);
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    private static Signature[] SignaturesOf(PackageManager pm, String pkg) throws Exception {
        PackageInfo info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES);
        return info.signatures;
    }

    private static AssetManager assetsOf(Context context, String pkg) {
        try {
            return context.createPackageContext(pkg, Context.CONTEXT_IGNORE_SECURITY).getAssets();
        } catch (Throwable ex) {
            Log.w(TAG, "Нет доступа к assets " + pkg + ": " + ex);
            return null;
        }
    }

    /** Открыть файл в конкретном пакете, null — там такого файла нет. */
    public static InputStream openIn(Context context, String pkg, String assetPath) {
        AssetManager am = assetsOf(context, pkg);
        if (am == null)
            return null;
        try {
            return am.open(assetPath);
        } catch (Throwable ex) {
            return null;
        }
    }

    /** Открыть файл из пакета, null — ни в одном пакете такого нет. */
    public static InputStream open(Context context, String assetPath) {
        List<String> pkgs = packages(context);
        for (int i = 0; i < pkgs.size(); i++) {
            AssetManager am = assetsOf(context, pkgs.get(i));
            if (am == null)
                continue;
            try {
                InputStream is = am.open(assetPath);
                Log.i(TAG, assetPath + " взят из пакета " + pkgs.get(i));
                return is;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /**
     * То же, но дескриптором: .cdb клавиатура маппит прямо из APK, не копируя.
     * Работает, пока файл лежит в пакете несжатым (aaptOptions noCompress).
     */
    public static AssetFileDescriptor openFd(Context context, String assetPath) {
        List<String> pkgs = packages(context);
        for (int i = 0; i < pkgs.size(); i++) {
            AssetManager am = assetsOf(context, pkgs.get(i));
            if (am == null)
                continue;
            try {
                AssetFileDescriptor afd = am.openFd(assetPath);
                Log.i(TAG, assetPath + " (fd) взят из пакета " + pkgs.get(i));
                return afd;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /**
     * Языки, для которых на устройстве есть словарь предсказаний: встроенный
     * английский плюс объявленные установленными пакетами. Пакет может
     * объявить язык без словаря (украинский пользуется русским), поэтому
     * каждый код проверяется по наличию файла.
     */
    public static List<String> availableLanguages(Context context) {
        List<String> result = new ArrayList<>();
        if (exists(context, "dictionaries/" + BUILTIN_LANGUAGE + "_base.txt"))
            result.add(BUILTIN_LANGUAGE);
        packages(context); // заполняет declaredLanguages
        List<String> declared;
        synchronized (LanguagePacks.class) {
            declared = new ArrayList<>(declaredLanguages);
        }
        for (int i = 0; i < declared.size(); i++) {
            String lang = declared.get(i);
            if (result.contains(lang))
                continue;
            if (exists(context, "dictionaries/" + lang + "_base.txt"))
                result.add(lang);
        }
        return result;
    }

    /** Есть ли такой файл — в самой клавиатуре или в каком-нибудь пакете. */
    public static boolean exists(Context context, String assetPath) {
        try {
            InputStream is = context.getAssets().open(assetPath);
            is.close();
            return true;
        } catch (Throwable ignored) {
        }
        InputStream is = open(context, assetPath);
        if (is == null)
            return false;
        try {
            is.close();
        } catch (Throwable ignored) {
        }
        return true;
    }
}
