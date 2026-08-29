#include "FpsStats.hpp"

#include <algorithm>
#include <cmath>

namespace vc {

void FpsStats::push(double dtMs, int64_t /*nowMs*/) {
    const double clamped = dtMs < 0.1 ? 0.1 : (dtMs > 250.0 ? 250.0 : dtMs);
    ring_[idx_] = clamped;
    idx_ = (idx_ + 1) % kWindow;
    if (count_ < kWindow) count_++;
    emaDt_ = emaDt_ * 0.9 + clamped * 0.1;
    if (clamped > 34.0) dropped_++;
}

void FpsStats::snapshot(double out[5]) const {
    if (count_ == 0) {
        out[0] = 0.0; out[1] = 0.0; out[2] = 0.0; out[3] = 0.0; out[4] = 0.0;
        return;
    }
    double sum = 0.0;
    for (size_t i = 0; i < count_; ++i) sum += ring_[i];
    const double avg = sum / static_cast<double>(count_);

    double sorted[kWindow];
    for (size_t i = 0; i < count_; ++i) sorted[i] = ring_[i];
    std::sort(sorted, sorted + count_);
    const size_t p99Idx = (count_ - 1) * 99 / 100;
    const double p99 = sorted[p99Idx];

    double var = 0.0;
    for (size_t i = 0; i < count_; ++i) {
        const double d = ring_[i] - avg;
        var += d * d;
    }
    const double jitter = std::sqrt(var / static_cast<double>(count_));

    out[0] = 1000.0 / (emaDt_ > 0.1 ? emaDt_ : 0.1); // fps (EMA-smoothed)
    out[1] = avg;
    out[2] = p99;
    out[3] = jitter;
    out[4] = static_cast<double>(dropped_);
}

void FpsStats::reset() {
    idx_ = 0;
    count_ = 0;
    emaDt_ = 16.7;
    dropped_ = 0;
}

} // namespace vc
