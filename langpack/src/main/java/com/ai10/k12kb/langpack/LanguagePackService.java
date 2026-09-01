package com.ai10.k12kb.langpack;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * Пустая заглушка. Существует только затем, чтобы у пакета был компонент с
 * действием com.ai10.k12kb.LANGUAGE_PACK: по нему клавиатура находит пакет
 * через queryIntentServices. Связываться с ним никто не будет — данные
 * читаются напрямую из assets пакета.
 */
public class LanguagePackService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
