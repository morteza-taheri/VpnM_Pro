#include <mbedtls/ssl.h>
#include <mbedtls/net_sockets.h>
#include <mbedtls/ctr_drbg.h>
#include <mbedtls/entropy.h>
#include <mbedtls/cipher.h>
#include <mbedtls/md.h>
#include <mbedtls/sha1.h>
#include <mbedtls/sha256.h>
#include <mbedtls/md5.h>
#include <mbedtls/error.h>
#include <mbedtls/debug.h>
#include <psa/crypto.h>
#include "softether_crypto.h"
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <poll.h>
#include <time.h>
#include <sys/socket.h>
#include <android/log.h>

#define TAG "SoftEtherCrypto"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static void mbedtls_android_debug_cb(void *ctx, int level, const char *file, int line, const char *str) {
    (void)ctx;
    (void)file;
    (void)line;
    LOGD("[mbedTLS %d] %s", level, str);
}

static uint64_t current_time_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)ts.tv_sec * 1000 + (uint64_t)(ts.tv_nsec / 1000000);
}

// AES encryption/decryption context
struct aes_context {
    mbedtls_cipher_context_t cipher_ctx;
    int mode;
    size_t key_len;
};

// SSL client context
struct ssl_context {
    mbedtls_ssl_context ssl;
    mbedtls_ssl_config conf;
    mbedtls_ctr_drbg_context ctr_drbg;
    mbedtls_entropy_context entropy;
    int socket_fd;
    int connected;
};

// Net BIO callbacks for mbedtls using non-blocking socket
static int softether_mbedtls_send(void *ctx, const unsigned char *buf, size_t len) {
    int fd = *(int *)ctx;
    if (fd < 0) return MBEDTLS_ERR_NET_INVALID_CONTEXT;
    ssize_t ret = send(fd, buf, len, MSG_NOSIGNAL);
    if (ret < 0) {
        if (errno == EAGAIN || errno == EWOULDBLOCK || errno == EINTR) {
            return MBEDTLS_ERR_SSL_WANT_WRITE;
        }
        LOGE("mbedtls send failed: %s (errno=%d)", strerror(errno), errno);
        return MBEDTLS_ERR_NET_SEND_FAILED;
    }
    return (int)ret;
}

static int softether_mbedtls_recv(void *ctx, unsigned char *buf, size_t len) {
    int fd = *(int *)ctx;
    if (fd < 0) return MBEDTLS_ERR_NET_INVALID_CONTEXT;
    ssize_t ret = recv(fd, buf, len, 0);
    if (ret < 0) {
        if (errno == EAGAIN || errno == EWOULDBLOCK || errno == EINTR) {
            return MBEDTLS_ERR_SSL_WANT_READ;
        }
        LOGE("mbedtls recv failed: %s (errno=%d)", strerror(errno), errno);
        return MBEDTLS_ERR_NET_RECV_FAILED;
    }
    if (ret == 0) {
        return MBEDTLS_ERR_SSL_PEER_CLOSE_NOTIFY;
    }
    return (int)ret;
}

// Random bytes generator
int generate_random_bytes(uint8_t* buffer, size_t len) {
    if (buffer == NULL || len == 0) return -1;
    int fd = open("/dev/urandom", O_RDONLY);
    if (fd >= 0) {
        ssize_t read_bytes = read(fd, buffer, len);
        close(fd);
        if (read_bytes == (ssize_t)len) {
            return 0;
        }
    }
    for (size_t i = 0; i < len; i++) {
        buffer[i] = (uint8_t)(rand() & 0xFF);
    }
    return 0;
}

// MD5 hashing
void md5_hash(const uint8_t* data, size_t data_len, uint8_t* hash) {
    if (hash == NULL) return;
    const mbedtls_md_info_t *md_info = mbedtls_md_info_from_type(MBEDTLS_MD_MD5);
    if (md_info == NULL) return;
    mbedtls_md(md_info, data ? data : (const uint8_t*)"", data_len, hash);
}

// SHA1 hashing
void sha1_hash(const uint8_t* data, size_t data_len, uint8_t* hash) {
    if (hash == NULL) return;
    const mbedtls_md_info_t *md_info = mbedtls_md_info_from_type(MBEDTLS_MD_SHA1);
    if (md_info == NULL) return;
    mbedtls_md(md_info, data ? data : (const uint8_t*)"", data_len, hash);
}

// SHA256 hashing
void sha256_hash(const uint8_t* data, size_t data_len, uint8_t* hash) {
    if (hash == NULL) return;
    const mbedtls_md_info_t *md_info = mbedtls_md_info_from_type(MBEDTLS_MD_SHA256);
    if (md_info == NULL) return;
    mbedtls_md(md_info, data ? data : (const uint8_t*)"", data_len, hash);
}

// HMAC-SHA256
void hmac_sha256(const uint8_t* key, size_t key_len,
                 const uint8_t* data, size_t data_len,
                 uint8_t* mac) {
    if (mac == NULL) return;
    const mbedtls_md_info_t* md_info = mbedtls_md_info_from_type(MBEDTLS_MD_SHA256);
    if (md_info == NULL) return;
    mbedtls_md_hmac(md_info, key, key_len, data, data_len, mac);
}

// AES Operations
aes_context_t* aes_create(int mode, const uint8_t* key, size_t key_len, 
                          const uint8_t* iv, size_t iv_len) {
    if (key == NULL || (key_len != 16 && key_len != 24 && key_len != 32)) {
        LOGE("Invalid key parameters");
        return NULL;
    }
    
    psa_crypto_init();

    aes_context_t* ctx = (aes_context_t*)calloc(1, sizeof(aes_context_t));
    if (ctx == NULL) {
        LOGE("Failed to allocate AES context");
        return NULL;
    }
    
    mbedtls_cipher_init(&ctx->cipher_ctx);
    ctx->mode = mode;
    ctx->key_len = key_len;
    
    mbedtls_cipher_type_t cipher_type;
    if (mode == AES_MODE_CBC) {
        if (key_len == 16) cipher_type = MBEDTLS_CIPHER_AES_128_CBC;
        else if (key_len == 24) cipher_type = MBEDTLS_CIPHER_AES_192_CBC;
        else cipher_type = MBEDTLS_CIPHER_AES_256_CBC;
    } else {
        if (key_len == 16) cipher_type = MBEDTLS_CIPHER_AES_128_GCM;
        else if (key_len == 24) cipher_type = MBEDTLS_CIPHER_AES_192_GCM;
        else cipher_type = MBEDTLS_CIPHER_AES_256_GCM;
    }
    
    const mbedtls_cipher_info_t* cipher_info = mbedtls_cipher_info_from_type(cipher_type);
    if (cipher_info == NULL || mbedtls_cipher_setup(&ctx->cipher_ctx, cipher_info) != 0) {
        LOGE("Failed to setup cipher");
        mbedtls_cipher_free(&ctx->cipher_ctx);
        free(ctx);
        return NULL;
    }
    
    mbedtls_cipher_setkey(&ctx->cipher_ctx, key, (int)(key_len * 8), MBEDTLS_ENCRYPT);
    if (iv != NULL && iv_len > 0) {
        mbedtls_cipher_set_iv(&ctx->cipher_ctx, iv, iv_len);
    }
    
    return ctx;
}

void aes_destroy(aes_context_t* ctx) {
    if (ctx == NULL) return;
    mbedtls_cipher_free(&ctx->cipher_ctx);
    free(ctx);
}

int aes_encrypt(aes_context_t* ctx, const uint8_t* plaintext, size_t plaintext_len,
                uint8_t* ciphertext, size_t* ciphertext_len) {
    if (ctx == NULL || plaintext == NULL || ciphertext == NULL || ciphertext_len == NULL) {
        return -1;
    }
    size_t out_len = 0;
    size_t finish_len = 0;
    if (mbedtls_cipher_reset(&ctx->cipher_ctx) != 0 ||
        mbedtls_cipher_update(&ctx->cipher_ctx, plaintext, plaintext_len, ciphertext, &out_len) != 0 ||
        mbedtls_cipher_finish(&ctx->cipher_ctx, ciphertext + out_len, &finish_len) != 0) {
        return -1;
    }
    *ciphertext_len = out_len + finish_len;
    return 0;
}

int aes_decrypt(aes_context_t* ctx, const uint8_t* ciphertext, size_t ciphertext_len,
                uint8_t* plaintext, size_t* plaintext_len) {
    if (ctx == NULL || ciphertext == NULL || plaintext == NULL || plaintext_len == NULL) {
        return -1;
    }
    size_t out_len = 0;
    size_t finish_len = 0;
    if (mbedtls_cipher_reset(&ctx->cipher_ctx) != 0 ||
        mbedtls_cipher_update(&ctx->cipher_ctx, ciphertext, ciphertext_len, plaintext, &out_len) != 0 ||
        mbedtls_cipher_finish(&ctx->cipher_ctx, plaintext + out_len, &finish_len) != 0) {
        return -1;
    }
    *plaintext_len = out_len + finish_len;
    return 0;
}

// SSL / TLS Client implementation
ssl_context_t* ssl_create_client(void) {
    psa_status_t psa_status = psa_crypto_init();
    if (psa_status != PSA_SUCCESS) {
        LOGE("psa_crypto_init failed: %d", (int)psa_status);
    }

    ssl_context_t* ctx = (ssl_context_t*)calloc(1, sizeof(ssl_context_t));
    if (ctx == NULL) {
        LOGE("Failed to allocate SSL context");
        return NULL;
    }
    
    ctx->socket_fd = -1;
    ctx->connected = 0;
    
    mbedtls_ssl_init(&ctx->ssl);
    mbedtls_ssl_config_init(&ctx->conf);
    mbedtls_ctr_drbg_init(&ctx->ctr_drbg);
    mbedtls_entropy_init(&ctx->entropy);
    
    const char* pers = "softether_android_vpn";
    int ret = mbedtls_ctr_drbg_seed(&ctx->ctr_drbg, mbedtls_entropy_func, &ctx->entropy,
                                    (const unsigned char*)pers, strlen(pers));
    if (ret != 0) {
        LOGE("mbedtls_ctr_drbg_seed failed: -0x%04X", (unsigned int)-ret);
        ssl_destroy(ctx);
        return NULL;
    }
    
    ret = mbedtls_ssl_config_defaults(&ctx->conf,
                                      MBEDTLS_SSL_IS_CLIENT,
                                      MBEDTLS_SSL_TRANSPORT_STREAM,
                                      MBEDTLS_SSL_PRESET_DEFAULT);
    if (ret != 0) {
        LOGE("mbedtls_ssl_config_defaults failed: -0x%04X", (unsigned int)-ret);
        ssl_destroy(ctx);
        return NULL;
    }
    
    // SoftEther and VPNGate servers use self-signed / dynamic certificates
    mbedtls_ssl_conf_authmode(&ctx->conf, MBEDTLS_SSL_VERIFY_NONE);
    mbedtls_ssl_conf_rng(&ctx->conf, mbedtls_ctr_drbg_random, &ctx->ctr_drbg);
    mbedtls_ssl_conf_dbg(&ctx->conf, mbedtls_android_debug_cb, NULL);
    mbedtls_debug_set_threshold(2);
    
    // SoftEther Protocol standard uses TLS 1.2 with self-signed certificate acceptance
    mbedtls_ssl_conf_min_version(&ctx->conf, MBEDTLS_SSL_MAJOR_VERSION_3, MBEDTLS_SSL_MINOR_VERSION_3);
    mbedtls_ssl_conf_max_version(&ctx->conf, MBEDTLS_SSL_MAJOR_VERSION_3, MBEDTLS_SSL_MINOR_VERSION_3);
    
    ret = mbedtls_ssl_setup(&ctx->ssl, &ctx->conf);
    if (ret != 0) {
        LOGE("mbedtls_ssl_setup failed: -0x%04X", (unsigned int)-ret);
        ssl_destroy(ctx);
        return NULL;
    }
    
    LOGD("SSL client context created successfully");
    return ctx;
}

void ssl_destroy(ssl_context_t* ctx) {
    if (ctx == NULL) return;
    if (ctx->connected) {
        mbedtls_ssl_close_notify(&ctx->ssl);
    }
    mbedtls_ssl_free(&ctx->ssl);
    mbedtls_ssl_config_free(&ctx->conf);
    mbedtls_ctr_drbg_free(&ctx->ctr_drbg);
    mbedtls_entropy_free(&ctx->entropy);
    free(ctx);
}

int ssl_connect(ssl_context_t* ctx, int socket_fd, const char* hostname) {
    if (ctx == NULL || socket_fd < 0) {
        LOGE("Invalid SSL parameters for connect");
        return -1;
    }
    
    ctx->socket_fd = socket_fd;
    mbedtls_ssl_set_bio(&ctx->ssl, &ctx->socket_fd, softether_mbedtls_send, softether_mbedtls_recv, NULL);
    mbedtls_ssl_set_hs_authmode(&ctx->ssl, MBEDTLS_SSL_VERIFY_NONE);
    
    if (hostname != NULL && hostname[0] != '\0') {
        mbedtls_ssl_set_hostname(&ctx->ssl, hostname);
    }
    
    LOGD("Starting TLS handshake with %s (fd=%d)...", hostname ? hostname : "server", socket_fd);
    
    uint64_t start_time = current_time_ms();
    int ret;
    
    while ((ret = mbedtls_ssl_handshake(&ctx->ssl)) != 0) {
        if (ret != MBEDTLS_ERR_SSL_WANT_READ && ret != MBEDTLS_ERR_SSL_WANT_WRITE) {
            char error_buf[128];
            mbedtls_strerror(ret, error_buf, sizeof(error_buf));
            LOGE("TLS handshake failed: -0x%04X (%s)", (unsigned int)-ret, error_buf);
            return -1;
        }
        
        struct pollfd pfd;
        pfd.fd = socket_fd;
        pfd.events = (ret == MBEDTLS_ERR_SSL_WANT_READ) ? POLLIN : POLLOUT;
        pfd.revents = 0;
        
        int poll_ret = poll(&pfd, 1, 1000);
        if (poll_ret < 0) {
            if (errno == EINTR) continue;
            LOGE("poll failed during TLS handshake: %s", strerror(errno));
            return -1;
        }
        
        if (current_time_ms() - start_time > 15000) {
            LOGE("TLS handshake timed out after 15 seconds");
            return -1;
        }
    }
    
    ctx->connected = 1;
    const char* version = mbedtls_ssl_get_version(&ctx->ssl);
    const char* ciphersuite = mbedtls_ssl_get_ciphersuite(&ctx->ssl);
    LOGD("TLS handshake successful! Negotiated %s (%s)", version ? version : "TLS", ciphersuite ? ciphersuite : "");
    return 0;
}

int ssl_read(ssl_context_t* ctx, uint8_t* buffer, size_t len) {
    if (ctx == NULL || buffer == NULL || len == 0 || !ctx->connected) {
        return -1;
    }
    
    while (1) {
        int ret = mbedtls_ssl_read(&ctx->ssl, buffer, len);
        if (ret > 0) {
            return ret;
        }
        if (ret == 0 || ret == MBEDTLS_ERR_SSL_PEER_CLOSE_NOTIFY) {
            return 0; // EOF
        }
        if (ret == MBEDTLS_ERR_SSL_WANT_READ) {
            struct pollfd pfd;
            pfd.fd = ctx->socket_fd;
            pfd.events = POLLIN;
            pfd.revents = 0;
            int pr = poll(&pfd, 1, 10000);
            if (pr <= 0) return -1;
            continue;
        }
        if (ret == MBEDTLS_ERR_SSL_WANT_WRITE) {
            struct pollfd pfd;
            pfd.fd = ctx->socket_fd;
            pfd.events = POLLOUT;
            pfd.revents = 0;
            int pr = poll(&pfd, 1, 5000);
            if (pr <= 0) return -1;
            continue;
        }
        char error_buf[128];
        mbedtls_strerror(ret, error_buf, sizeof(error_buf));
        LOGE("ssl_read error: -0x%04X (%s)", (unsigned int)-ret, error_buf);
        return -1;
    }
}

int ssl_write(ssl_context_t* ctx, const uint8_t* data, size_t len) {
    if (ctx == NULL || data == NULL || len == 0 || !ctx->connected) {
        return -1;
    }
    
    size_t total_written = 0;
    while (total_written < len) {
        int ret = mbedtls_ssl_write(&ctx->ssl, data + total_written, len - total_written);
        if (ret > 0) {
            total_written += ret;
            continue;
        }
        if (ret == MBEDTLS_ERR_SSL_WANT_WRITE) {
            struct pollfd pfd;
            pfd.fd = ctx->socket_fd;
            pfd.events = POLLOUT;
            pfd.revents = 0;
            int pr = poll(&pfd, 1, 5000);
            if (pr <= 0) return -1;
            continue;
        }
        if (ret == MBEDTLS_ERR_SSL_WANT_READ) {
            struct pollfd pfd;
            pfd.fd = ctx->socket_fd;
            pfd.events = POLLIN;
            pfd.revents = 0;
            int pr = poll(&pfd, 1, 5000);
            if (pr <= 0) return -1;
            continue;
        }
        char error_buf[128];
        mbedtls_strerror(ret, error_buf, sizeof(error_buf));
        LOGE("ssl_write error: -0x%04X (%s)", (unsigned int)-ret, error_buf);
        return -1;
    }
    
    return (int)total_written;
}

int ssl_has_pending(ssl_context_t* ctx) {
    if (ctx == NULL || !ctx->connected) return 0;
    return mbedtls_ssl_get_bytes_avail(&ctx->ssl) > 0;
}

void ssl_shutdown(ssl_context_t* ctx) {
    if (ctx == NULL || !ctx->connected) return;
    mbedtls_ssl_close_notify(&ctx->ssl);
    ctx->connected = 0;
}
