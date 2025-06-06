package com.gicsports.database

import com.gicsports.metrics.LevelDBStats
import com.gicsports.metrics.LevelDBStats.DbHistogramExt
import org.iq80.leveldb.{DB, ReadOptions, WriteBatch}

class RW(db: DB, readOptions: ReadOptions, batch: WriteBatch) extends ReadOnlyDB(db, readOptions) {
  def put[V](key: Key[V], value: V): Unit = {
    val bytes = key.encode(value)
    LevelDBStats.write.recordTagged(key, bytes)
    batch.put(key.keyBytes, bytes)
  }

  def put(key: Array[Byte], value: Array[Byte]): Unit = batch.put(key, value)

  def update[V](key: Key[V])(f: V => V): Unit = put(key, f(get(key)))

  /** Because of how leveldb batches work, you can increment a specific value only once! */
  def inc(key: Key[Int]): Int = {
    val newValue = get(key) + 1
    put(key, newValue)
    newValue
  }

  def delete(key: Array[Byte], statsKey: String): Unit = batch.delete(key)

  def delete(key: Array[Byte]): Unit = batch.delete(key)

  def delete[V](key: Key[V]): Unit = batch.delete(key.keyBytes)

  def filterHistory(key: Key[Seq[Int]], heightToRemove: Int): Unit = {
    val newValue = get(key).filterNot(_ == heightToRemove)
    if (newValue.nonEmpty) put(key, newValue)
    else delete(key)
  }
}
