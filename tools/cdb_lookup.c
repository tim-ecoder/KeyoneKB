#include "cdb.h"
#include <stdio.h>
#include <string.h>
int main(int argc, char **argv) {
    cdb_t c;
    if (cdb_open(&c, argv[1]) != 0) { printf("open failed\n"); return 1; }
    for (int i = 2; i < argc; i++) {
        const char *v; size_t vl;
        if (cdb_find(&c, argv[i], strlen(argv[i]), &v, &vl))
            printf("%s -> %.*s\n", argv[i], (int)vl, v);
        else
            printf("%s -> НЕ НАЙДЕНО\n", argv[i]);
    }
    cdb_close(&c);
    return 0;
}
