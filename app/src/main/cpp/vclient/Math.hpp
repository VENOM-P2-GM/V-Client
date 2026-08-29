#pragma once

#include <cmath>

namespace vc {

// --- small math helpers shared by the runtime -------------------------------

inline double clampd(double v, double lo, double hi) {
    return v < lo ? lo : (v > hi ? hi : v);
}

inline float clampf(float v, float lo, float hi) {
    return v < lo ? lo : (v > hi ? hi : v);
}

inline double lerp(double a, double b, double t) {
    return a + (b - a) * t;
}

/** Wraps degrees into (-180, 180]. */
inline double wrapDegrees(double deg) {
    double x = std::fmod(deg + 180.0, 360.0);
    if (x < 0) x += 360.0;
    return x - 180.0;
}

} // namespace vc
