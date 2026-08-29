#pragma once

#include <cmath>

namespace vc {

/**
 * Maps gyroscope angular velocity to a cross-window blur radius (dp).
 * Exponential smoothing with a ~120 ms time constant plus a small hysteresis
 * band so the blur fades fully out when the device is at rest.
 */
class MotionBlurModel {
public:
    void configure(float sensitivity, float maxRadiusDp) {
        sensitivity_ = sensitivity > 0.f ? sensitivity : 1.f;
        maxRadius_ = maxRadiusDp > 0.f ? maxRadiusDp : 18.f;
    }

    float push(double gyroX, double gyroY, double dtMs) {
        const double speed = std::fabs(gyroX) + std::fabs(gyroY); // rad/s
        const double dt = dtMs > 0.0 ? dtMs / 1000.0 : 1.0 / 60.0;
        const double target = clampd(speed * sensitivity_ * 12.0, 0.0, maxRadius_);
        const double alpha = 1.0 - std::exp(-dt / 0.12);
        radius_ += (target - radius_) * alpha;
        if (radius_ < 0.15) radius_ = 0.0; // hysteresis: release the blur completely
        return static_cast<float>(clampd(radius_, 0.0, maxRadius_));
    }

    void reset() { radius_ = 0.0; }

private:
    float sensitivity_ = 1.0f;
    float maxRadius_ = 18.0f;
    double radius_ = 0.0;
};

} // namespace vc
