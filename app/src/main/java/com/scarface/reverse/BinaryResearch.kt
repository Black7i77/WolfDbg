package com.scarface.reverse

import java.util.Locale

/** Bounded, local-only static binary metadata decoder. No execution, networking, or patching. */
object BinaryResearch {
    private fun u8(b: ByteArray, i: Int): Int = b[i].toInt() and 255
    private fun u16(b: ByteArray, i: Int, le: Boolean = true): Int {
        if (i < 0 || i.toLong() + 2 > b.size) return 0
        return if (le) u8(b, i) or (u8(b, i + 1) shl 8) else (u8(b, i) shl 8) or u8(b, i + 1)
    }
    private fun u32(b: ByteArray, i: Int, le: Boolean = true): Long {
        if (i < 0 || i.toLong() + 4 > b.size) return 0
        var n = 0L
        for (k in 0..3) n = n or ((u8(b, i + k).toLong()) shl (if (le) 8*k else 8*(3-k)))
        return n
    }
    private fun u64(b: ByteArray, i: Int, le: Boolean): Long {
        if (i < 0 || i.toLong() + 8 > b.size) return 0
        var n = 0L
        for (k in 0..7) n = n or (u8(b, i + k).toLong() shl (if (le) 8*k else 8*(7-k)))
        return n
    }
    private fun h(n: Long) = "0x" + java.lang.Long.toUnsignedString(n,16).uppercase(Locale.US)
    private fun isElf(b: ByteArray) = b.size >= 20 && u8(b,0)==127 && b[1]==69.toByte() && b[2]==76.toByte() && b[3]==70.toByte()
    private fun isZip(b: ByteArray) = b.size >= 4 && b[0]==80.toByte() && b[1]==75.toByte() && u8(b,2) in listOf(3,5,7) && u8(b,3) in listOf(4,6,8)

    fun report(b: ByteArray): String = when {
        b.isEmpty() -> "OPEN a file first."
        isElf(b) -> elf(b)
        b.size >= 2 && u16(b,0) == 0x5a4d -> pe(b)
        b.size >= 8 && b.copyOfRange(0,4).contentEquals("dex\n".toByteArray()) -> dex(b)
        isZip(b) -> apk(b)
        else -> "RAW / UNRECOGNISED\nLength: ${b.size} bytes\nFirst bytes: " + b.take(16).joinToString(" ") { "%02X".format(u8(byteArrayOf(it),0)) }
    }
    private fun elf(b: ByteArray): String = buildString {
        val clazz=u8(b,4); val le=u8(b,5)!=2
        append("ELF FILE HEADER\n")
        append("Class: ${if(clazz==2) "64-bit" else if(clazz==1) "32-bit" else "unknown"}\n")
        append("Endian: ${if(le) "little" else "big"}\n")
        append("Type: ${u16(b,16,le)}\nMachine: ${u16(b,18,le)}")
        append(" (${when(u16(b,18,le)){3->"x86";62->"x86_64";40->"ARM";183->"AArch64";243->"RISC-V";else->"other"}})\n")
        if (clazz==2 && b.size>=64) {
            append("Entry: ${h(u64(b,24,le))}\nProgram headers: ${h(u64(b,32,le))}\nSection table: ${h(u64(b,40,le))}\n")
            append("Sections: ${u16(b,60,le)}\n")
        } else if (clazz==1 && b.size>=52) {
            append("Entry: ${h(u32(b,24,le))}\nProgram headers: ${h(u32(b,28,le))}\nSection table: ${h(u32(b,32,le))}\n")
            append("Sections: ${u16(b,48,le)}\n")
        } else append("Truncated ELF header\n")
        append("Static metadata only; no code executed.")
    }
    private fun pe(b: ByteArray): String = buildString {
        append("DOS MZ HEADER\n")
        if(b.size<64){append("Truncated DOS header");return@buildString}
        val off=u32(b,0x3c)
        append("PE header offset: ${h(off)}\n")
        if(off> b.size.toLong()-24 || off>Int.MAX_VALUE){append("PE header outside file");return@buildString}
        val p=off.toInt()
        if(u32(b,p)!=0x00004550L){append("PE signature not found");return@buildString}
        val machine=u16(b,p+4);val sectionCount=u16(b,p+6);val optional=u16(b,p+20)
        append("PE IMAGE\nMachine: ${h(machine.toLong())}\nSections: $sectionCount\n")
        val opt=p+24
        if(opt.toLong()+optional<=b.size && optional>=20){
            val magic=u16(b,opt)
            append("Format: ${if(magic==0x20b) "PE32+" else if(magic==0x10b) "PE32" else "unknown"}\n")
            append("Entry RVA: ${h(u32(b,opt+16))}\n")
        } else append("Optional header truncated\n")
        append("Static metadata only; no code executed.")
    }
    private fun dex(b: ByteArray): String = buildString {
        append("ANDROID DEX HEADER\n")
        if(b.size<112){append("Truncated DEX header");return@buildString}
        append("Version: "); append(String(b,4,3,Charsets.US_ASCII));append('\n')
        append("Declared file size: ${u32(b,32)}\n")
        append("String IDs: ${u32(b,56)}\nType IDs: ${u32(b,64)}\n")
        append("Method IDs: ${u32(b,88)}\nClass definitions: ${u32(b,96)}\n")
        append("Header values are untrusted file metadata.")
    }
    private fun apk(b: ByteArray): String = buildString {
        append("ZIP / APK CENTRAL DIRECTORY\n")
        // Search for the ZIP end-of-central-directory record near EOF, not throughout
        // file contents. Untrusted entries are never decompressed or executed.
        val first = maxOf(0, b.size - (65535 + 22))
        var eocd = -1
        if (b.size >= 22) for (pos in b.size - 22 downTo first) {
            if (u32(b, pos) == 0x06054b50L && pos.toLong() + 22 + u16(b, pos + 20) == b.size.toLong()) {
                eocd = pos
                break
            }
        }
        if (eocd < 0) {
            append("Central directory not found (or unsupported ZIP layout).")
            return@buildString
        }
        val totalEntries = u16(b, eocd + 10)
        val dirSize = u32(b, eocd + 12)
        val dirOffset = u32(b, eocd + 16)
        if (totalEntries == 0xffff || dirOffset == 0xffffffffL || dirSize == 0xffffffffL) {
            append("ZIP64 metadata unsupported in this preview")
            return@buildString
        }
        if (dirOffset + dirSize > eocd.toLong() || dirOffset > Int.MAX_VALUE) {
            append("Invalid central-directory location")
            return@buildString
        }
        var pos = dirOffset.toInt()
        var count = 0
        var manifest = false
        var dex = 0
        var libs = 0
        val displayLimit = 200
        while (count < totalEntries && pos.toLong() + 46 <= eocd.toLong()) {
            if (u32(b, pos) != 0x02014b50L) break
            val nameLength = u16(b, pos + 28)
            val extraLength = u16(b, pos + 30)
            val commentLength = u16(b, pos + 32)
            val next = pos.toLong() + 46 + nameLength + extraLength + commentLength
            if (next > eocd.toLong() || next <= pos) break
            val name = String(b, pos + 46, nameLength, Charsets.UTF_8)
                .replace('\n', ' ').replace('\r', ' ')
            if (name == "AndroidManifest.xml") manifest = true
            if (name.endsWith(".dex")) dex++
            if (name.startsWith("lib/") && name.endsWith(".so")) libs++
            if (count < displayLimit) append(name.take(110)).append('\n')
            count++
            pos = next.toInt()
        }
        append("\nListed: ${minOf(count, displayLimit)} / $count parsed (declared $totalEntries)\n")
        append("Android manifest: ${if (manifest) "present" else "not found"}\n")
        append("DEX files: $dex\nNative libraries: $libs\n")
        append("No entries decompressed or extracted.\n")
        append("Read-only metadata. Some ZIP variants are unsupported.")
    }

    /** AArch64 fixed-width instruction preview. Only decoded instructions are named; unknown words remain .word. */
    fun arm64(b:ByteArray,start:Int, rows:Int=48): String = buildString {
        append("AARCH64 INSTRUCTION PREVIEW (PARTIAL DECODER)\n")
        if(b.isEmpty()){append("Open a file first");return@buildString}
        val aligned=(start.coerceIn(0,b.lastIndex)/4)*4
        val end=minOf(b.size.toLong(), aligned.toLong()+rows*4L).toInt()
        for(i in aligned until end step 4){
            if(i+4>b.size)break
            val w=u32(b,i)
            val s=when {
                w==0xd503201fL -> "nop"
                w==0xd65f03c0L -> "ret"
                w==0xd503205fL -> "wfe"
                w==0xd503207fL -> "wfi"
                (w and 0xfffffc1fL)==0xd65f0000L -> "ret x${(w ushr 5) and 31}"
                (w and 0xfffffc1fL)==0xd61f0000L -> "br x${(w ushr 5) and 31}"
                (w and 0xfffffc1fL)==0xd63f0000L -> "blr x${(w ushr 5) and 31}"
                (w and 0x7c000000L)==0x14000000L -> {
                    val imm=(w and 0x03ffffffL).toInt(); val signed=(imm shl 6) shr 4
                    val address=i.toLong()+signed.toLong(); "${if(w and 0x80000000L !=0L) "bl" else "b"} ${h(address)}"
                }
                else -> ".word ${h(w)}"
            }
            append("%08X  %08X  %s\n".format(Locale.US,i,w,s))
        }
        append("\nOffsets are file offsets, NOT virtual addresses. Decoder is incomplete.")
    }
}
