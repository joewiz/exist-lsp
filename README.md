# exist-lsp

[![License][license-img]][license-url]
[![GitHub release][release-img]][release-url]

Language Server Protocol support functions for eXist-db XQuery development

A new Java extension module that exposes eXist-db's XQuery compiler internals as XQuery functions, enabling LSP servers and editors to provide diagnostics, symbol information, completions, hover, go-to-definition, and find-all-references for XQuery code.

This module was originally proposed as part of exist-core ([PR #6130](https://github.com/eXist-db/exist/pull/6130)). Core developers requested it be extracted into a standalone EXPath package to decouple LSP releases from eXist-db releases. There is no predecessor module — the namespace `http://exist-db.org/xquery/lsp` is new.

## Requirements

- [eXist-db](https://exist-db.org) version `7.0.0` or greater
- [Java](https://adoptium.net/) version `21` or greater (for building from source)
- [Maven](https://maven.apache.org/) version `3.9` or greater (for building from source)

## Installation

1. Download the `exist-lsp-0.9.0-SNAPSHOT.xar` file from the GitHub [releases](https://github.com/joewiz/exist-lsp/releases) page.

2. Install it via the [dashboard](http://localhost:8080/exist/apps/dashboard/index.html) package manager, or use the [xst](https://www.npmjs.com/package/@existdb/xst) command-line tool:

```bash
xst package install exist-lsp-0.9.0-SNAPSHOT.xar
```

### Building from source

Building requires eXist-db `7.0.0-SNAPSHOT` installed in your local Maven repository. The published snapshot on the [eXist-db Maven repository](https://repo.exist-db.org) is stale (June 2024) and predates API changes on `develop` that this module depends on.

To install the required artifacts locally, clone and build eXist-db first:

```bash
git clone https://github.com/eXist-db/exist
cd exist
mvn -T1.5C clean install -DskipTests -Ddocker=false
```

Then build the XAR:

```bash
git clone https://github.com/joewiz/exist-lsp
cd exist-lsp
mvn clean package -DskipTests
```

The XAR package will be at `target/exist-lsp-0.9.0-SNAPSHOT.xar`.

> **Note:** CI works around the stale Maven snapshot by extracting the `exist.uber.jar` from the [`existdb/existdb:latest`](https://hub.docker.com/r/existdb/existdb) Docker image (built nightly from `develop`) and installing it into the local Maven repository before compilation.

## API

All functions are in the `http://exist-db.org/xquery/lsp` namespace (default prefix: `lsp`).

```xquery
import module namespace lsp = "http://exist-db.org/xquery/lsp";
```

### lsp:diagnostics

Compiles an XQuery expression and returns an array of diagnostic maps. Returns an empty array if compilation succeeds.

```xquery
lsp:diagnostics($expression as xs:string) as array(*)
lsp:diagnostics($expression as xs:string, $module-load-path as xs:string?) as array(*)
```

Each map contains:

| Key | Type | Description |
| --- | --- | --- |
| `line` | `xs:integer` | 0-based line number |
| `column` | `xs:integer` | 0-based column number |
| `severity` | `xs:integer` | LSP DiagnosticSeverity (1=error) |
| `code` | `xs:string` | W3C error code (e.g., `XPST0003`) |
| `message` | `xs:string` | Human-readable error description |

```xquery
lsp:diagnostics("let $x := 1 retrun $x")
(: => [ map { "line": 0, "column": 12, "severity": 1,
              "code": "XPST0003", "message": "..." } ] :)
```

### lsp:symbols

Extracts document symbols (function and variable declarations) from a compiled XQuery expression.

```xquery
lsp:symbols($expression as xs:string) as array(*)
lsp:symbols($expression as xs:string, $module-load-path as xs:string?) as array(*)
```

Each map contains:

| Key | Type | Description |
| --- | --- | --- |
| `name` | `xs:string` | Symbol name (e.g., `local:greet#1`, `$local:x`) |
| `kind` | `xs:integer` | LSP SymbolKind (12=Function, 13=Variable) |
| `line` | `xs:integer` | 0-based start line |
| `column` | `xs:integer` | 0-based start column |
| `detail` | `xs:string` | Signature or type information |

### lsp:completions

Returns completion items available in the context of an XQuery expression, including built-in functions, keywords, and user-declared symbols.

```xquery
lsp:completions($expression as xs:string) as array(*)
lsp:completions($expression as xs:string, $module-load-path as xs:string?) as array(*)
```

Each map contains:

| Key | Type | Description |
| --- | --- | --- |
| `label` | `xs:string` | Display text (e.g., `fn:count#1`) |
| `kind` | `xs:integer` | LSP CompletionItemKind (3=Function, 6=Variable, 14=Keyword) |
| `detail` | `xs:string` | Signature or type info |
| `documentation` | `xs:string` | Function description |
| `insertText` | `xs:string` | Text to insert (e.g., `fn:count()`) |

### lsp:hover

Returns hover information for the symbol at a given position.

```xquery
lsp:hover($expression as xs:string, $line as xs:integer, $column as xs:integer) as map(*)?
lsp:hover($expression as xs:string, $line as xs:integer, $column as xs:integer,
          $module-load-path as xs:string?) as map(*)?
```

Returns a map with `contents` (signature and documentation) and `kind` (`"function"` or `"variable"`), or an empty sequence if no symbol is found.

### lsp:definition

Returns the definition location of the symbol at a given position.

```xquery
lsp:definition($expression as xs:string, $line as xs:integer, $column as xs:integer) as map(*)?
lsp:definition($expression as xs:string, $line as xs:integer, $column as xs:integer,
               $module-load-path as xs:string?) as map(*)?
```

Returns a map with `line`, `column`, `name`, `kind`, and optionally `uri` (for cross-module definitions), or an empty sequence.

### lsp:references

Finds all references to the symbol at a given position, including the declaration.

```xquery
lsp:references($expression as xs:string, $line as xs:integer, $column as xs:integer) as array(*)
lsp:references($expression as xs:string, $line as xs:integer, $column as xs:integer,
               $module-load-path as xs:string?) as array(*)
```

Each map contains `line`, `column`, `name`, and `kind`.

## Module load path

All functions accept an optional `$module-load-path` parameter that controls how `import module` statements are resolved:

- `"xmldb:exist:///db"` or `"/db"` — resolve imports from the database
- A filesystem path — resolve imports from the filesystem
- `()` or omitted — use the default module load path

## License

GNU-LGPL v2.1 © [The eXist-db Authors](https://github.com/eXist-db/exist-lsp)

[license-img]: https://img.shields.io/badge/license-LGPL%20v2.1-blue.svg
[license-url]: https://www.gnu.org/licenses/lgpl-2.1
[release-img]: https://img.shields.io/badge/release-0.9.0--SNAPSHOT-yellow.svg
[release-url]: https://github.com/joewiz/exist-lsp
