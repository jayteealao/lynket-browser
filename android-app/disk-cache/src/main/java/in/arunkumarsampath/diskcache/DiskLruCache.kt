/*
 *
 *  Lynket
 *
 *  Copyright (C) 2022 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package `in`.arunkumarsampath.diskcache

import java.io.BufferedWriter
import java.io.Closeable
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.util.concurrent.Callable
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * A cache that uses a bounded amount of space on a filesystem. Each cache entry has a string key
 * and a fixed number of values. Each key must match the regex `[a-z0-9_-]{1,120}`. Values are byte
 * sequences, accessible as streams or files.
 *
 * The cache stores its data in a directory that must be exclusive to the cache. When the number of
 * stored bytes exceeds the limit, the cache removes entries in the background until the limit is
 * satisfied.
 *
 * Clients call [edit] to create or update the values of an entry. An entry may have only one editor
 * at one time. Every [edit] call must be matched by a call to [Editor.commit] or [Editor.abort].
 * Committing is atomic. Clients call [get] to read a snapshot of an entry.
 *
 * The journal format (file names, header and line format) is the one written by earlier releases,
 * so caches written before this port stay readable:
 *
 * ```
 * libcore.io.DiskLruCache
 * 1
 * 100
 * 2
 *
 * CLEAN 3400330d1dfc7f3f7f4b8d4d803dfcf6 832 21054
 * DIRTY 335c4c6028171cfddfbaae1a9c313c52
 * CLEAN 335c4c6028171cfddfbaae1a9c313c52 3934 2342
 * REMOVE 335c4c6028171cfddfbaae1a9c313c52
 * READ 335c4c6028171cfddfbaae1a9c313c52
 * ```
 */
class DiskLruCache private constructor(
  /** The directory where this cache stores its data. */
  val directory: File,
  private val appVersion: Int,
  private val valueCount: Int,
  private var maxSize: Long,
) : Closeable {

  /** This cache uses a single background thread to evict entries. */
  internal val executorService =
    ThreadPoolExecutor(0, 1, 60L, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>())
  private val journalFile = File(directory, JOURNAL_FILE)
  private val journalFileTmp = File(directory, JOURNAL_FILE_TEMP)
  private val journalFileBackup = File(directory, JOURNAL_FILE_BACKUP)
  private val lruEntries = LinkedHashMap<String, Entry>(0, 0.75f, true)
  private var size: Long = 0
  private var journalWriter: Writer? = null
  private var redundantOpCount = 0

  /**
   * To differentiate between old and current snapshots, each entry is given a sequence number each
   * time an edit is committed. A snapshot is stale if its sequence number is not equal to its
   * entry's sequence number.
   */
  private var nextSequenceNumber: Long = 0

  private val cleanupCallable = Callable<Void?> {
    synchronized(this@DiskLruCache) {
      if (journalWriter == null) {
        return@Callable null // Closed.
      }
      trimToSize()
      if (journalRebuildRequired()) {
        rebuildJournal()
        redundantOpCount = 0
      }
    }
    null
  }

  @Throws(IOException::class)
  private fun readJournal() {
    val reader = StrictLineReader(FileInputStream(journalFile), DiskCacheUtil.US_ASCII)
    val truncated: Boolean
    try {
      val magic = reader.readLine()
      val version = reader.readLine()
      val appVersionString = reader.readLine()
      val valueCountString = reader.readLine()
      val blank = reader.readLine()
      if (MAGIC != magic ||
        VERSION_1 != version ||
        appVersion.toString() != appVersionString ||
        valueCount.toString() != valueCountString ||
        "" != blank
      ) {
        throw IOException(
          "unexpected journal header: [$magic, $version, $valueCountString, $blank]"
        )
      }

      var lineCount = 0
      while (true) {
        try {
          readJournalLine(reader.readLine())
          lineCount++
        } catch (endOfJournal: EOFException) {
          break
        }
      }
      redundantOpCount = lineCount - lruEntries.size
      truncated = reader.hasUnterminatedLine()
    } finally {
      DiskCacheUtil.closeQuietly(reader)
    }

    // Close the reader first: some Windows JDKs cannot rename a file that is open for reading,
    // and rebuildJournal() renames the journal (repro: DiskLruCacheJournalTest's truncated-line
    // test fails on Temurin 21.0.9 for Windows when the reader is still open). A truncated last
    // line means rebuild before appending to it.
    if (truncated) {
      rebuildJournal()
    } else {
      journalWriter = BufferedWriter(
        OutputStreamWriter(FileOutputStream(journalFile, true), DiskCacheUtil.US_ASCII)
      )
    }
  }

  @Throws(IOException::class)
  private fun readJournalLine(line: String) {
    val firstSpace = line.indexOf(' ')
    if (firstSpace == -1) {
      throw IOException("unexpected journal line: $line")
    }

    val keyBegin = firstSpace + 1
    val secondSpace = line.indexOf(' ', keyBegin)
    val key: String
    if (secondSpace == -1) {
      key = line.substring(keyBegin)
      if (firstSpace == REMOVE.length && line.startsWith(REMOVE)) {
        lruEntries.remove(key)
        return
      }
    } else {
      key = line.substring(keyBegin, secondSpace)
    }

    val entry = lruEntries.getOrPut(key) { Entry(key) }

    if (secondSpace != -1 && firstSpace == CLEAN.length && line.startsWith(CLEAN)) {
      // Same split as java.lang.String.split: trailing empty strings are dropped.
      val parts = line.substring(secondSpace + 1).split(" ").dropLastWhile { it.isEmpty() }
        .toTypedArray()
      entry.readable = true
      entry.currentEditor = null
      entry.setLengths(parts)
    } else if (secondSpace == -1 && firstSpace == DIRTY.length && line.startsWith(DIRTY)) {
      entry.currentEditor = Editor(entry)
    } else if (secondSpace == -1 && firstSpace == READ.length && line.startsWith(READ)) {
      // This work was already done by calling lruEntries.get().
    } else {
      throw IOException("unexpected journal line: $line")
    }
  }

  /**
   * Computes the initial size and collects garbage as a part of opening the cache. Dirty entries
   * are assumed to be inconsistent and will be deleted.
   */
  @Throws(IOException::class)
  private fun processJournal() {
    deleteIfExists(journalFileTmp)
    val i = lruEntries.values.iterator()
    while (i.hasNext()) {
      val entry = i.next()
      if (entry.currentEditor == null) {
        for (t in 0 until valueCount) {
          size += entry.lengths[t]
        }
      } else {
        entry.currentEditor = null
        for (t in 0 until valueCount) {
          deleteIfExists(entry.getCleanFile(t))
          deleteIfExists(entry.getDirtyFile(t))
        }
        i.remove()
      }
    }
  }

  /**
   * Creates a new journal that omits redundant information. This replaces the current journal if it
   * exists.
   */
  @Synchronized
  @Throws(IOException::class)
  private fun rebuildJournal() {
    journalWriter?.close()
    BufferedWriter(
      OutputStreamWriter(FileOutputStream(journalFileTmp), DiskCacheUtil.US_ASCII)
    ).use { writer ->
      writer.write(MAGIC)
      writer.write("\n")
      writer.write(VERSION_1)
      writer.write("\n")
      writer.write(appVersion.toString())
      writer.write("\n")
      writer.write(valueCount.toString())
      writer.write("\n")
      writer.write("\n")

      for (entry in lruEntries.values) {
        if (entry.currentEditor != null) {
          writer.write("$DIRTY ${entry.key}\n")
        } else {
          writer.write("$CLEAN ${entry.key}${entry.getLengths()}\n")
        }
      }
    }

    if (journalFile.exists()) {
      renameTo(journalFile, journalFileBackup, true)
    }
    renameTo(journalFileTmp, journalFile, false)
    journalFileBackup.delete()

    journalWriter = BufferedWriter(
      OutputStreamWriter(FileOutputStream(journalFile, true), DiskCacheUtil.US_ASCII)
    )
  }

  /**
   * Returns a snapshot of the entry named [key], or null if it doesn't exist or is not currently
   * readable. If a value is returned, it is moved to the head of the LRU queue.
   */
  @Synchronized
  @Throws(IOException::class)
  operator fun get(key: String): Snapshot? {
    val writer = checkNotClosed()
    validateKey(key)
    val entry = lruEntries[key] ?: return null

    if (!entry.readable) {
      return null
    }

    // Open all streams eagerly to guarantee that we see a single published snapshot. If we opened
    // streams lazily then the streams could come from different edits.
    val ins = ArrayList<InputStream>(valueCount)
    try {
      for (i in 0 until valueCount) {
        ins.add(FileInputStream(entry.getCleanFile(i)))
      }
    } catch (e: FileNotFoundException) {
      // A file must have been deleted manually!
      ins.forEach { DiskCacheUtil.closeQuietly(it) }
      return null
    }

    redundantOpCount++
    writer.append("$READ ").append(key).append('\n')
    if (journalRebuildRequired()) {
      executorService.submit(cleanupCallable)
    }

    return Snapshot(key, entry.sequenceNumber, ins.toTypedArray(), entry.lengths)
  }

  /** Returns an editor for the entry named [key], or null if another edit is in progress. */
  @Throws(IOException::class)
  fun edit(key: String): Editor? = edit(key, ANY_SEQUENCE_NUMBER)

  @Synchronized
  @Throws(IOException::class)
  private fun edit(key: String, expectedSequenceNumber: Long): Editor? {
    val writer = checkNotClosed()
    validateKey(key)
    var entry = lruEntries[key]
    if (expectedSequenceNumber != ANY_SEQUENCE_NUMBER &&
      (entry == null || entry.sequenceNumber != expectedSequenceNumber)
    ) {
      return null // Snapshot is stale.
    }
    if (entry == null) {
      entry = Entry(key)
      lruEntries[key] = entry
    } else if (entry.currentEditor != null) {
      return null // Another edit is in progress.
    }

    val editor = Editor(entry)
    entry.currentEditor = editor

    // Flush the journal before creating files to prevent file leaks.
    writer.write("$DIRTY $key\n")
    writer.flush()
    return editor
  }

  /** Returns the maximum number of bytes that this cache should use to store its data. */
  @Synchronized
  fun getMaxSize(): Long = maxSize

  /**
   * Changes the maximum number of bytes the cache can store and queues a job to trim the existing
   * store, if necessary.
   */
  @Synchronized
  fun setMaxSize(maxSize: Long) {
    this.maxSize = maxSize
    executorService.submit(cleanupCallable)
  }

  /**
   * Returns the number of bytes currently being used to store the values in this cache. This may be
   * greater than the max size if a background deletion is pending.
   */
  @Synchronized
  fun size(): Long = size

  @Synchronized
  @Throws(IOException::class)
  private fun completeEdit(editor: Editor, success: Boolean) {
    val entry = editor.entry
    check(entry.currentEditor === editor)

    // If this edit is creating the entry for the first time, every index must have a value.
    if (success && !entry.readable) {
      for (i in 0 until valueCount) {
        if (!editor.written!![i]) {
          editor.abort()
          throw IllegalStateException("Newly created entry didn't create value for index $i")
        }
        if (!entry.getDirtyFile(i).exists()) {
          editor.abort()
          return
        }
      }
    }

    for (i in 0 until valueCount) {
      val dirty = entry.getDirtyFile(i)
      if (success) {
        if (dirty.exists()) {
          val clean = entry.getCleanFile(i)
          dirty.renameTo(clean)
          val oldLength = entry.lengths[i]
          val newLength = clean.length()
          entry.lengths[i] = newLength
          size = size - oldLength + newLength
        }
      } else {
        deleteIfExists(dirty)
      }
    }

    redundantOpCount++
    entry.currentEditor = null
    val writer = checkNotClosed()
    if (entry.readable || success) {
      entry.readable = true
      writer.write("$CLEAN ${entry.key}${entry.getLengths()}\n")
      if (success) {
        entry.sequenceNumber = nextSequenceNumber++
      }
    } else {
      lruEntries.remove(entry.key)
      writer.write("$REMOVE ${entry.key}\n")
    }
    writer.flush()

    if (size > maxSize || journalRebuildRequired()) {
      executorService.submit(cleanupCallable)
    }
  }

  /**
   * We only rebuild the journal when it will halve the size of the journal and eliminate at least
   * 2000 ops.
   */
  private fun journalRebuildRequired(): Boolean {
    val redundantOpCompactThreshold = 2000
    return redundantOpCount >= redundantOpCompactThreshold &&
      redundantOpCount >= lruEntries.size
  }

  /**
   * Drops the entry for [key] if it exists and can be removed. Entries actively being edited cannot
   * be removed.
   *
   * @return true if an entry was removed.
   */
  @Synchronized
  @Throws(IOException::class)
  fun remove(key: String): Boolean {
    val writer = checkNotClosed()
    validateKey(key)
    val entry = lruEntries[key]
    if (entry == null || entry.currentEditor != null) {
      return false
    }

    for (i in 0 until valueCount) {
      val file = entry.getCleanFile(i)
      if (file.exists() && !file.delete()) {
        throw IOException("failed to delete $file")
      }
      size -= entry.lengths[i]
      entry.lengths[i] = 0
    }

    redundantOpCount++
    writer.append("$REMOVE ").append(key).append('\n')
    lruEntries.remove(key)

    if (journalRebuildRequired()) {
      executorService.submit(cleanupCallable)
    }

    return true
  }

  /** Returns true if this cache has been closed. */
  @Synchronized
  fun isClosed(): Boolean = journalWriter == null

  private fun checkNotClosed(): Writer = journalWriter ?: throw IllegalStateException("cache is closed")

  /** Force buffered operations to the filesystem. */
  @Synchronized
  @Throws(IOException::class)
  fun flush() {
    val writer = checkNotClosed()
    trimToSize()
    writer.flush()
  }

  /** Closes this cache. Stored values will remain on the filesystem. */
  @Synchronized
  @Throws(IOException::class)
  override fun close() {
    if (journalWriter == null) {
      return // Already closed.
    }
    for (entry in ArrayList(lruEntries.values)) {
      entry.currentEditor?.abort()
    }
    trimToSize()
    journalWriter?.close()
    journalWriter = null
  }

  @Throws(IOException::class)
  private fun trimToSize() {
    while (size > maxSize) {
      val toEvict = lruEntries.entries.iterator().next()
      remove(toEvict.key)
    }
  }

  /**
   * Closes the cache and deletes all of its stored values. This will delete all files in the cache
   * directory including files that weren't created by the cache.
   */
  @Throws(IOException::class)
  fun delete() {
    close()
    DiskCacheUtil.deleteContents(directory)
  }

  private fun validateKey(key: String) {
    val matcher = LEGAL_KEY_PATTERN.matcher(key)
    require(matcher.matches()) { "keys must match regex $STRING_KEY_PATTERN: \"$key\"" }
  }

  /** A snapshot of the values for an entry. */
  inner class Snapshot internal constructor(
    private val key: String,
    private val sequenceNumber: Long,
    private val ins: Array<InputStream>,
    private val lengths: LongArray,
  ) : Closeable {

    /**
     * Returns an editor for this snapshot's entry, or null if either the entry has changed since
     * this snapshot was created or if another edit is in progress.
     */
    @Throws(IOException::class)
    fun edit(): Editor? = this@DiskLruCache.edit(key, sequenceNumber)

    /** Returns the unbuffered stream with the value for [index]. */
    fun getInputStream(index: Int): InputStream = ins[index]

    /** Returns the string value for [index]. */
    @Throws(IOException::class)
    fun getString(index: Int): String = inputStreamToString(getInputStream(index))

    /** Returns the byte length of the value for [index]. */
    fun getLength(index: Int): Long = lengths[index]

    override fun close() {
      for (stream in ins) {
        DiskCacheUtil.closeQuietly(stream)
      }
    }
  }

  /** Edits the values for an entry. */
  inner class Editor internal constructor(internal val entry: Entry) {
    internal val written: BooleanArray? = if (entry.readable) null else BooleanArray(valueCount)
    private var hasErrors = false
    private var committed = false

    /**
     * Returns an unbuffered input stream to read the last committed value, or null if no value has
     * been committed.
     */
    @Throws(IOException::class)
    fun newInputStream(index: Int): InputStream? {
      synchronized(this@DiskLruCache) {
        check(entry.currentEditor === this)
        if (!entry.readable) {
          return null
        }
        return try {
          FileInputStream(entry.getCleanFile(index))
        } catch (e: FileNotFoundException) {
          null
        }
      }
    }

    /** Returns the last committed value as a string, or null if no value has been committed. */
    @Throws(IOException::class)
    fun getString(index: Int): String? = newInputStream(index)?.let { inputStreamToString(it) }

    /**
     * Returns a new unbuffered output stream to write the value at [index]. If the underlying output
     * stream encounters errors when writing to the filesystem, this edit will be aborted when
     * [commit] is called. The returned output stream does not throw IOExceptions.
     */
    @Throws(IOException::class)
    fun newOutputStream(index: Int): OutputStream {
      require(index in 0 until valueCount) {
        "Expected index $index to be greater than 0 and less than the maximum value count of $valueCount"
      }
      synchronized(this@DiskLruCache) {
        check(entry.currentEditor === this)
        if (!entry.readable) {
          written!![index] = true
        }
        val dirtyFile = entry.getDirtyFile(index)
        val outputStream = try {
          FileOutputStream(dirtyFile)
        } catch (e: FileNotFoundException) {
          // Attempt to recreate the cache directory.
          directory.mkdirs()
          try {
            FileOutputStream(dirtyFile)
          } catch (e2: FileNotFoundException) {
            // We are unable to recover. Silently eat the writes.
            return NULL_OUTPUT_STREAM
          }
        }
        return FaultHidingOutputStream(outputStream)
      }
    }

    /** Sets the value at [index] to [value]. */
    @Throws(IOException::class)
    fun set(index: Int, value: String) {
      var writer: Writer? = null
      try {
        writer = OutputStreamWriter(newOutputStream(index), DiskCacheUtil.UTF_8)
        writer.write(value)
      } finally {
        DiskCacheUtil.closeQuietly(writer)
      }
    }

    /**
     * Commits this edit so it is visible to readers. This releases the edit lock so another edit
     * may be started on the same key.
     */
    @Throws(IOException::class)
    fun commit() {
      if (hasErrors) {
        completeEdit(this, false)
        remove(entry.key) // The previous entry is stale.
      } else {
        completeEdit(this, true)
      }
      committed = true
    }

    /**
     * Aborts this edit. This releases the edit lock so another edit may be started on the same key.
     */
    @Throws(IOException::class)
    fun abort() {
      completeEdit(this, false)
    }

    fun abortUnlessCommitted() {
      if (!committed) {
        try {
          abort()
        } catch (ignored: IOException) {
        }
      }
    }

    private inner class FaultHidingOutputStream(out: OutputStream) : FilterOutputStream(out) {
      override fun write(oneByte: Int) {
        try {
          out.write(oneByte)
        } catch (e: IOException) {
          hasErrors = true
        }
      }

      override fun write(buffer: ByteArray, offset: Int, length: Int) {
        try {
          out.write(buffer, offset, length)
        } catch (e: IOException) {
          hasErrors = true
        }
      }

      override fun close() {
        try {
          out.close()
        } catch (e: IOException) {
          hasErrors = true
        }
      }

      override fun flush() {
        try {
          out.flush()
        } catch (e: IOException) {
          hasErrors = true
        }
      }
    }
  }

  internal inner class Entry(val key: String) {

    /** Lengths of this entry's files. */
    val lengths = LongArray(valueCount)

    /** True if this entry has ever been published. */
    var readable = false

    /** The ongoing edit or null if this entry is not being edited. */
    var currentEditor: Editor? = null

    /** The sequence number of the most recently committed edit to this entry. */
    var sequenceNumber: Long = 0

    fun getLengths(): String {
      val result = StringBuilder()
      for (size in lengths) {
        result.append(' ').append(size)
      }
      return result.toString()
    }

    /** Set lengths using decimal numbers like "10123". */
    @Throws(IOException::class)
    fun setLengths(strings: Array<String>) {
      if (strings.size != valueCount) {
        throw invalidLengths(strings)
      }
      try {
        for (i in strings.indices) {
          lengths[i] = strings[i].toLong()
        }
      } catch (e: NumberFormatException) {
        throw invalidLengths(strings)
      }
    }

    private fun invalidLengths(strings: Array<String>): IOException =
      IOException("unexpected journal line: ${strings.contentToString()}")

    fun getCleanFile(i: Int): File = File(directory, "$key.$i")

    fun getDirtyFile(i: Int): File = File(directory, "$key.$i.tmp")
  }

  companion object {
    internal const val JOURNAL_FILE = "journal"
    internal const val JOURNAL_FILE_TEMP = "journal.tmp"
    internal const val JOURNAL_FILE_BACKUP = "journal.bkp"
    internal const val MAGIC = "libcore.io.DiskLruCache"
    internal const val VERSION_1 = "1"
    internal const val ANY_SEQUENCE_NUMBER: Long = -1
    internal const val STRING_KEY_PATTERN = "[a-z0-9_-]{1,120}"
    internal val LEGAL_KEY_PATTERN: Pattern = Pattern.compile(STRING_KEY_PATTERN)
    private const val CLEAN = "CLEAN"
    private const val DIRTY = "DIRTY"
    private const val REMOVE = "REMOVE"
    private const val READ = "READ"

    private val NULL_OUTPUT_STREAM: OutputStream = object : OutputStream() {
      override fun write(b: Int) {
        // Eat all writes silently.
      }
    }

    /**
     * Opens the cache in [directory], creating a cache if none exists there.
     *
     * @param directory a writable directory
     * @param valueCount the number of values per cache entry. Must be positive.
     * @param maxSize the maximum number of bytes this cache should use to store
     * @throws IOException if reading or writing the cache directory fails
     */
    @JvmStatic
    @Throws(IOException::class)
    fun open(directory: File, appVersion: Int, valueCount: Int, maxSize: Long): DiskLruCache {
      require(maxSize > 0) { "maxSize <= 0" }
      require(valueCount > 0) { "valueCount <= 0" }

      // If a bkp file exists, use it instead.
      val backupFile = File(directory, JOURNAL_FILE_BACKUP)
      if (backupFile.exists()) {
        val journalFile = File(directory, JOURNAL_FILE)
        // If journal file also exists just delete backup file.
        if (journalFile.exists()) {
          backupFile.delete()
        } else {
          renameTo(backupFile, journalFile, false)
        }
      }

      // Prefer to pick up where we left off.
      var cache = DiskLruCache(directory, appVersion, valueCount, maxSize)
      if (cache.journalFile.exists()) {
        try {
          cache.readJournal()
          cache.processJournal()
          return cache
        } catch (journalIsCorrupt: IOException) {
          println("DiskLruCache $directory is corrupt: ${journalIsCorrupt.message}, removing")
          cache.delete()
        }
      }

      // Create a new empty cache.
      directory.mkdirs()
      cache = DiskLruCache(directory, appVersion, valueCount, maxSize)
      cache.rebuildJournal()
      return cache
    }

    @Throws(IOException::class)
    private fun deleteIfExists(file: File) {
      if (file.exists() && !file.delete()) {
        throw IOException()
      }
    }

    @Throws(IOException::class)
    private fun renameTo(from: File, to: File, deleteDestination: Boolean) {
      if (deleteDestination) {
        deleteIfExists(to)
      }
      if (!from.renameTo(to)) {
        throw IOException()
      }
    }

    @Throws(IOException::class)
    private fun inputStreamToString(input: InputStream): String =
      DiskCacheUtil.readFully(InputStreamReader(input, DiskCacheUtil.UTF_8))
  }
}
