package com.gicsports.state

import java.io.File

import com.gicsports.Application
import com.gicsports.account.AddressScheme
import com.gicsports.common.state.ByteStr
import com.gicsports.database.{LevelDBWriter, openDB}
import com.gicsports.lang.directives.DirectiveSet
import com.gicsports.settings.WavesSettings
import com.gicsports.transaction.smart.WavesEnvironment
import com.gicsports.utils.ScorexLogging
import monix.eval.Coeval
import org.iq80.leveldb.DB
import org.openjdk.jmh.annotations.{Param, Scope, State, TearDown}

@State(Scope.Benchmark)
abstract class DBState extends ScorexLogging {
  @Param(Array("waves.conf"))
  var configFile = ""

  lazy val settings: WavesSettings = Application.loadApplicationConfig(Some(new File(configFile)).filter(_.exists()))

  lazy val db: DB = openDB(settings.dbSettings.directory)

  lazy val levelDBWriter: LevelDBWriter =
    LevelDBWriter.readOnly(
      db,
      settings.copy(dbSettings = settings.dbSettings.copy(maxCacheSize = 1))
    )

  AddressScheme.current = new AddressScheme { override val chainId: Byte = 'W' }

  lazy val environment = new WavesEnvironment(
    AddressScheme.current.chainId,
    Coeval.raiseError(new NotImplementedError("`tx` is not implemented")),
    Coeval(levelDBWriter.height),
    levelDBWriter,
    null,
    DirectiveSet.contractDirectiveSet,
    ByteStr.empty
  )

  @TearDown
  def close(): Unit = {
    db.close()
  }
}
