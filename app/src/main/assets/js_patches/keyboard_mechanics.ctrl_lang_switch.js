// @name Right Ctrl switches language
//Добавляем смену раскладки на ctrl

let kc_ctrl = json["key-group-processors"]
    .find(kgp => kgp["key-codes"]
        .find(kc => kc === "KEYCODE_CTRL_RIGHT")
    );


kc_ctrl["on-short-press"].unshift({
    "action-method-name": "ActionChangeKeyboardLayout",
    "need-update-visual-state": true
});

kc_ctrl["on-undo-short-press"] = [
    {
        "action-method-name": "ActionChangeBackKeyboardLayout",
        "need-update-visual-state": true
    }
];




