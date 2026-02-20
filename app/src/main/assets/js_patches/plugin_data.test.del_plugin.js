// @name [Test] Remove Yandex Maps plugin
//Для случая когда надо удалить лишний плагин
json["search-plugins"] = json["search-plugins"].filter((val) => val["package-name"] !== "ru.yandex.yandexmaps1");