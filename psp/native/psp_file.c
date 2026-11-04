// File access for FileHandle: plain newlib calls, paths relative to the EBOOT folder.
#include "psp_api.h"
#include <dirent.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

int32_t psp_file_size(void* path) {
    struct stat st;
    if (stat((const char*) path, &st) != 0) return -1;
    if (S_ISDIR(st.st_mode)) return -2;
    return (int32_t) st.st_size;
}

int32_t psp_file_read(void* path, void* buf, int32_t len) {
    FILE* f = fopen((const char*) path, "rb");
    if (!f) return -1;
    int32_t n = (int32_t) fread(buf, 1, len, f);
    fclose(f);
    return n;
}

int32_t psp_file_write(void* path, void* data, int32_t len, int32_t append) {
    FILE* f = fopen((const char*) path, append ? "ab" : "wb");
    if (!f) return -1;
    int32_t n = (int32_t) fwrite(data, 1, len, f);
    fclose(f);
    return n == len ? n : -1;
}

int32_t psp_file_delete(void* path) {
    struct stat st;
    if (stat((const char*) path, &st) != 0) return -1;
    return S_ISDIR(st.st_mode) ? rmdir((const char*) path) : remove((const char*) path);
}

int32_t psp_file_rename(void* from, void* to) {
    return rename((const char*) from, (const char*) to);
}

int32_t psp_file_mkdirs(void* path) {
    char buf[512];
    strncpy(buf, (const char*) path, sizeof buf - 1);
    buf[sizeof buf - 1] = 0;
    size_t n = strlen(buf);
    while (n > 0 && buf[n - 1] == '/') buf[--n] = 0;
    for (char* p = buf + 1; *p; p++) {
        if (*p == '/') {
            *p = 0;
            mkdir(buf, 0777);
            *p = '/';
        }
    }
    return mkdir(buf, 0777);
}

int64_t psp_file_mtime(void* path) {
    struct stat st;
    if (stat((const char*) path, &st) != 0) return 0;
    return (int64_t) st.st_mtime * 1000;
}

int32_t psp_dir_list(void* path, void* out, int32_t cap) {
    DIR* d = opendir((const char*) path);
    if (!d) return -1;
    char* o = out;
    int32_t len = 0;
    struct dirent* e;
    while ((e = readdir(d)) != NULL) {
        if (strcmp(e->d_name, ".") == 0 || strcmp(e->d_name, "..") == 0) continue;
        int32_t n = (int32_t) strlen(e->d_name);
        if (len + n + 1 > cap) break;
        memcpy(o + len, e->d_name, n);
        len += n;
        o[len++] = '\n';
    }
    closedir(d);
    return len;
}
