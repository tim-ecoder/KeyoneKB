package com.sateda.keyonekb2;

import android.content.Context;
import android.content.res.Resources;
import android.os.Environment;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.util.HashMap;

public class FileJsonUtils {

    public static String PATH;

    public static void Initialize(String packageName, Context context) {
        PATH = Environment.getExternalStorageDirectory().getAbsolutePath()+ "/Android/data/" + packageName + "/files/";
        LoadMappingFile(context);
    }

    private static void CheckFoldersAndCreate() {
        boolean exists = (new File(PATH)).exists();
        if (!exists) {
            new File(PATH).mkdirs();
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

    public static void SerializeToFile(ObjectMapper mapper, Object obj, String fileName) {

        String path = PATH;

        try {
            CheckFoldersAndCreate();
            // Open output stream
            FileOutputStream fOut = new FileOutputStream(path + fileName,false);
            // write integers as separated ascii's

            mapper.writeValue(fOut, obj);
            //fOut.write(content.getBytes());
            // Close output stream
            fOut.flush();
            fOut.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static <T> T DeserializeFromFile(String fileName, TypeReference<T> typeReference) {
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

    public static <T> T DeserializeFromFile(InputStream is, TypeReference<T> typeReference) {
        try {
            JsonMapper mapper= PrepareMapper();
            T obj = mapper.readValue(is, typeReference);
            is.close();
            return obj;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
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
}
