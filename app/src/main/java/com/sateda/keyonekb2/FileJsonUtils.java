package com.sateda.keyonekb2;

import android.content.Context;
import android.content.res.Resources;
import android.os.Environment;
import android.util.Log;
import android.view.KeyEvent;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.lang.reflect.Field;
import java.util.HashMap;

import static com.sateda.keyonekb2.InputMethodServiceCoreKeyPress.TAG2;

public class FileJsonUtils {

    public static String PATH;
    public static String PATH_DEF;
    public static void Initialize(String packageName, Context context) {
        PATH = Environment.getExternalStorageDirectory().getAbsolutePath()+ "/Android/data/" + packageName + "/files/";
        PATH_DEF = PATH +"default/";
        LoadMappingFile(context);
    }

    private static void CheckFoldersAndCreate() {
        CheckFoldersAndCreate(PATH);
    }

    private static void CheckFoldersAndCreate(String path) {
        if (!(new File(path)).exists()) {
            new File(path).mkdirs();
        }
    }

    public static boolean FileExists(String fileName) {
        CheckFoldersAndCreate();
        String fullFileName = PATH + fileName;
        return (new File(fullFileName)).exists();
    }

    public static JsonMapper PrepareMapper() {
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
        JsonMapper mapper = PrepareMapper();

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
            JsonMapper mapper= PrepareMapper();
            FileInputStream fIn = new FileInputStream(fullFileName);
            T obj = mapper.readValue(fIn, typeReference);
            fIn.close();
            return obj;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static <T> T DeserializeFromFile(InputStream is, TypeReference<T> typeReference) {
        try {
            JsonMapper mapper= PrepareMapper();
            T obj = mapper.readValue(is, typeReference);
            return obj;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static <T> T DeserializeFromJson(String resName, TypeReference<T> typeReference, Context context) {

        T object = null;
        Resources resources = context.getResources();
        try {
            if (FileJsonUtils.FileExists(resName + ".json")) {
                object = FileJsonUtils.DeserializeFromFile(resName + ".json", typeReference);
            } else if (resources.getIdentifier(resName, "raw", context.getPackageName()) != 0) {
                InputStream is = resources.openRawResource(resources.getIdentifier(resName, "raw", context.getPackageName()));
                object = FileJsonUtils.DeserializeFromFile(is, typeReference);
                is.close();
            }
            return object;
        } catch(Throwable ex) {
            Log.e(TAG2, "LOAD FROM JSON ERROR: "+ex.toString());
            return null;
        }
    }

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
    public static String SaveJsonResToFile(String resName, Context context){
        return SaveResToFile(resName, ".json", context);
    }

    private static String SaveResToFile(String resName, String fileExtensionName, Context context){
        Resources resources = context.getResources();

        String pathDef = PATH +"default/";
        CheckFoldersAndCreate(pathDef);
        String fileName = pathDef+ resName+fileExtensionName;
        int resId = resources.getIdentifier(resName, "raw", context.getPackageName());
        if(resId > 0) {

            InputStream is;
            is = resources.openRawResource(resId);

            try {
                FileOutputStream fOut = new FileOutputStream(fileName,false);
                copyLarge(is, fOut);
                fOut.flush();
                fOut.close();
                is.close();
            } catch (Throwable e) {
                Log.e(TAG2, "Save file error: "+e.toString());
            }
        } else {
            Log.e(TAG2, "Save file error: Can not find Resouкce: "+resName);
        }
        return pathDef;
    }

    public static HashMap<String, Double> ScanCodeKeyCodeMapping = new HashMap<String, Double>();

    private static void LoadMappingFile(Context context) {

        Gson gson;
        gson = new GsonBuilder()
                .enableComplexMapKeySerialization()
                .serializeNulls()
                //.setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE)
                //.setPrettyPrinting()
                //.setVersion(1.0)
                //.excludeFieldsWithoutExposeAnnotation()
                .create();

        Resources resources = context.getResources();

        try {
            // Open output stream
            InputStream is = resources.openRawResource(R.raw.scan_code_key_code);
            // write integers as separated ascii's

            //mapper.writeValue(stream, Instance.KeyboardLayoutList);
            java.io.Reader w = new InputStreamReader(is);
            //gson.toJson(Instance.ScanCodeKeyCodeMapping, w);
            ScanCodeKeyCodeMapping = gson.fromJson(w, ScanCodeKeyCodeMapping.getClass());
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //String baseFolder = getApplicationContext().getFileStreamPath(fileName).getAbsolutePath();
        //btSave.setText(baseFolder);

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
}
