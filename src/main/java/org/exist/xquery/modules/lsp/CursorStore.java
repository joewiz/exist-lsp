/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.modules.lsp;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;

import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;

/**
 * Server-side cursor store for query results.
 *
 * <p>Holds live {@link Sequence} references in a Caffeine cache, allowing
 * clients to paginate through query results without re-executing the query
 * or serializing the entire result set upfront.</p>
 *
 * <p>Cursors expire after 5 minutes of inactivity and the store holds at
 * most 100 concurrent cursors. Evicted sequences are released for GC.</p>
 *
 * <p>This is a singleton shared across all XQuery contexts within the
 * same eXist-db instance.</p>
 */
public final class CursorStore {

    private static final Logger logger = LogManager.getLogger(CursorStore.class);

    private static final long EXPIRE_AFTER_ACCESS_MINUTES = 5;
    private static final long MAXIMUM_SIZE = 100;

    private static final CursorStore INSTANCE = new CursorStore();

    private final Cache<String, CursorEntry> store;

    private CursorStore() {
        store = Caffeine.newBuilder()
                .expireAfterAccess(EXPIRE_AFTER_ACCESS_MINUTES, TimeUnit.MINUTES)
                .maximumSize(MAXIMUM_SIZE)
                .removalListener((key, value, cause) -> {
                        logger.debug("Cursor {} evicted ({})", key, cause);
                        if (value instanceof CursorEntry entry && entry.evalContext() != null) {
                            entry.evalContext().runCleanupTasks();
                        }
                })
                .build();
    }

    public static CursorStore getInstance() {
        return INSTANCE;
    }

    /**
     * Store a result sequence and return the cursor ID.
     * The evalContext is kept alive so that node references in the sequence
     * remain valid across fetch requests.
     */
    public String put(final String cursorId, final Sequence result, final int itemCount,
                      final XQueryContext evalContext) {
        store.put(cursorId, new CursorEntry(result, itemCount, evalContext));
        logger.debug("Cursor {} stored ({} items)", cursorId, itemCount);
        return cursorId;
    }

    /**
     * Retrieve a cursor entry by ID.
     */
    @Nullable
    public CursorEntry get(final String cursorId) {
        return store.getIfPresent(cursorId);
    }

    /**
     * Explicitly remove a cursor (client-initiated close).
     */
    public void remove(final String cursorId) {
        store.invalidate(cursorId);
        logger.debug("Cursor {} closed", cursorId);
    }

    /**
     * A cached cursor entry holding the result sequence, metadata,
     * and the eval context (kept alive so node references remain valid).
     */
    public record CursorEntry(Sequence result, int itemCount, XQueryContext evalContext) {
    }
}
