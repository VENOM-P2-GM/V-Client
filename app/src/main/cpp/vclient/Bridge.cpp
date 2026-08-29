#include "Bridge.hpp"

namespace vc {

BridgeStatus BridgeRuntime::status() {
    // Intentionally inert: no transport is compiled into this build.
    return BridgeStatus{
        false,
        "No bridge transport is compiled into this build. See docs/ARCHITECTURE.md#bridge"
    };
}

} // namespace vc
