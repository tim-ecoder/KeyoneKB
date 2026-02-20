// @name 9. Disable Shift+Enter action
// Для случая когда надо удалить одно из действий

let enter_osp = json["key-group-processors"]
    .find(kgp => kgp["key-codes"]
        .find(kc => kc === "KEYCODE_ENTER"))
    ["on-short-press"];

enter_osp.splice(
    enter_osp.findIndex(
        act => act["meta-mode-method-names"]
            && act["meta-mode-method-names"]
                .find (mmmn => mmmn === "MetaIsShiftPressed")),
    1);
