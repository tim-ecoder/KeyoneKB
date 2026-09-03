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

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
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
            // Разовый сбой PackageManager (например, после его перезапуска) не
            // должен запоминаться как «пакетов нет»: иначе все неанглийские
            // языки исчезали бы до перезапуска процесса.
            Log.w(TAG, "Поиск пакетов не удался, ответ не запоминаем: " + ex);
            return found;
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


    /**
     * Версия пакета, в котором лежит файл: 0 — файл свой или его нигде нет.
     *
     * Копии распакованных данных именуются с этой версией. Без неё обновлённый
     * пакет продолжал читаться из старой копии: имя совпадало, файл на месте,
     * и повода перечитывать не находилось.
     */
    public static int assetVersion(Context context, String assetPath) {
        List<String> pkgs = packages(context);
        for (int i = 0; i < pkgs.size(); i++) {
            AssetManager am = assetsOf(context, pkgs.get(i));
            if (am == null)
                continue;
            InputStream is = null;
            try {
                is = am.open(assetPath);
            } catch (Throwable ignored) {
                continue;
            } finally {
                Close(is);
            }
            try {
                return context.getPackageManager().getPackageInfo(pkgs.get(i), 0).versionCode;
            } catch (Throwable ex) {
                return 0;
            }
        }
        return 0;
    }

    /** Коды языков, объявленные конкретным пакетом, — как в его манифесте. */
    public static String declaredLanguagesOf(Context context, String pkg) {
        try {
            PackageManager pm = context.getPackageManager();
            List<ResolveInfo> services = pm.queryIntentServices(new Intent(PACK_ACTION),
                    PackageManager.GET_META_DATA);
            for (int i = 0; i < services.size(); i++) {
                ResolveInfo ri = services.get(i);
                if (ri.serviceInfo == null || !pkg.equals(ri.serviceInfo.packageName))
                    continue;
                if (ri.serviceInfo.metaData == null)
                    return null;
                return ri.serviceInfo.metaData.getString(META_LANGUAGES);
            }
        } catch (Throwable ex) {
            Log.w(TAG, "Языки пакета " + pkg + " не прочитаны: " + ex);
        }
        return null;
    }

    /** Закрыть, не мешая обработке основной ошибки. */
    public static void Close(Closeable c) {
        if (c == null)
            return;
        try {
            c.close();
        } catch (Throwable ignored) {
        }
    }

    /**
     * Копия файла из своих assets или из пакета — через временный файл.
     *
     * Служба ввода живёт до первого желания системы её выгрузить, и обрыв на
     * середине записи оставлял бы файл, который открывается, но внутри обрезан:
     * словарь считался бы загруженным и молча ничего не находил.
     */
    public static boolean copyAsset(Context context, String assetPath, File target) {
        InputStream is = null;
        FileOutputStream out = null;
        File tmp = new File(target.getAbsolutePath() + ".tmp");
        try {
            try {
                is = context.getAssets().open(assetPath);
            } catch (Throwable ownMiss) {
                is = open(context, assetPath);
            }
            if (is == null)
                return false;
            File dir = target.getParentFile();
            if (dir != null)
                dir.mkdirs();
            out = new FileOutputStream(tmp);
            byte[] buf = new byte[1 << 18];
            int n;
            while ((n = is.read(buf)) > 0) out.write(buf, 0, n);
            out.close();
            out = null;
            if (!tmp.renameTo(target)) {
                tmp.delete();
                return false;
            }
            return true;
        } catch (Throwable ex) {
            Log.w(TAG, "Не скопировали " + assetPath + ": " + ex);
            return false;
        } finally {
            Close(is);
            Close(out);
            if (tmp.exists())
                tmp.delete();
        }
    }

    /**
     * Убрать распакованные копии, оставшиеся от других размеров и версий.
     * Иначе перебор размеров словаря оставлял на диске сотни мегабайт.
     */
    public static void dropStaleCopies(File dir, String prefix, String keep) {
        File[] files = dir != null ? dir.listFiles() : null;
        if (files == null)
            return;
        for (int i = 0; i < files.length; i++) {
            String name = files[i].getName();
            if (name.startsWith(prefix) && !name.equals(keep))
                files[i].delete();
        }
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
