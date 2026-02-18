/*
 * Minimal CDB (Constant DataBase) reader — read-only, mmap-based.
 * Implements D.J. Bernstein's CDB format for fast O(1) key lookups.
 * Public domain.
 */
#ifndef CDB_H
#define CDB_H

#include <stdint.h>
#include <stddef.h>

typedef struct {
    const uint8_t *map;     /* mmap'd file data */
    size_t         size;    /* file size */
    int            fd;      /* file descriptor (for munmap) */
} cdb_t;

/* Open a CDB file (mmap). Returns 0 on success, -1 on error. */
int cdb_open(cdb_t *cdb, const char *path);

/* Close and unmap. */
void cdb_close(cdb_t *cdb);

/*
 * Look up key. On hit, sets *val and *vlen to the value pointer and length.
 * The pointer is valid until cdb_close(). Returns 1 if found, 0 if not.
 */
int cdb_find(const cdb_t *cdb, const char *key, size_t klen,
             const char **val, size_t *vlen);

/* CDB hash function (DJB hash). */
static inline uint32_t cdb_hash(const char *buf, size_t len) {
    uint32_t h = 5381;
    for (size_t i = 0; i < len; i++)
        h = ((h << 5) + h) ^ (uint8_t)buf[i];
    return h;
}

#endif /* CDB_H */
