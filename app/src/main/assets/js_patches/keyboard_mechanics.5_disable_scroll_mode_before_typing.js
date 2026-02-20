// @name 5. Disable chats reading/scrolling default mode
json["on-start-input-actions"] = json["on-start-input-actions"]
    .filter(act => act["action-method-name"] !== "ActionTryEnableGestureInputScrollMode");
