import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

class Worker {
    void run(ExecutorService pool) {
        List<String> results = new ArrayList<>();
        pool.submit(() -> {
            results.add("x");
        });
        results.add("y");
    }
}
