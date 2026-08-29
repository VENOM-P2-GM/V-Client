#pragma once

#include <cstddef>
#include <cstdint>

namespace vc {

/**
 * Frame statistics over a sliding window:
 * EMA-smoothed FPS, average/p99 frame time, jitter (stddev), dropped frames.
 */
class FpsStats {
public:
    void push(double dtMs, int64_t nowMs);

    /** Fills out[5] = {fps, avgFrameMs, p99FrameMs, jitterMs, droppedFrames}. */
    void snapshot(double out[5]) const;

    void reset();

private:
    static constexpr size_t kWindow = 240;
    double ring_[kWindow] = {};
    size_t idx_ = 0;
    size_t count_ = 0;
    double emaDt_ = 16.7;
    int64_t dropped_ = 0;
};

} // namespace vc
