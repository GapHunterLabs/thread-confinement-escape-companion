package dev.gaphunter.threadconfinementescapecompanion.inspection

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ThreadConfinementEscapeInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(ThreadConfinementEscapeInspection::class.java)
    }

    fun `test a list captured by executor submit and touched afterward unsynchronized is flagged`() {
        myFixture.configureByText(
            "Worker1.java",
            """
            import java.util.ArrayList;
            import java.util.List;
            import java.util.concurrent.ExecutorService;

            class Worker1 {
                void run(ExecutorService pool) {
                    List<String> results = new ArrayList<>();
                    pool.submit(() -> {
                        results.add("x");
                    });
                    results.add("y");
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.any { it.description?.contains("CWE-362") == true })
    }

    fun `test a list captured by new Thread start and touched afterward unsynchronized is flagged`() {
        myFixture.configureByText(
            "Worker2.java",
            """
            import java.util.HashMap;
            import java.util.Map;

            class Worker2 {
                void run() {
                    Map<String, String> cache = new HashMap<>();
                    new Thread(() -> {
                        cache.put("k", "v");
                    }).start();
                    cache.get("k");
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.any { it.description?.contains("CWE-362") == true })
    }

    fun `test post hand-off access wrapped in synchronized is not flagged`() {
        myFixture.configureByText(
            "Worker3.java",
            """
            import java.util.ArrayList;
            import java.util.List;
            import java.util.concurrent.ExecutorService;

            class Worker3 {
                void run(ExecutorService pool) {
                    List<String> results = new ArrayList<>();
                    pool.submit(() -> {
                        synchronized (results) {
                            results.add("x");
                        }
                    });
                    synchronized (results) {
                        results.add("y");
                    }
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("CWE-362") == true })
    }

    fun `test no access after the hand-off at all is not flagged`() {
        myFixture.configureByText(
            "Worker4.java",
            """
            import java.util.ArrayList;
            import java.util.List;
            import java.util.concurrent.ExecutorService;

            class Worker4 {
                void run(ExecutorService pool) {
                    List<String> results = new ArrayList<>();
                    pool.submit(() -> {
                        results.add("x");
                    });
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("CWE-362") == true })
    }

    fun `test a Map declared type backed by a ConcurrentHashMap initializer is not flagged`() {
        myFixture.configureByText(
            "Worker6.java",
            """
            import java.util.Map;
            import java.util.concurrent.ConcurrentHashMap;
            import java.util.concurrent.ExecutorService;

            class Worker6 {
                void run(ExecutorService pool) {
                    Map<String, String> cache = new ConcurrentHashMap<>();
                    pool.submit(() -> {
                        cache.put("k", "v");
                    });
                    cache.get("k");
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("CWE-362") == true })
    }

    fun `test a List backed by Collections synchronizedList is not flagged`() {
        myFixture.configureByText(
            "Worker7.java",
            """
            import java.util.ArrayList;
            import java.util.Collections;
            import java.util.List;
            import java.util.concurrent.ExecutorService;

            class Worker7 {
                void run(ExecutorService pool) {
                    List<String> results = Collections.synchronizedList(new ArrayList<>());
                    pool.submit(() -> {
                        results.add("x");
                    });
                    results.add("y");
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("CWE-362") == true })
    }

    fun `test a non-mutable local type is out of this plugin's scope`() {
        myFixture.configureByText(
            "Worker5.java",
            """
            import java.util.concurrent.ExecutorService;

            class Worker5 {
                void run(ExecutorService pool) {
                    String label = "x";
                    pool.submit(() -> {
                        System.out.println(label);
                    });
                    System.out.println(label);
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("CWE-362") == true })
    }
}
