// @name 9b. Change Shift+Enter action into Alt+Enter action
// Меняем условие срабатывания ActionUnCrLf с MetaIsShiftPressed на MetaIsAltPressed
// (эквивалент замены Shift+Enter -> Alt+Enter для клавиши ENTER)

let enter_osp = json["key-group-processors"]
    .find(kgp => kgp["key-codes"]
        .find(kc => kc === "KEYCODE_ENTER"))
    ["on-short-press"];

let uncrlf_action = enter_osp.find(
    act => act["meta-mode-method-names"]
        && act["meta-mode-method-names"]
            .find(mmmn => mmmn === "MetaIsShiftPressed")
        && act["action-method-name"] === "ActionUnCrLf"
);

if (uncrlf_action) {
    uncrlf_action["meta-mode-method-names"] = uncrlf_action["meta-mode-method-names"]
        .map(mmmn => mmmn === "MetaIsShiftPressed" ? "MetaIsAltPressed" : mmmn);
}
