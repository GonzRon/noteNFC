package com.loosecannon.servicetag.data.room

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

fun inMemoryDb(): AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>()
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.Default)
    .build()

/**
 * A database on disk. An in-memory database is a single connection, so Room cannot give it the
 * read-only reader pool that makes `withReadTransaction` actually read-only — a test about read
 * transactions has to sit on a file.
 */
fun fileBackedDb(path: java.io.File): AppDatabase = Room
    .databaseBuilder<AppDatabase>(name = path.absolutePath)
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.Default)
    .build()
