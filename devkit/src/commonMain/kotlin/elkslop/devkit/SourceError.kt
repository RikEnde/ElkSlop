// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.devkit

/** A mistake in a source file, at [line] (from 1). The message reads `line 12: No such label: loop`. */
class SourceError(val line: Int, val reason: String) : Exception("line $line: $reason")
