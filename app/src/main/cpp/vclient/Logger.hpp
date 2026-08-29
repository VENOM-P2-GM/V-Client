#pragma once

#include <cstddef>
#include <deque>
#include <fstream>
#include <mutex>
#include <string>
#include <vector>

namespace vc {

/**
 * Minimal, dependency-free file logger for the native runtime.
 * Thread-safe; bounded ring buffer mirrors recent lines for crash reports.
 */
class Logger {
public:
    enum class Level { Debug = 0, Info, Warn, Error };

    bool open(const std::string& path, size_t maxBytes = 512 * 1024);
    void write(Level level, const std::string& tag, const std::string& msg);
    std::vector<std::string> recent(size_t n) const;
    void flush();
    void close();

private:
    void rotateIfNeeded();

    mutable std::mutex mu_;
    std::ofstream out_;
    std::string path_;
    size_t maxBytes_ = 512 * 1024;
    size_t written_ = 0;
    std::deque<std::string> ring_;
    static constexpr size_t kRingSize = 256;
};

const char* levelName(Logger::Level level);

} // namespace vc
