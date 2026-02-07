// @name Disable language switch on key 0
//Отключение смены раскладки на key0

let kc_key0 = json["key-group-processors"]
    .find(kgp => kgp["key-codes"]
        .find(kc => kc === "KEYCODE_0")
    );

kc_key0["on-short-press"] = kc_key0["on-short-press"].filter((a) => a["action-method-name"] !== "ActionChangeKeyboardLayout");
kc_key0["on-undo-short-press"] = null;