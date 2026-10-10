package io.github.volovatovta.docviewer.core.model

import java.io.Closeable

public interface ImageStore : Closeable {
    public fun open(imageId: String): ByteArray?

    override fun close() {}

    public object Empty : ImageStore {
        override fun open(imageId: String): ByteArray? = null
    }
}
