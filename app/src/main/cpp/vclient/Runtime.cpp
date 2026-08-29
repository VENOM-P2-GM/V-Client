#include "Runtime.hpp"

#include <sys/stat.h>

#include <string>

#include "CrashHandler.hpp"
#include "FpsStats.hpp"
#include "Logger.hpp"

namespace vc {

namespace {

bool ensureDir(const std::string& path) {
    return ::mkdir(path.c_str(), 0770) == 0 || errno == EEXIST;
}

bool ensureDirRecursive(const std::string& path) {
    std::string current;
    for (size_t i = 0; i < path.size(); ++i) {
        current.push_back(path[i]);
        if (path[i] == '/' && i > 0) {
            if (!ensureDir(current)) return false;
        }
    }
    return ensureDir(path);
}

} // namespace

Runtime& Runtime::get() {
    static Runtime instance;
    return instance;
}

bool Runtime::initialize(const std::string& rootDir) {
    if (initialized_) return true;
    root_ = rootDir;

    // Everything the runtime writes stays inside V Client's isolated root.
    const std::string logsDir = root_ + "/logs";
    const std::string crashesDir = root_ + "/crashes";
    if (!ensureDirRecursive(logsDir) || !ensureDirRecursive(crashesDir)) {
        return false;
    }

    if (!logger_.open(logsDir + "/native.log")) {
        return false;
    }

    const bool crashGuard = crash::install(crashesDir);
    logger_.write(Logger::Level::Info, "Runtime",
                  version_ + " initialized (crashGuard=" + (crashGuard ? "on" : "off") + ")");

    initialized_ = true;
    return true;
}

void Runtime::shutdown() {
    if (!initialized_) return;
    logger_.write(Logger::Level::Info, "Runtime", "shutting down");
    logger_.flush();
    logger_.close();
    fps_.reset();
    initialized_ = false;
}

Logger& Runtime::logger() { return logger_; }
FpsStats& Runtime::fps() { return fps_; }

} // namespace vc
