#pragma once

#include <string>

#include "FpsStats.hpp"
#include "Logger.hpp"

namespace vc {

/**
 * The native runtime singleton. Owns the logger (isolated logs dir), frame
 * statistics and the crash guard. Initialized from JNI with V Client's
 * isolated root directory; everything it writes stays inside that root.
 */
class Runtime {
public:
    static Runtime& get();

    bool initialize(const std::string& rootDir);
    void shutdown();

    bool initialized() const { return initialized_; }
    Logger& logger();
    FpsStats& fps();
    const std::string& version() const { return version_; }
    const std::string& rootDir() const { return root_; }

private:
    Runtime() = default;

    bool initialized_ = false;
    std::string root_;
    std::string version_ = "V Client native runtime 1.0.0";
    Logger logger_;
    FpsStats fps_;
};

} // namespace vc
