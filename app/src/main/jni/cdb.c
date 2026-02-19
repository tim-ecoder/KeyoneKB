/*
 * CDB reader + writer — mmap-based read, sequential write.
 * Implements D.J. Bernstein's CDB format.
 */

#include "cdb.h"
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <string.h>
#include <stdlib.h>

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

/* --- CDB Make (writer) --- */

static inline void put_u32(uint8_t *p, uint32_t v) {
    p[0] = v & 0xff;
    p[1] = (v >> 8) & 0xff;
    p[2] = (v >> 16) & 0xff;
    p[3] = (v >> 24) & 0xff;
}

int cdb_make_start(cdb_make_t *cm, const char *path) {
    memset(cm, 0, sizeof(*cm));
    cm->fd = open(path, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (cm->fd < 0) return -1;

    /* Write placeholder header (2048 bytes of zeros) */
    uint8_t zeros[2048];
    memset(zeros, 0, sizeof(zeros));
    if (write(cm->fd, zeros, sizeof(zeros)) != sizeof(zeros)) {
        close(cm->fd);
        cm->fd = -1;
        return -1;
    }
    cm->pos = 2048;

    cm->hpcap = 4096;
    cm->hplist = (cdb_hp_t *)malloc(cm->hpcap * sizeof(cdb_hp_t));
    if (!cm->hplist) {
        close(cm->fd);
        cm->fd = -1;
        return -1;
    }
    cm->hpcount = 0;
    return 0;
}

int cdb_make_add(cdb_make_t *cm, const char *key, size_t klen,
                 const char *val, size_t vlen) {
    if (cm->fd < 0) return -1;

    /* Grow hp list if needed */
    if (cm->hpcount >= cm->hpcap) {
        uint32_t newcap = cm->hpcap * 2;
        cdb_hp_t *newlist = (cdb_hp_t *)realloc(cm->hplist, newcap * sizeof(cdb_hp_t));
        if (!newlist) return -1;
        cm->hplist = newlist;
        cm->hpcap = newcap;
    }

    /* Record position and hash */
    uint32_t h = cdb_hash(key, klen);
    cm->hplist[cm->hpcount].hash = h;
    cm->hplist[cm->hpcount].pos = cm->pos;
    cm->hpcount++;

    /* Write record: keylen(4) + vallen(4) + key + val */
    uint8_t hdr[8];
    put_u32(hdr, (uint32_t)klen);
    put_u32(hdr + 4, (uint32_t)vlen);
    write(cm->fd, hdr, 8);
    write(cm->fd, key, klen);
    write(cm->fd, val, vlen);
    cm->pos += 8 + klen + vlen;

    return 0;
}

int cdb_make_finish(cdb_make_t *cm) {
    if (cm->fd < 0) return -1;

    /* Count entries per hash table (256 tables) */
    uint32_t counts[256];
    memset(counts, 0, sizeof(counts));
    for (uint32_t i = 0; i < cm->hpcount; i++) {
        counts[cm->hplist[i].hash & 0xFF]++;
    }

    /* Build and write each hash table */
    uint8_t final_header[2048];

    for (int t = 0; t < 256; t++) {
        uint32_t nslots = counts[t] * 2;

        put_u32(final_header + t * 8, cm->pos);
        put_u32(final_header + t * 8 + 4, nslots);

        if (nslots == 0) continue;

        /* Allocate and zero-fill slot table */
        uint8_t *slots = (uint8_t *)calloc(nslots, 8);
        if (!slots) { close(cm->fd); cm->fd = -1; free(cm->hplist); return -1; }

        /* Insert entries using open addressing */
        for (uint32_t i = 0; i < cm->hpcount; i++) {
            if ((cm->hplist[i].hash & 0xFF) != (uint32_t)t) continue;
            uint32_t slot = (cm->hplist[i].hash >> 8) % nslots;
            while (get_u32(slots + slot * 8 + 4) != 0) {
                slot = (slot + 1) % nslots;
            }
            put_u32(slots + slot * 8, cm->hplist[i].hash);
            put_u32(slots + slot * 8 + 4, cm->hplist[i].pos);
        }

        write(cm->fd, slots, nslots * 8);
        cm->pos += nslots * 8;
        free(slots);
    }

    /* Rewrite header at file start */
    lseek(cm->fd, 0, SEEK_SET);
    write(cm->fd, final_header, 2048);

    close(cm->fd);
    cm->fd = -1;
    free(cm->hplist);
    cm->hplist = NULL;
    return 0;
}
