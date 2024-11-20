//Проверка как работает навешивание действия на одну из группы букв. Тогда как вся группа букв в базовом механикс определена все вместе.

json["key-group-processors"].unshift(    {
                     "key-codes": [
                       "KEYCODE_Q"
                     ],
                     "on-short-press": [
                       {
                         "meta-mode-method-names": [
                           "MetaIsCtrlPressed"
                         ],
                         "action-method-name": "ActionMoveCursorPrevWord",
                         "method-needs-key-event-parameter": true,
                         "stop-processing-at-success-result": true
                       }
                     ]
                   });