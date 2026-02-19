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
    const uint8_t *map;     /* pointer to CDB data start */
    size_t         size;    /* CDB data size */
    int            fd;      /* file descriptor (-1 if not owned) */
    void          *mmap_base; /* actual mmap base (may differ from map if offset) */
    size_t         mmap_len;  /* actual mmap length */
} cdb_t;

/* Open a CDB file (mmap). Returns 0 on success, -1 on error. */
int cdb_open(cdb_t *cdb, const char *path);

/* Open CDB from an existing fd at offset (for mmap from APK). Does not own fd. */
int cdb_open_fd(cdb_t *cdb, int fd, size_t offset, size_t length);

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

/* --- CDB Make (writer) --- */

typedef struct {
    uint32_t hash;
    uint32_t pos;
} cdb_hp_t;

typedef struct {
    int       fd;
    uint32_t  pos;      /* current write position */
    cdb_hp_t *hplist;   /* (hash, position) for all added records */
    uint32_t  hpcount;
    uint32_t  hpcap;
} cdb_make_t;

/* Start building a new CDB file. Returns 0 on success. */
int cdb_make_start(cdb_make_t *cm, const char *path);

/* Add a key-value record. */
int cdb_make_add(cdb_make_t *cm, const char *key, size_t klen,
                 const char *val, size_t vlen);

/* Finalize: write hash tables and header, close file. */
int cdb_make_finish(cdb_make_t *cm);

#endif /* CDB_H */
