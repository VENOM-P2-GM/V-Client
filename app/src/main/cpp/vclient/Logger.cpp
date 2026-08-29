#include "Logger.hpp"

#include <chrono>
#include <cstdio>
#include <ctime>

namespace vc {

const char* levelName(Logger::Level level) {
    switch (level) {
        case Logger::Level::Debug: return "D";
        case Logger::Level::Info: return "I";
        case Logger::Level::Warn: return "W";
        case Logger::Level::Error: return "E";
    }
    return "?";
}

bool Logger::open(const std::string& path, size_t maxBytes) {
    std::lock_guard<std::mutex> lock(mu_);
    path_ = path;
    maxBytes_ = maxBytes;
    out_.open(path, std::ios::app);
    if (!out_.is_open()) return false;
    written_ = static_cast<size_t>(out_.tellp());
    return true;
}

void Logger::write(Level level, const std::string& tag, const std::string& msg) {
    std::lock_guard<std::mutex> lock(mu_);
    if (!out_.is_open()) return;

    using namespace std::chrono;
    const auto now = system_clock::now();
    const std::time_t t = system_clock::to_time_t(now);
    std::tm tm{};
    localtime_r(&t, &tm);
    char stamp[32];
    std::snprintf(stamp, sizeof(stamp), "%02d-%02d %02d:%02d:%02d",
                  tm.tm_mon + 1, tm.tm_mday, tm.tm_hour, tm.tm_min, tm.tm_sec);

    char line[1024];
    std::snprintf(line, sizeof(line), "%s %s [%s] %s\n", stamp, levelName(level), tag.c_str(), msg.c_str());

    rotateIfNeeded();
    out_ << line;
    written_ += std::string(line).size();
    out_.flush();

    if (ring_.size() >= kRingSize) ring_.pop_front();
    ring_.emplace_back(line);
}

std::vector<std::string> Logger::recent(size_t n) const {
    std::lock_guard<std::mutex> lock(mu_);
    if (n > ring_.size()) n = ring_.size();
    return std::vector<std::string>(ring_.end() - static_cast<long>(n), ring_.end());
}

void Logger::flush() {
    std::lock_guard<std::mutex> lock(mu_);
    if (out_.is_open()) out_.flush();
}

void Logger::close() {
    std::lock_guard<std::mutex> lock(mu_);
    if (out_.is_open()) {
        out_.flush();
        out_.close();
    }
}

void Logger::rotateIfNeeded() {
    if (written_ < maxBytes_) return;
    out_.close();
    std::string rotated = path_ + ".1";
    std::remove(rotated.c_str());
    std::rename(path_.c_str(), rotated.c_str());
    out_.open(path_, std::ios::app);
    written_ = 0;
}

} // namespace vc
