#pragma once

#include <string>

namespace vc::crash {

/**
 * Installs SIGSEGV/SIGABRT/SIGBUS/SIGFPE/SIGILL/SIGTRAP handlers that write a
 * tombstone (signal, fault address, unwind backtrace) into the isolated
 * crashes/ directory, then re-raise so the system still processes the crash.
 *
 * The handler only uses async-signal-safe calls (open/write) and manual
 * number formatting.
 */
bool install(const std::string& crashDir);

} // namespace vc::crash
