package com.batmudcn.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for persistent translation cache.
 * Mirrors Python PersistentCache SQLite schema.
 */
@Entity(
    tableName = "translations",
    indices = [Index(value = ["key"], unique = true)]
)
data class TranslationEntity(
    @PrimaryKey
    val key: String,        // normalized source text (or hashed key for long text)
    val value: String,      // translated text
)
