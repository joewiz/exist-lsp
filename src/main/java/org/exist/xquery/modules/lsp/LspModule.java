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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.exist.xquery.FunctionDSL.functionDefs;

/**
 * XQuery function module providing Language Server Protocol support.
 *
 * <p>This module exposes eXist-db's XQuery compiler internals as XQuery functions,
 * enabling LSP servers to provide diagnostics, symbol information, and other
 * language intelligence features.</p>
 *
 * <h3>Cursor store configuration</h3>
 * <p>The cursor store used by {@code lsp:eval}/{@code lsp:fetch} can be configured
 * via module parameters in {@code exist.xml}:</p>
 * <ul>
 *   <li>{@code cursor.maximumSize} — max concurrent cursors (default: 100, LRU eviction)</li>
 *   <li>{@code cursor.expireAfterAccess} — inactivity timeout in ms (default: 300000 = 5 min)</li>
 *   <li>{@code cursor.maximumWeight} — max total estimated memory in bytes across all cursors
 *       (default: 0 = unlimited). Each cursor's weight is estimated as item count × 1KB.</li>
 * </ul>
 *
 * <p>Example {@code exist.xml} in the XAR package:</p>
 * <pre>{@code
 * <java>
 *   <namespace>http://exist-db.org/xquery/lsp</namespace>
 *   <class>org.exist.xquery.modules.lsp.LspModule</class>
 *   <parameter name="cursor.maximumSize" value="50"/>
 *   <parameter name="cursor.expireAfterAccess" value="600000"/>
 *   <parameter name="cursor.maximumWeight" value="536870912"/>
 * </java>
 * }</pre>
 *
 * @author eXist-db
 */
public class LspModule extends AbstractInternalModule {

    private static final Logger logger = LogManager.getLogger(LspModule.class);

    public static final String NAMESPACE_URI = "http://exist-db.org/xquery/lsp";

    public static final String PREFIX = "lsp";

    public static final String RELEASE = "0.9.0-SNAPSHOT";

    public static final String PARAM_CURSOR_MAXIMUM_SIZE = "cursor.maximumSize";
    public static final String PARAM_CURSOR_EXPIRE_AFTER_ACCESS = "cursor.expireAfterAccess";
    public static final String PARAM_CURSOR_MAXIMUM_WEIGHT = "cursor.maximumWeight";

    public static final FunctionDef[] functions = functionDefs(
            functionDefs(Close.class,
                    Close.FS_CLOSE),
            functionDefs(Completions.class,
                    Completions.FS_COMPLETIONS),
            functionDefs(Definition.class,
                    Definition.FS_DEFINITION),
            functionDefs(Diagnostics.class,
                    Diagnostics.FS_DIAGNOSTICS),
            functionDefs(Eval.class,
                    Eval.FS_EVAL),
            functionDefs(Fetch.class,
                    Fetch.FS_FETCH),
            functionDefs(Hover.class,
                    Hover.FS_HOVER),
            functionDefs(References.class,
                    References.FS_REFERENCES),
            functionDefs(Symbols.class,
                    Symbols.FS_SYMBOLS)
    );

    public LspModule(final Map<String, List<?>> parameters) {
        super(functions, parameters, true);

        // Configure cursor store from module parameters
        final long maxSize = getLongParam(parameters, PARAM_CURSOR_MAXIMUM_SIZE, 100);
        final long expireMs = getLongParam(parameters, PARAM_CURSOR_EXPIRE_AFTER_ACCESS, 300_000);
        final long maxWeight = getLongParam(parameters, PARAM_CURSOR_MAXIMUM_WEIGHT, 0);

        CursorStore.configure(maxSize, expireMs, maxWeight);
        logger.info("LSP cursor store: maximumSize={}, expireAfterAccess={}ms, maximumWeight={}",
                maxSize, expireMs, maxWeight > 0 ? maxWeight : "unlimited");
    }

    private static long getLongParam(final Map<String, List<?>> parameters,
                                      final String name, final long defaultValue) {
        if (parameters == null) return defaultValue;
        final List<?> values = parameters.get(name);
        if (values == null || values.isEmpty()) return defaultValue;
        try {
            return Long.parseLong(values.get(0).toString());
        } catch (final NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }

    @Override
    public String getDefaultPrefix() {
        return PREFIX;
    }

    @Override
    public String getDescription() {
        return "Functions for Language Server Protocol support, exposing compiler diagnostics and symbol information";
    }

    @Override
    public String getReleaseVersion() {
        return RELEASE;
    }

    static QName qname(final String localPart) {
        return new QName(localPart, NAMESPACE_URI, PREFIX);
    }
}
