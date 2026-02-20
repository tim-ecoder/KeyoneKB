// @name Del double/triple: delete word/line
//Для случая когда надо добавить функции on-double-press и пр. для DEL

let kc_del = json["key-group-processors"]
    .find(kgp => kgp["key-codes"]
        .find(kc => kc === "KEYCODE_DEL")
    );
kc_del["on-double-press"] = [

        {
          "action-method-name": "ActionDeletePrevWord",

          "stop-processing-at-success-result": true
        }, {
          "meta-mode-method-names": [
            "MetaIsShiftPressed"
          ],
          "action-method-name": "ActionDeleteFwdWord",
          "stop-processing-at-success-result": true
        }
      ];
kc_del["on-long-press"] = [
    {
        "meta-mode-method-names": [
            "MetaIsShiftPressed"
        ],
        "action-method-name": "ActionDeleteUntilFwdCrLf",
        "stop-processing-at-success-result": true
    },
    {
        "action-method-name": "ActionDeleteUntilPrevCrLf",
        "stop-processing-at-success-result": true
    }
];
kc_del["on-triple-press"] = [
        {
            "meta-mode-method-names": [
                "MetaIsShiftPressed"
            ],
            "action-method-name": "ActionDeleteUntilFwdCrLf",
            "stop-processing-at-success-result": true
        },
        {
            "action-method-name": "ActionDeleteUntilPrevCrLf",
            "stop-processing-at-success-result": true
        }
    ];

