#pragma once

#include <string>

namespace vc {

struct BridgeStatus {
    bool available;
    std::string detail;
};

/**
 * Future native bridge ABI.
 *
 * The design keeps live game data optional: the Kotlin side exposes a
 * BridgeDataSource; when a transport is eventually implemented (for example a
 * Shizuku/ADB-backed socket feeding player snapshots), it plugs in here and
 * flips BridgeDataSource.available — no module, renderer or profile code
 * changes. No transport ships in this build, by design: V Client's shipped
 * feature set works purely through public Android APIs.
 */
class BridgeRuntime {
public:
    static BridgeStatus status();
};

} // namespace vc
