package com.archon.python;

import java.util.Set;

/**
 * Standard library module names for Python 3.10+.
 *
 * <p>Used to filter external dependencies from the dependency graph.
 * Python's standard library includes ~300 modules. This set contains
 * the majority of commonly used modules from the standard library.
 *
 * <p>Module list sourced from Python 3.10 standard library documentation.
 */
public class PythonStdlib {

    /**
     * Standard library modules for Python 3.10+.
     * Includes the majority of the ~300 modules in Python's standard library.
     */
    private static final Set<String> STDLIB_MODULES = Set.of(
        // Core built-in modules
        "__future__", "__main__", "_ast", "_thread", "abc", "argparse", "array",
        "ast", "asynchat", "asyncio", "asyncio.streams", "asyncore", "atexit",
        "auditevents", "audioop", "aifc", "antigravity",

        "base64", "bdb", "binascii", "binhex", "bisect", "builtins", "bz2",

        "calendar", "cgi", "cgitb", "chunk", "cmath", "cmd", "code", "codecs",
        "codeop", "collections", "collections.abc", "colorsys", "compileall",
        "concurrent", "concurrent.futures", "configparser", "contextlib",
        "contextvars", "copy", "copyreg", "cProfile", "crypt", "cryptolib",
        "csv", "ctypes", "ctypes.util", "ctypes.wintypes", "curses",
        "curses.ascii", "curses.panel", "curses.textpad",

        "dataclasses", "datetime", "dbm", "dbm.dumb", "dbm.gnu", "dbm.ndbm",
        "decimal", "difflib", "dis", "distutils", "distutils.command",
        "distutils.core", "distutils.extension", "doctest",

        "email", "email.generator", "email.header", "email.message", "email.mime",
        "email.mime.multipart", "email.mime.text", "email.parser", "email.policy",
        "encodings", "encodings.aliases", "encodings.ascii",
        "encodings.base64_codec", "encodings.bz2_codec", "encodings.hex_codec",
        "encodings.idna", "encodings.punycode", "encodings.quopri_codec",
        "encodings.raw_unicode_escape", "encodings.rot_13",
        "encodings.string_escape", "encodings.unicode_escape",
        "encodings.unicode_internal", "encodings.utf_8_sig", "enum", "errno",
        "exit",

        "faulthandler", "fcntl", "filecmp", "fileinput", "fnmatch", "formatter",
        "fractions", "ftplib", "functools",

        "gc", "getopt", "getpass", "gettext", "glob", "graphlib", "grp", "gzip",

        "hashlib", "heapq", "hmac", "html", "html.entities", "html.parser",
        "http", "http.client", "http.cookies", "http.cookiejar", "http.server",

        "imghdr", "imaplib", "imp", "importlib", "importlib.abc",
        "importlib.machinery", "importlib.metadata", "importlib.resources",
        "importlib.util", "inspect", "io", "ipaddress", "itertools",

        "json",

        "keyword",

        "lib2to3", "linecache", "locale", "logging", "logging.config",
        "logging.handlers", "lzma",

        "mailbox", "mailcap", "marshal", "math", "mimetypes", "mmap",
        "modulefinder", "msilib", "msvcrt", "multiprocessing",
        "multiprocessing.managers", "multiprocessing.pool",
        "multiprocessing.shared_memory",

        "netrc", "nis", "nntplib", "numbers",

        "objgraph", "operator", "optparse", "os", "os.path", "ossaudiodev",

        "pathlib", "pdb", "pickle", "pickletools", "pipes", "pkgutil", "platform",
        "plistlib", "poplib", "posix", "posixpath", "pprint", "profile", "pstats",
        "pty", "pwd", "py_compile", "pyclbr", "pydoc",

        "queue", "quopri",

        "random", "re", "readline", "reprlib", "resource", "rlcompleter",
        "runpy",

        "sched", "secrets", "select", "selectors", "shelve", "shlex", "shutil",
        "signal", "site", "smtpd", "smtplib", "sndhdr", "socket", "socketserver",
        "spwd", "sqlite3", "ssl", "stat", "statistics", "string", "stringprep",
        "struct", "subprocess", "sunau", "symbol", "symtable", "sys", "sysconfig",

        "tabnanny", "tarfile", "telnetlib", "tempfile", "termios", "test",
        "test.support", "test.support.script_helper", "textwrap", "thread",
        "threading", "time", "timeit", "tkinter", "tkinter.colorchooser",
        "tkinter.constants", "tkinter.dialog", "tkinter.dnd", "tkinter.filedialog",
        "tkinter.font", "tkinter.messagebox", "tkinter.scrolledtext",
        "tkinter.simpledialog", "tkinter.tix", "tkinter.ttk", "token", "tokenize",
        "tomllib", "trace", "traceback", "tracemalloc", "tty", "turtle",
        "turtledemo", "types", "typing", "typing_extensions",

        "uu", "unicodedata", "unittest", "unittest.mock", "urllib",
        "urllib.error", "urllib.parse", "urllib.request", "urllib.response",
        "urllib.robotparser",

        "uuid",

        "venv",

        "warnings", "wave", "weakref", "webbrowser", "winreg", "winsound",
        "wsgiref",

        "xdrlib", "xml", "xml.dom", "xml.dom.minidom", "xml.dom.pulldom",
        "xml.etree", "xml.etree.ElementTree", "xml.parsers", "xml.parsers.expat",
        "xml.sax", "xmlrpc", "xmlrpc.client", "xmlrpc.server",

        "zipapp", "zipfile", "zipimport", "zlib", "zoneinfo"
    );

    /**
     * Checks if a module name belongs to Python's standard library.
     *
     * @param moduleName the module name to check (e.g., "os", "sys.path", "numpy")
     * @return true if the module is in the standard library, false otherwise
     */
    public static boolean isStdlib(String moduleName) {
        if (moduleName == null || moduleName.isEmpty()) {
            return false;
        }

        // Extract base module (before first dot)
        // "sys.path" -> "sys", "os.path" -> "os"
        String baseModule = moduleName.split("\\.")[0].toLowerCase();

        return STDLIB_MODULES.contains(baseModule);
    }
}
