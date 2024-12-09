package com.sateda.keyonekb2;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.os.BuildCompat;
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
import static com.sateda.keyonekb2.InputMethodServiceCoreKeyPress.TAG2;

public class FileJsonUtils {

    public static String PATH;
    public static String PATH_DEF;
    public static String APP_FILES_DIR = "KeyoneKb2";
    public static String DEFAULT_FOLDER = "default";
    public static String JsonFileExt = ".json";
    public static String JsFileExt = ".js";
    public static String JsPatchesAssetFolder = "js_patches";

    public static void Initialize() {

        if(PATH == null || PATH.isEmpty()) {
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

        return SaveAssetToFile(resName +JsonFileExt, PATH_DEF, ResNameNoFolder(resName), context);
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

    public static <T> T DeserializeFromJsonApplyPatches(String resName, TypeReference<T> typeReference, Context context) throws Exception {

        T object = null;
        Context psc = GetContext(context);
        KeyoneKb2Settings keyoneKb2Settings = KeyoneKb2Settings.Get(psc.getSharedPreferences(KeyoneKb2Settings.APP_PREFERENCES, Context.MODE_PRIVATE));
        List<String> JsPatches = new ArrayList<>();
        JsPatchesMap.put(resName, JsPatches);
        String noFolderName = ResNameNoFolder(resName);

        try {
            if(ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {

                // В первую очередь грузим js-патчи, чтобы они потом в морде показались

                File[] jsFiles = findFilenamesMatchingRegex(noFolderName + ".*\\.js", new File(PATH));
                List<File> jsFilesActive = new ArrayList<>();
                if(jsFiles != null) {
                    for (File jsPatch : jsFiles) {
                        JsPatches.add(jsPatch.getName());
                        if (keyoneKb2Settings.GetBooleanValue(jsPatch.getName()))
                            jsFilesActive.add(jsPatch);
                    }
                }

                // Если в папке уже сформированный json берем его
                if (FileJsonUtils.JsonsExist(noFolderName)) {
                    CustomizationLoadVariants.put(noFolderName, ResLoadVariant.CustomJson);
                    return FileJsonUtils.DeserializeFromFile(noFolderName + JsonFileExt, typeReference);
                }

                // Накидываем патчи на дефолтный json
                if (jsFilesActive.size() > 0) {

                    InputStream is_base_json = getResStream(resName, context);
                    String base_json = slurp(is_base_json, 1024);
                    is_base_json.close();

                    String[] jss = new String[jsFilesActive.size()];
                    int i = 0;
                    for (File f1 : jsFilesActive) {

                        FileInputStream fIn = new FileInputStream(f1);
                        jss[i] = slurp(fIn, 1024);
                        fIn.close();
                        i++;
                    }

                    String output = patchJsonAndSaveResult(noFolderName, base_json, jss);
                    CustomizationLoadVariants.put(noFolderName, ResLoadVariant.JsPatched);
                    return DeserializeFromString(output, typeReference);
                }

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

    private static String patchJsonAndSaveResult(String resName, String base_json, String[] Jscripts) throws IOException {
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


        FileOutputStream fOut = new FileOutputStream(PATH+ resName +JsFileExt+JsonFileExt,false);
        InputStream stream = new ByteArrayInputStream(updatingJsonText.getBytes(StandardCharsets.UTF_8));
        copyLarge(stream, fOut);
        fOut.flush();
        fOut.close();
        stream.close();
        return updatingJsonText;
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
            if (!deviceContext.moveSharedPreferencesFrom(context, KeyoneKb2Settings.APP_PREFERENCES)) {
                Log.w(TAG, "Failed to migrate shared preferences.");
            }
            return deviceContext;
        }
         */
        return context;
    }

    public static void LogErrorToGui(String text) {
        KeyoneIME.DEBUG_TEXT += "\r\n";
        KeyoneIME.DEBUG_TEXT += text;
        KeyoneIME.DEBUG_TEXT += "\r\n";
        if(KeyoneIME.DEBUG_UPDATE != null)
            KeyoneIME.DEBUG_UPDATE.DebugUpdated();
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
        return dir.listFiles(file -> file.getName().matches(regex));
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
