package com.loosecannon.servicetag.data.room

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers

/**
 * [queryContext] defaults to a real thread pool for a test with no dispatcher of its own to offer
 * (a DAO test, a restore proof). A ViewModel or controller fixture that put a test dispatcher on
 * `Dispatchers.Main` should pass a `StandardTestDispatcher` on that *same* `TestCoroutineScheduler`
 * instead: Room's writes then queue on the one virtual clock the test already drives, so a launched
 * save genuinely suspends at its first Room call (the in-flight guard idiom holds) rather than
 * racing a real thread pool or — if Room shared the exact `UnconfinedTestDispatcher` Main uses —
 * running to completion inline before the guard ever sees it in flight.
 */
fun inMemoryDb(queryContext: CoroutineContext = Dispatchers.Default): AppDatabase =
    Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(queryContext)
        .build()

/**
 * A database on disk. An in-memory database is a single connection, so Room cannot give it the
 * read-only reader pool that makes `withReadTransaction` actually read-only — a test about read
 * transactions has to sit on a file.
 */
fun fileBackedDb(path: java.io.File, queryContext: CoroutineContext = Dispatchers.Default): AppDatabase = Room
    .databaseBuilder<AppDatabase>(name = path.absolutePath)
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(queryContext)
    .build()
