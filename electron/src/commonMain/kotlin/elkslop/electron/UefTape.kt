// SPDX-FileCopyrightText: 2026 The Elkslop contributors
// SPDX-License-Identifier: BSD-3-Clause

package elkslop.electron

/**
 * A cassette loaded from a UEF image. It plays one bit each time the ULA asks,
 * so it only moves while the motor runs, and stays silent past the end.
 *
 * Understands data (&0100, &0102, &0104), carrier (&0110, &0111) and gap (&0112,
 * &0116) chunks. Other chunks don't change the signal this model can represent,
 * such as security cycles (&0114), and are skipped. Takes plain UEF bytes: images
 * are usually gzipped, and the host unpacks them.
 */
class UefTape private constructor(private val bits: ByteArray) : CassettePort {
    /** Tape position in bit periods: 1/1200 of a second each. */
    var position: Int = 0
        private set
    val length: Int get() = bits.size
    val atEnd: Boolean get() = position == bits.size

    fun rewind() { position = 0 }

    override fun readBit(): TapeBit {
        if (atEnd) return TapeBit.SILENCE
        return when (bits[position++]) {
            ZERO -> TapeBit.ZERO
            ONE -> TapeBit.ONE
            else -> TapeBit.SILENCE
        }
    }

    private class Recorder {
        var bits = ByteArray(1 shl 16)
        var size = 0

        fun add(bit: Byte, count: Int = 1) {
            if (size + count > bits.size) bits = bits.copyOf(maxOf(bits.size * 2, size + count))
            bits.fill(bit, size, size + count)
            size += count
        }

        fun addByte(value: Int, dataBits: Int = 8, parity: Char = 'N', stopBits: Int = 1) {
            add(ZERO)
            var ones = 0
            for (i in 0 until dataBits) {
                val bit = (value ushr i) and 1
                ones += bit
                add(if (bit == 1) ONE else ZERO)
            }
            if (parity == 'E' || parity == 'O') {
                val wantOdd = parity == 'O'
                add(if ((ones % 2 == 1) == wantOdd) ZERO else ONE)
            }
            add(ONE, stopBits)
        }

        /** A carrier of [cycles] 2400 Hz cycles: a 1 bit is two cycles. */
        fun addCarrier(cycles: Int) = add(ONE, (cycles + 1) / 2)
    }

    companion object {
        private const val ZERO: Byte = 0
        private const val ONE: Byte = 1
        private const val SILENCE: Byte = 2
        private const val BITS_PER_SECOND = 1200
        private const val MAX_GAP_SECONDS = 3600f
        private const val HEADER_SIZE = 12
        private const val CHUNK_HEADER_SIZE = 6
        private const val MAGIC = "UEF File!\u0000"

        /** @throws IllegalArgumentException if [image] is not a well-formed UEF file. */
        fun parse(image: ByteArray): UefTape {
            require(image.size >= HEADER_SIZE && MAGIC.indices.all { image[it].toInt() == MAGIC[it].code }) {
                "Not a UEF tape image"
            }
            val recorder = Recorder()
            var offset = HEADER_SIZE
            while (offset < image.size) {
                require(image.size - offset >= CHUNK_HEADER_SIZE) { "UEF image is truncated" }
                val id = image.u16(offset)
                val size = image.u16(offset + 2).toLong() or (image.u16(offset + 4).toLong() shl 16)
                val start = offset + CHUNK_HEADER_SIZE
                require(size <= image.size - start) { "UEF chunk ${chunkName(id)} is truncated" }
                recorder.addChunk(id, image, start, size.toInt())
                offset = start + size.toInt()
            }
            return UefTape(recorder.bits.copyOf(recorder.size))
        }

        private fun Recorder.addChunk(id: Int, image: ByteArray, start: Int, size: Int) {
            val end = start + size
            fun need(bytes: Int) = require(size >= bytes) { "UEF chunk ${chunkName(id)} is too short" }
            when (id) {
                0x0100 -> for (i in start until end) addByte(image.u8(i))
                0x0102 -> {
                    need(1)
                    val count = (size - 1) * 8 - image.u8(start)
                    require(count >= 0) { "UEF chunk &0102 has a bad bit count" }
                    for (i in 0 until count) {
                        add(if ((image.u8(start + 1 + i / 8) ushr (i % 8)) and 1 == 1) ONE else ZERO)
                    }
                }
                0x0104 -> {
                    need(3)
                    val dataBits = image.u8(start)
                    val parity = image.u8(start + 1).toChar()
                    // A negative stop count asks for an extra short wave: not modelled.
                    val stopBits = kotlin.math.abs(image[start + 2].toInt())
                    require(dataBits in 1..8 && parity in "NEO" && stopBits in 0..2) {
                        "UEF chunk &0104 has an unknown format"
                    }
                    for (i in start + 3 until end) addByte(image.u8(i), dataBits, parity, stopBits)
                }
                0x0110 -> {
                    need(2)
                    addCarrier(image.u16(start))
                }
                0x0111 -> {
                    need(4)
                    addCarrier(image.u16(start))
                    addByte(0xAA)
                    addCarrier(image.u16(start + 2))
                }
                // Gap length counts half periods of the 1200 Hz base frequency.
                0x0112 -> {
                    need(2)
                    add(SILENCE, (image.u16(start) + 1) / 2)
                }
                0x0116 -> {
                    need(4)
                    val bits = image.u16(start).toLong() or (image.u16(start + 2).toLong() shl 16)
                    val seconds = Float.fromBits(bits.toInt())
                    require(seconds in 0f..MAX_GAP_SECONDS) { "UEF chunk &0116 has a bad gap length" }
                    add(SILENCE, (seconds * BITS_PER_SECOND).toInt())
                }
            }
        }

        private fun ByteArray.u8(offset: Int) = this[offset].toInt() and 0xFF
        private fun ByteArray.u16(offset: Int) = u8(offset) or (u8(offset + 1) shl 8)

        private fun chunkName(id: Int) = "&" + id.toString(16).uppercase().padStart(4, '0')
    }
}
