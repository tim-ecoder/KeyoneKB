package com.ai10.k12kb;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Environment;
import androidx.core.app.ActivityCompat;
import androidx.core.os.BuildCompat;
import android.util.Log;
import android.view.KeyEvent;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static android.content.ContentValues.TAG;
import static com.ai10.k12kb.InputMethodServiceCoreKeyPress.TAG2;

public class FileJsonUtils {

    public static String PATH;
    public static String PATH_DEF;
    public static String APP_FILES_DIR;
    public static String DEFAULT_FOLDER = "default";
    public static String JsonFileExt = ".json";
    public static String JsFileExt = ".js";
    public static String JsPatchesAssetFolder = "js_patches";

    public static void Initialize(Context context) {

        if(PATH == null || PATH.isEmpty()) {
            APP_FILES_DIR = context.getString(R.string.app_files_dir);
            PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + APP_FILES_DIR + "/";
            PATH_DEF = PATH + DEFAULT_FOLDER + "/";
        }
    }



    //region FOLDERS AND FILES

    private static void CheckFoldersAndCreate() {
        CheckFoldersAndCreate(PATH);
    }

    private static void CheckFoldersAndCreate(String path) {
        if (!(new File(path)).exists()) {
            new File(path).mkdirs();
        }
    }

    public static void CheckFoldersAndCreateJsPatches(String path) {
        path = PATH +"/"+path;
        if (!(new File(path)).exists()) {
            new File(path).mkdirs();
        }
    }

    public static boolean FileExists(String fileName) {
        CheckFoldersAndCreate();
        String fullFileName = PATH + fileName;
        return (new File(fullFileName)).exists();
    }

    public static boolean JsonsExist(String res_name)
    {
        CheckFoldersAndCreate();
        if(FileJsonUtils.FileExists(res_name + JsonFileExt)) return true;
        return false;
    }

    public static String SaveJsonResToFile(String resName, Context context){

        return SaveAssetToFile(resName +JsonFileExt, PATH_DEF, ResNameNoFolder(resName)+JsonFileExt, context);
    }

    public static String SaveAssetToFile(String assetFile, String NEW_PATH, String saveFile, Context context){

        CheckFoldersAndCreate(NEW_PATH);
        String fileName = NEW_PATH + saveFile;

        AssetManager am = context.getAssets();
        try {
            InputStream is = am.open(assetFile);

            FileOutputStream fOut = new FileOutputStream(fileName,false);
            copyLarge(is, fOut);
            fOut.flush();
            fOut.close();
            is.close();

        } catch (Throwable e) {
            Log.e(TAG2, "Save file error: "+e.toString());
        }
        return NEW_PATH;
    }

    //endregion

    //region SERIALIZE

    private static JsonMapper PrepareJsonMapper() {
        JsonMapper mapper = JsonMapper.builder().disable(MapperFeature.AUTO_DETECT_CREATORS,
                MapperFeature.AUTO_DETECT_FIELDS,
                MapperFeature.AUTO_DETECT_GETTERS,
                MapperFeature.AUTO_DETECT_IS_GETTERS).build();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_DEFAULT);
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE);
        mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        return mapper;
    }

    public static void SerializeToFile(Object obj, String fileName) {
        JsonMapper mapper = PrepareJsonMapper();

        CheckFoldersAndCreate(PATH_DEF);
        String fullFileName = PATH_DEF + fileName;
        try {
            FileOutputStream fOut = new FileOutputStream(fullFileName,false);
            mapper.writeValue(fOut, obj);
            fOut.flush();
            fOut.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static <T> T DeserializeFromFile(String fileName, TypeReference<T> typeReference) {
        try {
            CheckFoldersAndCreate();
            String fullFileName = PATH + fileName;
            JsonMapper mapper= PrepareJsonMapper();
            InputStream fIn = new FileInputStream(fullFileName);
            T obj = mapper.readValue(fIn, typeReference);
            fIn.close();
            return obj;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static <T> T DeserializeFromFile(InputStream is, TypeReference<T> typeReference) throws IOException {
        JsonMapper mapper= PrepareJsonMapper();
        T obj = mapper.readValue(is, typeReference);
        return obj;
    }

    private static <T> T DeserializeFromString(String s, TypeReference<T> typeReference) throws IOException {
        JsonMapper mapper= PrepareJsonMapper();
        T obj = mapper.readValue(s, typeReference);
        return obj;
    }

    //endregion

    //region PATCH JSON

    public enum ResLoadVariant {
        DefaultFromAsset,
        CustomJson,
        JsPatched
    }

    public static Map<String, ResLoadVariant> CustomizationLoadVariants = new HashMap<>();

    public static Hashtable<String, List<String>> JsPatchesMap = new Hashtable<>();
    public static Hashtable<String, String> JsPatchDescriptions = new Hashtable<>();

    /**
     * Один js-патч. Живёт либо в assets (js_patches/), либо на диске в PATH.
     * Диск переопределяет assets при совпадении имени, поэтому патч, положенный
     * на sdcard, заменяет собой встроенный, а не добавляется к нему вторым.
     */
    private static final class JsPatchSource {
        final String name;
        final File file;   // null для патча из assets

        JsPatchSource(String name, File file) {
            this.name = name;
            this.file = file;
        }

        boolean isFromAssets() {
            return file == null;
        }
    }

    /**
     * Собирает патчи для ресурса: сначала встроенные из assets, затем дисковые
     * поверх них. Патчи из assets доступны всегда — им не нужны ни разрешение на
     * запись, ни предварительное сохранение на диск.
     */
    private static List<JsPatchSource> CollectJsPatches(String noFolderName, Context context) {
        LinkedHashMap<String, JsPatchSource> byName = new LinkedHashMap<>();

        try {
            String[] assetPatches = context.getAssets().list(JsPatchesAssetFolder);
            if (assetPatches != null) {
                for (String name : assetPatches) {
                    if (!name.endsWith(".js")) continue;
                    if (!name.startsWith(noFolderName)) continue;
                    byName.put(name, new JsPatchSource(name, null));
                }
            }
        } catch (Throwable ex) {
            Log.w(TAG2, "Can not list asset js patches: " + ex);
        }

        // Диск доступен только с разрешением на запись; отсутствие разрешения
        // больше не отключает патчи целиком — встроенные продолжают работать.
        try {
            if (PATH != null
                    && ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED) {
                File[] jsFiles = findFilenamesMatchingRegex(noFolderName + ".*\\.js", new File(PATH));
                if (jsFiles != null) {
                    for (File f : jsFiles) {
                        byName.put(f.getName(), new JsPatchSource(f.getName(), f));
                    }
                }
            }
        } catch (Throwable ex) {
            Log.w(TAG2, "Can not list disk js patches: " + ex);
        }

        return new ArrayList<>(byName.values());
    }

    private static String ReadJsPatch(JsPatchSource patch, Context context) throws IOException {
        InputStream is = patch.isFromAssets()
                ? context.getAssets().open(JsPatchesAssetFolder + "/" + patch.name)
                : new FileInputStream(patch.file);
        try {
            return slurp(is, 1024);
        } finally {
            is.close();
        }
    }

    /** Первая строка вида "// @name ..." — человекочитаемое название патча. */
    private static String ReadJsPatchDescription(JsPatchSource patch, Context context) {
        try {
            String body = ReadJsPatch(patch, context);
            int eol = body.indexOf('\n');
            String firstLine = (eol >= 0 ? body.substring(0, eol) : body).trim();
            if (firstLine.startsWith(JS_NAME_PREFIX)) {
                return firstLine.substring(JS_NAME_PREFIX.length()).trim();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static <T> T DeserializeFromJsonApplyPatches(String resName, TypeReference<T> typeReference, Context context) throws Exception {

        T object = null;
        Context psc = GetContext(context);
        K12KbSettings k12KbSettings = K12KbSettings.Get(psc.getSharedPreferences(K12KbSettings.APP_PREFERENCES, Context.MODE_PRIVATE));
        List<String> JsPatches = new ArrayList<>();
        String noFolderName = ResNameNoFolder(resName);
        JsPatchesMap.put(noFolderName, JsPatches);

        try {
            // Патчи из assets работают без разрешений и без копирования на диск;
            // дисковые дополняют их и переопределяют по имени.
            List<JsPatchSource> patches = CollectJsPatches(noFolderName, context);
            List<JsPatchSource> active = new ArrayList<>();
            for (JsPatchSource patch : patches) {
                JsPatches.add(patch.name);
                String desc = ReadJsPatchDescription(patch, context);
                if (desc != null) {
                    JsPatchDescriptions.put(patch.name, desc);
                }
                if (k12KbSettings.GetBooleanValue(patch.name))
                    active.add(patch);
            }

            boolean canUseDisk = PATH != null
                    && ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED;

            // Если в папке уже сформированный json берем его
            if (canUseDisk && FileJsonUtils.JsonsExist(noFolderName)) {
                CustomizationLoadVariants.put(noFolderName, ResLoadVariant.CustomJson);
                return FileJsonUtils.DeserializeFromFile(noFolderName + JsonFileExt, typeReference);
            }

            // Накидываем патчи на дефолтный json
            if (!active.isEmpty()) {

                InputStream is_base_json = getResStream(resName, context);
                String base_json = slurp(is_base_json, 1024);
                is_base_json.close();

                String[] jss = new String[active.size()];
                for (int i = 0; i < active.size(); i++) {
                    jss[i] = ReadJsPatch(active.get(i), context);
                }

                String patched = patchJson(base_json, jss);
                // Результат пишем на диск только если он доступен: патчи должны
                // работать и без разрешения на запись.
                if (canUseDisk) {
                    SavePatchResult(noFolderName, patched);
                }
                CustomizationLoadVariants.put(noFolderName, ResLoadVariant.JsPatched);
                return DeserializeFromString(patched, typeReference);
            }

            CustomizationLoadVariants.put(noFolderName, ResLoadVariant.DefaultFromAsset);
            InputStream is = getResStream(resName, context);
            object = FileJsonUtils.DeserializeFromFile(is, typeReference);
            is.close();

            return object;
        } catch (EvaluatorException ex) {
            Log.e(TAG2, String.format("EVALUATE JavaScript patch at JSON %s ERROR: %s LINE: %s COL: %s TEXT: %s", resName, ex.toString(), ex.lineNumber(), ex.columnNumber(), ex.lineSource()));
            throw new Exception(String.format("EVALUATE JavaScript patch at JSON %s ERROR: %s LINE: %s COL: %s TEXT: %s", resName, ex.toString(), ex.lineNumber(), ex.columnNumber(), ex.lineSource()));
        }
        catch(Throwable ex) {
            Log.e(TAG2, String.format("LOAD FROM JSON %s ERROR: %s", resName, ex.toString()));
            throw new Exception(String.format("LOAD FROM JSON %s ERROR: %s", resName, ex.toString()), ex);
        }
    }

    private static String ResNameNoFolder(String noFolderName) {
        int folderPos = noFolderName.lastIndexOf("/");
        if(folderPos >= 0)
            noFolderName = noFolderName.substring(folderPos + 1, noFolderName.length());
        return noFolderName;
    }

    private static InputStream getResStream(String resName, Context context) throws IOException {
        AssetManager am = context.getAssets();
        return am.open(resName+JsonFileExt);

    }

    /** Прогоняет json через цепочку js-патчей. Ничего не пишет на диск. */
    private static String patchJson(String base_json, String[] Jscripts) throws IOException {
        String updatingJsonText = base_json;


        // Every Rhino VM begins with the enter()
        // This Context is not Android's Context
        org.mozilla.javascript.Context rhino = org.mozilla.javascript.Context.enter();

        // Turn off optimization to make Rhino Android compatible
        rhino.setOptimizationLevel(-1);
        rhino.setLanguageVersion(org.mozilla.javascript.Context.VERSION_1_7);
        try {
            Scriptable scope = rhino.initStandardObjects();

            for (String Jscript: Jscripts ) {

                String jsCode = "function patch_json(json_text) { const json=JSON.parse(json_text); " + Jscript + " return JSON.stringify(json,null,'\\t');}";

                // Note the forth argument is 1, which means the JavaScript source has
                // been compressed to only one line using something like YUI
                rhino.evaluateString(scope, jsCode, "JavaScript", 1, null);

                // Get the functionName defined in JavaScriptCode
                Object obj = scope.get("patch_json", scope);

                if (obj instanceof Function) {
                    Function jsFunction = (Function) obj;

                    Object[] params = new Object[]{updatingJsonText};
                    // Call the function with params
                    Object jsResult = jsFunction.call(rhino, scope, scope, params);
                    // Parse the jsResult object to a String
                    updatingJsonText = org.mozilla.javascript.Context.toString(jsResult);
                }
            }
        } catch(Throwable e) {
            throw e;
        } finally {
            org.mozilla.javascript.Context.exit();
        }


        return updatingJsonText;
    }

    /**
     * Кладёт результат патчей рядом с остальными файлами на диске — чтобы его
     * можно было посмотреть и отредактировать вручную. Для работы патчей не
     * обязательно: при недоступном диске json остаётся только в памяти.
     */
    private static void SavePatchResult(String resName, String json) {
        try {
            FileOutputStream fOut = new FileOutputStream(PATH + resName + JsFileExt + JsonFileExt, false);
            InputStream stream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
            try {
                copyLarge(stream, fOut);
                fOut.flush();
            } finally {
                fOut.close();
                stream.close();
            }
        } catch (Throwable ex) {
            Log.w(TAG2, "Can not save patch result for " + resName + ": " + ex);
        }
    }

    //endregion

    //region OTHER UTIL

    public static Context GetContext(Context context) {

        /* Подготовка к запуску приложения до входа пользователя
        в таком режиме сбрасываются настройки (надо разобраться)

        if (BuildCompat.isAtLeastN()) {
            // All N devices have split storage areas, but we may need to
            // move the existing preferences to the new device protected
            // storage area, which is where the data lives from now on.
            final Context deviceContext = context.createDeviceProtectedStorageContext();
            if (!deviceContext.moveSharedPreferencesFrom(context, K12KbSettings.APP_PREFERENCES)) {
                Log.w(TAG, "Failed to migrate shared preferences.");
            }
            return deviceContext;
        }
         */
        return context;
    }

    public static void LogErrorToGui(String text) {
        K12KbIME.DEBUG_TEXT += "\r\n";
        K12KbIME.DEBUG_TEXT += text;
        K12KbIME.DEBUG_TEXT += "\r\n";
        if(K12KbIME.DEBUG_UPDATE != null)
            K12KbIME.DEBUG_UPDATE.DebugUpdated();
    }

    public static boolean SleepWithWakes(int sleep_lim) {
        int sleep = 10;
        for (int i = 0; i < sleep_lim / sleep; i++) {
            try {
                Thread.sleep(sleep);
            } catch (Throwable ignore) {
                return false;
            }
        }
        return true;
    }

    public static int GetKeyCodeIntFromKeyEventOrInt(String keyCode) throws NoSuchFieldException, IllegalAccessException {
        int value;
        if(keyCode.startsWith("KEYCODE_") || keyCode.startsWith("META_")) {
            Field f = KeyEvent.class.getField(keyCode);
            value = f.getInt(null);
        } else {
            value = Integer.valueOf(keyCode);
        }
        return value;
    }

    //endregion

    //region PRIV TOOLs

    private static long copyLarge(InputStream input, OutputStream output) throws IOException
    {
        byte[] buffer = new byte[4096];
        long count = 0L;
        int n = 0;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    private static String slurp(final InputStream is, final int bufferSize) throws IOException {
        final char[] buffer = new char[bufferSize];
        final StringBuilder out = new StringBuilder();
        try (Reader in = new InputStreamReader(is, "UTF-8")) {
            for (;;) {
                int rsz = in.read(buffer, 0, buffer.length);
                if (rsz < 0)
                    break;
                out.append(buffer, 0, rsz);
            }
        }
        return out.toString();
    }

    private static File[] findFilenamesMatchingRegex(String regex, File dir) {
        return dir.listFiles(new java.io.FileFilter() { public boolean accept(File file) { return file.getName().matches(regex); } });
    }

    private static final String JS_NAME_PREFIX = "// @name ";

    private static String readJsPatchName(File file) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            String firstLine = reader.readLine();
            if (firstLine != null && firstLine.startsWith(JS_NAME_PREFIX)) {
                return firstLine.substring(JS_NAME_PREFIX.length()).trim();
            }
        } catch (Exception ignored) {
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
        }
        return null;
    }


    //endregion

    //region FixedSizeSet

    //public FixedSizeSet<String> PackageHistory = new FixedSizeSet<>(4);

    public class FixedSizeSet<E> extends AbstractSet<E> {
        private final LinkedHashMap<E, E> contents;

        FixedSizeSet(final int maxCapacity) {
            contents = new LinkedHashMap<E, E>(maxCapacity * 4 /3, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<E, E> eldest) {
                    return size() == maxCapacity;
                }
            };
        }

        @Override
        public Iterator<E> iterator() {
            return contents.keySet().iterator();
        }

        @Override
        public int size() {
            return contents.size();
        }

        public boolean add(E e) {
            boolean hadNull = false;
            if (e == null) {
                hadNull = contents.containsKey(null);
            }
            E previous = contents.put(e, e);
            return e == null ? hadNull : previous != null;
        }

        @Override
        public boolean contains(Object o) {
            return contents.containsKey(o);
        }
    }

    //endregion
}
