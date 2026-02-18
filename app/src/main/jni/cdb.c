/*
 * Minimal CDB reader — mmap-based, read-only.
 * Implements D.J. Bernstein's CDB format.
 */

#include "cdb.h"
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <string.h>

/* Read a little-endian uint32 from buffer. */
static inline uint32_t get_u32(const uint8_t *p) {
    return (uint32_t)p[0] | ((uint32_t)p[1] << 8) |
           ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24);
}

int cdb_open(cdb_t *cdb, const char *path) {
    cdb->map = NULL;
    cdb->size = 0;
    cdb->fd = -1;
    cdb->mmap_base = NULL;
    cdb->mmap_len = 0;

    int fd = open(path, O_RDONLY);
    if (fd < 0) return -1;

    struct stat st;
    if (fstat(fd, &st) < 0 || st.st_size < 2048) {
        close(fd);
        return -1;
    }

    void *m = mmap(NULL, st.st_size, PROT_READ, MAP_PRIVATE, fd, 0);
    if (m == MAP_FAILED) {
        close(fd);
        return -1;
    }

    cdb->map = (const uint8_t *)m;
    cdb->size = st.st_size;
    cdb->fd = fd;
    cdb->mmap_base = m;
    cdb->mmap_len = st.st_size;
    return 0;
}

int cdb_open_fd(cdb_t *cdb, int fd, size_t offset, size_t length) {
    cdb->map = NULL;
    cdb->size = 0;
    cdb->fd = -1;
    cdb->mmap_base = NULL;
    cdb->mmap_len = 0;

    if (fd < 0 || length < 2048) return -1;

    /* mmap offset must be page-aligned */
    long page_size = sysconf(_SC_PAGE_SIZE);
    size_t aligned = (offset / page_size) * page_size;
    size_t extra = offset - aligned;
    size_t map_len = length + extra;

    void *m = mmap(NULL, map_len, PROT_READ, MAP_PRIVATE, fd, aligned);
    if (m == MAP_FAILED) return -1;

    cdb->mmap_base = m;
    cdb->mmap_len = map_len;
    cdb->map = (const uint8_t *)m + extra;
    cdb->size = length;
    cdb->fd = -1; /* don't own the fd */
    return 0;
}

void cdb_close(cdb_t *cdb) {
    if (cdb->mmap_base) {
        munmap(cdb->mmap_base, cdb->mmap_len);
        cdb->mmap_base = NULL;
        cdb->map = NULL;
    }
    if (cdb->fd >= 0) {
        close(cdb->fd);
        cdb->fd = -1;
    }
}

int cdb_find(const cdb_t *cdb, const char *key, size_t klen,
             const char **val, size_t *vlen) {
    if (!cdb->map || cdb->size < 2048) return 0;

    uint32_t h = cdb_hash(key, klen);
    /* Header: 256 entries of (pos, nslots), each 8 bytes */
    uint32_t idx = (h & 0xff) * 8;
    uint32_t htab_pos  = get_u32(cdb->map + idx);
    uint32_t htab_len  = get_u32(cdb->map + idx + 4);

    if (htab_len == 0) return 0;

    uint32_t slot = (h >> 8) % htab_len;

    for (uint32_t i = 0; i < htab_len; i++) {
        uint32_t entry_off = htab_pos + ((slot + i) % htab_len) * 8;
        if (entry_off + 8 > cdb->size) return 0;

        uint32_t stored_hash = get_u32(cdb->map + entry_off);
        uint32_t rec_pos     = get_u32(cdb->map + entry_off + 4);

        if (rec_pos == 0) return 0; /* empty slot = not found */

        if (stored_hash == h) {
            /* Verify key match */
            if (rec_pos + 8 > cdb->size) return 0;
            uint32_t rk = get_u32(cdb->map + rec_pos);
            uint32_t rv = get_u32(cdb->map + rec_pos + 4);
            if (rk != klen) continue;
            if (rec_pos + 8 + rk + rv > cdb->size) return 0;
            if (memcmp(cdb->map + rec_pos + 8, key, klen) != 0) continue;
            /* Found! */
            *val = (const char *)(cdb->map + rec_pos + 8 + rk);
            *vlen = rv;
            return 1;
        }
    }
    return 0;
}
