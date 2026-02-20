// @name 8. APP_SWITCH button controls NAV mode
// NAV режим управляется через кнопку APP_SWITCH (ее функция по переключению приложений отключается)

let nav_kgp = json["key-group-processors"]
    .find(kgp => kgp["key-codes"]
        .find(kc => kc === "KEYCODE_SYM"));

nav_kgp["key-codes"].push("KEYCODE_APP_SWITCH");