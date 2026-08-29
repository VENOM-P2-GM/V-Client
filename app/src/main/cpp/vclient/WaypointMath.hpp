#pragma once

#include <cmath>
#include <numbers>

#include "Math.hpp"

namespace vc {

struct WaypointResult {
    bool onScreen;      // in front of the camera and inside the frustum
    float screenX;      // pixels (valid when onScreen)
    float screenY;
    double distance;    // horizontal distance in blocks
    float bearingDeg;   // relative bearing: 0 = straight ahead, 90 = right
};

/**
 * Projects a world-space waypoint onto the screen using Minecraft camera
 * conventions:
 *   yaw   0 -> facing +Z (south), increasing clockwise (90 -> west)
 *   pitch positive -> looking down
 * The result also carries a relative bearing for the compass-style arrow
 * used by the Waypoints HUD.
 */
inline WaypointResult projectWaypoint(
    double px, double py, double pz,
    double yawDeg, double pitchDeg,
    double tx, double ty, double tz,
    double fovHDeg,
    double screenW, double screenH) {

    WaypointResult r{false, 0.f, 0.f, 0.0, 0.f};
    const double dx = tx - px;
    const double dy = ty - py;
    const double dz = tz - pz;
    r.distance = std::sqrt(dx * dx + dz * dz);

    const double yaw = yawDeg * std::numbers::pi / 180.0;
    const double pitch = pitchDeg * std::numbers::pi / 180.0;

    // Relative bearing for the direction arrow.
    const double targetYaw = std::atan2(-dx, dz) * 180.0 / std::numbers::pi;
    r.bearingDeg = static_cast<float>(wrapDegrees(targetYaw - yawDeg));

    // Camera basis.
    const double fx = -std::sin(yaw) * std::cos(pitch);
    const double fy = -std::sin(pitch);
    const double fz = std::cos(yaw) * std::cos(pitch);
    // right = worldUp x forward = (fz, 0, -fx)
    const double rx = fz, ry = 0.0, rz = -fx;
    // up = forward x right
    const double ux = fy * rz - fz * ry;
    const double uy = fz * rx - fx * rz;
    const double uz = fx * ry - fy * rx;

    const double cz = dx * fx + dy * fy + dz * fz;
    if (cz <= 0.05) return r; // behind the camera

    const double cx = dx * rx + dy * ry + dz * rz;
    const double cy = dx * ux + dy * uy + dz * uz;

    const double aspect = screenW / (screenH > 0.0 ? screenH : 1.0);
    const double tanHalfH = std::tan(fovHDeg * std::numbers::pi / 180.0 / 2.0);
    const double tanHalfV = tanHalfH / aspect;

    const double ndcX = cx / (cz * tanHalfH);
    const double ndcY = cy / (cz * tanHalfV);

    r.screenX = static_cast<float>((ndcX * 0.5 + 0.5) * screenW);
    r.screenY = static_cast<float>((0.5 - ndcY * 0.5) * screenH);
    r.onScreen = ndcX >= -1.1 && ndcX <= 1.1 && ndcY >= -1.1 && ndcY <= 1.1;
    return r;
}

} // namespace vc
