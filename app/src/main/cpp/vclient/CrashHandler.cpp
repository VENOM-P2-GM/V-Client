#include "CrashHandler.hpp"

#include <fcntl.h>
#include <signal.h>
#include <sys/time.h>
#include <unistd.h>
#include <unwind.h>

#include <cstring>
#include <cstdint>

namespace vc::crash {

namespace {

constexpr int kMaxFrames = 64;

struct CrashContext {
    char dir[256];
    bool installed = false;
};
CrashContext g_ctx = {};

uintptr_t g_frames[kMaxFrames] = {};
volatile size_t g_frameCount = 0;

_Unwind_Reason_Code unwindCallback(struct _Unwind_Context* context, void* /*arg*/) {
    if (g_frameCount >= kMaxFrames) return _URC_END_OF_STACK;
    const uintptr_t pc = _Unwind_GetIP(context);
    if (pc != 0) {
        g_frames[g_frameCount] = pc;
        g_frameCount = g_frameCount + 1;
    }
    return _URC_NO_REASON;
}

// --- async-signal-safe output helpers -----------------------------------------

void writeStr(int fd, const char* s) {
    const size_t len = strlen(s);
    if (len > 0) (void)::write(fd, s, len);
}

void writeDec(int fd, long value) {
    char buf[24];
    int i = 0;
    unsigned long v = value < 0 ? (unsigned long)(-(value + 1)) + 1UL : (unsigned long)value;
    if (value < 0) writeStr(fd, "-");
    if (v == 0) {
        writeStr(fd, "0");
        return;
    }
    while (v > 0 && i < 23) {
        buf[i++] = (char)('0' + (v % 10));
        v /= 10;
    }
    char out[24];
    int n = 0;
    for (int j = i - 1; j >= 0; --j) out[n++] = buf[j];
    (void)::write(fd, out, (size_t)n);
}

void writeHex(int fd, uintptr_t value) {
    char buf[20];
    int i = 0;
    if (value == 0) {
        writeStr(fd, "0x0");
        return;
    }
    while (value > 0 && i < 19) {
        const unsigned d = (unsigned)(value & 0xF);
        buf[i++] = (char)(d < 10 ? '0' + d : 'a' + (d - 10));
        value >>= 4;
    }
    writeStr(fd, "0x");
    char out[20];
    int n = 0;
    for (int j = i - 1; j >= 0; --j) out[n++] = buf[j];
    (void)::write(fd, out, (size_t)n);
}

long nowMs() {
    struct timeval tv {};
    gettimeofday(&tv, nullptr);
    return tv.tv_sec * 1000L + tv.tv_usec / 1000L;
}

void appendDec(char* dst, size_t capacity, long value, size_t& pos) {
    char buf[24];
    int i = 0;
    unsigned long v = (unsigned long)(value < 0 ? -value : value);
    if (value == 0) buf[i++] = '0';
    while (v > 0 && i < 23) {
        buf[i++] = (char)('0' + (v % 10));
        v /= 10;
    }
    if (value < 0 && pos < capacity) dst[pos++] = '-';
    while (i > 0 && pos < capacity) dst[pos++] = buf[--i];
}

void handleSignal(int sig, siginfo_t* info, void* /*uctx*/) {
    if (g_ctx.installed) {
        // Build "<dir>/native-crash-<ms>.txt" manually (no snprintf in handlers).
        char path[320];
        size_t pos = 0;
        const size_t dirLen = strlen(g_ctx.dir);
        if (dirLen < sizeof(path)) {
            memcpy(path, g_ctx.dir, dirLen);
            pos = dirLen;
        }
        const char* mid = "/native-crash-";
        const size_t midLen = strlen(mid);
        if (pos + midLen + 24 < sizeof(path)) {
            memcpy(path + pos, mid, midLen);
            pos += midLen;
            appendDec(path, sizeof(path), nowMs(), pos);
            const char* ext = ".txt";
            memcpy(path + pos, ext, 5);
        } else {
            path[0] = '\0';
        }

        const int fd = ::open(path, O_WRONLY | O_CREAT | O_TRUNC, 0640);
        if (fd >= 0) {
            writeStr(fd, "V Client native crash\nsignal: ");
            writeDec(fd, sig);
            if (info != nullptr && (sig == SIGSEGV || sig == SIGBUS || sig == SIGFPE || sig == SIGILL)) {
                writeStr(fd, "\nfault address: ");
                writeHex(fd, (uintptr_t)info->si_addr);
            }
            writeStr(fd, "\ntime(ms): ");
            writeDec(fd, nowMs());

            g_frameCount = 0;
            _Unwind_Backtrace(unwindCallback, nullptr);
            writeStr(fd, "\nbacktrace:\n");
            for (size_t i = 0; i < g_frameCount; ++i) {
                writeStr(fd, "  #");
                writeDec(fd, (long)i);
                writeStr(fd, "  ");
                writeHex(fd, g_frames[i]);
                writeStr(fd, "\n");
            }
            writeStr(fd, "\n(end of tombstone)\n");
            ::close(fd);
        }
    }

    // Restore the default handler and re-raise so the system processes the crash.
    ::signal(sig, SIG_DFL);
    ::raise(sig);
}

} // namespace

bool install(const std::string& crashDir) {
    if (crashDir.empty() || crashDir.size() >= sizeof(g_ctx.dir)) return false;
    g_ctx = CrashContext{};
    strncpy(g_ctx.dir, crashDir.c_str(), sizeof(g_ctx.dir) - 1);
    g_ctx.installed = true;

    struct sigaction sa = {};
    sa.sa_sigaction = handleSignal;
    sa.sa_flags = SA_SIGINFO;
    sigemptyset(&sa.sa_mask);

    const int signals[] = {SIGSEGV, SIGABRT, SIGBUS, SIGFPE, SIGILL, SIGTRAP};
    bool all = true;
    for (const int s : signals) {
        if (::sigaction(s, &sa, nullptr) != 0) all = false;
    }
    return all;
}

} // namespace vc::crash
