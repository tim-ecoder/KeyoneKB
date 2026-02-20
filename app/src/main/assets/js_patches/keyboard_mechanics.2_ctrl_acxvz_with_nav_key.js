// @name 2. Ctrl+A/C/X/V/Z in NAV mode
//Чтобы команды CTRL+ACXVZ работали в NAV режиме (и в постоянном и при удержании)

let nav_kgp = json["nav-key-group-processors"];

nav_kgp.push(
    {
        "key-codes": [
            "KEYCODE_A"
        ],
        "on-short-press": [
            {
                "action-method-name": "ActionSendCtrlPlusKey",
                "method-needs-key-press-parameter": true,
                "stop-processing-at-success-result": true
            }
        ]
    },
    {
        "key-codes": [
            "KEYCODE_C"
        ],
        "on-short-press": [
            {
                "action-method-name": "ActionSendCtrlPlusKey",
                "method-needs-key-press-parameter": true,
                "stop-processing-at-success-result": true
            }
        ]
    }, {
        "key-codes": [
            "KEYCODE_X"
        ],
        "on-short-press": [
            {
                "action-method-name": "ActionSendCtrlPlusKey",
                "method-needs-key-press-parameter": true,
                "stop-processing-at-success-result": true
            }
        ]
    },
    {
        "key-codes": [
            "KEYCODE_V"
        ],
        "on-short-press": [
            {
                "action-method-name": "ActionSendCtrlPlusKey",
                "method-needs-key-press-parameter": true,
                "stop-processing-at-success-result": true
            }
        ]
    },
    {
        "key-codes": [
            "KEYCODE_Z"
        ],
        "on-short-press": [
            {
                "action-method-name": "ActionSendCtrlPlusKey",
                "method-needs-key-press-parameter": true,
                "stop-processing-at-success-result": true
            }
        ]
    }
);

